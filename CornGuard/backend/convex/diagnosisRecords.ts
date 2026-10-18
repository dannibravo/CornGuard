import { ConvexError, v } from "convex/values";
import { Doc } from "./_generated/dataModel";
import { mutation, query, QueryCtx } from "./_generated/server";
import { assertOwnerOrAdmin, currentUserId, requireActiveUser } from "./lib/access";
import { HEALTHY, RECENT_MS, WINDOW_MS, scheduleRecompute } from "./barangayStats";
import { areaKey } from "./outbreakAlerts";
import { areaField } from "./schema";

/** Cap for server-side aggregation — capstone scale, same bound the Firestore version used. */
const MAX_SCAN = 1000;
/** Shares at or above this confidence (with a photo) are verified automatically (caps 3 rule). */
// The model is trained with label smoothing 0.1, so its top score is about 92.5% (field photos lower).
// At 60% the emulator test set had 110/126 scans pass with ~5% wrong; below that it is close to guessing.
const AUTO_VERIFY_CONFIDENCE = 0.6;
/** Anti-spam: at most this many shares per user per rolling 24 h. */
export const MAX_SHARES_PER_DAY = 30;
const DAY_MS = 24 * 60 * 60 * 1000;
/** Scans older than this can still be shared, but never auto-verify (outside the outbreak window). */
const AUTO_VERIFY_MAX_AGE_MS = 14 * DAY_MS;
const CLOCK_SKEW_MS = 5 * 60 * 1000;

async function toRecord(ctx: QueryCtx, r: Doc<"diagnosisRecords">) {
  return {
    recordId: r._id,
    userId: r.userId,
    farmId: r.farmId ?? null,
    diseaseCode: r.diseaseCode,
    confidence: r.confidence,
    imageUrl: r.imageId ? await ctx.storage.getUrl(r.imageId) : null,
    capturedAt: r.capturedAt,
    barangay: r.barangay,
    municipality: r.municipality,
    province: r.province,
    modelVersion: r.modelVersion,
    verificationStatus: r.verificationStatus,
    source: r.source,
  };
}

/**
 * Shares one on-device scan to the cloud. Idempotent per (user, localId), so a retried share
 * after a dropped connection returns the existing record instead of duplicating it.
 */
export const share = mutation({
  args: {
    localId: v.string(),
    farmId: v.optional(v.string()),
    diseaseCode: v.string(),
    confidence: v.number(),
    imageId: v.optional(v.id("_storage")),
    capturedAt: v.number(),
    barangay: v.string(),
    municipality: v.string(),
    province: v.string(),
    latitude: v.optional(v.number()),
    longitude: v.optional(v.number()),
    modelVersion: v.string(),
  },
  handler: async (ctx, { farmId, ...args }) => {
    const user = await requireActiveUser(ctx);
    const existing = await ctx.db
      .query("diagnosisRecords")
      .withIndex("by_user_local", (q) => q.eq("userId", user._id).eq("localId", args.localId))
      .unique();
    if (existing) {
      // The duplicate upload's file is no longer needed.
      if (args.imageId && args.imageId !== existing.imageId) await ctx.storage.delete(args.imageId);
      return existing._id;
    }
    // Anti-spam checks (the phone supplies these values, so the server sanity-checks them).
    const now = Date.now();
    if (args.capturedAt > now + CLOCK_SKEW_MS) throw new ConvexError("The scan's date is in the future.");
    if (!(args.confidence >= 0 && args.confidence <= 1)) throw new ConvexError("Invalid confidence.");
    // The heatmap places reports by barangay, so a scan without one can't count anywhere.
    if (!args.barangay.trim() || !args.municipality.trim()) {
      throw new ConvexError("Set the scan's location (barangay) before sharing.");
    }
    const sharedToday = (
      await ctx.db.query("diagnosisRecords").withIndex("by_user", (q) => q.eq("userId", user._id)).collect()
    ).filter((r) => r._creationTime > now - DAY_MS).length;
    if (sharedToday >= MAX_SHARES_PER_DAY) {
      throw new ConvexError(`You can share up to ${MAX_SHARES_PER_DAY} scans a day. Please try again tomorrow.`);
    }

    // Same rule as caps 3 — confident detections count on the heatmap immediately, the rest wait
    // for an admin — but only with a photo (so an admin can check it) and only for recent scans.
    const autoVerify = args.confidence >= AUTO_VERIFY_CONFIDENCE && !!args.imageId &&
      args.capturedAt >= now - AUTO_VERIFY_MAX_AGE_MS;
    const farm = farmId ? ctx.db.normalizeId("farms", farmId) : null;
    const id = await ctx.db.insert("diagnosisRecords", {
      ...args,
      userId: user._id,
      farmId: farm ?? undefined,
      verificationStatus: autoVerify ? "verified" : "unverified",
      source: "ai_scan",
    });
    await scheduleRecompute(ctx, args);
    return id;
  },
});

export const listForUser = query({
  args: { userId: v.string() },
  handler: async (ctx, { userId }) => {
    if (!(await currentUserId(ctx))) return [];
    const id = ctx.db.normalizeId("users", userId);
    if (!id) return [];
    const records = await ctx.db
      .query("diagnosisRecords")
      .withIndex("by_user", (q) => q.eq("userId", id))
      .order("desc")
      .collect();
    return await Promise.all(records.map((r) => toRecord(ctx, r)));
  },
});

