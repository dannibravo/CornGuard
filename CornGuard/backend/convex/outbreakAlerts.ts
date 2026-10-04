import { v } from "convex/values";
import { internal } from "./_generated/api";
import { Doc, Id } from "./_generated/dataModel";
import { MutationCtx, QueryCtx, internalMutation } from "./_generated/server";
import { BARANGAY_NEIGHBORS } from "./barangayNeighbors";

/**
 * Outbreak push alerts, ported from caps 3 (my-app/convex/notifications.sendOutbreakAlert), with an
 * admin in the loop: when a barangay + disease first turns "severe" (barangayStats.recompute) the
 * outbreak is queued as "pending" (queueOutbreak). Nothing is sent until an admin approves it in the
 * admin website (admin.approveOutbreak -> sendOutbreak); admins can also raise one manually.
 *
 * Recipients: every active farmer whose profile is in that barangay or a bordering one. Profiles
 * hold free-typed names ("Valencia") while outbreaks use the boundary file's official ones
 * ("City of Valencia"), so both sides are compared in a normalised form.
 */

export const DISEASE_LABEL: Record<string, string> = {
  northern_leaf_blight: "Northern Leaf Blight",
  common_rust: "Common Rust",
  gray_leaf_spot: "Gray Leaf Spot",
};

const clean = (s: string) =>
  s.toLowerCase().replace(/\(pob\.?\)|\(capital\)/g, " ").replace(/[^a-z0-9ñ]+/g, " ").trim();

const normalizeMunicipality = (s: string) =>
  clean(s).replace(/^city of /, "").replace(/ city$/, "").trim();

/** Normalised "barangay|municipality" key used to match profiles against boundary names. */
export const areaKey = (barangay: string, municipality: string) =>
  `${clean(barangay)}|${normalizeMunicipality(municipality)}`;

/** True if the barangay exists in the bundled Bukidnon boundaries. */
export const isKnownBarangay = (barangay: string, municipality: string) =>
  `${barangay}||${municipality}` in BARANGAY_NEIGHBORS;

/** Joins disease names: "A", "A and B", "A, B and C". */
export function diseaseList(codes: string[]) {
  const names = codes.map((c) => DISEASE_LABEL[c] ?? c);
  return names.length <= 1 ? names.join("") : `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
}

/** Farmers in the barangay or a neighbouring one who can receive a push (active account + device). */
export async function recipientsFor(ctx: QueryCtx, barangay: string, municipality: string) {
  const neighbours = BARANGAY_NEIGHBORS[`${barangay}||${municipality}`] ?? [];
  const targets = new Set([
    areaKey(barangay, municipality),
    ...neighbours.map((key) => {
      const [b, m] = key.split("||");
      return areaKey(b, m);
    }),
  ]);
  // Full scan: the user table is small, and matching needs the normalised names anyway.
  const nearby = (await ctx.db.query("users").collect()).filter(
    (u) => u.accountStatus !== "suspended" && !!u.barangay && targets.has(areaKey(u.barangay, u.municipality ?? "")),
  );
  const withDevice: Doc<"users">[] = [];
  for (const user of nearby) {
    const devices = await ctx.db
      .query("deviceTokens")
      .withIndex("by_user", (q) => q.eq("userId", user._id))
      .collect();
    if (devices.some((d) => d.active)) withDevice.push(user);
  }
  return { nearby, withDevice };
}

/**
 * Called by barangayStats.recompute when a barangay + disease turns severe. Adds the disease to the
 * barangay's pending outbreak (one combined alert per barangay) or opens a new pending one.
 */
export async function queueOutbreak(
  ctx: MutationCtx,
  { barangay, municipality, diseaseCode }: { barangay: string; municipality: string; diseaseCode: string },
) {
  const pending = await ctx.db
    .query("outbreaks")
    .withIndex("by_area_status", (q) => q.eq("barangay", barangay).eq("municipality", municipality).eq("status", "pending"))
    .first();
  if (pending) {
    if (!pending.diseaseCodes.includes(diseaseCode)) {
      await ctx.db.patch(pending._id, { diseaseCodes: [...pending.diseaseCodes, diseaseCode] });
    }
    return pending._id;
  }
  return await ctx.db.insert("outbreaks", {
    barangay, municipality, diseaseCodes: [diseaseCode],
    status: "pending", source: "automatic", declaredAt: Date.now(),
  });
}

/** Test cleanup (CLI only): `npx convex run outbreakAlerts:deleteOutbreaks "{outbreakIds:[...]}"`. */
export const deleteOutbreaks = internalMutation({
  args: { outbreakIds: v.array(v.string()) },
  handler: async (ctx, { outbreakIds }) => {
    for (const raw of outbreakIds) {
      const id = ctx.db.normalizeId("outbreaks", raw);
      if (id && (await ctx.db.get(id))) await ctx.db.delete(id);
    }
  },
});

/** Sends an approved outbreak to the farmers nearby. Scheduled by admin.approveOutbreak / admin.raiseAlert. */
export const sendOutbreak = internalMutation({
  args: { outbreakId: v.id("outbreaks") },
  handler: async (ctx, { outbreakId }) => {
    const outbreak = await ctx.db.get(outbreakId);
    if (!outbreak || outbreak.status !== "approved") return;
    const { barangay, municipality, diseaseCodes } = outbreak;
    const { nearby, withDevice } = await recipientsFor(ctx, barangay, municipality);

    const diseases = diseaseList(diseaseCodes);
    const area = `${barangay}, ${municipality}`;
    const title = `${diseases} outbreak reported nearby`;
    const message = outbreak.message?.trim() ||
      `Multiple corn farms in ${area} have confirmed ${diseases}. Check the outbreak map.`;
    for (const user of withDevice) {
      const notificationId: Id<"notifications"> = await ctx.db.insert("notifications", {
        recipientUserId: user._id,
        type: "outbreak_alert",
        title,
        message,
        diseaseCode: diseaseCodes[0],
        deliveryStatus: "pending",
      });
      await ctx.scheduler.runAfter(0, internal.push.sendNotification, { notificationId });
    }
    await ctx.db.patch(outbreakId, { recipientsNotified: withDevice.length });
    console.log(`Outbreak alert: ${diseases} in ${area} -> ${nearby.length} farmer(s) nearby, ${withDevice.length} notified`);
  },
});
