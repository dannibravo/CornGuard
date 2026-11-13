import { ConvexError, v } from "convex/values";
import { Doc, Id } from "./_generated/dataModel";
import { mutation, query, QueryCtx } from "./_generated/server";
import { currentUserId, requireActiveUser, requireUser } from "./lib/access";
import { isKnownBarangay } from "./outbreakAlerts";

/** Most farms one farmer can save (My Farms). */
const MAX_FARMS = 20;

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
    onboardingStatus: user.onboardingStatus ?? "done",
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
    // A brand-new account (no profile yet) is "Fresh": the app shows it the onboarding tutorial.
    const isNewAccount = user.name === undefined && user.onboardingStatus === undefined;
    await ctx.db.patch(user._id, {
      ...(isNewAccount ? { onboardingStatus: "fresh" as const } : {}),
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

/**
 * Saves a farm (My Farms): a pin dropped in the app plus the Bukidnon barangay it falls in (the app
 * resolves it from the bundled boundaries and sends the official names). Farms are private: only
 * their owner can list them, and a farm's location leaves the owner only with a scan they share.
 */
export const createFarm = mutation({
  args: {
    name: v.string(),
    barangay: v.string(),
    municipality: v.string(),
    province: v.string(),
    latitude: v.number(),
    longitude: v.number(),
  },
  handler: async (ctx, args) => {
    const user = await requireActiveUser(ctx);
    const name = args.name.trim();
    if (name.length < 1 || name.length > 40) throw new ConvexError("Give the farm a name (up to 40 characters).");
    if (!isKnownBarangay(args.barangay, args.municipality)) {
      throw new ConvexError("Pick a spot inside Bukidnon: farms can only be saved on the outbreak map's area.");
    }
    const existing = await ctx.db.query("farms").withIndex("by_owner", (q) => q.eq("ownerId", user._id)).take(MAX_FARMS);
    if (existing.length >= MAX_FARMS) throw new ConvexError(`You can save up to ${MAX_FARMS} farms.`);
    return await ctx.db.insert("farms", { ownerId: user._id, ...args, name });
  },
});

/** Removes one of the caller's farms. Scans already shared from it keep their location. */
export const deleteFarm = mutation({
  args: { farmId: v.string() },
  handler: async (ctx, { farmId }) => {
    const user = await requireUser(ctx);
    const id = ctx.db.normalizeId("farms", farmId);
    const farm = id ? await ctx.db.get(id) : null;
    if (!farm || farm.ownerId !== user._id) throw new ConvexError("That farm isn't yours.");
    await ctx.db.delete(farm._id);
  },
});

/** The caller's own farms. Farm locations are private: asking for another user's farms returns []. */
export const farmsForUser = query({
  args: { userId: v.string() },
  handler: async (ctx, { userId }) => {
    const me = await currentUserId(ctx);
    if (!me || me !== userId) return [];
    const farms = await ctx.db
      .query("farms")
      .withIndex("by_owner", (q) => q.eq("ownerId", me as Id<"users">))
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

/** The app's first-sign-in tutorial was finished or skipped; it won't be shown again. */
export const completeOnboarding = mutation({
  args: {},
  handler: async (ctx) => {
    const user = await requireUser(ctx);
    if (user.onboardingStatus !== "done") await ctx.db.patch(user._id, { onboardingStatus: "done" });
  },
});
