// CORNGUARD Cloud Functions — pure logic, separated from the firebase-functions/firebase-admin
// wrappers in index.mjs so it's unit-testable without any emulator.

/**
 * Guards firebase/functions index.mjs's `promoteToAdmin` callable. Throws a plain Error with a
 * `code` property (mapped to HttpsError by the caller) if the caller isn't authorized.
 *
 * This is the only path allowed to grant admin (security/firestore.rules rejects any client
 * write to `role` on their own users/{uid} doc — see access-control-matrix.md). Bootstrapping
 * the very first admin is explicitly out of scope here: see scripts/bootstrap-first-admin.mjs.
 */
export function assertCanPromoteToAdmin(callerAuth, targetUid) {
  if (!callerAuth) {
    const err = new Error('Must be signed in.');
    err.code = 'unauthenticated';
    throw err;
  }
  if (callerAuth.token?.role !== 'admin') {
    const err = new Error('Only an existing admin can promote another user.');
    err.code = 'permission-denied';
    throw err;
  }
  if (typeof targetUid !== 'string' || targetUid.length === 0) {
    const err = new Error('targetUid is required.');
    err.code = 'invalid-argument';
    throw err;
  }
}

/**
 * Builds the notifications/{id} payload for a new comment, or null if no notification should be
 * created (e.g. the post owner commenting on their own post).
 */
export function buildCommentNotification(post, comment, postId) {
  if (!post || !comment) return null;
  if (post.user_id === comment.user_id) return null; // don't notify yourself

  return {
    recipient_user_id: post.user_id,
    type: 'community_reply',
    title: 'New reply on your post',
    message: typeof comment.body === 'string' ? comment.body.slice(0, 140) : '',
    related_post_id: postId,
    delivery_status: 'pending',
  };
}

/**
 * Computes the upvote_count delta for a communityPosts/{postId}/votes/{voterId} write, per
 * firebase/security/access-control-matrix.md: "do not trust client-supplied aggregate count" —
 * this is the actual computation nothing else in Sprint 0/1 built. votes/{voterId} only ever goes
 * from not-existing to existing (a vote) or existing to not-existing (an unvote) — updates are
 * denied by security/firestore.rules — so `existedBefore`/`existsAfter` fully describe the change.
 *
 * Returns 0 (no-op) for any shape that isn't a clean create or delete.
 */
export function computeVoteCountDelta(existedBefore, existsAfter) {
  if (!existedBefore && existsAfter) return 1;
  if (existedBefore && !existsAfter) return -1;
  return 0;
}

/**
 * Decides how a notifications/{id} document should be delivered, per
 * firebase/notifications/fcm-plan.md's "Delivery targeting" section: per-user token lookup when
 * `recipient_user_id` is set, an FCM topic send when `area_scope` is set instead, or null if
 * neither is present (malformed document — nothing to deliver).
 *
 * `area_scope` is expected to already be a valid FCM topic name (e.g. "barangay_malaybalay_poblacion")
 * — whatever creates the notification (onCommentCreate today; the future outbreak-rule evaluation
 * path once D-07 is approved) is responsible for formatting it, not this function.
 */
export function resolveNotificationTarget(notification) {
  if (notification?.recipient_user_id) {
    return { type: 'token', recipientUserId: notification.recipient_user_id };
  }
  if (notification?.area_scope) {
    return { type: 'topic', topic: notification.area_scope };
  }
  return null;
}

/**
 * Builds the FCM data payload for a notifications/{id} document, per
 * firebase/notifications/fcm-plan.md's "Payload shape" section. Shared by both the per-token and
 * topic delivery paths — the payload shape doesn't depend on how it's routed.
 */
export function buildFcmPayload(notification, notificationId) {
  return {
    notification_id: notificationId,
    type: notification?.type ?? '',
    title: notification?.title ?? '',
    message: notification?.message ?? '',
    related_post_id: notification?.related_post_id ?? '',
    related_record_id: notification?.related_record_id ?? '',
  };
}
