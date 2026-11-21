/**
 * Location/identity redaction for the public map (mapReports). Pure functions with no Convex
 * imports, so they can be unit-tested (scripts/map-privacy.test.mts).
 *
 * The farms table already treats a farm's exact pin as private: `users.farmsForUser` returns []
 * for another user's farms. `diagnosisRecords.share` copies a farm's exact coordinates onto a
 * scan when it's shared from that farm, and the old `mapReports` re-exposed those exact
 * coordinates, plus the reporter's real name, to every signed-in user. This module is the fix:
 * mapReports keeps a viewer's own reports at full precision (it's their own data), and coarsens
 * everyone else's to their barangay's approximate centroid — never their exact farm location.
 * The reporter's name is dropped from the public response entirely; admins who need the exact
 * point and the reporter's identity (cluster investigation, dispute resolution) use the separate,
 * admin-gated `admin.mapReports` query instead, which does not call this module at all.
 */

// No imports here by design (same reason as lib/outbreakRules.ts): callers pass the centroid
// table in, so this file stays a plain, dependency-free module the test file can import directly.

export type MapPoint = { lat: number; lng: number };
export type CentroidTable = Record<string, MapPoint>;

/** Exact point, raw lookup by "barangay||municipality" (same convention as barangayNeighbors.ts). */
export function centroidFor(centroids: CentroidTable, barangay: string, municipality: string): MapPoint | null {
  return centroids[`${barangay}||${municipality}`] ?? null;
}

/**
 * The location to show a viewer for one report: exact, if it's their own; otherwise the
 * barangay's approximate centroid. Returns null when the viewer isn't the owner and no centroid
 * is known for that barangay — callers must drop the report rather than fall back to the exact
 * coordinates, since this function exists specifically to never do that for someone else's report.
 */
export function locationFor(
  centroids: CentroidTable,
  report: { userId: string; barangay: string; municipality: string; latitude: number; longitude: number },
  viewerId: string,
): { latitude: number; longitude: number } | null {
  if (report.userId === viewerId) return { latitude: report.latitude, longitude: report.longitude };
  const centroid = centroidFor(centroids, report.barangay, report.municipality);
  return centroid ? { latitude: centroid.lat, longitude: centroid.lng } : null;
}
