// CORNGUARD Cloud Functions (Sprint 1, Panes)
//
// Status: written and tested against the local emulator (see firebase/tests/). NOT deployed to
// the live cornguard-dev project — like Storage, Cloud Functions Gen 2 requires the Blaze
// billing plan, which the team deferred (see claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md
// D-01 dev-provisioning note and firebase/README.md). Deploy once that's revisited.
//
// These functions exist because security/firestore.rules assumes two things no Sprint 0 artifact
// actually implemented: (1) a way to grant the `role: admin` custom auth claim the rules check
// via isAdmin(), and (2) the backend-only writer for notifications/{id} the rules require (client
// writes are hard-denied there).

import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { assertCanPromoteToAdmin, buildCommentNotification, buildFcmPayload } from './logic.mjs';

initializeApp();

/**
 * Promotes a user to admin: sets the Auth custom claim security rules check, and mirrors it to
 * the Firestore users/{uid}.role field so the app UI stays consistent with what the rules allow.
 * Callable only by an existing admin (assertCanPromoteToAdmin).
 */
export const promoteToAdmin = onCall(async (request) => {
  try {
    assertCanPromoteToAdmin(request.auth, request.data?.targetUid);
  } catch (err) {
    throw new HttpsError(err.code ?? 'internal', err.message);
  }

  const targetUid = request.data.targetUid;
  await getAuth().setCustomUserClaims(targetUid, { role: 'admin' });
  await getFirestore().doc(`users/${targetUid}`).update({ role: 'admin' });

  return { ok: true, targetUid };
});

/**
 * Notifies a community post's author when someone replies, per
 * firebase/notifications/fcm-plan.md ("community_reply" trigger). Writes directly via the Admin
 * SDK — the one path notifications/{id} is allowed to be created from, since clients are hard-
 * denied create/delete on that collection in security/firestore.rules.
 */
export const onCommentCreate = onDocumentCreated(
  'communityPosts/{postId}/comments/{commentId}',
  async (event) => {
    const comment = event.data?.data();
    const postSnap = await getFirestore().doc(`communityPosts/${event.params.postId}`).get();
    if (!postSnap.exists) return;

    const payload = buildCommentNotification(postSnap.data(), comment, event.params.postId);
    if (!payload) return;

    await getFirestore()
      .collection('notifications')
      .add({ ...payload, created_at: FieldValue.serverTimestamp() });
  }
);

/**
 * Delivers a push notification whenever notifications/{id} is created — by onCommentCreate
 * above, or eventually by the outbreak-rule evaluation path once D-07 is approved. Marks
 * delivery_status so failures are visible and don't retry indefinitely (see fcm-plan.md's
 * "Failure handling" section — avoids notification storms from a misconfigured token).
 */
export const onNotificationCreate = onDocumentCreated(
  'notifications/{notificationId}',
  async (event) => {
    const notification = event.data?.data();
    const fcmPayload = buildFcmPayload(notification, event.params.notificationId);
    if (!fcmPayload) return; // area-scoped notification: routed via topic, not per-token here

    const tokensSnap = await getFirestore()
      .collection('deviceTokens')
      .where('user_id', '==', notification.recipient_user_id)
      .where('active', '==', true)
      .get();

    const tokens = tokensSnap.docs.map((d) => d.data().fcm_token).filter(Boolean);
    if (tokens.length === 0) {
      await event.data.ref.update({ delivery_status: 'failed' });
      return;
    }

    try {
      await getMessaging().sendEachForMulticast({ tokens, data: fcmPayload });
      await event.data.ref.update({ delivery_status: 'sent' });
    } catch {
      await event.data.ref.update({ delivery_status: 'failed' });
    }
  }
);
