import { v } from "convex/values";
import { Doc, Id } from "./_generated/dataModel";
import { mutation, query, QueryCtx } from "./_generated/server";
import { currentUserId, requireActiveUser } from "./lib/access";

/** Shape returned to the app for `AuthUser` / `UserProfile`. */
async function toProfile(ctx: QueryCtx, user: Doc<"users">) {
  const farms = await ctx.db
    .query("farms")
    .withIndex("by_owner", (q) => q.eq("ownerId", user._id))
    .collect();
  return {
    userId: user._id,
    email: user.email ?? null,
    displayName: user.name ?? null,
    mobileNumber: user.mobileNumber ?? null,
    barangay: user.barangay ?? "",
    municipality: user.municipality ?? "",
    province: user.province ?? "",
    role: user.role ?? "farmer",
    accountStatus: user.accountStatus ?? "active",
    farmIds: farms.map((f) => f._id as string),
  };
}

/** The signed-in user, or null. Also used by the app to learn its own user id after sign-in. */
export const viewer = query({
  args: {},
  handler: async (ctx) => {
    const userId = await currentUserId(ctx);
    if (!userId) return null;
    const user = await ctx.db.get(userId);
    return user ? await toProfile(ctx, user) : null;
  },
});

export const get = query({
  args: { userId: v.string() },
  handler: async (ctx, { userId }) => {
    if (!(await currentUserId(ctx))) return null;
    const id = ctx.db.normalizeId("users", userId);
    const user = id ? await ctx.db.get(id) : null;
    // A signed-up user without a display name has no profile yet (same as a missing Firestore doc).
    if (!user || user.name === undefined) return null;
    return await toProfile(ctx, user);
  },
});

/** Second step of registration. Role and account status are always set server-side. */
export const createProfile = mutation({
  args: {
    displayName: v.string(),
    barangay: v.string(),
    municipality: v.string(),
    province: v.string(),
  },
  handler: async (ctx, args) => {
    const user = await requireActiveUser(ctx);
    await ctx.db.patch(user._id, {
      name: args.displayName,
      barangay: args.barangay,
      municipality: args.municipality,
      province: args.province,
      role: user.role ?? "farmer",
      accountStatus: user.accountStatus ?? "active",
    });
  },
});

/** Only these profile fields are user-editable (role / accountStatus are admin-only). */
export const update = mutation({
  args: {
    displayName: v.optional(v.string()),
    barangay: v.optional(v.string()),
    municipality: v.optional(v.string()),
    province: v.optional(v.string()),
    mobileNumber: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    const user = await requireActiveUser(ctx);
    const patch: Partial<Doc<"users">> = {};
    if (args.displayName !== undefined) patch.name = args.displayName;
    if (args.barangay !== undefined) patch.barangay = args.barangay;
    if (args.municipality !== undefined) patch.municipality = args.municipality;
    if (args.province !== undefined) patch.province = args.province;
    if (args.mobileNumber !== undefined) patch.mobileNumber = args.mobileNumber;
    await ctx.db.patch(user._id, patch);
  },
});

export const createFarm = mutation({
  args: {
    name: v.string(),
    barangay: v.string(),
    municipality: v.string(),
    province: v.string(),
    latitude: v.optional(v.number()),
    longitude: v.optional(v.number()),
  },
  handler: async (ctx, args) => {
    const user = await requireActiveUser(ctx);
    return await ctx.db.insert("farms", { ownerId: user._id, ...args });
  },
});

export const farmsForUser = query({
  args: { userId: v.string() },
  handler: async (ctx, { userId }) => {
    if (!(await currentUserId(ctx))) return [];
    const ownerId = ctx.db.normalizeId("users", userId);
    if (!ownerId) return [];
    const farms = await ctx.db
      .query("farms")
      .withIndex("by_owner", (q) => q.eq("ownerId", ownerId as Id<"users">))
      .collect();
    return farms.map((f) => ({
      farmId: f._id,
      ownerUserId: f.ownerId,
      name: f.name,
      barangay: f.barangay,
      municipality: f.municipality,
      province: f.province,
      latitude: f.latitude ?? null,
      longitude: f.longitude ?? null,
    }));
  },
});
