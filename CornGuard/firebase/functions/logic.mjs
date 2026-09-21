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
 * Builds the FCM data payload for a notifications/{id} document, per
 * firebase/notifications/fcm-plan.md's "Payload shape" section. Returns null if the notification
 * has no per-user recipient (area-scoped notifications route through topics, handled elsewhere).
 */
export function buildFcmPayload(notification, notificationId) {
  if (!notification?.recipient_user_id) return null;
  return {
    notification_id: notificationId,
    type: notification.type ?? '',
    title: notification.title ?? '',
    message: notification.message ?? '',
    related_post_id: notification.related_post_id ?? '',
    related_record_id: notification.related_record_id ?? '',
  };
}
