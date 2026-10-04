import { v } from "convex/values";
import { mutation } from "./_generated/server";
import { requireUser } from "./lib/access";

/** One row per (user, device) — the old `deviceTokens/{uid}-{deviceId}` document. */
export const registerToken = mutation({
  args: { deviceId: v.string(), fcmToken: v.string() },
  handler: async (ctx, { deviceId, fcmToken }) => {
    const user = await requireUser(ctx);
    const existing = await ctx.db
      .query("deviceTokens")
      .withIndex("by_user_device", (q) => q.eq("userId", user._id).eq("deviceId", deviceId))
      .unique();
    if (existing) {
      await ctx.db.patch(existing._id, { fcmToken, active: true });
    } else {
      await ctx.db.insert("deviceTokens", { userId: user._id, deviceId, fcmToken, active: true });
    }
  },
});

export const deactivateToken = mutation({
  args: { deviceId: v.string() },
  handler: async (ctx, { deviceId }) => {
    const user = await requireUser(ctx);
    const existing = await ctx.db
      .query("deviceTokens")
      .withIndex("by_user_device", (q) => q.eq("userId", user._id).eq("deviceId", deviceId))
      .unique();
    if (existing) await ctx.db.patch(existing._id, { active: false });
  },
});
