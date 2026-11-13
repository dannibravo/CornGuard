// Drives the automatic disease-level alerts by hand, for testing on a phone/emulator. DEV only.
//
//   node scripts/alert-scenarios.mjs scan <account 1-8> <barangay> <municipality> [disease] [confidence]
//       Shares one confident scan (with a photo, so it auto-verifies) as demo<account>@cornguard.app
//       (account 1 = demo@cornguard.app), then prints the area's level and any alert it triggered.
//   node scripts/alert-scenarios.mjs status <barangay> <municipality>
//       Prints the area's levels, its bordering barangays, and each demo account's latest notifications.
//   node scripts/alert-scenarios.mjs history <barangay> <municipality> [weeks=6] [healthyPerWeek=3] [disease] [diseaseFarmsPerWeek=0]
//       Gives the area past verified scans (replacing earlier seeded ones), so it leaves "collecting data"
//       and has a baseline: e.g. `history Poblacion "City of Valencia" 6 3 common_rust 1` = 6 weeks,
//       3 healthy scans a week and 1 farm a week reporting common rust (baseline 1 farm/week).
//
// Disease codes: northern_leaf_blight (default), common_rust, gray_leaf_spot.
// Names must match the map's boundary file exactly, e.g. "Poblacion" "City of Valencia".
// Accounts are created on first use with profile barangay Poblacion, City of Valencia.
// Start over (wipes ALL map data and alerts on the dev deployment): npx convex run devAccounts:resetMapData

import { ConvexHttpClient } from "convex/browser";
import { anyApi } from "convex/server";
import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";

const api = anyApi;
const url = readFileSync(new URL("../.env.local", import.meta.url), "utf8").match(/^CONVEX_URL=(.+)$/m)?.[1]?.trim();
if (!url) throw new Error("Set CONVEX_URL in .env.local.");

const PASSWORD = "demo1234";
const emailOf = (n) => (n === 1 ? "demo@cornguard.app" : `demo${n}@cornguard.app`);
const nameOf = (n) => (n === 1 ? "Demo Farmer" : `Demo Farmer ${n}`);
const TINY_JPEG = Buffer.from(
  "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACP/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AVN//2Q==",
  "base64",
);

const geo = JSON.parse(readFileSync(
  new URL("../../android/app/src/main/assets/geo/bukidnon-barangays.json", import.meta.url), "utf8"));
const neighbours = JSON.parse(
  readFileSync(new URL("../convex/barangayNeighbors.ts", import.meta.url), "utf8")
    .replace(/^[\s\S]*?=\s*/, "").replace(/;\s*$/, "").replace(/,(\s*})\s*$/, "$1"));

/** A point inside the barangay (average of its outline), for the map dot. */
function pointIn(barangay, municipality) {
  const f = geo.features.find((x) => x.properties.barangay === barangay && x.properties.municipality === municipality);
  if (!f) throw new Error(`"${barangay}", "${municipality}" is not in the boundary file (check spelling).`);
  const ring = f.geometry.type === "Polygon" ? f.geometry.coordinates[0] : f.geometry.coordinates[0][0];
  const [x, y] = ring.reduce(([sx, sy], [px, py]) => [sx + px / ring.length, sy + py / ring.length], [0, 0]);
  return { latitude: y, longitude: x };
}

async function session(n) {
  const anon = new ConvexHttpClient(url);
  const params = { email: emailOf(n), password: PASSWORD };
  let result;
  try {
    result = await anon.action(api.auth.signIn, { provider: "password", params: { ...params, flow: "signIn" } });
  } catch {
    result = await anon.action(api.auth.signIn, { provider: "password", params: { ...params, flow: "signUp" } });
  }
  const client = new ConvexHttpClient(url);
  client.setAuth(result.tokens.token);
  const viewer = await client.query(api.users.viewer, {});
  if (!(await client.query(api.users.get, { userId: viewer.userId }))) {
    await client.mutation(api.users.createProfile, {
      displayName: nameOf(n), barangay: "Poblacion", municipality: "City of Valencia", province: "Bukidnon",
    });
  }
  return client;
}

