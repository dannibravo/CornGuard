// End-to-end check of the CornGuard Convex backend, using the same calls the Android app makes
// (auth:signIn for tokens, then authenticated queries/mutations and an image upload).
//
// Usage (against a DEV or local deployment only — it creates test accounts and data):
//   node scripts/smoke-test.mjs [deploymentUrl]
// Defaults to CONVEX_URL from .env.local. The first-admin step needs the Convex CLI:
//   the script prints the exact `npx convex run admin:bootstrapFirstAdmin` command and waits.

import { ConvexHttpClient } from "convex/browser";
import { anyApi } from "convex/server";
import { readFileSync } from "node:fs";
import { execSync } from "node:child_process";

const api = anyApi;
const url =
  process.argv[2] ??
  readFileSync(new URL("../.env.local", import.meta.url), "utf8").match(/^CONVEX_URL=(.+)$/m)?.[1]?.trim();
if (!url) throw new Error("No deployment URL (pass one or set CONVEX_URL in .env.local).");

const run = Date.now();
const password = "correct-horse-42";
let failures = 0;

function check(label, condition, detail = "") {
  console.log(`${condition ? "PASS" : "FAIL"}  ${label}${detail ? `  (${detail})` : ""}`);
  if (!condition) failures++;
}

async function expectError(label, fn) {
  try {
    await fn();
    check(label, false, "no error thrown");
  } catch (e) {
    check(label, true, String(e.data ?? e.message).split("\n")[0].slice(0, 90));
  }
}

async function signIn(email, flow) {
  const anon = new ConvexHttpClient(url);
  const result = await anon.action(api.auth.signIn, {
    provider: "password",
    params: { email, password, flow },
  });
  const client = new ConvexHttpClient(url);
  client.setAuth(result.tokens.token);
  return { client, tokens: result.tokens };
}

// --- Accounts -------------------------------------------------------------------------------
const emailA = `farmer-a-${run}@test.dev`;
const emailB = `farmer-b-${run}@test.dev`;
const a = await signIn(emailA, "signUp");
const b = await signIn(emailB, "signUp");
check("sign up returns JWT + refresh token", !!a.tokens.token && !!a.tokens.refreshToken);

const viewerA = await a.client.query(api.users.viewer, {});
check("viewer returns own user id + email", viewerA?.email === emailA, viewerA?.userId);
check("profile absent before createProfile", (await a.client.query(api.users.get, { userId: viewerA.userId })) === null);

for (const [client, name] of [[a.client, "Farmer A"], [b.client, "Farmer B"]]) {
  await client.mutation(api.users.createProfile, {
    displayName: name, barangay: "Poblacion", municipality: "Valencia", province: "Bukidnon",
  });
}
const profileA = await b.client.query(api.users.get, { userId: viewerA.userId });
check("createProfile stores profile, role forced to farmer", profileA?.displayName === "Farmer A" && profileA.role === "farmer");
const viewerB = await b.client.query(api.users.viewer, {});

// --- Onboarding ("Fresh" accounts get the first-sign-in tutorial once) ------------------------
check("new account is Fresh", (await a.client.query(api.users.viewer, {}))?.onboardingStatus === "fresh");
await a.client.mutation(api.users.completeOnboarding, {});
check("completing the tutorial marks it done", (await a.client.query(api.users.viewer, {}))?.onboardingStatus === "done");
await a.client.mutation(api.users.createProfile, {
  displayName: "Farmer A", barangay: "Poblacion", municipality: "Valencia", province: "Bukidnon",
});
check("re-saving the profile doesn't make an account Fresh again", (await a.client.query(api.users.viewer, {}))?.onboardingStatus === "done");

await expectError("wrong password rejected", () =>
  new ConvexHttpClient(url).action(api.auth.signIn, {
    provider: "password", params: { email: emailA, password: "nope-nope-nope", flow: "signIn" },
  }));
await expectError("sign-up over an existing email with another password rejected", () =>
  new ConvexHttpClient(url).action(api.auth.signIn, {
    provider: "password", params: { email: emailA, password: "a-different-pass-9", flow: "signUp" },
  }));
await expectError("password shorter than 8 characters rejected", () =>
  new ConvexHttpClient(url).action(api.auth.signIn, {
    provider: "password", params: { email: `short-${run}@test.dev`, password: "abc", flow: "signUp" },
  }));
