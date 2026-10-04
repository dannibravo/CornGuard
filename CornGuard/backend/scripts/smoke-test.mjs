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
const farmId = await a.client.mutation(api.users.createFarm, {
  name: "North field", barangay: "Poblacion", municipality: "Valencia", province: "Bukidnon",
});
const farms = await a.client.query(api.users.farmsForUser, { userId: viewerA.userId });
check("farm created and listed", farms.length === 1 && farms[0].farmId === farmId);

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
const shareScan = async (client, localId, confidence, diseaseCode = "northern_leaf_blight", withImage = true) => {
  const id = await client.mutation(api.diagnosisRecords.share, {
    localId, diseaseCode, confidence, capturedAt: Date.now(), imageId: withImage ? await upload(client) : undefined,
    barangay: TEST_BRGY, municipality: "Valencia", province: "Bukidnon", modelVersion: "cornguard_mobilenetv2_v3",
  });
  created.push([client, id]);
  return id;
};
const share = (client) => shareScan(client, "42", 0.93);
const recordId = await share(a.client);
check("share is idempotent per localId", (await share(a.client)) === recordId);

const mine = await a.client.query(api.diagnosisRecords.listForUser, { userId: viewerA.userId });
check("share at >= 85% is auto-verified", mine.find((r) => r.recordId === recordId)?.verificationStatus === "verified");
const lowId = await shareScan(a.client, "43", 0.6);
const mine2 = await a.client.query(api.diagnosisRecords.listForUser, { userId: viewerA.userId });
check("share below 85% waits for an admin", mine2.find((r) => r.recordId === lowId)?.verificationStatus === "unverified");

const statFor = async (client) => {
  await new Promise((r) => setTimeout(r, 2500)); // recompute runs as a scheduled mutation
  const stats = await client.query(api.barangayStats.getAllStats, {});
  return stats.find((s) => s.barangay === TEST_BRGY && s.diseaseCode === "northern_leaf_blight");
};
let stat = await statFor(b.client);
check("one farm's report -> mild", stat?.severityTier === "mild", stat && `score ${stat.weightedScore}, farms ${stat.distinctFarms}`);
await shareScan(b.client, "44", 0.95);
stat = await statFor(b.client);
check("two farms -> moderate", stat?.severityTier === "moderate", stat && `score ${stat.weightedScore}, farms ${stat.distinctFarms}`);
const c = await signIn(`farmer-c-${run}@test.dev`, "signUp");
await c.client.mutation(api.users.createProfile, {
  displayName: "Farmer C", barangay: "Poblacion", municipality: "Valencia", province: "Bukidnon",
});
await shareScan(c.client, "45", 0.96);
await shareScan(a.client, "46", 0.91);
stat = await statFor(b.client);
check("3 farms + score >= 4 -> SEVERE outbreak", stat?.severityTier === "severe" && stat?.isActiveOutbreak,
  stat && `score ${stat.weightedScore}, farms ${stat.distinctFarms}, reports ${stat.rawReportCount}`);

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

// --- Outbreak review (admin confirms before farmers are notified) ------------------------------
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const testOutbreakIds = [];
const pending = (await admin.query(api.admin.listOutbreaks, { status: "pending" })).find((o) => o.barangay === TEST_BRGY);
if (pending) testOutbreakIds.push(pending.outbreakId);
check("SEVERE barangay is queued as a PENDING outbreak", pending?.diseaseCodes.includes("northern_leaf_blight") && pending.recipientsNotified === null,
  pending && `diseases ${pending.diseaseCodes.join(",")}`);
const outbreakPushes = async () => (await admin.query(api.admin.recentNotifications, { limit: 100 }))
  .filter((n) => n.type === "outbreak_alert" && n.createdAt > run).length;
