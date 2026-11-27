import { Doc, Id } from "../_generated/dataModel";
import { MutationCtx } from "../_generated/server";

/**
 * Append-only verification audit trail (D-02 data preparation; schema `verificationEvents`).
 * Every change of a shared scan's or a post's verification status adds one row here, written
 * in the same transaction as the change. Rows are never patched or deleted — not even when the
 * scan or post itself is removed — so the history can always be reconstructed.
 */
export type VerificationTarget = Doc<"verificationEvents">["targetType"];
export type VerificationEventReason = Doc<"verificationEvents">["reason"];
type Status = Doc<"verificationEvents">["toStatus"];

export async function logVerification(
  ctx: MutationCtx,
  event: {
    targetType: VerificationTarget;
    targetId: string;
    fromStatus: Status | null;
    toStatus: Status;
    reason: VerificationEventReason;
    actorId: Id<"users"> | null;
  },
) {
  await ctx.db.insert("verificationEvents", {
    targetType: event.targetType,
    targetId: event.targetId,
    ...(event.fromStatus ? { fromStatus: event.fromStatus } : {}),
    toStatus: event.toStatus,
    reason: event.reason,
    ...(event.actorId ? { actorId: event.actorId } : {}),
    at: Date.now(),
  });
}