const again = await signIn(emailA, "signIn");
check("sign in with correct password", !!again.tokens.token);

const refreshed = await new ConvexHttpClient(url).action(api.auth.signIn, { refreshToken: a.tokens.refreshToken });
check("refresh token yields new JWT", !!refreshed?.tokens?.token && refreshed.tokens.token !== a.tokens.token);

// --- Farms ----------------------------------------------------------------------------------
// A farm is a pin plus the official Bukidnon barangay it falls in (resolved by the app).
const FARM = { barangay: "Poblacion", municipality: "City of Valencia", province: "Bukidnon", latitude: 7.9063, longitude: 125.0917 };
const farmId = await a.client.mutation(api.users.createFarm, { name: "  North field  ", ...FARM });
const farms = await a.client.query(api.users.farmsForUser, { userId: viewerA.userId });
check("farm created and listed (name trimmed, pin kept)",
  farms.length === 1 && farms[0].farmId === farmId && farms[0].name === "North field" && farms[0].latitude === FARM.latitude);

// --- Image upload + post --------------------------------------------------------------------
const jpeg = Buffer.from(
  "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACP/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AVN//2Q==",
  "base64",
);
const uploadUrl = await a.client.mutation(api.storage.generateUploadUrl, {});
const uploaded = await fetch(uploadUrl, { method: "POST", headers: { "Content-Type": "image/jpeg" }, body: jpeg });
const { storageId } = await uploaded.json();
check("image upload returns storageId", !!storageId);

const postId = await a.client.mutation(api.posts.create, {
  title: "Rust spreading", body: "Orange pustules on lower leaves", diseaseTag: "common_rust",
  imageId: storageId, barangay: "Poblacion", municipality: "Valencia", province: "Bukidnon",
});
const feed = await b.client.query(api.posts.feed, {});
const inFeed = feed.find((p) => p.postId === postId);
check("new post appears in feed (has createdAt)", !!inFeed && inFeed.createdAt > 0);
check("post imageUrl resolves", !!inFeed?.imageUrl && (await fetch(inFeed.imageUrl)).ok);
const areaFeed = await b.client.query(api.posts.feed, { areaField: "barangay", areaValue: "Poblacion", diseaseTag: "common_rust" });
check("area + disease filtered feed", areaFeed.some((p) => p.postId === postId));
const nearby = await b.client.query(api.posts.nearbyReports, { areaField: "barangay", areaValue: "Poblacion" });
check("nearby reports", nearby.some((r) => r.postId === postId));

// --- Comments, votes, ownership -------------------------------------------------------------
await b.client.mutation(api.posts.addComment, { postId, body: "Same on my farm." });
const comments = await a.client.query(api.posts.comments, { postId });
check("comment listed", comments.length === 1 && comments[0].body === "Same on my farm.");

const voted = await b.client.mutation(api.posts.toggleUpvote, { postId });
const afterVote = (await b.client.query(api.posts.get, { postId })).upvoteCount;
const unvoted = await b.client.mutation(api.posts.toggleUpvote, { postId });
const afterUnvote = (await b.client.query(api.posts.get, { postId })).upvoteCount;
check("upvote toggles counter 1 then 0", voted === true && afterVote === 1 && unvoted === false && afterUnvote === 0);

await expectError("non-owner cannot edit post", () =>
  b.client.mutation(api.posts.updateContent, { postId, title: "hijacked" }));
await expectError("non-admin cannot moderate", () =>
  b.client.mutation(api.admin.setPostModeration, { postId, status: "hidden" }));
await expectError("signed-out user cannot post", () =>
  new ConvexHttpClient(url).mutation(api.posts.create, {
    title: "x", body: "x", diseaseTag: "healthy", barangay: "x", municipality: "x", province: "x",
  }));

