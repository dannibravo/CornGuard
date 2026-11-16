// Unit tests for the map-privacy redaction rules (convex/lib/mapPrivacy.ts). Run: npm run test:rules
import assert from "node:assert/strict";
import { test } from "node:test";
import { centroidFor, locationFor } from "../convex/lib/mapPrivacy.ts";
import { BARANGAY_CENTROIDS } from "../convex/lib/barangayCentroids.ts";

// A barangay known to exist in the bundled boundaries (asserted, not assumed, so this test fails
// loudly if the generated data file is ever empty or the key convention changes).
const [KNOWN_KEY, KNOWN_POINT] = Object.entries(BARANGAY_CENTROIDS)[0];
const [KNOWN_BARANGAY, KNOWN_MUNICIPALITY] = KNOWN_KEY.split("||");

test("centroidFor: known barangay returns its generated centroid", () => {
  assert.deepEqual(centroidFor(BARANGAY_CENTROIDS, KNOWN_BARANGAY, KNOWN_MUNICIPALITY), KNOWN_POINT);
});

test("centroidFor: unknown barangay returns null, not a guess", () => {
  assert.equal(centroidFor(BARANGAY_CENTROIDS, "Nowhere At All", "Nonexistent Town"), null);
});

test("locationFor: the viewer's own report keeps its exact coordinates", () => {
  const report = {
    userId: "user_1", barangay: KNOWN_BARANGAY, municipality: KNOWN_MUNICIPALITY,
    latitude: 8.123456, longitude: 125.123456,
  };
  assert.deepEqual(locationFor(BARANGAY_CENTROIDS, report, "user_1"), { latitude: 8.123456, longitude: 125.123456 });
});

test("locationFor: someone else's report is coarsened to the barangay centroid, never the exact pin", () => {
  const report = {
    userId: "user_1", barangay: KNOWN_BARANGAY, municipality: KNOWN_MUNICIPALITY,
    latitude: 8.123456, longitude: 125.123456,
  };
  const result = locationFor(BARANGAY_CENTROIDS, report, "user_2");
  assert.notEqual(result, null);
  assert.deepEqual(result, { latitude: KNOWN_POINT.lat, longitude: KNOWN_POINT.lng });
  // It must not leak the exact farm coordinates to a non-owner under any circumstance.
  assert.notEqual(result!.latitude, 8.123456);
  assert.notEqual(result!.longitude, 125.123456);
});

test("locationFor: a signed-out/anonymous viewer (empty id) is never treated as the owner", () => {
  const report = {
    userId: "user_1", barangay: KNOWN_BARANGAY, municipality: KNOWN_MUNICIPALITY,
    latitude: 8.123456, longitude: 125.123456,
  };
  const result = locationFor(BARANGAY_CENTROIDS, report, "");
  assert.notEqual(result, null);
  assert.deepEqual(result, { latitude: KNOWN_POINT.lat, longitude: KNOWN_POINT.lng });
});

test("locationFor: someone else's report in an unknown barangay is dropped (null), not shown exactly", () => {
  const report = {
    userId: "user_1", barangay: "Nowhere At All", municipality: "Nonexistent Town",
    latitude: 8.123456, longitude: 125.123456,
  };
  assert.equal(locationFor(BARANGAY_CENTROIDS, report, "user_2"), null);
});

test("every generated centroid is a plausible Bukidnon-area lat/lng (sanity bound, not survey-grade)", () => {
  for (const [key, point] of Object.entries(BARANGAY_CENTROIDS)) {
    assert.ok(point.lat > 6 && point.lat < 10, `${key} latitude ${point.lat} looks out of range`);
    assert.ok(point.lng > 123 && point.lng < 126, `${key} longitude ${point.lng} looks out of range`);
  }
});
