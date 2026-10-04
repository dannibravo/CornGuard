import { getAuthUserId } from "@convex-dev/auth/server";
import { ConvexError } from "convex/values";
import { Doc, Id } from "../_generated/dataModel";
import { MutationCtx, QueryCtx } from "../_generated/server";

/**
 * Server-side access rules — the Convex replacement for firebase/security/firestore.rules.
 * Queries return empty/null for signed-out callers; mutations throw.
 */

export async function currentUserId(ctx: QueryCtx): Promise<Id<"users"> | null> {
  return await getAuthUserId(ctx);
}

export async function requireUser(ctx: QueryCtx | MutationCtx): Promise<Doc<"users">> {
  const userId = await getAuthUserId(ctx);
  if (!userId) throw new ConvexError("You must be signed in.");
  const user = await ctx.db.get(userId);
  if (!user) throw new ConvexError("Account not found.");
  return user;
}

/** For writes: signed in and not suspended. */
export async function requireActiveUser(ctx: MutationCtx): Promise<Doc<"users">> {
  const user = await requireUser(ctx);
  if (user.accountStatus === "suspended") {
    throw new ConvexError("This account is suspended.");
  }
  return user;
}

export async function requireAdmin(ctx: QueryCtx | MutationCtx): Promise<Doc<"users">> {
  const user = await requireUser(ctx);
  if (user.role !== "admin") throw new ConvexError("Admin access required.");
  return user;
}

export function assertOwnerOrAdmin(ownerId: Id<"users">, user: Doc<"users">) {
  if (ownerId !== user._id && user.role !== "admin") {
    throw new ConvexError("You can only change your own content.");
  }
}

export function isAdmin(user: Doc<"users"> | null): boolean {
  return user?.role === "admin";
}
