/**
 * Weekly verified-report aggregation for the admin dashboard chart and its CSV export
 * (admin.weeklyReports). Pure functions so they can be unit-tested without Convex
 * (scripts/weekly-reports.test.mts).
 *
 * Weeks run Monday 00:00 to Sunday 24:00 Philippine time (UTC+8, no daylight saving), the
 * local week the Municipal Agriculture Office reports in.
 */

const DAY_MS = 24 * 60 * 60 * 1000;
export const WEEK_MS = 7 * DAY_MS;
const PHT_OFFSET_MS = 8 * 60 * 60 * 1000;
/** Most weeks one request may ask for (half a year). */
export const MAX_WEEKS = 26;

/** Start (epoch ms) of the PHT Monday-based week containing [t]. */
export function weekStartOf(t: number): number {
  const local = t + PHT_OFFSET_MS;
  const dayStart = Math.floor(local / DAY_MS) * DAY_MS;
  // 1970-01-01 was a Thursday: (days + 3) % 7 is 0 on Mondays.
  const daysSinceMonday = ((Math.floor(local / DAY_MS) + 3) % 7 + 7) % 7;
  return dayStart - daysSinceMonday * DAY_MS - PHT_OFFSET_MS;
}

/** The [weeks] week starts ending with the week containing [endAt], oldest first. */
export function weekStarts(endAt: number, weeks: number): number[] {
  const n = Math.max(1, Math.min(MAX_WEEKS, Math.floor(weeks)));
  const last = weekStartOf(endAt);
  return Array.from({ length: n }, (_, i) => last - (n - 1 - i) * WEEK_MS);
}

export type WeeklyInput = {
  userId: string;
  diseaseCode: string;
  barangay: string;
  municipality: string;
  capturedAt: number;
};

export type WeeklyRow = {
  weekStart: number;
  barangay: string;
  municipality: string;
  diseaseCode: string;
  /** Verified scans that week. */
  scans: number;
  /** Distinct farmers reporting it that week (the outbreak engine's "farms": one per account). */
  farms: number;
};

/**
 * Groups [records] by (week, barangay, municipality, disease). Records outside the requested
 * weeks are ignored. Rows are sorted by week, then municipality, barangay and disease.
 */
export function aggregateWeekly(records: WeeklyInput[], starts: number[]): WeeklyRow[] {
  if (starts.length === 0) return [];
  const from = starts[0];
  const to = starts[starts.length - 1] + WEEK_MS;
  const groups = new Map<string, { row: WeeklyRow; users: Set<string> }>();
  for (const r of records) {
    if (r.capturedAt < from || r.capturedAt >= to) continue;
    const weekStart = weekStartOf(r.capturedAt);
    const key = [weekStart, r.municipality, r.barangay, r.diseaseCode].join("|");
    let g = groups.get(key);
    if (!g) {
      g = {
        row: { weekStart, barangay: r.barangay, municipality: r.municipality, diseaseCode: r.diseaseCode, scans: 0, farms: 0 },
        users: new Set(),
      };
      groups.set(key, g);
    }
    g.row.scans += 1;
    g.users.add(r.userId);
  }
  return [...groups.values()]
    .map(({ row, users }) => ({ ...row, farms: users.size }))
    .sort((a, b) =>
      a.weekStart - b.weekStart ||
      a.municipality.localeCompare(b.municipality) ||
      a.barangay.localeCompare(b.barangay) ||
      a.diseaseCode.localeCompare(b.diseaseCode));
}
