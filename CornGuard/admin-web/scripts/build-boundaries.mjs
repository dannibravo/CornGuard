// Builds the admin web's barangay outlines (src/assets/bukidnon-barangays.json) from the more
// detailed faeldon/philippines-json-maps "hires" boundaries, keeping our barangay and municipality
// names exactly as they are (stats, baselines and search key on them).
//
//   node scripts/build-boundaries.mjs <folder>
//
// <folder> holds Bukidnon's files from the v1.1.1 release asset geojson-hires.zip
// (https://github.com/faeldon/philippines-json-maps/releases/download/v1.1.1/geojson-hires.zip,
// SHA-256 d6aedda878af0884f112d992183639df61f5e74af380cf71a589055f9437a348):
//   geojson/municities/hires/bgysubmuns-municity-10013NN000.0.1.json   (22 files, one per municipality)
//   geojson/provdists/hires/municities-provdist-1001300000.0.1.json   (municipality names)
// Data: OCHA COD-AB Philippines (NAMRIA, PSA), PSGC 3Q 2026. See THIRD_PARTY_NOTICES.md.
//
// The Android app keeps its own, coarser file (android/app/src/main/assets/geo/); the backend's
// neighbour and centroid tables are generated from that one, not from this.
//
// Every one of our 464 barangays must be matched by name AND pass a location check: at least 80%
// of our old outline lies inside the new one, or (for small or oddly shaped barangays the old
// ~550 m simplification distorted) its centre moved under 400 m or no other new outline covers more
// of the old shape. Otherwise nothing is written.
//
// The names and the location check use the CURRENT file as the reference. On a re-run (the file
// already holds these outlines) every barangay simply matches itself.

import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const dir = process.argv[2];
if (!dir) throw new Error("Usage: node scripts/build-boundaries.mjs <folder with the hires files>");
const OUT = new URL("../src/assets/bukidnon-barangays.json", import.meta.url);

// Their spelling -> ours (ours are what the data uses; theirs are PSGC 3Q 2026 / COD-AB).
const MUNICIPALITY = { "City of Malaybalay": "City of Malaybalay (Capital)" };
const BARANGAY = {
  "Cabanglasan|Lambagan": "Lambangan",
  "City of Malaybalay (Capital)|Indalasa": "Indalaza",
  "City of Malaybalay (Capital)|Kibalabag": "Kabalabag",
  "City of Malaybalay (Capital)|St. Peter": "Saint Peter",
  "City of Valencia|Kahaponan": "Kahapunan",
  "City of Valencia|Lurugan": "Lurogan",
  "City of Valencia|Nabag-o": "Nabago",
  "Kalilangan|Barorawon": "Baborawon",
  "Kalilangan|Macaopao": "Maca-opao",
  "Kitaotao|Balocbocan": "Balukbukan",
  "Quezon|Merangeran": "Merangerang",
  "Sumilao|Kulasi": "Culasi",
};
const norm = (s) => s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase()
  .replace(/\(pob\.\)/g, "").replace(/\bsto\.\s*/g, "santo ").replace(/\bsta\.\s*/g, "santa ")
  .replace(/[-.]/g, " ").replace(/\s+/g, " ").trim();

const readJson = (path) => JSON.parse(readFileSync(path, "utf8"));
const files = readdirSync(dir, { recursive: true }).map(String);
const find = (re) => files.filter((f) => re.test(f.replace(/\\/g, "/"))).map((f) => join(dir, f));
const [provinceFile] = find(/municities-provdist-1001300000\.0\.1\.json$/);
if (!provinceFile) throw new Error("municities-provdist-1001300000.0.1.json not found in " + dir);
const munName = Object.fromEntries(readJson(provinceFile).features
  .map((f) => [String(f.properties.adm3_psgc), f.properties.adm3_en]));

// --- geometry helpers --------------------------------------------------------------------------
const outerRings = (g) => (g.type === "Polygon" ? [g.coordinates[0]] : g.coordinates.map((p) => p[0]));
const inRing = (ring, x, y) => {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i], [xj, yj] = ring[j];
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
};
const insideAny = (g, x, y) => outerRings(g).some((r) => inRing(r, x, y));
// Share of the old outline's area that lies inside the new one: a 40x40 grid of points over the
// old outline's bounding box, counting those inside it. (A centre-point test fails on curved,
// boomerang-shaped barangays whose centre lies outside even their own outline.)
const MIN_OVERLAP = 0.8;
const overlapShare = (oldG, newG) => {
  const pts = outerRings(oldG).flat();
  const xs = pts.map((p) => p[0]), ys = pts.map((p) => p[1]);
  const [x0, x1, y0, y1] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];
  let inOld = 0, inBoth = 0;
  for (let i = 0; i <= 40; i++) {
    for (let j = 0; j <= 40; j++) {
      const x = x0 + ((x1 - x0) * i) / 40, y = y0 + ((y1 - y0) * j) / 40;
      if (!insideAny(oldG, x, y)) continue;
      inOld++;
      if (insideAny(newG, x, y)) inBoth++;
    }
  }
  return inOld ? inBoth / inOld : 0;
};
const MAX_CENTRE_SHIFT_M = 400;
const centre = (g) => {
  const pts = outerRings(g).flat();
  return [pts.reduce((s, p) => s + p[0], 0) / pts.length, pts.reduce((s, p) => s + p[1], 0) / pts.length];
};
const metresApart = ([ax, ay], [bx, by]) =>
  Math.hypot((ax - bx) * 111_320 * Math.cos((ay * Math.PI) / 180), (ay - by) * 110_540);
