// Seeds demo reports onto the Outbreak Heatmap, through the same `diagnosisRecords:share` call
// the app makes, so the severity engine (barangayStats) computes the tiers exactly as in real use.
//
//   node scripts/seed-map-demo.mjs          add the demo reports
//   node scripts/seed-map-demo.mjs --clear  remove them again
//
// Each share carries a photo, as in the app (auto-verify requires one). Set SEED_IMAGES to a
// dataset folder with Blight/ Common_Rust/ Gray_Leaf_Spot/ subfolders to upload real leaf photos
// (nicer in the admin website's review screens); otherwise a tiny placeholder JPEG is used.
//
// DEV deployments only. Uses the demo account plus two helper farmer accounts (created on first
// run) so one barangay reaches "severe" (3 farms). Every point is checked against the bundled
// Bukidnon boundaries so the barangay/municipality match the map polygons.

import { ConvexHttpClient } from "convex/browser";
import { anyApi } from "convex/server";
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const api = anyApi;
const url = readFileSync(new URL("../.env.local", import.meta.url), "utf8").match(/^CONVEX_URL=(.+)$/m)?.[1]?.trim();
if (!url) throw new Error("Set CONVEX_URL in .env.local.");
const clear = process.argv.includes("--clear");

const FOLDER = { northern_leaf_blight: "Blight", common_rust: "Common_Rust", gray_leaf_spot: "Gray_Leaf_Spot" };
const TINY_JPEG = Buffer.from(
  "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACP/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AVN//2Q==",
  "base64",
);
function photoFor(diseaseCode, i) {
  const dir = process.env.SEED_IMAGES;
  if (!dir) return TINY_JPEG;
  const folder = join(dir, FOLDER[diseaseCode]);
  const files = readdirSync(folder).filter((f) => /\.(jpe?g|png)$/i.test(f)).sort();
  return readFileSync(join(folder, files[(i * 7) % files.length]));
}
async function upload(client, bytes) {
  const uploadUrl = await client.mutation(api.storage.generateUploadUrl, {});
  const res = await fetch(uploadUrl, { method: "POST", headers: { "Content-Type": "image/jpeg" }, body: bytes });
  return (await res.json()).storageId;
}

const PASSWORD = "CornGuard-Demo-2026";
const ACCOUNTS = [
  { email: "demo@cornguard.app", name: "Demo Farmer" },
  { email: "demo2@cornguard.app", name: "Demo Farmer 2" },
  { email: "demo3@cornguard.app", name: "Demo Farmer 3" },
];

// account index, disease, confidence, [lng, lat]
const REPORTS = [
  // Valencia: 3 farms, weighted score 4 -> SEVERE (Northern Leaf Blight)
  [0, "northern_leaf_blight", 0.95, [125.09, 7.91]],
  [0, "northern_leaf_blight", 0.93, [125.092, 7.912]],
  [1, "northern_leaf_blight", 0.96, [125.088, 7.908]],
  [2, "northern_leaf_blight", 0.92, [125.091, 7.907]],
  // Malaybalay: 2 farms -> MODERATE (Common Rust)
  [1, "common_rust", 0.9, [125.128, 8.157]],
  [2, "common_rust", 0.88, [125.1285, 8.1575]],
  // Maramag: 1 farm -> MILD (Gray Leaf Spot)
  [2, "gray_leaf_spot", 0.91, [125.005, 7.763]],
];

// --- Point-in-polygon against the app's bundled boundaries -----------------------------------
const geo = JSON.parse(readFileSync(
  new URL("../../android/app/src/main/assets/geo/bukidnon-barangays.json", import.meta.url), "utf8"));
const inRing = (ring, x, y) => {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i], [xj, yj] = ring[j];
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
};
const resolve = ([x, y]) => {
  for (const f of geo.features) {
    const polys = f.geometry.type === "Polygon" ? [f.geometry.coordinates] : f.geometry.coordinates;
    for (const [outer, ...holes] of polys) {
      if (inRing(outer, x, y) && !holes.some((h) => inRing(h, x, y))) return f.properties;
    }
  }
  return null;
};

async function session({ email, name }) {
  const anon = new ConvexHttpClient(url);
  let result;
  try {
    result = await anon.action(api.auth.signIn, { provider: "password", params: { email, password: PASSWORD, flow: "signIn" } });
  } catch {
    result = await anon.action(api.auth.signIn, { provider: "password", params: { email, password: PASSWORD, flow: "signUp" } });
  }
  const client = new ConvexHttpClient(url);
  client.setAuth(result.tokens.token);
  const viewer = await client.query(api.users.viewer, {});
  if (!(await client.query(api.users.get, { userId: viewer.userId }))) {
    await client.mutation(api.users.createProfile, {
      displayName: name, barangay: "Poblacion", municipality: "City of Valencia", province: "Bukidnon",
    });
  }
  return client;
}

const clients = [];
for (const account of ACCOUNTS) clients.push(await session(account));

for (const [i, [who, diseaseCode, confidence, point]] of REPORTS.entries()) {
  const area = resolve(point);
  if (!area) throw new Error(`Point ${point} is outside Bukidnon`);
  const client = clients[who];
  const recordId = await client.mutation(api.diagnosisRecords.share, {
    localId: `map-demo-${i}`, diseaseCode, confidence, capturedAt: Date.now(),
    barangay: area.barangay, municipality: area.municipality, province: "Bukidnon",
    latitude: point[1], longitude: point[0], modelVersion: "cornguard_mobilenetv2_v3",
    imageId: clear ? undefined : await upload(client, photoFor(diseaseCode, i)),
  });
  if (clear) {
    await client.mutation(api.diagnosisRecords.remove, { recordId });
    console.log(`removed  ${diseaseCode.padEnd(21)} ${area.barangay}, ${area.municipality}`);
  } else {
    console.log(`shared   ${diseaseCode.padEnd(21)} ${Math.round(confidence * 100)}%  ${area.barangay}, ${area.municipality}  (${ACCOUNTS[who].email})`);
  }
}

await new Promise((r) => setTimeout(r, 3000)); // recompute runs as a scheduled mutation
const stats = await clients[0].query(api.barangayStats.getAllStats, {});
console.log(`\nbarangayDiseaseStats now (${stats.length} rows):`);
for (const s of stats) {
  console.log(`  ${s.severityTier.toUpperCase().padEnd(8)} ${s.diseaseCode.padEnd(21)} ${s.barangay}, ${s.municipality}  score ${s.weightedScore}, farms ${s.distinctFarms}, reports ${s.rawReportCount}`);
}
