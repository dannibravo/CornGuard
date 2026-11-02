import { ConvexError, v } from "convex/values";
import { Id } from "./_generated/dataModel";
import { mutation, MutationCtx, QueryCtx } from "./_generated/server";
import { requireActiveUser } from "./lib/access";
import { reportReason, reportTargetType } from "./schema";

const NOTE_MAX = 300;

/**
 * Flags a visible post or comment for admin review. One report per user per item: a repeat
 * returns quietly. Your own content can't be reported. Nothing is hidden for anyone else.
 */
export const create = mutation({
  args: { targetType: reportTargetType, targetId: v.string(), reason: reportReason, note: v.optional(v.string()) },
  handler: async (ctx, { targetType, targetId, reason, note }) => {
    const user = await requireActiveUser(ctx);

    let authorId: Id<"users">;
    let postId: Id<"communityPosts">;
    let normalizedId: string;
    if (targetType === "post") {
      const id = ctx.db.normalizeId("communityPosts", targetId);
      const post = id ? await ctx.db.get(id) : null;
      if (!post || post.moderationStatus !== "visible") throw new ConvexError("Post not found.");
      authorId = post.userId;
      postId = post._id;
      normalizedId = post._id;
    } else {
      const id = ctx.db.normalizeId("comments", targetId);
      const comment = id ? await ctx.db.get(id) : null;
      if (!comment || comment.moderationStatus !== "visible") throw new ConvexError("Comment not found.");
      authorId = comment.userId;
      postId = comment.postId;
      normalizedId = comment._id;
    }
    if (authorId === user._id) throw new ConvexError("You can't report your own content.");

    const existing = await ctx.db
      .query("reports")
      .withIndex("by_target_reporter", (q) =>
        q.eq("targetType", targetType).eq("targetId", normalizedId).eq("reporterId", user._id),
      )
      .first();
    if (existing) return null;

    const trimmed = note?.trim().slice(0, NOTE_MAX);
    await ctx.db.insert("reports", {
      targetType,
      targetId: normalizedId,
      postId,
      reporterId: user._id,
      reason,
      note: trimmed ? trimmed : undefined,
      status: "open",
    });
    return null;
  },
});

/** Posts and comments this viewer has reported, so they stop seeing them. */
export async function reportedTargetIds(ctx: QueryCtx, viewerId: Id<"users"> | null) {
  const posts = new Set<string>();
  const comments = new Set<string>();
  if (!viewerId) return { posts, comments };
  const rows = await ctx.db
    .query("reports")
    .withIndex("by_reporter", (q) => q.eq("reporterId", viewerId))
    .collect();
  rows.forEach((r) => (r.targetType === "post" ? posts : comments).add(r.targetId));
  return { posts, comments };
}

/** Closes every open report on one item (admin hid/removed it, or dismissed the reports). */
export async function resolveOpenReports(
  ctx: MutationCtx,
  targetType: "post" | "comment",
  targetId: string,
  outcome: "dismissed" | "actioned",
  adminId: Id<"users">,
) {
  const open = await ctx.db
    .query("reports")
    .withIndex("by_target_status", (q) =>
      q.eq("targetType", targetType).eq("targetId", targetId).eq("status", "open"),
    )
    .collect();
  const now = Date.now();
  await Promise.all(open.map((r) => ctx.db.patch(r._id, { status: outcome, resolvedBy: adminId, resolvedAt: now })));
  return open.length;
}