// True when no other new barangay in the municipality covers more of the old outline than `mine`.
const candidatesByMunicipality = new Map();
const isBestOverlap = (oldG, mineNew, municipality) => {
  const share = overlapShare(oldG, mineNew.geometry);
  return (candidatesByMunicipality.get(municipality) ?? [])
    .every((other) => other === mineNew || overlapShare(oldG, other.geometry) <= share);
};
const round = (c) => (typeof c[0] === "number" ? [+c[0].toFixed(6), +c[1].toFixed(6)] : c.map(round));

// --- match ------------------------------------------------------------------------------------
const ours = readJson(OUT).features;
const ourIndex = new Map(ours.map((f) => [`${norm(f.properties.municipality)}|${norm(f.properties.barangay)}`, f]));
const used = new Map();
const overlaps = [];
const notes = [];
const problems = [];
const features = [];

for (const file of find(/bgysubmuns-municity-10013\d\d000\.0\.1\.json$/)) {
  const code = file.match(/municity-(\d+)\.0\.1\.json$/)[1];
  const municipality = MUNICIPALITY[munName[code]] ?? munName[code];
  if (!municipality) { problems.push(`no municipality name for ${code}`); continue; }
  const all = readJson(file).features;
  candidatesByMunicipality.set(municipality, all.filter((f) => f.properties.geo_level === "Bgy" && f.geometry));
  for (const f of all) {
    if (f.properties.geo_level !== "Bgy") continue;
    const barangay = BARANGAY[`${municipality}|${f.properties.adm4_en}`] ?? f.properties.adm4_en;
    const key = `${norm(municipality)}|${norm(barangay)}`;
    const mine = ourIndex.get(key);
    if (!mine) { problems.push(`not in our list: ${f.properties.adm4_en}, ${municipality}`); continue; }
    if (used.has(key)) { problems.push(`duplicate: ${barangay}, ${municipality}`); continue; }
    if (!f.geometry) { problems.push(`no shape: ${barangay}, ${municipality}`); continue; }
    const overlap = overlapShare(mine.geometry, f.geometry);
    if (overlap < MIN_OVERLAP) {
      // The old outlines were simplified at ~550 m, so small town barangays and odd shapes spill
      // into neighbours. Still the same place if its centre barely moved, or if no other new
      // outline covers more of the old shape than its own.
      const moved = metresApart(centre(mine.geometry), centre(f.geometry));
      if (moved > MAX_CENTRE_SHIFT_M && !isBestOverlap(mine.geometry, f, municipality)) {
        problems.push(`location check failed: ${barangay}, ${municipality} (${Math.round(overlap * 100)}% overlap, centre moved ${Math.round(moved)} m)`);
        continue;
      }
      notes.push(`${barangay}, ${municipality}: ${Math.round(overlap * 100)}% overlap, centre moved ${Math.round(moved)} m`);
    }
    overlaps.push(overlap);
    used.set(key, true);
    features.push({
      type: "Feature",
      properties: { barangay: mine.properties.barangay, municipality: mine.properties.municipality },
      geometry: { type: f.geometry.type, coordinates: round(f.geometry.coordinates) },
    });
  }
}
for (const [key, f] of ourIndex) {
  if (!used.has(key)) problems.push(`missing: ${f.properties.barangay}, ${f.properties.municipality}`);
}
if (problems.length) {
  console.error(problems.join("\n"));
  throw new Error(`${problems.length} problem(s); nothing written.`);
}

// Same order as before (by municipality, then barangay), so diffs stay readable.
const order = new Map(ours.map((f, i) => [`${f.properties.municipality}|${f.properties.barangay}`, i]));
features.sort((a, b) => order.get(`${a.properties.municipality}|${a.properties.barangay}`)
  - order.get(`${b.properties.municipality}|${b.properties.barangay}`));
const text = JSON.stringify({ type: "FeatureCollection", features });
writeFileSync(OUT, text);
const points = features.reduce((s, f) => s + outerRings(f.geometry).flat().length, 0);
console.log(`${features.length} barangays, ${new Set(features.map((f) => f.properties.municipality)).size} municipalities, `
  + `${points} outer-ring points, ${Math.round(text.length / 1024)} KB -> ${OUT.pathname}`);
if (notes.length) console.log(`accepted below ${MIN_OVERLAP * 100}% overlap (same place, coarse old outline):\n  ${notes.join("\n  ")}`);
overlaps.sort((a, b) => a - b);
console.log(`old outline inside new: lowest ${Math.round(overlaps[0] * 100)}%, `
  + `median ${Math.round(overlaps[overlaps.length >> 1] * 100)}%`);
