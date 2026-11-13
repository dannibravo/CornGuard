/**
 * Outbreak level rules: pure functions with no Convex imports, so they can be unit-tested
 * (scripts/outbreak-rules.test.mts). Used by barangayStats.recompute.
 * Method, sources and limitations: docs/OUTBREAK_DETECTION_METHOD.md.
 *
 * An outbreak is disease "in excess of normal expectancy" (WHO), or one that "rises significantly
 * above its general equilibrium level" (DA-BPI / PhilRice / IRRI, 2020). So a barangay is first
 * watched until it has enough data to know what is normal there ("collecting", outbreak status
 * Undetermined). After that, the farms reporting a disease in the last 7 days are compared with the
 * barangay's own weekly baseline for that disease. Weeks that were themselves outbreak-level are
 * left out of that baseline, so past outbreaks don't raise "normal" and hide later ones (Serfling
 * 1963; Farrington et al. 1996; Noufaily et al. 2013).
 */

/**
 * DEVELOPMENT VALUES pending expert approval (decision gate D-07). Change them here only, and
 * record the approving expert in docs/OUTBREAK_DETECTION_METHOD.md.
 */
export const OUTBREAK_RULES = {
  /** The current period: the last 7 days (rolling). */
  windowDays: 7,
  /** Earlier 7-day weeks that make up the baseline (rolling 12 weeks). */
  baselineWeeks: 12,
  /** Warm-up: weeks since the barangay's first verified scan before detection starts. */
  warmupWeeks: 4,
  /** Warm-up: verified scans of any kind (healthy included) needed in the baseline weeks. */
  warmupMinScans: 10,
  /** Moderate (awareness notice): at least this many times the baseline... */
  moderateRatio: 2,
  /** Severe (potential-outbreak warning): at least this many times the baseline... */
  severeRatio: 3,
  /** ...and at least this many independent farms, so tiny or zero baselines can't fire on 1–2 reports. */
  minModerateFarms: 3,
  minSevereFarms: 5,
  /** Weighted score needed (sum of the confidence weights below). */
  moderateScore: 2,
  severeScore: 4,
} as const;

export type OutbreakRules = typeof OUTBREAK_RULES;
export type OutbreakTier = "mild" | "moderate" | "severe";
/**
 * collecting  = not enough data yet: outbreak status Undetermined, no alerts.
 * provisional = detection on, but the baseline has fewer than `baselineWeeks` weeks of data.
 * established = full baseline.
 */
export type MonitoringStatus = "collecting" | "provisional" | "established";

export const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

/**
 * Strength of evidence of one verified scan, from the model's confidence. This is like case
 * classification in disease surveillance (confirmed / probable / suspected): weaker evidence still
 * counts, but less. Scaled to the model's real range (label smoothing caps its top score near 92.5%).
 */
export function weightOf(confidence: number) {
  if (confidence >= 0.8) return 1.0;
  if (confidence >= 0.6) return 0.6;
  return 0.3;
}

/**
 * Baseline: average farms reporting the disease per week, over the weeks that HAVE data. A week in
 * which the barangay shared no scans at all is missing data, not a zero, so it is left out; a week
 * with scans but none of this disease is a real zero.
 *
 * Outbreak weeks are left out too (Serfling 1963: the normal level comes from non-epidemic weeks;
 * Farrington et al. 1996 / Noufaily et al. 2013: past outbreaks in the baseline mask later ones).
 * A week counts as an outbreak week if it would have been at least Moderate against the plain
 * average (the same 2x / 3-farm cut-off as alerts). One pass; they still count as data weeks.
 * farmsNeeded is a function declaration below, so it is hoisted.
 */