check("no outbreak push is sent before approval", (await outbreakPushes()) === 0);
await expectError("non-admin cannot list outbreaks", () => b.client.query(api.admin.listOutbreaks, {}));
await expectError("non-admin cannot approve an outbreak", () => b.client.mutation(api.admin.approveOutbreak, { outbreakId: pending.outbreakId }));
await expectError("non-admin cannot raise an alert", () => b.client.mutation(api.admin.raiseAlert, {
  barangay: "Barangay 1", municipality: "City of Malaybalay (Capital)", diseaseCodes: ["common_rust"] }));
await expectError("non-admin cannot read admin lists", () => b.client.query(api.admin.listRecords, {}));
await admin.mutation(api.admin.approveOutbreak, { outbreakId: pending.outbreakId, note: "smoke test" });
await sleep(2500);
const approved = (await admin.query(api.admin.listOutbreaks, { status: "approved" })).find((o) => o.outbreakId === pending.outbreakId);
check("approval sends the alert (recipients recorded)", approved?.recipientsNotified !== null && approved?.reviewedBy !== null,
  approved && `notified ${approved.recipientsNotified}`);
await expectError("an outbreak can't be approved twice", () => admin.mutation(api.admin.approveOutbreak, { outbreakId: pending.outbreakId }));
await expectError("manual alert for an unknown barangay rejected", () => admin.mutation(api.admin.raiseAlert, {
  barangay: "Nowhere", municipality: "Atlantis", diseaseCodes: ["common_rust"] }));
const manualId = await admin.mutation(api.admin.raiseAlert, {
  barangay: "Barangay 1", municipality: "City of Malaybalay (Capital)", diseaseCodes: ["common_rust", "gray_leaf_spot"],
  message: "Smoke test alert - please ignore." });
testOutbreakIds.push(manualId);
await sleep(2500);
const manual = (await admin.query(api.admin.listOutbreaks, {})).find((o) => o.outbreakId === manualId);
check("manual alert is approved and sent at once", manual?.status === "approved" && manual.source === "manual" && manual.recipientsNotified !== null);
const st = await admin.query(api.admin.stats, {});
const recs = await admin.query(api.admin.listRecords, { status: "unverified" });
const usersList = await admin.query(api.admin.listUsers, {});
check("admin stats + lists work", st.users >= 3 && recs.some((r) => r.recordId === noPhotoId) &&
  usersList.some((u) => u.email === emailA), `users ${st.users}, scans ${st.scans}, pending outbreaks ${st.pendingOutbreaks}`);

const notifications = await admin.query(api.admin.recentNotifications, { limit: 10 });
const reply = notifications.find((n) => n.type === "community_reply" && n.recipientUserId === viewerA.userId);
check("reply notification recorded for post author", !!reply, reply?.deliveryStatus);

await admin.mutation(api.admin.setPostModeration, { postId, status: "hidden" });
check("hidden post leaves feed", !(await b.client.query(api.posts.feed, {})).some((p) => p.postId === postId));
check("author still sees own hidden post", (await a.client.query(api.posts.get, { postId })) !== null);

await shareScan(b.client, "1", 0.99, "healthy");
await expectError("user cannot delete another user's record", () =>
  b.client.mutation(api.diagnosisRecords.remove, { recordId }));

for (const [client, id] of created) {
  await client.mutation(api.diagnosisRecords.remove, { recordId: id });
}
stat = await statFor(b.client);
check("deleting the reports clears the severity row", !stat);
try {   // remove this run's outbreak rows so the admin history only shows real ones
  execSync(`npx convex run outbreakAlerts:deleteOutbreaks "{outbreakIds:[${testOutbreakIds.map((i) => `'${i}'`).join(",")}]}"`,
    { stdio: "ignore", env: process.env });
} catch { /* Windows: Node may crash on exit after the command succeeded */ }
check("test outbreaks cleaned up", !(await admin.query(api.admin.listOutbreaks, {})).some((o) => testOutbreakIds.includes(o.outbreakId)));

await a.client.action(api.auth.signOut, {});
check("sign out", true);

console.log(`\n${failures === 0 ? "ALL CHECKS PASSED" : `${failures} CHECK(S) FAILED`}`);
process.exit(failures === 0 ? 0 : 1);
