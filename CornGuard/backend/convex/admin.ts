import { ConvexError, v } from "convex/values";
import { internal } from "./_generated/api";
import { Doc, Id } from "./_generated/dataModel";
import { QueryCtx, internalMutation, mutation, query } from "./_generated/server";
import { scheduleRecompute } from "./barangayStats";
import { requireAdmin } from "./lib/access";
import { DISEASE_LABEL, isKnownBarangay, recipientsFor } from "./outbreakAlerts";
import { moderationStatus, verificationStatus } from "./schema";

const reviewStatus = v.union(v.literal("verified"), v.literal("rejected"));

export const setRecordVerification = mutation({
  args: { recordId: v.string(), status: reviewStatus },
  handler: async (ctx, { recordId, status }) => {
    const admin = await requireAdmin(ctx);
    const id = ctx.db.normalizeId("diagnosisRecords", recordId);
    const record = id ? await ctx.db.get(id) : null;
    if (!id || !record) throw new ConvexError("Record not found.");
    await ctx.db.patch(id, { verificationStatus: status, verifiedBy: admin._id, verifiedAt: Date.now() });
    await scheduleRecompute(ctx, record); // heatmap severity changes with verification
  },
});

export const setPostVerification = mutation({
  args: { postId: v.string(), status: reviewStatus },
  handler: async (ctx, { postId, status }) => {
    const admin = await requireAdmin(ctx);
    const id = ctx.db.normalizeId("communityPosts", postId);
    if (!id || !(await ctx.db.get(id))) throw new ConvexError("Post not found.");
    await ctx.db.patch(id, { verificationStatus: status, verifiedBy: admin._id, verifiedAt: Date.now() });
  },
});

export const setPostModeration = mutation({
  args: { postId: v.string(), status: moderationStatus },
  handler: async (ctx, { postId, status }) => {
    await requireAdmin(ctx);
    const id = ctx.db.normalizeId("communityPosts", postId);
    if (!id || !(await ctx.db.get(id))) throw new ConvexError("Post not found.");
    await ctx.db.patch(id, { moderationStatus: status });
  },
});

export const setCommentModeration = mutation({
  args: { commentId: v.string(), status: moderationStatus },
  handler: async (ctx, { commentId, status }) => {
    await requireAdmin(ctx);
    const id = ctx.db.normalizeId("comments", commentId);
    if (!id || !(await ctx.db.get(id))) throw new ConvexError("Comment not found.");
    await ctx.db.patch(id, { moderationStatus: status });
  },
});

export const setAccountStatus = mutation({
  args: { userId: v.string(), status: v.union(v.literal("active"), v.literal("suspended")) },
  handler: async (ctx, { userId, status }) => {
    const admin = await requireAdmin(ctx);
    const id = ctx.db.normalizeId("users", userId);
    if (!id || !(await ctx.db.get(id))) throw new ConvexError("User not found.");
    if (id === admin._id && status === "suspended") throw new ConvexError("You can't suspend your own account.");
    await ctx.db.patch(id, { accountStatus: status });
    // A suspended user's reports stop counting toward outbreak severity (and count again if reactivated).
    const records = await ctx.db.query("diagnosisRecords").withIndex("by_user", (q) => q.eq("userId", id)).collect();
    const areas = new Map(records.map((r) => [`${r.barangay}|${r.municipality}|${r.diseaseCode}`, r]));
    for (const r of areas.values()) await scheduleRecompute(ctx, r);
  },
});

/** Only an existing admin can promote another user (formerly the promoteToAdmin callable). */
export const promoteToAdmin = mutation({
  args: { userId: v.string() },
  handler: async (ctx, { userId }) => {
    await requireAdmin(ctx);
    const id = ctx.db.normalizeId("users", userId);
    if (!id || !(await ctx.db.get(id))) throw new ConvexError("User not found.");
    await ctx.db.patch(id, { role: "admin" });
  },
});

/**
 * Creates the very first admin. Not callable from the app — run once from a terminal:
 *   npx convex run admin:bootstrapFirstAdmin '{"email":"you@example.com"}'
 */
export const bootstrapFirstAdmin = internalMutation({
  args: { email: v.string() },
  handler: async (ctx, { email }) => {
    const user = await ctx.db
      .query("users")
      .withIndex("email", (q) => q.eq("email", email))
      .unique();
    if (!user) throw new ConvexError(`No account registered with ${email}.`);
    await ctx.db.patch(user._id, { role: "admin" });
    return user._id;
  },
});

