import { ConvexError, v } from "convex/values";
import { internal } from "./_generated/api";
import { Doc, Id } from "./_generated/dataModel";
import { mutation, query, QueryCtx } from "./_generated/server";
import { assertOwnerOrAdmin, currentUserId, isAdmin, requireActiveUser } from "./lib/access";
import { areaField } from "./schema";

async function authorName(ctx: QueryCtx, userId: Id<"users">) {
  return (await ctx.db.get(userId))?.name ?? "Farmer";
}

/** Visible comments on a post (plus the viewer's own hidden ones), oldest first. */
async function visibleComments(ctx: QueryCtx, postId: Id<"communityPosts">, viewerId: Id<"users"> | null) {
  const rows = await ctx.db
    .query("comments")
    .withIndex("by_post", (q) => q.eq("postId", postId))
    .collect();
  return rows.filter((c) => c.moderationStatus === "visible" || c.userId === viewerId);
}

async function toComment(ctx: QueryCtx, c: Doc<"comments">) {
  return {
    commentId: c._id,
    postId: c.postId,
    userId: c.userId,
    authorName: await authorName(ctx, c.userId),
    body: c.body,
    moderationStatus: c.moderationStatus,
    createdAt: c._creationTime,
  };
}

async function toPost(ctx: QueryCtx, p: Doc<"communityPosts">) {
  const viewerId = await currentUserId(ctx);
  const comments = await visibleComments(ctx, p._id, viewerId);
  const liked = viewerId
    ? await ctx.db
        .query("postVotes")
        .withIndex("by_post_user", (q) => q.eq("postId", p._id).eq("userId", viewerId))
        .unique()
    : null;
  return {
    authorName: await authorName(ctx, p.userId),
    commentCount: comments.length,
    likedByMe: liked !== null,
    recentComments: await Promise.all(comments.slice(-2).map((c) => toComment(ctx, c))),
    postId: p._id,
    userId: p.userId,
    linkedDiagnosisRecordId: p.linkedDiagnosisRecordId ?? null,
    title: p.title,
    body: p.body,
    diseaseTag: p.diseaseTag,
    imageUrl: p.imageId ? await ctx.storage.getUrl(p.imageId) : null,
    barangay: p.barangay,
    municipality: p.municipality,
    province: p.province,
    verificationStatus: p.verificationStatus,
    moderationStatus: p.moderationStatus,
    upvoteCount: p.upvoteCount,
    createdAt: p._creationTime,
  };
}

/** Visible posts, optionally narrowed to one area, newest first. */
function visiblePosts(
  ctx: QueryCtx,
  area: { field: "barangay" | "municipality" | "province"; value: string } | null,
) {
  const posts = ctx.db.query("communityPosts");
  if (!area) return posts.withIndex("by_moderation", (q) => q.eq("moderationStatus", "visible"));
  switch (area.field) {
    case "barangay":
      return posts.withIndex("by_moderation_barangay", (q) =>
        q.eq("moderationStatus", "visible").eq("barangay", area.value),
      );
    case "municipality":
      return posts.withIndex("by_moderation_municipality", (q) =>
        q.eq("moderationStatus", "visible").eq("municipality", area.value),
      );
    case "province":
      return posts.withIndex("by_moderation_province", (q) =>
        q.eq("moderationStatus", "visible").eq("province", area.value),
      );
  }
}

async function loadPost(ctx: QueryCtx, postId: string) {
  const id = ctx.db.normalizeId("communityPosts", postId);
  return id ? await ctx.db.get(id) : null;
}

export const create = mutation({
  args: {
    linkedDiagnosisRecordId: v.optional(v.string()),
    title: v.string(),
    body: v.string(),
    diseaseTag: v.string(),
    imageId: v.optional(v.id("_storage")),
    barangay: v.string(),
    municipality: v.string(),
    province: v.string(),
  },
  handler: async (ctx, args) => {
    const user = await requireActiveUser(ctx);
    // Status fields and the counter are never client-supplied.
    return await ctx.db.insert("communityPosts", {
      ...args,
      userId: user._id,
      verificationStatus: "unverified",
      moderationStatus: "visible",
      upvoteCount: 0,
    });
  },
});

export const feed = query({
  args: {
    areaField: v.optional(areaField),
    areaValue: v.optional(v.string()),
    diseaseTag: v.optional(v.string()),
    limit: v.optional(v.number()),
  },
  handler: async (ctx, { areaField, areaValue, diseaseTag, limit }) => {
    if (!(await currentUserId(ctx))) return [];
    let q = visiblePosts(ctx, areaField && areaValue !== undefined ? { field: areaField, value: areaValue } : null);
    if (diseaseTag) q = q.filter((f) => f.eq(f.field("diseaseTag"), diseaseTag));
    const posts = await q.order("desc").take(limit ?? 50);
    return await Promise.all(posts.map((p) => toPost(ctx, p)));
  },
});

/** Visible posts are readable by any signed-in user; hidden/removed ones only by the author or an admin. */
export const get = query({
  args: { postId: v.string() },
  handler: async (ctx, { postId }) => {
    const userId = await currentUserId(ctx);
    if (!userId) return null;
    const post = await loadPost(ctx, postId);
    if (!post) return null;
    if (post.moderationStatus !== "visible" && post.userId !== userId) {
      const viewer = await ctx.db.get(userId);
      if (!isAdmin(viewer)) return null;
    }
    return await toPost(ctx, post);
  },
});

