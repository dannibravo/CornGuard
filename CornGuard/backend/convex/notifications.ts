import { v } from "convex/values";
import { Id } from "./_generated/dataModel";
import { internalMutation, mutation, query, QueryCtx } from "./_generated/server";
import { currentUserId, requireUser } from "./lib/access";

/**
 * The farmer's in-app notification inbox (the app's Dashboard bell): the same `notifications`
 * rows push.sendNotification delivers, so an alert stays readable after its push is dismissed.
 */

const INBOX_LIMIT = 50;

function ownRows(ctx: QueryCtx, userId: Id<"users">) {
  return ctx.db
    .query("notifications")
    .withIndex("by_recipient", (q) => q.eq("recipientUserId", userId));
}

/** The caller's notifications, newest first; empty when signed out. */
export const mine = query({
  args: {},
  handler: async (ctx) => {
    const userId = await currentUserId(ctx);
    if (!userId) return [];
    const rows = await ownRows(ctx, userId).order("desc").take(INBOX_LIMIT);
    return rows.map((n) => ({
      notificationId: n._id,
      type: n.type,
      title: n.title,
      message: n.message,
      relatedPostId: n.relatedPostId ?? null,
      diseaseCode: n.diseaseCode ?? null,
      createdAt: n._creationTime,
      read: n.readAt !== undefined,
    }));
  },
});

/** Unread count for the bell badge; 0 when signed out. */
export const unreadCount = query({
  args: {},
  handler: async (ctx) => {
    const userId = await currentUserId(ctx);
    if (!userId) return 0;
    const rows = await ownRows(ctx, userId).collect();
    return rows.filter((n) => n.readAt === undefined).length;
  },
});

/** Idempotent; ignores ids that don't exist or belong to someone else. */
export const markRead = mutation({
  args: { notificationId: v.string() },
  handler: async (ctx, { notificationId }) => {
    const user = await requireUser(ctx);
    const id = ctx.db.normalizeId("notifications", notificationId);
    const row = id ? await ctx.db.get(id) : null;
    if (!row || row.recipientUserId !== user._id || row.readAt !== undefined) return null;
    await ctx.db.patch(row._id, { readAt: Date.now() });
    return null;
  },
});

/**
 * One-time cleanup when the inbox ships: notifications sent before read tracking existed have no
 * `readAt`, so farmers would open the app to a badge full of old alerts. Marks everyone's rows
 * created before `before` (epoch ms) as read; they stay listed. Idempotent. CLI only, once per
 * deployment:
 *
 *   npx convex run notifications:markReadBefore "{before: <epoch ms of the release>}"
 */
export const markReadBefore = internalMutation({
  args: { before: v.number() },
  handler: async (ctx, { before }) => {
    let marked = 0;
    for (const row of await ctx.db.query("notifications").collect()) {
      if (row.readAt === undefined && row._creationTime < before) {
        await ctx.db.patch(row._id, { readAt: before });
        marked++;
      }
    }
    return { marked };
  },
});

export const markAllRead = mutation({
  args: {},
  handler: async (ctx) => {
    const user = await requireUser(ctx);
    const now = Date.now();
    for (const row of await ownRows(ctx, user._id).collect()) {
      if (row.readAt === undefined) await ctx.db.patch(row._id, { readAt: now });
    }
    return null;
  },
});