// --- Shared scans + heatmap severity ---------------------------------------------------------
// Scans go to a made-up barangay (matches no map polygon, no coordinates) so test data never
// shows up on the real heatmap; they're deleted again at the end.
const TEST_BRGY = `SmokeTest-${run}`;
const created = []; // [client, recordId]
const upload = async (client) => {
  const url = await client.mutation(api.storage.generateUploadUrl, {});
  const res = await fetch(url, { method: "POST", headers: { "Content-Type": "image/jpeg" }, body: jpeg });
  return (await res.json()).storageId;
};
// Like the app: every share carries its photo (auto-verify requires one).
const shareScan = async (client, localId, confidence, diseaseCode = "northern_leaf_blight", withImage = true, capturedAt = Date.now(), barangay = TEST_BRGY) => {
  const id = await client.mutation(api.diagnosisRecords.share, {
    localId, diseaseCode, confidence, capturedAt, imageId: withImage ? await upload(client) : undefined,
    barangay, municipality: "Valencia", province: "Bukidnon", modelVersion: "cornguard_mobilenetv2_v3",
  });
  created.push([client, id]);
  return id;
};
const scanTime = Date.now();
const share = (client) => shareScan(client, "42", 0.93, undefined, true, scanTime);
const recordId = await share(a.client);
check("share is idempotent per localId + capture time", (await share(a.client)) === recordId);

const mine = await a.client.query(api.diagnosisRecords.listForUser, { userId: viewerA.userId });
check("share at >= 60% is auto-verified", mine.find((r) => r.recordId === recordId)?.verificationStatus === "verified");
const lowId = await shareScan(a.client, "43", 0.5);
const mine2 = await a.client.query(api.diagnosisRecords.listForUser, { userId: viewerA.userId });
check("share below 60% waits for an admin", mine2.find((r) => r.recordId === lowId)?.verificationStatus === "unverified");

const statFor = async (client) => {
  await new Promise((r) => setTimeout(r, 2500)); // recompute runs as a scheduled mutation
  const stats = await client.query(api.barangayStats.getAllStats, {});
  return stats.find((s) => s.barangay === TEST_BRGY && s.diseaseCode === "northern_leaf_blight");
};
let stat = await statFor(b.client);
const statText = (s) => s && `${s.monitoringStatus}, score ${s.weightedScore}, farms ${s.distinctFarms}, ` +
  `baseline ${s.baselineFarms}, moderate at ${s.moderateAt}, severe at ${s.severeAt}`;
check("one farm's report -> mild", stat?.severityTier === "mild", statText(stat));
const newProfile = async (letter) => {
  const f = await signIn(`farmer-${letter}-${run}@test.dev`, "signUp");
  await f.client.mutation(api.users.createProfile, {
    displayName: `Farmer ${letter.toUpperCase()}`, barangay: "Poblacion", municipality: "Valencia", province: "Bukidnon",
  });
  return f;
};
const c = await newProfile("c");
await shareScan(b.client, "44", 0.95);
await shareScan(c.client, "45", 0.96);
await shareScan(a.client, "46", 0.91);
stat = await statFor(b.client);
// The barangay has no history, so nothing is known about what is normal there yet.
check("no history: 3 farms + score 4 stay MILD, outbreak status Undetermined (collecting data)",
  stat?.severityTier === "mild" && stat?.monitoringStatus === "collecting", statText(stat));

// --- Anti-spam rules on share ---------------------------------------------------------------
const noPhotoId = await shareScan(a.client, "47", 0.97, "gray_leaf_spot", false);
const mine3 = await a.client.query(api.diagnosisRecords.listForUser, { userId: viewerA.userId });
check("confident share WITHOUT a photo is not auto-verified", mine3.find((r) => r.recordId === noPhotoId)?.verificationStatus === "unverified");
await expectError("scan dated in the future rejected", () => a.client.mutation(api.diagnosisRecords.share, {
  localId: "48", diseaseCode: "common_rust", confidence: 0.9, capturedAt: Date.now() + 3600_000,
  barangay: TEST_BRGY, municipality: "Valencia", province: "Bukidnon", modelVersion: "cornguard_mobilenetv2_v3",
}));
const d = await signIn(`farmer-d-${run}@test.dev`, "signUp");
await d.client.mutation(api.users.createProfile, { displayName: "Farmer D", barangay: TEST_BRGY, municipality: "Valencia", province: "Bukidnon" });
for (let i = 0; i < 30; i++) await shareScan(d.client, `rl-${i}`, 0.5, "common_rust", false);
await expectError("31st share within 24 h rejected (rate limit)", () => shareScan(d.client, "rl-30", 0.5, "common_rust", false));

