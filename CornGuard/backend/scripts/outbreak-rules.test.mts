// Unit tests for the outbreak level rules (convex/lib/outbreakRules.ts). Run: npm run test:rules
import assert from "node:assert/strict";
import { test } from "node:test";
import {
  OUTBREAK_RULES, WEEK_MS, classifyOutbreak, farmsNeeded, isDataPoorCluster, monitoringStatus, neighbourhoodWeeks,
  weeklyBaseline, weightOf,
} from "../convex/lib/outbreakRules.ts";

const NOW = 1_800_000_000_000;

test("confidence weights", () => {
  assert.equal(weightOf(0.93), 1.0);
  assert.equal(weightOf(0.8), 1.0);
  assert.equal(weightOf(0.7), 0.6);
  assert.equal(weightOf(0.6), 0.6);
  assert.equal(weightOf(0.5), 0.3);
});

test("warm-up: collecting until 4 weeks since the first scan AND 10 scans", () => {
  const status = (weeks: number, scans: number, dataWeeks = 4) =>
    monitoringStatus({ firstScanAt: NOW - weeks * WEEK_MS, historyScans: scans, dataWeeks, now: NOW });
  assert.equal(monitoringStatus({ firstScanAt: null, historyScans: 0, dataWeeks: 0, now: NOW }), "collecting");
  assert.equal(status(3.9, 50), "collecting");   // not 4 weeks yet
  assert.equal(status(8, 9), "collecting");      // too few scans
  assert.equal(status(4, 10), "provisional");
  assert.equal(status(20, 40, 11), "provisional");
  assert.equal(status(20, 40, 12), "established");
});

test("baseline skips weeks with no data at all, but counts real zeros", () => {
  assert.deepEqual(weeklyBaseline([]), { baseline: 0, dataWeeks: 0, excludedWeeks: 0 });
  assert.deepEqual(weeklyBaseline([
    { hasData: true, farms: 2 }, { hasData: false, farms: 0 }, { hasData: true, farms: 0 }, { hasData: true, farms: 1 },
  ]), { baseline: 1, dataWeeks: 3, excludedWeeks: 0 });
});

const weeksOf = (...farms: number[]) => farms.map((f) => ({ hasData: true, farms: f }));

test("outbreak weeks are left out of the baseline (Serfling / Farrington)", () => {
  // 9 normal weeks at 3 farms + 3 outbreak weeks at 12: plain average 5.25 -> Moderate cut-off 11.
  const r = weeklyBaseline(weeksOf(3, 3, 3, 3, 3, 3, 3, 3, 3, 12, 12, 12));
  assert.deepEqual(r, { baseline: 3, dataWeeks: 12, excludedWeeks: 3 });
  assert.equal(farmsNeeded(r.baseline, 3, 5), 9); // Severe at 9, not 16
});

test("a single spike in a quiet area is left out (3-farm floor)", () => {
  // Average 0.6 -> cut-off max(3, 2) = 3: the 3-farm week is an outbreak week.
  assert.deepEqual(weeklyBaseline(weeksOf(0, 0, 0, 0, 3)), { baseline: 0, dataWeeks: 5, excludedWeeks: 1 });
});

test("steady weeks: nothing is left out", () => {
  assert.deepEqual(weeklyBaseline(weeksOf(2, 3, 2, 3, 2, 3)), { baseline: 2.5, dataWeeks: 6, excludedWeeks: 0 });
  // Just under the cut-off (average 2.5 -> 5): a 4-farm week stays in.
  assert.equal(weeklyBaseline(weeksOf(2, 2, 2, 4)).excludedWeeks, 0);
});

test("guard: if every week were flagged, the plain average is used", () => {
  const rules = { ...OUTBREAK_RULES, moderateRatio: 0, minModerateFarms: 0 };
  assert.deepEqual(weeklyBaseline(weeksOf(1, 2, 3), rules), { baseline: 2, dataWeeks: 3, excludedWeeks: 0 });
});

test("cut-offs: 2x / 3x the baseline, never below 3 / 5 farms", () => {
  // baseline farms per week -> [moderate at, severe at]
  const table: [number, number, number][] = [
    [0, 3, 5], [0.5, 3, 5], [1, 3, 5], [1.5, 3, 5], [2, 4, 6], [2.5, 5, 8], [3, 6, 9], [5, 10, 15],
  ];
  for (const [baseline, moderate, severe] of table) {
    assert.equal(farmsNeeded(baseline, 2, 3), moderate, `moderate @ ${baseline}`);
    assert.equal(farmsNeeded(baseline, 3, 5), severe, `severe @ ${baseline}`);
  }
});

test("while collecting, any number of reports stays Mild (Undetermined)", () => {
  assert.equal(classifyOutbreak({ farms: 20, score: 20, baseline: 0, status: "collecting" }).tier, "mild");
});

test("zero baseline: the farm minimums decide", () => {
  const level = (farms: number, score = farms) => classifyOutbreak({ farms, score, baseline: 0, status: "provisional" }).tier;
  assert.equal(level(2), "mild");
  assert.equal(level(3), "moderate");
  assert.equal(level(4), "moderate");
  assert.equal(level(5), "severe");
});

test("a disease that is normally present needs more farms", () => {
  const r = classifyOutbreak({ farms: 5, score: 5, baseline: 2, status: "established" });
  assert.deepEqual(r, { tier: "moderate", moderateAt: 4, severeAt: 6 });
  assert.equal(classifyOutbreak({ farms: 3, score: 3, baseline: 2, status: "established" }).tier, "mild");
  assert.equal(classifyOutbreak({ farms: 6, score: 6, baseline: 2, status: "established" }).tier, "severe");
});

test("weak evidence: farms are enough but the score is not", () => {
  assert.equal(classifyOutbreak({ farms: 3, score: 1.8, baseline: 0, status: "provisional" }).tier, "mild");
  assert.equal(classifyOutbreak({ farms: 5, score: 3.5, baseline: 0, status: "provisional" }).tier, "moderate");
});

test("borrowed baseline: averaged over the neighbours that scan; silent ones don't dilute", () => {
  const own = [{ hasData: false, farms: 0 }, { hasData: false, farms: 0 }];
  const a = [{ hasData: true, farms: 4 }, { hasData: true, farms: 2 }];
  const b = [{ hasData: true, farms: 2 }, { hasData: false, farms: 0 }];
  const silent = [{ hasData: false, farms: 0 }, { hasData: false, farms: 0 }];
  const { activeAreas, weeks } = neighbourhoodWeeks([own, a, b, silent]);
  assert.equal(activeAreas, 2);
  assert.deepEqual(weeks, [{ hasData: true, farms: 3 }, { hasData: true, farms: 1 }]);
  assert.equal(weeklyBaseline(weeks).baseline, 2);
  assert.deepEqual(neighbourhoodWeeks([own, silent]), {
    activeAreas: 0, weeks: [{ hasData: false, farms: 0 }, { hasData: false, farms: 0 }],
  });
});

test("data-poor cluster: Severe's minimums, only while collecting", () => {
  assert.equal(isDataPoorCluster({ farms: 5, score: 4, status: "collecting" }), true);
  assert.equal(isDataPoorCluster({ farms: 4, score: 5, status: "collecting" }), false);
  assert.equal(isDataPoorCluster({ farms: 5, score: 3.5, status: "collecting" }), false);
  assert.equal(isDataPoorCluster({ farms: 9, score: 9, status: "provisional" }), false);
});