/** Upserts published reference content for one disease. */
export const updateDiseaseReference = mutation({
  args: {
    diseaseCode: v.string(),
    displayName: v.optional(v.string()),
    symptoms: v.optional(v.string()),
    treatmentSteps: v.optional(v.string()),
    preventionSteps: v.optional(v.string()),
    causes: v.optional(v.string()),
    duration: v.optional(v.string()),
    sourceReference: v.optional(v.string()),
    contentVersion: v.optional(v.string()),
  },
  handler: async (ctx, { diseaseCode, ...fields }) => {
    await requireAdmin(ctx);
    const defined = Object.fromEntries(Object.entries(fields).filter(([, value]) => value !== undefined));
    const existing = await ctx.db
      .query("diseaseReferences")
      .withIndex("by_code", (q) => q.eq("diseaseCode", diseaseCode))
      .unique();
    if (existing) {
      await ctx.db.patch(existing._id, { ...defined, publishedAt: Date.now() });
    } else {
      await ctx.db.insert("diseaseReferences", {
        diseaseCode,
        displayName: fields.displayName ?? diseaseCode,
        symptoms: fields.symptoms ?? "",
        treatmentSteps: fields.treatmentSteps ?? "",
        preventionSteps: fields.preventionSteps ?? "",
        causes: fields.causes ?? "",
        duration: fields.duration ?? "",
        sourceReference: fields.sourceReference ?? "",
        contentVersion: fields.contentVersion ?? "1",
        publishedAt: Date.now(),
      });
    }
  },
});

// ------------------------------------------------------------------------------------------------
// Admin website (admin-web/) — lists and outbreak review. Everything requires an admin account.
// ------------------------------------------------------------------------------------------------

const nameOf = (u: Doc<"users"> | null) => u?.name ?? u?.email ?? "Unknown";

/** Dashboard counters. */
export const stats = query({
  args: {},
  handler: async (ctx) => {
    await requireAdmin(ctx);
    const [users, records, posts, outbreaks, sev] = await Promise.all([
      ctx.db.query("users").collect(),
      ctx.db.query("diagnosisRecords").collect(),
      ctx.db.query("communityPosts").collect(),
      ctx.db.query("outbreaks").withIndex("by_status", (q) => q.eq("status", "pending")).collect(),
      ctx.db.query("barangayDiseaseStats").collect(),
    ]);
    const count = <T,>(xs: T[], f: (x: T) => boolean) => xs.filter(f).length;
    return {
      users: users.length,
      admins: count(users, (u) => u.role === "admin"),
      suspendedUsers: count(users, (u) => u.accountStatus === "suspended"),
      scans: records.length,
      scansUnverified: count(records, (r) => r.verificationStatus === "unverified"),
      scansVerified: count(records, (r) => r.verificationStatus === "verified"),
      scansRejected: count(records, (r) => r.verificationStatus === "rejected"),
      posts: posts.length,
      postsUnverified: count(posts, (p) => p.verificationStatus === "unverified" && p.moderationStatus === "visible"),
      pendingOutbreaks: outbreaks.length,
      severeAreas: count(sev, (s) => s.severityTier === "severe" && s.rawReportCount > 0),
    };
  },
});

/** Shared scans for review (newest first), with photo and reporter. */
export const listRecords = query({
  args: { status: v.optional(verificationStatus), userId: v.optional(v.string()), limit: v.optional(v.number()) },
  handler: async (ctx, { status, userId, limit }) => {
    await requireAdmin(ctx);
    const uid = userId ? ctx.db.normalizeId("users", userId) : null;
    if (userId && !uid) return [];
    const rows = uid
      ? (await ctx.db.query("diagnosisRecords").withIndex("by_user", (q) => q.eq("userId", uid)).order("desc").collect())
          .filter((r) => !status || r.verificationStatus === status)
      : status
        ? await ctx.db.query("diagnosisRecords").withIndex("by_status", (q) => q.eq("verificationStatus", status)).order("desc").take(limit ?? 200)
        : await ctx.db.query("diagnosisRecords").order("desc").take(limit ?? 200);
    return await Promise.all(rows.map(async (r) => {
      const [user, verifier] = await Promise.all([ctx.db.get(r.userId), r.verifiedBy ? ctx.db.get(r.verifiedBy) : null]);
      return {
        recordId: r._id, userId: r.userId, userName: nameOf(user), userSuspended: user?.accountStatus === "suspended",
        diseaseCode: r.diseaseCode, confidence: r.confidence, status: r.verificationStatus,
        imageUrl: r.imageId ? await ctx.storage.getUrl(r.imageId) : null,
        barangay: r.barangay, municipality: r.municipality,
        hasLocation: r.latitude !== undefined && r.longitude !== undefined,
        capturedAt: r.capturedAt, sharedAt: r._creationTime, modelVersion: r.modelVersion,
        verifiedBy: verifier ? nameOf(verifier) : null, verifiedAt: r.verifiedAt ?? null,
      };
    }));
  },
});