export const remove = mutation({
  args: { recordId: v.string() },
  handler: async (ctx, { recordId }) => {
    const user = await requireActiveUser(ctx);
    const id = ctx.db.normalizeId("diagnosisRecords", recordId);
    const record = id ? await ctx.db.get(id) : null;
    if (!record) return;
    assertOwnerOrAdmin(record.userId, user);
    if (record.imageId) await ctx.storage.delete(record.imageId);
    await ctx.db.delete(record._id);
    await scheduleRecompute(ctx, record);
  },
});

/**
 * Verified disease reports from the last RECENT_MS (28 days) — what the map and "Reports near you"
 * show. Never Healthy scans, never scans from suspended accounts (they don't count toward severity
 * either).
 */
async function recentDiseaseReports(ctx: QueryCtx, diseaseCode: string | undefined) {
  const since = Date.now() - RECENT_MS;
  const records = (await verifiedRecords(ctx, diseaseCode))
    .filter((r) => r.capturedAt >= since && r.diseaseCode !== HEALTHY);
  const suspended = new Map<string, boolean>();
  const out: Doc<"diagnosisRecords">[] = [];
  for (const r of records) {
    let isSuspended = suspended.get(r.userId);
    if (isSuspended === undefined) {
      isSuspended = (await ctx.db.get(r.userId))?.accountStatus === "suspended";
      suspended.set(r.userId, isSuspended);
    }
    if (!isSuspended) out.push(r);
  }
  return out;
}

/** "active" reports count toward severity (last 14 days); "recent" ones are shown faded (14-28 days). */
const stageOf = (capturedAt: number) => (capturedAt >= Date.now() - WINDOW_MS ? "active" : "recent");

/**
 * One heatmap marker per verified disease report with coordinates from the last 28 days
 * (signed-in users only: markers are farm locations). Severity comes from barangayStats.getAllStats.
 */
export const mapReports = query({
  args: {},
  handler: async (ctx) => {
    if (!(await currentUserId(ctx))) return [];
    // The age filter uses the clock, but Convex re-runs a query only when data it read changes. Reading
    // the severity rows ties this query to the daily decay cron (which rewrites every row), so open maps
    // drop expired markers at least once a day — the same schedule on which the levels decay.
    await ctx.db.query("barangayDiseaseStats").collect();
    const records = await recentDiseaseReports(ctx, undefined);
    return records
      .filter((r) => r.latitude !== undefined && r.longitude !== undefined)
      .map((r) => ({
        stage: stageOf(r.capturedAt),
        recordId: r._id,
        diseaseCode: r.diseaseCode,
        confidence: r.confidence,
        capturedAt: r.capturedAt,
        latitude: r.latitude!,
        longitude: r.longitude!,
        barangay: r.barangay,
        municipality: r.municipality,
      }));
  },
});

async function verifiedRecords(ctx: QueryCtx, diseaseCode: string | undefined) {
  let q = ctx.db
    .query("diagnosisRecords")
    .withIndex("by_status", (i) => i.eq("verificationStatus", "verified"));
  if (diseaseCode) q = q.filter((f) => f.eq(f.field("diseaseCode"), diseaseCode));
  return await q.order("desc").take(MAX_SCAN);
}

/**
 * "Reports near you": verified disease reports from the last 28 days in one area. Pass `municipality`
 * with a barangay — many towns have a "Poblacion" — and names are compared normalised ("Valencia"
 * matches "City of Valencia"), since profiles hold free-typed names.
 */
export const verifiedOccurrences = query({
  args: {
    areaField,
    areaValue: v.string(),
    municipality: v.optional(v.string()),
    diseaseCode: v.optional(v.string()),
    limit: v.optional(v.number()),
  },
  handler: async (ctx, { areaField, areaValue, municipality, diseaseCode, limit }) => {
    if (!(await currentUserId(ctx))) return [];
    const wanted = areaField === "barangay" && municipality ? areaKey(areaValue, municipality) : null;
    const records = await recentDiseaseReports(ctx, diseaseCode);
    return records
      .filter((r) => (wanted ? areaKey(r.barangay, r.municipality) === wanted : r[areaField] === areaValue))
      .slice(0, limit ?? 200)
      .map((r) => ({
        occurrenceId: r._id,
        diseaseCode: r.diseaseCode,
        barangay: r.barangay,
        municipality: r.municipality,
        occurredAt: r.capturedAt,
        verificationStatus: r.verificationStatus,
      }));
  },
});

export const heatmapAggregates = query({
  args: { areaField, diseaseCode: v.optional(v.string()) },
  handler: async (ctx, { areaField, diseaseCode }) => {
    if (!(await currentUserId(ctx))) return [];
    const records = await verifiedRecords(ctx, diseaseCode);
    const byArea = new Map<string, Record<string, number>>();
    for (const r of records) {
      const area = r[areaField];
      if (!area) continue;
      const breakdown = byArea.get(area) ?? {};
      breakdown[r.diseaseCode] = (breakdown[r.diseaseCode] ?? 0) + 1;
      byArea.set(area, breakdown);
    }
    return [...byArea.entries()].map(([areaLabel, diseaseBreakdown]) => ({
      areaLabel,
      count: Object.values(diseaseBreakdown).reduce((a, b) => a + b, 0),
      diseaseBreakdown,
    }));
  },
});