// --- My Farms: private pins, scans filed at the farm ----------------------------------------
await expectError("farm outside the Bukidnon boundaries rejected", () => a.client.mutation(api.users.createFarm, {
  ...FARM, name: "Elsewhere", barangay: "Nowhere", municipality: "Cagayan de Oro",
}));
check("another user can't see your farms", (await b.client.query(api.users.farmsForUser, { userId: viewerA.userId })).length === 0);
await expectError("can't share a scan from someone else's farm", () => b.client.mutation(api.diagnosisRecords.share, {
  localId: "farm-x", farmId, diseaseCode: "healthy", confidence: 0.9, capturedAt: Date.now(),
  barangay: TEST_BRGY, municipality: "Valencia", province: "Bukidnon", modelVersion: "cornguard_mobilenetv2_v3",
}));
// Healthy, so this never touches the map; the phone sends another barangay on purpose.
const farmScanId = await a.client.mutation(api.diagnosisRecords.share, {
  localId: "farm-1", farmId, diseaseCode: "healthy", confidence: 0.9, capturedAt: Date.now(),
  barangay: TEST_BRGY, municipality: "Valencia", province: "Bukidnon", latitude: 1, longitude: 1,
  modelVersion: "cornguard_mobilenetv2_v3",
});
created.push([a.client, farmScanId]);
const farmScan = (await a.client.query(api.diagnosisRecords.listForUser, { userId: viewerA.userId })).find((r) => r.recordId === farmScanId);
check("scan shared from a farm is filed in the farm's barangay",
  farmScan?.farmId === farmId && farmScan?.barangay === FARM.barangay && farmScan?.municipality === FARM.municipality,
  farmScan && `${farmScan.barangay}, ${farmScan.municipality}`);
await expectError("can't remove someone else's farm", () => b.client.mutation(api.users.deleteFarm, { farmId }));
await a.client.mutation(api.users.deleteFarm, { farmId });
check("owner removes a farm", (await a.client.query(api.users.farmsForUser, { userId: viewerA.userId })).length === 0);

// --- Devices --------------------------------------------------------------------------------
await a.client.mutation(api.devices.registerToken, { deviceId: "dev-1", fcmToken: "fake-token" });
await a.client.mutation(api.devices.registerToken, { deviceId: "dev-1", fcmToken: "fake-token-2" });
await a.client.mutation(api.devices.deactivateToken, { deviceId: "dev-1" });
check("device token register/deactivate", true);

// --- Admin ----------------------------------------------------------------------------------
console.log(`\nPromoting ${emailA} to admin via the CLI...`);
try {
  execSync(`npx convex run admin:bootstrapFirstAdmin "{email:'${emailA}'}"`, { stdio: "ignore", env: process.env });
} catch {
  // On Windows, Node can crash on exit *after* the command has succeeded; the next check verifies it.
}
check("first admin bootstrapped", (await a.client.query(api.users.viewer, {}))?.role === "admin");
const admin = (await signIn(emailA, "signIn")).client;
await admin.mutation(api.admin.setRecordVerification, { recordId, status: "verified" });
const occurrences = await b.client.query(api.diagnosisRecords.verifiedOccurrences, {
  areaField: "barangay", areaValue: TEST_BRGY,
});
check("verified record shows as occurrence", occurrences.some((o) => o.occurrenceId === recordId));
const heat = await b.client.query(api.diagnosisRecords.heatmapAggregates, { areaField: "municipality" });
check("heatmap aggregates", (heat.find((h) => h.areaLabel === "Valencia")?.diseaseBreakdown.northern_leaf_blight ?? 0) >= 1);
const nearSameTown = await b.client.query(api.diagnosisRecords.verifiedOccurrences, {
  areaField: "barangay", areaValue: TEST_BRGY, municipality: "City of Valencia",
});
check("'Reports near you' matches the town by normalised name", nearSameTown.some((o) => o.occurrenceId === recordId));
const nearOtherTown = await b.client.query(api.diagnosisRecords.verifiedOccurrences, {
  areaField: "barangay", areaValue: TEST_BRGY, municipality: "Maramag",
});
check("'Reports near you' ignores the same barangay name in another town", !nearOtherTown.some((o) => o.occurrenceId === recordId));
const healthyId = await a.client.mutation(api.diagnosisRecords.share, {
  localId: "healthy-map", diseaseCode: "healthy", confidence: 0.95, capturedAt: Date.now(), imageId: await upload(a.client),
  barangay: TEST_BRGY, municipality: "Valencia", province: "Bukidnon", latitude: 7.9, longitude: 125.09,
  modelVersion: "cornguard_mobilenetv2_v3",
});
created.push([a.client, healthyId]);
const markers = await b.client.query(api.diagnosisRecords.mapReports, {});
check("Healthy scans never appear on the map", !markers.some((m) => m.recordId === healthyId));
check("map markers are tagged active / recent", markers.every((m) => m.stage === "active" || m.stage === "recent"));
check("map markers carry the reporter's name", markers.every((m) => typeof m.farmerName === "string" && m.farmerName.length > 0));