/** Per-user history used as an authenticity signal for posts and scans. */
async function authorHistory(ctx: QueryCtx, userId: Id<"users">) {
  const records = await ctx.db.query("diagnosisRecords").withIndex("by_user", (q) => q.eq("userId", userId)).collect();
  const posts = await ctx.db.query("communityPosts").withIndex("by_user", (q) => q.eq("userId", userId)).collect();
  return {
    scansShared: records.length,
    scansVerified: records.filter((r) => r.verificationStatus === "verified").length,
    scansRejected: records.filter((r) => r.verificationStatus === "rejected").length,
    posts: posts.length,
    postsRemoved: posts.filter((p) => p.moderationStatus !== "visible").length,
  };
}

/** Community posts with the signals an admin needs to judge authenticity. */
export const listPosts = query({
  args: { limit: v.optional(v.number()) },
  handler: async (ctx, { limit }) => {
    await requireAdmin(ctx);
    const posts = await ctx.db.query("communityPosts").order("desc").take(limit ?? 200);
    const history = new Map<string, Awaited<ReturnType<typeof authorHistory>>>();
    return await Promise.all(posts.map(async (p) => {
      const author = await ctx.db.get(p.userId);
      if (!history.has(p.userId)) history.set(p.userId, await authorHistory(ctx, p.userId));
      const linkedId = p.linkedDiagnosisRecordId ? ctx.db.normalizeId("diagnosisRecords", p.linkedDiagnosisRecordId) : null;
      const linked = linkedId ? await ctx.db.get(linkedId) : null;
      const comments = await ctx.db.query("comments").withIndex("by_post", (q) => q.eq("postId", p._id)).collect();
      return {
        postId: p._id, userId: p.userId, userName: nameOf(author), userSuspended: author?.accountStatus === "suspended",
        title: p.title, body: p.body, diseaseTag: p.diseaseTag,
        imageUrl: p.imageId ? await ctx.storage.getUrl(p.imageId) : null,
        barangay: p.barangay, municipality: p.municipality, createdAt: p._creationTime,
        verificationStatus: p.verificationStatus, moderationStatus: p.moderationStatus,
        upvotes: p.upvoteCount, comments: comments.length,
        linkedScan: linked ? {
          recordId: linked._id, diseaseCode: linked.diseaseCode, confidence: linked.confidence,
          status: linked.verificationStatus, imageUrl: linked.imageId ? await ctx.storage.getUrl(linked.imageId) : null,
          sameAuthor: linked.userId === p.userId,
        } : null,
        authorHistory: history.get(p.userId)!,
      };
    }));
  },
});

export const listComments = query({
  args: { limit: v.optional(v.number()) },
  handler: async (ctx, { limit }) => {
    await requireAdmin(ctx);
    const comments = await ctx.db.query("comments").order("desc").take(limit ?? 300);
    return await Promise.all(comments.map(async (c) => {
      const [author, post] = await Promise.all([ctx.db.get(c.userId), ctx.db.get(c.postId)]);
      return {
        commentId: c._id, userId: c.userId, userName: nameOf(author), body: c.body,
        postId: c.postId, postTitle: post?.title ?? "(deleted post)",
        moderationStatus: c.moderationStatus, createdAt: c._creationTime,
      };
    }));
  },
});

export const listUsers = query({
  args: {},
  handler: async (ctx) => {
    await requireAdmin(ctx);
    const users = await ctx.db.query("users").order("desc").collect();
    return await Promise.all(users.map(async (u) => ({
      userId: u._id, name: u.name ?? null, email: u.email ?? null,
      role: u.role ?? "farmer", accountStatus: u.accountStatus ?? "active",
      barangay: u.barangay ?? "", municipality: u.municipality ?? "", createdAt: u._creationTime,
      hasDevice: (await ctx.db.query("deviceTokens").withIndex("by_user", (q) => q.eq("userId", u._id)).collect())
        .some((d) => d.active),
      ...(await authorHistory(ctx, u._id)),
    })));
  },
});

