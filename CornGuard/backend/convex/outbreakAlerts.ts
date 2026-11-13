import { v } from "convex/values";
import { internal } from "./_generated/api";
import { Doc, Id } from "./_generated/dataModel";
import { MutationCtx, QueryCtx, internalMutation } from "./_generated/server";
import { BARANGAY_NEIGHBORS } from "./barangayNeighbors";

/**
 * Automatic disease-level alerts, sent without an admin step:
 *  - a barangay + disease rising to MODERATE (2x its baseline) sends an awareness notice
 *    ("disease_awareness");
 *  - rising to SEVERE (3x its baseline) sends a potential-outbreak warning ("outbreak_alert").
 * barangayStats.recompute calls autoAlert only when the level goes UP, and never while the barangay
 * is still collecting data for its baseline; going down sends nothing. Validation happens before
 * the level is calculated, not before sending: only verified reports (confident scans with a photo,
 * or admin-verified) count toward a level (panel requirement D-07). Admins can still dismiss a
 * false alarm afterwards and unverify the reports behind it.
 *
 * Cooldown: the same level for the same barangay + disease is not sent again within 14 days, so a
 * level dipping overnight and rising again doesn't repeat the alert.
 *
 * Recipients: every active farmer whose profile is in that barangay or one that borders it
 * (BARANGAY_NEIGHBORS, generated from the boundary map, so it includes border barangays of the next
 * town). Profiles hold free-typed names ("Valencia") while outbreaks use the boundary file's
 * official ones ("City of Valencia"), so both sides are compared in a normalised form.
 */

export type AlertLevel = "moderate" | "severe";

/** Don't repeat the same level for the same barangay + disease within this time. */
const COOLDOWN_MS = 14 * 24 * 60 * 60 * 1000;
/** Diseases crossing the same level in one barangay within this delay go out as one alert. */
const BATCH_DELAY_MS = 60 * 1000;

/** The fixed message for each level; only the disease and place names are filled in. */
const TEMPLATES: Record<AlertLevel, {
  type: string;
  title: (diseases: string, barangay: string) => string;
  message: (diseases: string, area: string) => string;
}> = {
  moderate: {
    type: "disease_awareness",
    title: (diseases, barangay) => `${diseases} cases rising in ${barangay}`,
    message: (diseases, area) =>
      `More corn farms than usual in ${area}, near you, have reported ${diseases} this week. ` +
      `Check your field and the outbreak map.`,
  },
  severe: {
    type: "outbreak_alert",
    title: (diseases) => `Potential outbreak: ${diseases} near you`,
    message: (diseases, area) =>
      `Reports of ${diseases} in ${area}, close to your farm, are far above the usual level for this area. ` +
      `Inspect your crops and take precautions. See the outbreak map for details.`,
  },
};

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
 * Called by barangayStats.recompute when a barangay + disease rises to `level`. Skips it if that level
 * was already alerted for this disease here within the cooldown (even if an admin dismissed it as a
 * false alarm); otherwise joins the barangay's alert that is about to go out at this level, or
 * schedules a new one.
 */
/** The numbers behind an alert, stored on it as `trigger`. */
export type AlertSnapshot = {
  farms: number; baselineFarms: number; threshold: number; monitoringStatus: string; baselineSource?: string;
};

export async function autoAlert(
  ctx: MutationCtx,
  { barangay, municipality, diseaseCode, level, snapshot }:
    { barangay: string; municipality: string; diseaseCode: string; level: AlertLevel; snapshot: AlertSnapshot },
) {
  const trigger = { diseaseCode, ...snapshot };
  const recent = await ctx.db
    .query("outbreaks")
    .withIndex("by_area_level", (q) => q.eq("barangay", barangay).eq("municipality", municipality)
      .eq("level", level).gte("declaredAt", Date.now() - COOLDOWN_MS))
    .collect();
  if (recent.some((o) => o.diseaseCodes.includes(diseaseCode))) return null;

  const scheduled = recent.find((o) => o.status === "scheduled");
  if (scheduled) {
    await ctx.db.patch(scheduled._id, {
      diseaseCodes: [...scheduled.diseaseCodes, diseaseCode],
      trigger: [...(scheduled.trigger ?? []), trigger],
    });
    return scheduled._id;
  }
  const outbreakId = await ctx.db.insert("outbreaks", {
    barangay, municipality, diseaseCodes: [diseaseCode], level,
    status: "scheduled", source: "automatic", declaredAt: Date.now(), trigger: [trigger],
  });
  await ctx.scheduler.runAfter(BATCH_DELAY_MS, internal.outbreakAlerts.sendOutbreak, { outbreakId });
  return outbreakId;
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

/**
 * Sends an alert to the farmers in the barangay and the bordering ones, using its level's template.
 * Scheduled by autoAlert ("scheduled" -> "sent"; skipped if an admin dismissed it in the meantime)
 * and by devAccounts.sendTestAlert ("approved", custom TEST message).
 */
export const sendOutbreak = internalMutation({
  args: { outbreakId: v.id("outbreaks") },
  handler: async (ctx, { outbreakId }) => {
    const outbreak = await ctx.db.get(outbreakId);
    if (!outbreak || (outbreak.status !== "scheduled" && outbreak.status !== "approved")) return;
    const { barangay, municipality, diseaseCodes } = outbreak;
    const level: AlertLevel = outbreak.level ?? "severe";
    const template = TEMPLATES[level];
    const { nearby, withDevice } = await recipientsFor(ctx, barangay, municipality);

    const diseases = diseaseList(diseaseCodes);
    const area = `${barangay}, ${municipality}`;
    const title = template.title(diseases, barangay);
    const message = outbreak.message?.trim() || template.message(diseases, area);
    for (const user of withDevice) {
      const notificationId: Id<"notifications"> = await ctx.db.insert("notifications", {
        recipientUserId: user._id,
        type: template.type,
        title,
        message,
        diseaseCode: diseaseCodes[0],
        deliveryStatus: "pending",
      });
      await ctx.scheduler.runAfter(0, internal.push.sendNotification, { notificationId });
    }
    await ctx.db.patch(outbreakId, {
      recipientsNotified: withDevice.length,
      ...(outbreak.status === "scheduled" ? { status: "sent" as const } : {}),
    });
    console.log(`${level} alert: ${diseases} in ${area} -> ${nearby.length} farmer(s) nearby, ${withDevice.length} notified`);
  },
});