// --- Baseline monitoring: history makes a barangay leave "collecting data" ---------------------
// Backdated shares aren't auto-verified, so the admin verifies them. Two verified scans a week for
// the 5 weeks before this one = 10 scans over more than 4 weeks: enough for detection to start.
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const DAY = 24 * 60 * 60 * 1000;
const h1 = await newProfile("h1");
const h2 = await newProfile("h2");
const backdated = async (client, localId, diseaseCode, week, barangay) => {
  const id = await shareScan(client, localId, 0.93, diseaseCode, true, Date.now() - (7 * week + 3.5) * DAY, barangay);
  await admin.mutation(api.admin.setRecordVerification, { recordId: id, status: "verified" });
};
const statIn = async (barangay) => {
  await sleep(2500); // recompute runs as a scheduled mutation
  return (await b.client.query(api.barangayStats.getAllStats, {}))
    .find((s) => s.barangay === barangay && s.diseaseCode === "northern_leaf_blight");
};

// Where the disease is normally reported (2 farms a week), 3 farms this week are not unusual.
const BUSY_BRGY = `${TEST_BRGY}-b2`;
for (let week = 1; week <= 5; week++) {
  await backdated(h1.client, `b2-h1-${week}`, "northern_leaf_blight", week, BUSY_BRGY);
  await backdated(h2.client, `b2-h2-${week}`, "northern_leaf_blight", week, BUSY_BRGY);
}
for (const [f, n] of [[a, "a"], [b, "b"], [c, "c"]]) await shareScan(f.client, `b2-now-${n}`, 0.95, "northern_leaf_blight", true, Date.now(), BUSY_BRGY);
const busyStat = await statIn(BUSY_BRGY);
check("usually present (baseline 2 farms/week): 3 farms -> only MILD (Moderate needs 4, Severe 6)",
  busyStat?.monitoringStatus === "provisional" && busyStat?.severityTier === "mild" && busyStat?.baselineFarms === 2 &&
  busyStat?.moderateAt === 4 && busyStat?.severeAt === 6 && busyStat?.lastWeekFarms === 2, statText(busyStat));

// Where the barangay scans regularly but has never reported this disease (baseline 0), a spike
// raises the level: 3 farms -> Moderate, 5 farms -> Severe.
const SPIKE_BRGY = `${TEST_BRGY}-b0`;
for (let week = 1; week <= 5; week++) {
  await backdated(h1.client, `b0-h1-${week}`, "healthy", week, SPIKE_BRGY);
  await backdated(h2.client, `b0-h2-${week}`, "healthy", week, SPIKE_BRGY);
}
for (const [f, n] of [[a, "a"], [b, "b"], [c, "c"]]) await shareScan(f.client, `b0-now-${n}`, 0.95, "northern_leaf_blight", true, Date.now(), SPIKE_BRGY);
let spikeStat = await statIn(SPIKE_BRGY);
check("baseline 0: 3 farms this week -> MODERATE", spikeStat?.monitoringStatus === "provisional" &&
  spikeStat?.severityTier === "moderate" && spikeStat?.baselineFarms === 0 && spikeStat?.dataWeeks === 5, statText(spikeStat));
const e = await newProfile("e");
const f = await newProfile("f");
for (const [x, n] of [[e, "e"], [f, "f"]]) await shareScan(x.client, `b0-now-${n}`, 0.95, "northern_leaf_blight", true, Date.now(), SPIKE_BRGY);
spikeStat = await statIn(SPIKE_BRGY);
check("baseline 0: 5 farms this week -> SEVERE", spikeStat?.severityTier === "severe" && spikeStat?.isActiveOutbreak, statText(spikeStat));