export function weeklyBaseline(weeks: { hasData: boolean; farms: number }[], rules: OutbreakRules = OUTBREAK_RULES) {
  const withData = weeks.filter((w) => w.hasData);
  if (withData.length === 0) return { baseline: 0, dataWeeks: 0, excludedWeeks: 0 };
  const average = (list: { farms: number }[]) => list.reduce((sum, w) => sum + w.farms, 0) / list.length;
  const rawBaseline = average(withData);
  const outbreakAt = farmsNeeded(rawBaseline, rules.moderateRatio, rules.minModerateFarms);
  const normal = withData.filter((w) => w.farms < outbreakAt);
  // Guard: not every week can be 2x the average, but never divide by zero.
  if (normal.length === 0) return { baseline: rawBaseline, dataWeeks: withData.length, excludedWeeks: 0 };
  return {
    baseline: average(normal),
    dataWeeks: withData.length,
    excludedWeeks: withData.length - normal.length,
  };
}

/** Whether the barangay has enough data for detection yet (see MonitoringStatus). */
export function monitoringStatus(
  input: { firstScanAt: number | null; historyScans: number; dataWeeks: number; now: number },
  rules: OutbreakRules = OUTBREAK_RULES,
): MonitoringStatus {
  const weeksActive = input.firstScanAt === null ? 0 : (input.now - input.firstScanAt) / WEEK_MS;
  if (weeksActive < rules.warmupWeeks || input.historyScans < rules.warmupMinScans) return "collecting";
  return input.dataWeeks >= rules.baselineWeeks ? "established" : "provisional";
}

/** Farms needed for a level: `ratio` × the baseline, but never fewer than `minFarms`. */
export function farmsNeeded(baseline: number, ratio: number, minFarms: number) {
  return Math.max(minFarms, Math.ceil(ratio * baseline - 1e-9));
}

/**
 * The outbreak level for one barangay + disease. While "collecting" it is always Mild (shown as
 * Undetermined): nothing is known yet about what is normal there.
 */
export function classifyOutbreak(
  input: { farms: number; score: number; baseline: number; status: MonitoringStatus },
  rules: OutbreakRules = OUTBREAK_RULES,
) {
  const moderateAt = farmsNeeded(input.baseline, rules.moderateRatio, rules.minModerateFarms);
  const severeAt = farmsNeeded(input.baseline, rules.severeRatio, rules.minSevereFarms);
  let tier: OutbreakTier = "mild";
  if (input.status !== "collecting") {
    if (input.farms >= severeAt && input.score >= rules.severeScore) tier = "severe";
    else if (input.farms >= moderateAt && input.score >= rules.moderateScore) tier = "moderate";
  }
  return { tier, moderateAt, severeAt };
}

/** Whose weekly history the baseline came from: the barangay's own, or its neighbourhood's (borrowed). */
export type BaselineSource = "own" | "neighbours";

/**
 * Borrowed baseline for a barangay without enough data of its own: pools it with its bordering
 * barangays. Each input is one area's week list. Per week, hasData = any area scanned, and farms =
 * the areas' total divided by the number of ACTIVE areas (those that scanned at all in these weeks),
 * so it stays a per-barangay level and neighbours with no app users don't dilute it.
 */
export function neighbourhoodWeeks(areas: { hasData: boolean; farms: number }[][]) {
  const active = areas.filter((weeks) => weeks.some((w) => w.hasData));
  const length = Math.max(0, ...areas.map((weeks) => weeks.length));
  return {
    activeAreas: active.length,
    weeks: Array.from({ length }, (_, i) => ({
      hasData: active.some((weeks) => weeks[i]?.hasData),
      farms: active.length === 0 ? 0 : active.reduce((sum, weeks) => sum + (weeks[i]?.farms ?? 0), 0) / active.length,
    })),
  };
}

/**
 * A cluster in an area with no baseline at all (not even borrowed): as many farms and as much
 * evidence as Severe needs. Not alerted automatically (nothing is known about what is normal there);
 * shown to admins for review instead.
 */
export function isDataPoorCluster(
  input: { farms: number; score: number; status: MonitoringStatus },
  rules: OutbreakRules = OUTBREAK_RULES,
) {
  return input.status === "collecting" && input.farms >= rules.minSevereFarms && input.score >= rules.severeScore;
}
