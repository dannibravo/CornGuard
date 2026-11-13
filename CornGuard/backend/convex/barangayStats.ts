import { v } from "convex/values";
import { internal } from "./_generated/api";
import { MutationCtx, internalMutation, query } from "./_generated/server";
import { Doc, Id } from "./_generated/dataModel";
import { currentUserId } from "./lib/access";
import {
  BaselineSource, OUTBREAK_RULES, WEEK_MS, classifyOutbreak, monitoringStatus, neighbourhoodWeeks, weeklyBaseline,
  weightOf,
} from "./lib/outbreakRules";
import { BARANGAY_NEIGHBORS } from "./barangayNeighbors";
import { DISEASE_LABEL, autoAlert } from "./outbreakAlerts";

const TIER_RANK = { mild: 0, moderate: 1, severe: 2 } as const;

/**
 * Barangay-level outbreak level for the heatmap, originally ported from caps 3. Only verified
 * reports from active accounts count. A barangay is first watched until it has enough data
 * ("collecting", outbreak status Undetermined, no alerts); after that, the farms reporting a
 * disease in the last 7 days are compared with the barangay's rolling weekly baseline for it,
 * following the WHO / DA-BPI definition of an outbreak. Rule: lib/outbreakRules.ts; method and
 * sources: docs/OUTBREAK_DETECTION_METHOD.md.
 */
export const WINDOW_MS = OUTBREAK_RULES.windowDays * 24 * 60 * 60 * 1000; // rolling 7-day window
/** Map markers stay visible for 4 weeks (faded once they no longer count), then disappear. */
export const RECENT_MS = 28 * 24 * 60 * 60 * 1000;

export const HEALTHY = "healthy";
/** Every code a shared scan can have; all of them show the barangay is actively scanning. */
const ALL_CODES = [HEALTHY, ...Object.keys(DISEASE_LABEL)];

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

/**
 * One barangay's verified history for a disease: its reports in the current 7 days, distinct farms
 * in each baseline week (and whether the barangay scanned anything at all that week), its scan count
 * over those weeks, and its first verified scan ever.
 */
async function areaHistory(
  ctx: MutationCtx,
  { barangay, municipality, diseaseCode, windowStart, isSuspended }: {
    barangay: string; municipality: string; diseaseCode: string; windowStart: number;
    isSuspended: (userId: Id<"users">) => Promise<boolean>;
  },
) {
  const weeks = OUTBREAK_RULES.baselineWeeks;
  // The current 7 days plus the baseline weeks before them.
  const since = windowStart - weeks * WEEK_MS;
  /** 0 = the current 7 days, 1..weeks = the baseline weeks (1 = the week just before). */
  const weekOf = (t: number) => (t >= windowStart ? 0 : Math.floor((windowStart - t) / WEEK_MS) + 1);
  const verifiedFor = (code: string) => ctx.db
    .query("diagnosisRecords")
    .withIndex("by_area_disease_status_time", (q) => q.eq("barangay", barangay).eq("municipality", municipality)
      .eq("diseaseCode", code).eq("verificationStatus", "verified").gte("capturedAt", since))
    .collect();

  // This disease: the current 7 days, and distinct farms in each baseline week.
  const inWindow: Doc<"diagnosisRecords">[] = [];
  const weekFarms = Array.from({ length: weeks + 1 }, () => new Set<Id<"users">>());
  for (const r of await verifiedFor(diseaseCode)) {
    if (await isSuspended(r.userId)) continue;
    const w = weekOf(r.capturedAt);
    if (w === 0) inWindow.push(r);
    if (w <= weeks) weekFarms[w].add(r.userId);
  }
  // Scanning activity of any kind (healthy included): which baseline weeks have data at all, how
  // many scans there were, and when the barangay first shared a verified scan.
  const weekScans = new Array<number>(weeks + 1).fill(0);
  let firstScanAt: number | null = null;
  for (const code of ALL_CODES) {
    for (const r of await verifiedFor(code)) {
      if (!(await isSuspended(r.userId))) weekScans[weekOf(r.capturedAt)]++;
    }
    const first = await ctx.db
      .query("diagnosisRecords")
      .withIndex("by_area_disease_status_time", (q) => q.eq("barangay", barangay).eq("municipality", municipality)
        .eq("diseaseCode", code).eq("verificationStatus", "verified"))
      .order("asc")
      .first();
    if (first && (firstScanAt === null || first.capturedAt < firstScanAt)) firstScanAt = first.capturedAt;
  }
  return {
    inWindow,
    baselineWeeks: weekFarms.slice(1).map((farms, i) => ({ hasData: weekScans[i + 1] > 0, farms: farms.size })),
    historyScans: weekScans.slice(1).reduce((a, b) => a + b, 0),
    firstScanAt,
    lastWeekFarms: weekFarms[1].size,
  };
}