/** Only title/body/diseaseTag are author-editable. */
export const updateContent = mutation({
  args: {
    postId: v.string(),
    title: v.optional(v.string()),
    body: v.optional(v.string()),
    diseaseTag: v.optional(v.string()),
  },
  handler: async (ctx, { postId, ...fields }) => {
    const user = await requireActiveUser(ctx);
    const post = await loadPost(ctx, postId);
    if (!post) throw new ConvexError("Post not found.");
    if (post.userId !== user._id) throw new ConvexError("You can only edit your own posts.");
    const patch: Partial<Doc<"communityPosts">> = {};
    if (fields.title !== undefined) patch.title = fields.title;
    if (fields.body !== undefined) patch.body = fields.body;
    if (fields.diseaseTag !== undefined) patch.diseaseTag = fields.diseaseTag;
    await ctx.db.patch(post._id, patch);
  },
});

export const remove = mutation({
  args: { postId: v.string() },
  handler: async (ctx, { postId }) => {
    const user = await requireActiveUser(ctx);
    const post = await loadPost(ctx, postId);
    if (!post) return;
    assertOwnerOrAdmin(post.userId, user);
    for (const c of await ctx.db.query("comments").withIndex("by_post", (q) => q.eq("postId", post._id)).collect()) {
      await ctx.db.delete(c._id);
    }
    for (const vote of await ctx.db.query("postVotes").withIndex("by_post", (q) => q.eq("postId", post._id)).collect()) {
      await ctx.db.delete(vote._id);
    }
    if (post.imageId) await ctx.storage.delete(post.imageId);
    await ctx.db.delete(post._id);
  },
});

/**
 * Adds a comment and — unless the author is replying to their own post — records a
 * `community_reply` notification and schedules its push (formerly the onCommentCreate function).
 */
export const addComment = mutation({
  args: { postId: v.string(), body: v.string() },
  handler: async (ctx, { postId, body }) => {
    const user = await requireActiveUser(ctx);
    const post = await loadPost(ctx, postId);
    if (!post || post.moderationStatus !== "visible") throw new ConvexError("Post not found.");
    const commentId = await ctx.db.insert("comments", {
      postId: post._id,
      userId: user._id,
      body,
      moderationStatus: "visible",
    });
    if (post.userId !== user._id) {
      const notificationId = await ctx.db.insert("notifications", {
        recipientUserId: post.userId,
        type: "community_reply",
        title: "New reply on your post",
        message: body.slice(0, 140),
        relatedPostId: post._id,
        deliveryStatus: "pending",
      });
      await ctx.scheduler.runAfter(0, internal.push.sendNotification, { notificationId });
    }
    return commentId;
  },
});

/** Visible comments, oldest first; the viewer also sees their own hidden ones. */
export const comments = query({
  args: { postId: v.string() },
  handler: async (ctx, { postId }) => {
    const userId = await currentUserId(ctx);
    if (!userId) return [];
    const id = ctx.db.normalizeId("communityPosts", postId);
    if (!id) return [];
    const rows = await visibleComments(ctx, id, userId);
    return await Promise.all(rows.map((c) => toComment(ctx, c)));
  },
});

/** Toggles the viewer's vote and adjusts the counter in the same transaction. Returns true if now voted. */
export const toggleUpvote = mutation({
  args: { postId: v.string() },
  handler: async (ctx, { postId }) => {
    const user = await requireActiveUser(ctx);
    const post = await loadPost(ctx, postId);
    if (!post) throw new ConvexError("Post not found.");
    const existing = await ctx.db
      .query("postVotes")
      .withIndex("by_post_user", (q) => q.eq("postId", post._id).eq("userId", user._id as Id<"users">))
      .unique();
    if (existing) {
      await ctx.db.delete(existing._id);
      await ctx.db.patch(post._id, { upvoteCount: Math.max(0, post.upvoteCount - 1) });
      return false;
    }
    await ctx.db.insert("postVotes", { postId: post._id, userId: user._id });
    await ctx.db.patch(post._id, { upvoteCount: post.upvoteCount + 1 });
    return true;
  },
});

export const nearbyReports = query({
  args: {
    areaField,
    areaValue: v.string(),
    diseaseTag: v.optional(v.string()),
    limit: v.optional(v.number()),
  },
  handler: async (ctx, { areaField, areaValue, diseaseTag, limit }) => {
    if (!(await currentUserId(ctx))) return [];
    let q = visiblePosts(ctx, { field: areaField, value: areaValue });
    if (diseaseTag) q = q.filter((f) => f.eq(f.field("diseaseTag"), diseaseTag));
    const posts = await q.order("desc").take(limit ?? 50);
    return posts.map((p) => ({
      postId: p._id,
      diseaseTag: p.diseaseTag,
      barangay: p.barangay,
      municipality: p.municipality,
      createdAt: p._creationTime,
      verificationStatus: p.verificationStatus,
    }));
  },
});