// --- Automatic alerts: Moderate = awareness, Severe = potential-outbreak warning, no admin step --
// Each goes out a minute after it is raised (diseases crossing the same level together share one alert).
const testOutbreakIds = [];
const allAlerts = async () => (await admin.query(api.admin.listOutbreaks, {})).filter((o) => o.barangay.startsWith(TEST_BRGY));
check("no alert while a barangay is collecting data, or at its usual level",
  !(await allAlerts()).some((o) => o.barangay === TEST_BRGY || o.barangay === BUSY_BRGY));
const alertsHere = async () => (await allAlerts())
  .filter((o) => o.barangay === SPIKE_BRGY && o.diseaseCodes.includes("northern_leaf_blight"));
let alerts = await alertsHere();
testOutbreakIds.push(...alerts.map((o) => o.outbreakId));
check("rising to moderate and to severe raised one alert each (automatic)",
  alerts.length === 2 && alerts.some((o) => o.level === "moderate") && alerts.some((o) => o.level === "severe") &&
  alerts.every((o) => o.source === "automatic"), alerts.map((o) => `${o.level}:${o.status}`).join(", "));
const deadline = Date.now() + 90_000;
while (Date.now() < deadline && alerts.some((o) => o.status !== "sent")) {
  await sleep(5000);
  alerts = await alertsHere();
}
check("both alerts are sent without an admin approving them",
  alerts.length === 2 && alerts.every((o) => o.status === "sent" && o.recipientsNotified !== null && o.reviewedBy === null),
  alerts.map((o) => `${o.level}:${o.status}, notified ${o.recipientsNotified}`).join("; "));
await shareScan(h1.client, "b0-after-severe", 0.95, "northern_leaf_blight", true, Date.now(), SPIKE_BRGY);
await sleep(2500);
check("another report at the same level sends nothing new", (await alertsHere()).length === 2);
await expectError("non-admin cannot list outbreaks", () => b.client.query(api.admin.listOutbreaks, {}));
await expectError("non-admin cannot read admin lists", () => b.client.query(api.admin.listRecords, {}));
if (alerts[0]) {
  const { outbreakId } = alerts[0];
  await expectError("non-admin cannot dismiss an alert", () => b.client.mutation(api.admin.dismissOutbreak, { outbreakId }));
  await expectError("there is no approve step any more (no admin.approveOutbreak)",
    () => admin.mutation(api.admin.approveOutbreak, { outbreakId }));
  await admin.mutation(api.admin.dismissOutbreak, { outbreakId, note: "smoke test" });
  const dismissed = (await alertsHere()).find((o) => o.outbreakId === outbreakId);
  check("admin can mark an alert a false alarm", dismissed?.status === "dismissed" && dismissed?.reviewedBy !== null);
}
await expectError("admins can no longer raise alerts by hand (no admin.raiseAlert)",
  () => admin.mutation(api.admin.raiseAlert, {}));
const st = await admin.query(api.admin.stats, {});
const recs = await admin.query(api.admin.listRecords, { status: "unverified" });
const usersList = await admin.query(api.admin.listUsers, {});
check("admin stats + lists work", st.users >= 3 && recs.some((r) => r.recordId === noPhotoId) &&
  usersList.some((u) => u.email === emailA) && typeof st.alertsSent === "number",
  `users ${st.users}, scans ${st.scans}, alerts sent ${st.alertsSent}`);

const notifications = await admin.query(api.admin.recentNotifications, { limit: 10 });
const reply = notifications.find((n) => n.type === "community_reply" && n.recipientUserId === viewerA.userId);
check("reply notification recorded for post author", !!reply, reply?.deliveryStatus);

// --- Reports (farmers flag posts/comments; admins resolve) ------------------------------------
const commentId = (await b.client.query(api.posts.comments, { postId }))[0].commentId;
await b.client.mutation(api.reports.create, { targetType: "post", targetId: postId, reason: "wrong_info", note: "  Wrong treatment advice  " });
await b.client.mutation(api.reports.create, { targetType: "post", targetId: postId, reason: "spam" });
await expectError("cannot report your own post", () =>
  a.client.mutation(api.reports.create, { targetType: "post", targetId: postId, reason: "spam" }));