/** Recompute the severity row for one barangay + municipality + disease. */
export const recompute = internalMutation({
  args: { barangay: v.string(), municipality: v.string(), diseaseCode: v.string() },
  handler: async (ctx, { barangay, municipality, diseaseCode }) => {
    const now = Date.now();
    const windowStart = now - WINDOW_MS;

    // Reports from suspended accounts don't count (admin.setAccountStatus triggers a recompute).
    const suspended = new Map<Id<"users">, boolean>();
    const isSuspended = async (userId: Id<"users">) => {
      if (!suspended.has(userId)) suspended.set(userId, (await ctx.db.get(userId))?.accountStatus === "suspended");
      return suspended.get(userId)!;
    };
    const history = (b: string, m: string) => areaHistory(ctx, { barangay: b, municipality: m, diseaseCode, windowStart, isSuspended });

    // The barangay's own history first.
    const own = await history(barangay, municipality);
    const { inWindow } = own;
    let { baseline: baselineFarms, dataWeeks, excludedWeeks } = weeklyBaseline(own.baselineWeeks);
    let historyScans = own.historyScans;
    const firstScanAt = own.firstScanAt;
    let status = monitoringStatus({ firstScanAt, historyScans, dataWeeks, now });
    let baselineSource: BaselineSource = "own";
    let neighbourAreas: number | undefined;

    // Not enough data of its own: borrow the neighbourhood's (it + bordering barangays), averaged per
    // active barangay. Only the baseline is borrowed; this week's farms are still its own.
    if (status === "collecting") {
      const areas = [own];
      for (const key of BARANGAY_NEIGHBORS[`${barangay}||${municipality}`] ?? []) {
        const [b, m] = key.split("||");
        areas.push(await history(b, m));
      }
      const active = areas.filter((a) => a.baselineWeeks.some((w) => w.hasData));
      const pooled = neighbourhoodWeeks(areas.map((a) => a.baselineWeeks));
      const pooledBaseline = weeklyBaseline(pooled.weeks);
      const pooledScans = active.reduce((sum, a) => sum + a.historyScans, 0);
      const firsts = areas.map((a) => a.firstScanAt).filter((t): t is number => t !== null);
      const pooledFirst = firsts.length ? Math.min(...firsts) : null;
      const pooledStatus = monitoringStatus({
        firstScanAt: pooledFirst, historyScans: pooledScans, dataWeeks: pooledBaseline.dataWeeks, now,
      });
      if (pooledStatus !== "collecting") {
        ({ baseline: baselineFarms, dataWeeks, excludedWeeks } = pooledBaseline);
        historyScans = pooledScans;
        status = "provisional"; // a borrowed baseline is never "established"
        baselineSource = "neighbours";
        neighbourAreas = pooled.activeAreas;
      }
    }

    const weightedScore = inWindow.reduce((sum, r) => sum + weightOf(r.confidence), 0);
    const distinctFarms = new Set(inWindow.map((r) => r.userId)).size;
    const { tier: severityTier, moderateAt, severeAt } = classifyOutbreak({
      farms: distinctFarms,
      score: weightedScore,
      baseline: baselineFarms,
      status,
    });

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
      usualFarms: undefined,
      monitoringStatus: status,
      baselineFarms,
      excludedWeeks,
      baselineSource,
      neighbourAreas,
      lastWeekFarms: own.lastWeekFarms,
      dataWeeks,
      historyScans,
      monitoringSince: firstScanAt ?? undefined,
      moderateAt,
      severeAt,
      severityTier,
      isActiveOutbreak: severityTier === "severe",
      outbreakDeclaredAt: justBecameSevere ? now : existing?.outbreakDeclaredAt,
      lastUpdated: now,
    };
    if (existing) {
      await ctx.db.patch(existing._id, row);
    } else if (inWindow.length > 0 || status !== "collecting") {
      // Also kept with no reports this week once the baseline exists, for the admin map's usual levels.
      await ctx.db.insert("barangayDiseaseStats", row);
    }
    // When the level goes UP (to moderate, or to severe, also straight from mild), farmers in and
    // around the barangay are alerted automatically. Going down sends nothing, and nothing is sent
    // while the barangay is still collecting data (classifyOutbreak keeps it Mild then).
    const rose = TIER_RANK[severityTier] > TIER_RANK[existing?.severityTier ?? "mild"];
    if (rose && severityTier !== "mild" && status !== "collecting") {
      await autoAlert(ctx, {
        barangay, municipality, diseaseCode, level: severityTier,
        // Why it fired, kept on the alert (this row's numbers keep rolling afterwards).
        snapshot: {
          farms: distinctFarms,
          baselineFarms,
          threshold: (severityTier === "severe" ? severeAt : moderateAt) ?? 0,
          monitoringStatus: status,
          baselineSource,
        },
      });
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

/**
 * Severity rows for the heatmap (signed-in users). Rows with no reports left in the window are dropped,
 * unless includeBaselines (admin map): then rows of barangays with a baseline stay, to show usual levels.
 * The app calls it without arguments.
 */
export const getAllStats = query({
  args: { includeBaselines: v.optional(v.boolean()) },
  handler: async (ctx, { includeBaselines }) => {
    if (!(await currentUserId(ctx))) return [];
    const rows = await ctx.db.query("barangayDiseaseStats").collect();
    return rows
      .filter((r) => r.rawReportCount > 0 ||
        (includeBaselines === true && (r.monitoringStatus ?? "collecting") !== "collecting"))
      .map((r) => ({
        barangay: r.barangay,
        municipality: r.municipality,
        diseaseCode: r.diseaseCode,
        severityTier: r.severityTier,
        weightedScore: r.weightedScore,
        distinctFarms: r.distinctFarms,
        rawReportCount: r.rawReportCount,
        // Rows from before baseline monitoring read as "collecting" until the next recompute.
        monitoringStatus: r.monitoringStatus ?? "collecting",
        baselineFarms: r.baselineFarms ?? null,
        excludedWeeks: r.excludedWeeks ?? 0,
        baselineSource: r.baselineSource ?? "own",
        neighbourAreas: r.neighbourAreas ?? null,
        lastWeekFarms: r.lastWeekFarms ?? null,
        dataWeeks: r.dataWeeks ?? 0,
        historyScans: r.historyScans ?? 0,
        monitoringSince: r.monitoringSince ?? null,
        moderateAt: r.moderateAt ?? null,
        severeAt: r.severeAt ?? null,
        isActiveOutbreak: r.isActiveOutbreak,
        lastUpdated: r.lastUpdated,
      }));
  },
});
