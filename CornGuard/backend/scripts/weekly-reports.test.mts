// Unit tests for the admin dashboard's weekly aggregation (convex/lib/weeklyReports.ts). Run: npm run test:rules
import assert from "node:assert/strict";
import { test } from "node:test";
import { aggregateWeekly, MAX_WEEKS, WEEK_MS, weekStartOf, weekStarts } from "../convex/lib/weeklyReports.ts";

// Monday 2026-11-23 00:00 PHT = Sunday 2026-11-22 16:00 UTC.
const MON = Date.UTC(2026, 10, 22, 16, 0, 0);
const HOUR = 60 * 60 * 1000;

test("weekStartOf: any moment in a PHT week maps to its Monday 00:00 PHT", () => {
  assert.equal(weekStartOf(MON), MON);
  assert.equal(weekStartOf(MON + 3 * 24 * HOUR + 5 * HOUR), MON); // Thursday
  assert.equal(weekStartOf(MON + WEEK_MS - 1), MON); // Sunday 23:59:59.999 PHT
  assert.equal(weekStartOf(MON + WEEK_MS), MON + WEEK_MS); // next Monday
  assert.equal(weekStartOf(MON - 1), MON - WEEK_MS); // Sunday night belongs to the previous week
});

test("weekStartOf: a UTC Sunday afternoon is already Monday in PHT", () => {
  // 2026-11-22 17:00 UTC = Monday 01:00 PHT.
  assert.equal(weekStartOf(Date.UTC(2026, 10, 22, 17, 0, 0)), MON);
});

test("weekStarts: oldest first, ending with the current week, clamped", () => {
  assert.deepEqual(weekStarts(MON + 2 * HOUR, 3), [MON - 2 * WEEK_MS, MON - WEEK_MS, MON]);
  assert.equal(weekStarts(MON, 0).length, 1);
  assert.equal(weekStarts(MON, 1000).length, MAX_WEEKS);
});

test("aggregateWeekly: groups by week/barangay/disease and counts distinct farmers", () => {
  const base = { barangay: "Dalirig", municipality: "Manolo Fortich", diseaseCode: "common_rust" };
  const rows = aggregateWeekly(
    [
      { ...base, userId: "u1", capturedAt: MON + HOUR },
      { ...base, userId: "u1", capturedAt: MON + 2 * HOUR }, // same farmer again: 2 scans, 1 farm
      { ...base, userId: "u2", capturedAt: MON + 3 * HOUR },
      { ...base, userId: "u3", capturedAt: MON - HOUR }, // previous week
      { ...base, diseaseCode: "gray_leaf_spot", userId: "u1", capturedAt: MON + HOUR },
      { ...base, userId: "u9", capturedAt: MON - 5 * WEEK_MS }, // outside the window: ignored
    ],
    weekStarts(MON, 2),
  );
  assert.deepEqual(rows, [
    { weekStart: MON - WEEK_MS, ...base, scans: 1, farms: 1 },
    { weekStart: MON, ...base, scans: 3, farms: 2 },
    { weekStart: MON, ...base, diseaseCode: "gray_leaf_spot", scans: 1, farms: 1 },
  ]);
});

test("aggregateWeekly: same barangay name in two towns stays separate", () => {
  const rows = aggregateWeekly(
    [
      { userId: "u1", barangay: "Poblacion", municipality: "City of Valencia", diseaseCode: "common_rust", capturedAt: MON },
      { userId: "u2", barangay: "Poblacion", municipality: "Maramag", diseaseCode: "common_rust", capturedAt: MON },
    ],
    weekStarts(MON, 1),
  );
  assert.equal(rows.length, 2);
});