check("reported post leaves the reporter's feed", !(await b.client.query(api.posts.feed, {})).some((p) => p.postId === postId));
check("reported post stays in the author's feed", (await a.client.query(api.posts.feed, {})).some((p) => p.postId === postId));
check("reporter can't open the reported post", (await b.client.query(api.posts.get, { postId })) === null);

await a.client.mutation(api.reports.create, { targetType: "comment", targetId: commentId, reason: "offensive" });
check("reported comment leaves the reporter's list", !(await a.client.query(api.posts.comments, { postId })).some((c) => c.commentId === commentId));
check("reported comment stays for its author", (await b.client.query(api.posts.comments, { postId })).some((c) => c.commentId === commentId));
check("reported comment drops out of the reporter's comment count", (await a.client.query(api.posts.get, { postId })).commentCount === 0);

await expectError("non-admin cannot list reports", () => b.client.query(api.admin.listReports, {}));
const openReports = await admin.query(api.admin.listReports, {});
const postReport = openReports.find((r) => r.targetId === postId);
check("post report queued once per reporter (repeat ignored)",
  postReport?.reporterCount === 1 && postReport.reasonCounts.wrong_info === 1 && !postReport.reasonCounts.spam,
  JSON.stringify(postReport?.reasonCounts));
check("report note trimmed", postReport?.notes[0] === "Wrong treatment advice");
check("comment report queued", openReports.some((r) => r.targetType === "comment" && r.targetId === commentId));
const openBefore = (await admin.query(api.admin.stats, {})).reportsOpen;
check("stats count open reported items", openBefore >= 2, String(openBefore));

await admin.mutation(api.admin.resolveReports, { targetType: "comment", targetId: commentId, outcome: "dismissed" });
check("dismissed report leaves the open queue", !(await admin.query(api.admin.listReports, {})).some((r) => r.targetId === commentId));
check("dismissed report listed as resolved",
  (await admin.query(api.admin.listReports, { status: "resolved" })).find((r) => r.targetId === commentId)?.outcome === "dismissed");
check("dismissing keeps the comment visible", (await b.client.query(api.posts.comments, { postId })).some((c) => c.commentId === commentId));
check("stats open count drops after dismiss", (await admin.query(api.admin.stats, {})).reportsOpen === openBefore - 1);

await admin.mutation(api.admin.setPostModeration, { postId, status: "hidden" });
check("hiding a reported post resolves its report as actioned",
  (await admin.query(api.admin.listReports, { status: "resolved" })).find((r) => r.targetId === postId)?.outcome === "actioned");
check("hidden post leaves feed", !(await b.client.query(api.posts.feed, {})).some((p) => p.postId === postId));
check("author still sees own hidden post", (await a.client.query(api.posts.get, { postId })) !== null);

await shareScan(b.client, "1", 0.99, "healthy");
await expectError("user cannot delete another user's record", () =>
  b.client.mutation(api.diagnosisRecords.remove, { recordId }));

// Newest first: deleting the backdated "usual level" scans before the current ones would lower the
// usual level while current reports remain, raising the level and sending real (test) alerts.
for (const [client, id] of [...created].reverse()) {
  await client.mutation(api.diagnosisRecords.remove, { recordId: id });
}
stat = await statFor(b.client);
check("deleting the reports clears the severity row", !stat);
// Any alert this run raised anywhere (its barangays all start with TEST_BRGY), not only the ones checked above.
testOutbreakIds.push(...(await admin.query(api.admin.listOutbreaks, {}))
  .filter((o) => o.barangay.startsWith(TEST_BRGY) && !testOutbreakIds.includes(o.outbreakId)).map((o) => o.outbreakId));
try {   // remove this run's outbreak rows so the admin history only shows real ones
  execSync(`npx convex run outbreakAlerts:deleteOutbreaks "{outbreakIds:[${testOutbreakIds.map((i) => `'${i}'`).join(",")}]}"`,
    { stdio: "ignore", env: process.env });
} catch { /* Windows: Node may crash on exit after the command succeeded */ }
check("test outbreaks cleaned up", !(await admin.query(api.admin.listOutbreaks, {})).some((o) => testOutbreakIds.includes(o.outbreakId)));

await a.client.action(api.auth.signOut, {});
check("sign out", true);

console.log(`\n${failures === 0 ? "ALL CHECKS PASSED" : `${failures} CHECK(S) FAILED`}`);
process.exit(failures === 0 ? 0 : 1);
