import { v } from "convex/values";
import { internalMutation, internalQuery } from "./_generated/server";

/** Database side of push delivery (push.ts runs in the Node runtime and can't touch ctx.db). */

export const loadDelivery = internalQuery({
  args: { notificationId: v.id("notifications") },
  handler: async (ctx, { notificationId }) => {
    const notification = await ctx.db.get(notificationId);
    if (!notification) return null;
    const recipientId = notification.recipientUserId;
    const tokens = recipientId
      ? (
          await ctx.db
            .query("deviceTokens")
            .withIndex("by_user", (q) => q.eq("userId", recipientId))
            .collect()
        )
          .filter((t) => t.active)
          .map((t) => t.fcmToken)
      : [];
    return { notification, tokens };
  },
});

/** Deactivates device tokens FCM reports as no longer valid (app uninstalled, other project, …). */
export const deactivateTokens = internalMutation({
  args: { fcmTokens: v.array(v.string()) },
  handler: async (ctx, { fcmTokens }) => {
    const dead = new Set(fcmTokens);
    for (const row of await ctx.db.query("deviceTokens").collect()) {
      if (row.active && dead.has(row.fcmToken)) await ctx.db.patch(row._id, { active: false });
    }
  },
});

export const setDeliveryStatus = internalMutation({
  args: {
    notificationId: v.id("notifications"),
    status: v.union(v.literal("sent"), v.literal("failed")),
  },
  handler: async (ctx, { notificationId, status }) => {
    await ctx.db.patch(notificationId, { deliveryStatus: status });
  },
});
