import { v } from "convex/values";
import { internal } from "./_generated/api";
import { MutationCtx, internalMutation, query } from "./_generated/server";
import { currentUserId } from "./lib/access";
import { queueOutbreak } from "./outbreakAlerts";

/**
 * Barangay-level outbreak severity for the heatmap — ported from caps 3
 * (my-app/convex/barangayStats.ts) with the same rules. Only verified reports count.
 */
const WINDOW_MS = 14 * 24 * 60 * 60 * 1000; // 14-day rolling window
const MODERATE_SCORE = 2.0;
const SEVERE_SCORE = 4.0;
const SEVERE_MIN_FARMS = 3;
const MODERATE_MIN_FARMS = 2;

export const HEALTHY = "healthy";

function weightOf(confidence: number) {
  if (confidence >= 0.9) return 1.0;
  if (confidence >= 0.7) return 0.6;
  return 0.3;
}

/** Schedules a recompute for the area/disease a record belongs to (no-op if not mappable). */
export async function scheduleRecompute(
  ctx: MutationCtx,
  record: { barangay: string; municipality: string; diseaseCode: string },
) {
  if (!record.barangay || record.diseaseCode === HEALTHY) return;
  await ctx.scheduler.runAfter(0, internal.barangayStats.recompute, {
    barangay: record.barangay,
    municipality: record.municipality,
    diseaseCode: record.diseaseCode,
  });
}

/** Recompute the severity row for one barangay + municipality + disease. */
export const recompute = internalMutation({
  args: { barangay: v.string(), municipality: v.string(), diseaseCode: v.string() },
  handler: async (ctx, { barangay, municipality, diseaseCode }) => {
    const now = Date.now();
    const windowStart = now - WINDOW_MS;

    const verified = await ctx.db
      .query("diagnosisRecords")
      .withIndex("by_area_disease_status_time", (q) =>
        q
          .eq("barangay", barangay)
          .eq("municipality", municipality)
          .eq("diseaseCode", diseaseCode)
          .eq("verificationStatus", "verified")
          .gte("capturedAt", windowStart),
      )
      .collect();
    // Reports from suspended accounts don't count (admin.setAccountStatus triggers a recompute).
    const inWindow = [];
    for (const r of verified) {
      if ((await ctx.db.get(r.userId))?.accountStatus !== "suspended") inWindow.push(r);
    }

    const weightedScore = inWindow.reduce((sum, r) => sum + weightOf(r.confidence), 0);
    const distinctFarms = new Set(inWindow.map((r) => r.userId)).size;

    let severityTier: "mild" | "moderate" | "severe" = "mild";
    if (weightedScore >= SEVERE_SCORE && distinctFarms >= SEVERE_MIN_FARMS) {
      severityTier = "severe";
    } else if (weightedScore >= MODERATE_SCORE || distinctFarms >= MODERATE_MIN_FARMS) {
      severityTier = "moderate";
    }

    const existing = await ctx.db
      .query("barangayDiseaseStats")
      .withIndex("by_area_disease", (q) =>
        q.eq("barangay", barangay).eq("municipality", municipality).eq("diseaseCode", diseaseCode),
      )
      .unique();
    const justBecameSevere = severityTier === "severe" && existing?.severityTier !== "severe";

    const row = {
      barangay,
      municipality,
      diseaseCode,
      windowStart,
      windowEnd: now,
      weightedScore,
      distinctFarms,
      rawReportCount: inWindow.length,
      severityTier,
      isActiveOutbreak: severityTier === "severe",
      outbreakDeclaredAt: justBecameSevere ? now : existing?.outbreakDeclaredAt,
      lastUpdated: now,
    };
    if (existing) {
      await ctx.db.patch(existing._id, row);
    } else if (inWindow.length > 0) {
      await ctx.db.insert("barangayDiseaseStats", row);
    }
    // On the transition into "severe" (as caps 3), queue the outbreak for admin review; farmers are
    // only notified once an admin approves it in the admin website.
    if (justBecameSevere) {
      await queueOutbreak(ctx, { barangay, municipality, diseaseCode });
    }
  },
});

/** Daily decay: re-evaluate every row so reports older than the window drop out. */
export const recomputeAll = internalMutation({
  args: {},
  handler: async (ctx) => {
    for (const row of await ctx.db.query("barangayDiseaseStats").collect()) {
      await ctx.scheduler.runAfter(0, internal.barangayStats.recompute, {
        barangay: row.barangay,
        municipality: row.municipality,
        diseaseCode: row.diseaseCode,
      });
    }
  },
});

/** Severity rows for the heatmap (signed-in users). Rows with no reports left in the window are dropped. */
export const getAllStats = query({
  args: {},
  handler: async (ctx) => {
    if (!(await currentUserId(ctx))) return [];
    const rows = await ctx.db.query("barangayDiseaseStats").collect();
    return rows
      .filter((r) => r.rawReportCount > 0)
      .map((r) => ({
        barangay: r.barangay,
        municipality: r.municipality,
        diseaseCode: r.diseaseCode,
        severityTier: r.severityTier,
        weightedScore: r.weightedScore,
        distinctFarms: r.distinctFarms,
        rawReportCount: r.rawReportCount,
        isActiveOutbreak: r.isActiveOutbreak,
        lastUpdated: r.lastUpdated,
      }));
  },
});
