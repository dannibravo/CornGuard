import { modifyAccountCredentials } from "@convex-dev/auth/server";
import { ConvexError, v } from "convex/values";
import { internal } from "./_generated/api";
import type { Id } from "./_generated/dataModel";
import { internalAction, internalMutation, type MutationCtx } from "./_generated/server";
import { DISEASE_LABEL, isKnownBarangay } from "./outbreakAlerts";
import { scheduleRecompute } from "./barangayStats";

/**
 * Sets a new password for an existing email/password account, keeping the account and all its data
 * (there is no password-reset flow in the app yet). CLI only — internal functions can't be called
 * from the app or the admin website:
 *
 *   npx convex run devAccounts:setPassword "{email:'demo@cornguard.app', password:'demo1234'}"
 */
export const setPassword = internalAction({
  args: { email: v.string(), password: v.string() },
  handler: async (ctx, { email, password }) => {
    if (password.length < 8) throw new ConvexError("Passwords need at least 8 characters.");
    await modifyAccountCredentials(ctx, { provider: "password", account: { id: email, secret: password } });
    return `Password updated for ${email}`;
  },
});

async function deleteUser(ctx: MutationCtx, userId: Id<"users">) {
  const removeFile = async (id?: Id<"_storage">) => { if (id) await ctx.storage.delete(id).catch(() => {}); };
  for (const r of await ctx.db.query("diagnosisRecords").withIndex("by_user", (q) => q.eq("userId", userId)).collect()) {
    await removeFile(r.imageId);
    await ctx.db.delete(r._id);
  }
  for (const p of await ctx.db.query("communityPosts").withIndex("by_user", (q) => q.eq("userId", userId)).collect()) {
    for (const c of await ctx.db.query("comments").withIndex("by_post", (q) => q.eq("postId", p._id)).collect()) await ctx.db.delete(c._id);
    for (const pv of await ctx.db.query("postVotes").withIndex("by_post", (q) => q.eq("postId", p._id)).collect()) await ctx.db.delete(pv._id);
    await removeFile(p.imageId);
    await ctx.db.delete(p._id);
  }
  for (const c of await ctx.db.query("comments").filter((q) => q.eq(q.field("userId"), userId)).collect()) await ctx.db.delete(c._id);
  for (const pv of await ctx.db.query("postVotes").filter((q) => q.eq(q.field("userId"), userId)).collect()) await ctx.db.delete(pv._id);
  for (const f of await ctx.db.query("farms").withIndex("by_owner", (q) => q.eq("ownerId", userId)).collect()) await ctx.db.delete(f._id);
  for (const t of await ctx.db.query("deviceTokens").withIndex("by_user", (q) => q.eq("userId", userId)).collect()) await ctx.db.delete(t._id);
  for (const n of await ctx.db.query("notifications").withIndex("by_recipient", (q) => q.eq("recipientUserId", userId)).collect()) await ctx.db.delete(n._id);
  // Convex Auth rows
  for (const s of await ctx.db.query("authSessions").withIndex("userId", (q) => q.eq("userId", userId)).collect()) {
    for (const t of await ctx.db.query("authRefreshTokens").withIndex("sessionId", (q) => q.eq("sessionId", s._id)).collect()) await ctx.db.delete(t._id);
    await ctx.db.delete(s._id);
  }
  for (const a of await ctx.db.query("authAccounts").withIndex("userIdAndProvider", (q) => q.eq("userId", userId)).collect()) {
    for (const c of await ctx.db.query("authVerificationCodes").withIndex("accountId", (q) => q.eq("accountId", a._id)).collect()) await ctx.db.delete(c._id);
    await ctx.db.delete(a._id);
  }
  await ctx.db.delete(userId);
}

/**
 * Removes leftover test data from a DEV deployment: every account whose email ends with `@test.dev`
 * (smoke-test and emulator accounts) with all its scans, posts, comments, votes, farms, devices,
 * notifications, photos and sign-in rows; plus, with `history: true`, all outbreaks and
 * notifications (test alerts), and with `demoReports: true`, the seeded map reports
 * (`localId` "map-demo-*"). Severity stats are then recomputed. CLI only:
 *
 *   npx convex run devAccounts:purgeTestData "{history: true, demoReports: true}"
 */
export const purgeTestData = internalMutation({
  args: { history: v.optional(v.boolean()), demoReports: v.optional(v.boolean()) },
  handler: async (ctx, { history, demoReports }) => {
    const testUsers = (await ctx.db.query("users").collect()).filter((u) => u.email?.endsWith("@test.dev"));
    for (const u of testUsers) await deleteUser(ctx, u._id);
    let outbreaks = 0, notifications = 0, reports = 0;
    if (history) {
      for (const o of await ctx.db.query("outbreaks").collect()) { await ctx.db.delete(o._id); outbreaks++; }
      for (const n of await ctx.db.query("notifications").collect()) { await ctx.db.delete(n._id); notifications++; }
    }
    if (demoReports) {
      for (const r of await ctx.db.query("diagnosisRecords").collect()) {
        if (!r.localId?.startsWith("map-demo-")) continue;
        if (r.imageId) await ctx.storage.delete(r.imageId).catch(() => {});
        await ctx.db.delete(r._id);
        reports++;
      }
    }
    await ctx.scheduler.runAfter(0, internal.barangayStats.recomputeAll, {});
    return { usersDeleted: testUsers.map((u) => u.email), outbreaks, notifications, demoReports: reports };
  },
});