async function printStatus(client, barangay, municipality) {
  const stats = (await client.query(api.barangayStats.getAllStats, {}))
    .filter((s) => s.barangay === barangay && s.municipality === municipality);
  console.log(`\nLevels in ${barangay}, ${municipality}:`);
  if (stats.length === 0) console.log("  (no reports)");
  for (const s of stats) {
    const level = s.monitoringStatus === "collecting" ? "UNDETERMINED" : s.severityTier.toUpperCase();
    console.log(`  ${level.padEnd(12)} ${s.diseaseCode.padEnd(21)} farms this week ${s.distinctFarms}, score ${s.weightedScore.toFixed(1)}`);
    console.log(s.monitoringStatus === "collecting"
      ? `               collecting data: ${s.historyScans} of 10 scans, since ${s.monitoringSince ? new Date(s.monitoringSince).toLocaleDateString() : "—"} (needs 4 weeks)`
      : `               ${s.monitoringStatus} baseline ${(s.baselineFarms ?? 0).toFixed(2)} farms/week over ${s.dataWeeks} week(s), ` +
        `last week ${s.lastWeekFarms}; moderate at ${s.moderateAt}, severe at ${s.severeAt} farms`);
  }
  console.log(`Bordering barangays (their farmers are alerted too):\n  ${(neighbours[`${barangay}||${municipality}`] ?? [])
    .map((k) => k.replace("||", ", ")).join("\n  ")}`);
}

async function printInboxes() {
  console.log("\nLatest notifications per demo account (alerts go out ~1 minute after a level rises):");
  for (let n = 1; n <= 6; n++) {
    const client = await session(n);
    const inbox = (await client.query(api.notifications.mine, {}))
      .filter((x) => x.type === "outbreak_alert" || x.type === "disease_awareness").slice(0, 3);
    console.log(`  ${emailOf(n)}:`);
    if (inbox.length === 0) console.log("    (none)");
    for (const x of inbox) {
      console.log(`    ${new Date(x.createdAt).toLocaleTimeString()}  [${x.type}] ${x.title}`);
    }
  }
}

const [cmd, ...rest] = process.argv.slice(2);
if (cmd === "scan") {
  const [acct, barangay, municipality, disease = "northern_leaf_blight", conf = "0.95"] = rest;
  const n = Number(acct);
  if (!(n >= 1 && n <= 8) || !barangay || !municipality) {
    throw new Error('Usage: scan <account 1-8> "<barangay>" "<municipality>" [disease] [confidence]');
  }
  const client = await session(n);
  const uploadUrl = await client.mutation(api.storage.generateUploadUrl, {});
  const res = await fetch(uploadUrl, { method: "POST", headers: { "Content-Type": "image/jpeg" }, body: TINY_JPEG });
  const { storageId } = await res.json();
  await client.mutation(api.diagnosisRecords.share, {
    localId: `scenario-${Date.now()}`, diseaseCode: disease, confidence: Number(conf), capturedAt: Date.now(),
    barangay, municipality, province: "Bukidnon", ...pointIn(barangay, municipality),
    modelVersion: "cornguard_mobilenetv2_v3", imageId: storageId,
  });
  console.log(`Shared ${disease} ${Math.round(Number(conf) * 100)}% as ${emailOf(n)} in ${barangay}, ${municipality}.`);
  await new Promise((r) => setTimeout(r, 3000)); // the level is recomputed by a scheduled mutation
  await printStatus(client, barangay, municipality);
} else if (cmd === "history") {
  const [barangay, municipality, weeks = "6", healthyPerWeek = "3", disease, farmsPerWeek = "0"] = rest;
  if (!barangay || !municipality) {
    throw new Error('Usage: history "<barangay>" "<municipality>" [weeks=6] [healthyPerWeek=3] [disease] [diseaseFarmsPerWeek=0]');
  }
  for (let n = 1; n <= 6; n++) await session(n); // the seeder uses the demo accounts
  // Same quoting as smoke-test.mjs (works in cmd and bash): "{key:'value', n:1}".
  const args = `{barangay:'${barangay}', municipality:'${municipality}', weeks:${Number(weeks)}, ` +
    `healthyPerWeek:${Number(healthyPerWeek)}, clear:true` +
    (disease ? `, diseaseCode:'${disease}', diseaseFarmsPerWeek:${Number(farmsPerWeek)}` : "") + "}";
  try {
    console.log(execSync(`npx convex run devAccounts:seedAreaHistory "${args}"`, { encoding: "utf8", env: process.env }).trim());
  } catch (e) {
    console.log(e.stdout?.toString().trim() || e.message);
  }
  await new Promise((r) => setTimeout(r, 3000));
  await printStatus(await session(1), barangay, municipality);
} else if (cmd === "status") {
  const [barangay, municipality] = rest;
  if (!barangay || !municipality) throw new Error('Usage: status "<barangay>" "<municipality>"');
  await printStatus(await session(1), barangay, municipality);
  await printInboxes();
} else {
  console.log("Commands: scan <account 1-8> <barangay> <municipality> [disease] [confidence] | " +
    "status <barangay> <municipality> | history <barangay> <municipality> [weeks] [healthyPerWeek] [disease] [farmsPerWeek]");
}