/** Outbreak review queue / history, with the current severity numbers for each disease. */
export const listOutbreaks = query({
  args: { status: v.optional(v.union(v.literal("pending"), v.literal("approved"), v.literal("dismissed"))) },
  handler: async (ctx, { status }) => {
    await requireAdmin(ctx);
    const rows = status
      ? await ctx.db.query("outbreaks").withIndex("by_status", (q) => q.eq("status", status)).order("desc").take(200)
      : await ctx.db.query("outbreaks").order("desc").take(200);
    return await Promise.all(rows.map(async (o) => {
      const severity = [];
      for (const code of o.diseaseCodes) {
        const s = await ctx.db.query("barangayDiseaseStats").withIndex("by_area_disease", (q) =>
          q.eq("barangay", o.barangay).eq("municipality", o.municipality).eq("diseaseCode", code)).unique();
        severity.push({ diseaseCode: code, tier: s?.severityTier ?? null, farms: s?.distinctFarms ?? 0,
          reports: s?.rawReportCount ?? 0, score: s?.weightedScore ?? 0 });
      }
      const reviewer = o.reviewedBy ? await ctx.db.get(o.reviewedBy) : null;
      const { nearby, withDevice } = o.status === "pending"
        ? await recipientsFor(ctx, o.barangay, o.municipality)
        : { nearby: [], withDevice: [] };
      return {
        outbreakId: o._id, barangay: o.barangay, municipality: o.municipality, diseaseCodes: o.diseaseCodes,
        status: o.status, source: o.source, declaredAt: o.declaredAt, message: o.message ?? null,
        reviewedBy: reviewer ? nameOf(reviewer) : null, reviewedAt: o.reviewedAt ?? null, note: o.note ?? null,
        recipientsNotified: o.recipientsNotified ?? null,
        wouldNotify: o.status === "pending" ? { farmersNearby: nearby.length, withDevice: withDevice.length } : null,
        severity,
      };
    }));
  },
});

const outbreakId = v.string();

/** Approve a pending outbreak: farmers nearby are notified (one combined alert). */
export const approveOutbreak = mutation({
  args: { outbreakId, note: v.optional(v.string()) },
  handler: async (ctx, { outbreakId, note }) => {
    const admin = await requireAdmin(ctx);
    const id = ctx.db.normalizeId("outbreaks", outbreakId);
    const o = id ? await ctx.db.get(id) : null;
    if (!id || !o) throw new ConvexError("Outbreak not found.");
    if (o.status !== "pending") throw new ConvexError(`This outbreak was already ${o.status}.`);
    await ctx.db.patch(id, { status: "approved", reviewedBy: admin._id, reviewedAt: Date.now(), note });
    await ctx.scheduler.runAfter(0, internal.outbreakAlerts.sendOutbreak, { outbreakId: id });
  },
});

/** Dismiss a pending outbreak (false alarm): nobody is notified. */
export const dismissOutbreak = mutation({
  args: { outbreakId, note: v.optional(v.string()) },
  handler: async (ctx, { outbreakId, note }) => {
    const admin = await requireAdmin(ctx);
    const id = ctx.db.normalizeId("outbreaks", outbreakId);
    const o = id ? await ctx.db.get(id) : null;
    if (!id || !o) throw new ConvexError("Outbreak not found.");
    if (o.status !== "pending") throw new ConvexError(`This outbreak was already ${o.status}.`);
    await ctx.db.patch(id, { status: "dismissed", reviewedBy: admin._id, reviewedAt: Date.now(), note });
  },
});

/** Manually raise an outbreak alert for a barangay (replaces caps 3's "Raise Alert"); sent immediately. */
export const raiseAlert = mutation({
  args: {
    barangay: v.string(), municipality: v.string(),
    diseaseCodes: v.array(v.string()), message: v.optional(v.string()),
  },
  handler: async (ctx, { barangay, municipality, diseaseCodes, message }) => {
    const admin = await requireAdmin(ctx);
    if (!isKnownBarangay(barangay, municipality)) throw new ConvexError("Unknown barangay / municipality.");
    const codes = diseaseCodes.filter((c) => c in DISEASE_LABEL);
    if (codes.length === 0) throw new ConvexError("Choose at least one disease.");
    const now = Date.now();
    const id = await ctx.db.insert("outbreaks", {
      barangay, municipality, diseaseCodes: codes, status: "approved", source: "manual",
      declaredAt: now, message: message?.trim() || undefined, reviewedBy: admin._id, reviewedAt: now,
    });
    await ctx.scheduler.runAfter(0, internal.outbreakAlerts.sendOutbreak, { outbreakId: id });
    return id;
  },
});

export const recentNotifications = query({
  args: { limit: v.optional(v.number()) },
  handler: async (ctx, { limit }) => {
    await requireAdmin(ctx);
    const rows = await ctx.db.query("notifications").order("desc").take(limit ?? 50);
    return rows.map((n) => ({
      notificationId: n._id,
      type: n.type,
      recipientUserId: n.recipientUserId ?? null,
      areaScope: n.areaScope ?? null,
      title: n.title,
      deliveryStatus: n.deliveryStatus,
      createdAt: n._creationTime,
    }));
  },
});