/**
 * Empties the Outbreak Map on a DEV deployment: every shared scan (with its photo), every severity
 * row, every outbreak and the push log. Accounts, farms, devices and community posts are kept.
 * CLI only:
 *
 *   npx convex run devAccounts:resetMapData
 */
export const resetMapData = internalMutation({
  args: {},
  handler: async (ctx) => {
    let records = 0, stats = 0, outbreaks = 0, notifications = 0;
    for (const r of await ctx.db.query("diagnosisRecords").collect()) {
      if (r.imageId) await ctx.storage.delete(r.imageId).catch(() => {});
      await ctx.db.delete(r._id);
      records++;
    }
    for (const s of await ctx.db.query("barangayDiseaseStats").collect()) { await ctx.db.delete(s._id); stats++; }
    for (const o of await ctx.db.query("outbreaks").collect()) { await ctx.db.delete(o._id); outbreaks++; }
    for (const n of await ctx.db.query("notifications").collect()) { await ctx.db.delete(n._id); notifications++; }
    return { records, stats, outbreaks, notifications };
  },
});

/**
 * Moves a shared scan to corrected coordinates on a DEV deployment (e.g. one whose manual barangay
 * pick stored the barangay's centroid instead of the field's GPS). Barangay/municipality are kept.
 * CLI only:
 *
 *   npx convex run devAccounts:setRecordLocation "{recordId:'k97...', latitude:7.9, longitude:124.95}"
 */
export const setRecordLocation = internalMutation({
  args: { recordId: v.id("diagnosisRecords"), latitude: v.number(), longitude: v.number() },
  handler: async (ctx, { recordId, latitude, longitude }) => {
    const record = await ctx.db.get(recordId);
    if (!record) throw new ConvexError("No such record.");
    await ctx.db.patch(recordId, { latitude, longitude });
    return { before: [record.latitude, record.longitude], after: [latitude, longitude] };
  },
});

/**
 * Deletes one shared scan (and its photo) on a DEV deployment, then recomputes its barangay's
 * severity. For removing a mistaken test report. CLI only:
 *
 *   npx convex run devAccounts:deleteRecord "{recordId:'k97...'}"
 */
export const deleteRecord = internalMutation({
  args: { recordId: v.id("diagnosisRecords") },
  handler: async (ctx, { recordId }) => {
    const record = await ctx.db.get(recordId);
    if (!record) throw new ConvexError("No such record.");
    if (record.imageId) await ctx.storage.delete(record.imageId).catch(() => {});
    await ctx.db.delete(recordId);
    await scheduleRecompute(ctx, record);
    return { deleted: recordId, barangay: record.barangay, municipality: record.municipality };
  },
});

/**
 * Sends a TEST outbreak push to the farmers in and around a barangay, to check FCM delivery. CLI only
 * (needs the deploy key) — the admin website has no manual alert, by design. Recorded in the outbreak
 * History as source "manual" with a TEST message.
 *
 *   npx convex run devAccounts:sendTestAlert "{barangay:'Poblacion', municipality:'City of Valencia', diseaseCode:'northern_leaf_blight'}"
 */
export const sendTestAlert = internalMutation({
  args: { barangay: v.string(), municipality: v.string(), diseaseCode: v.string() },
  handler: async (ctx, { barangay, municipality, diseaseCode }) => {
    if (!isKnownBarangay(barangay, municipality)) throw new ConvexError("Unknown barangay / municipality.");
    if (!(diseaseCode in DISEASE_LABEL)) throw new ConvexError("Unknown disease code.");
    const now = Date.now();
    const outbreakId = await ctx.db.insert("outbreaks", {
      barangay, municipality, diseaseCodes: [diseaseCode], status: "approved", source: "manual",
      declaredAt: now, reviewedAt: now, message: "TEST NOTIFICATION — please ignore.",
    });
    await ctx.scheduler.runAfter(0, internal.outbreakAlerts.sendOutbreak, { outbreakId });
    return outbreakId;
  },
});

/**
 * Re-dates a shared scan, to test how the map ages reports without waiting weeks: 0-14 days =
 * active (counted), 14-28 = faded, older = hidden. The severity level is recomputed. CLI only:
 *
 *   npx convex run devAccounts:backdateRecord "{recordId:'<id>', daysAgo:20}"
 */
export const backdateRecord = internalMutation({
  args: { recordId: v.id("diagnosisRecords"), daysAgo: v.number() },
  handler: async (ctx, { recordId, daysAgo }) => {
    const record = await ctx.db.get(recordId);
    if (!record) throw new ConvexError("Record not found.");
    const capturedAt = Date.now() - daysAgo * 24 * 60 * 60 * 1000;
    await ctx.db.patch(recordId, { capturedAt });
    await scheduleRecompute(ctx, record);
    return `${record.diseaseCode} in ${record.barangay}, ${record.municipality} now dated ${new Date(capturedAt).toISOString().slice(0, 10)}`;
  },
});
