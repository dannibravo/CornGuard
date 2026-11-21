// End-to-end test of the real onCommentCreate -> onNotificationCreate trigger chain, run against
// the Firestore + Functions emulators together (not just the logic unit tests). Confirms the
// triggers in firebase/functions/index.mjs are actually wired up, not just correct in isolation.
//
// Run via: firebase emulators:exec --only firestore,functions "npm --prefix tests run test:integration"
// (from firebase/). Requires FIRESTORE_EMULATOR_HOST to be set by emulators:exec.

import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { initializeApp, getApps } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';

let db;

before(async () => {
  if (getApps().length === 0) {
    initializeApp({ projectId: process.env.GCLOUD_PROJECT || 'cornguard-dev' });
  }
  db = getFirestore();

  // The Functions emulator logs "initialized" for each trigger slightly before its Firestore
  // event subscription is actually live — without this, the very first write in a fresh emulator
  // session can race the subscription and the test times out even though the trigger is correct
  // (confirmed: identical code passes reliably on a second run). A fixed settle delay is simpler
  // and more honest than a flaky-test retry wrapper.
  await new Promise((resolve) => setTimeout(resolve, 3000));
});

async function waitFor(predicateFn, { timeoutMs = 20000, intervalMs = 300 } = {}) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const result = await predicateFn();
    if (result) return result;
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error('Timed out waiting for condition');
}

test('a comment on someone else\'s post creates a notification, which the FCM trigger then processes', async () => {
  await db.doc('communityPosts/postA').set({
    user_id: 'ownerA',
    title: 'Rust on my leaves',
    body: 'Seeing orange spots',
    disease_tag: 'common_rust',
    barangay: 'X', municipality: 'Y', province: 'Bukidnon',
    verification_status: 'unverified',
    moderation_status: 'visible',
    upvote_count: 0,
    created_at: FieldValue.serverTimestamp(),
  });

  await db.collection('communityPosts/postA/comments').add({
    user_id: 'commenterB',
    body: 'Try removing infected leaves and applying fungicide',
    moderation_status: 'visible',
    created_at: FieldValue.serverTimestamp(),
  });

  // Step 1: onCommentCreate should fire and create the notification.
  const notifDoc = await waitFor(async () => {
    const snap = await db.collection('notifications')
      .where('recipient_user_id', '==', 'ownerA')
      .where('related_post_id', '==', 'postA')
      .get();
    return snap.empty ? null : snap.docs[0];
  });

  const notification = notifDoc.data();
  assert.equal(notification.type, 'community_reply');
  assert.equal(notification.title, 'New reply on your post');
  assert.ok(notification.message.includes('fungicide'));

  // Step 2: onNotificationCreate should then fire on that same document. No device tokens exist
  // for ownerA in this test, so the "no tokens found" branch runs, marking it failed rather than
  // retrying indefinitely (see fcm-plan.md's Failure handling section) — this proves the second
  // trigger actually ran, not just the first.
  await waitFor(async () => {
    const fresh = await notifDoc.ref.get();
    return fresh.data().delivery_status === 'failed' ? fresh : null;
  });
});

test('a post owner commenting on their own post creates no notification', async () => {
  await db.doc('communityPosts/postB').set({
    user_id: 'ownerC',
    title: 'Update', body: 'Resolved it myself', disease_tag: 'unknown',
    barangay: 'X', municipality: 'Y', province: 'Bukidnon',
    verification_status: 'unverified', moderation_status: 'visible',
    upvote_count: 0, created_at: FieldValue.serverTimestamp(),
  });

  await db.collection('communityPosts/postB/comments').add({
    user_id: 'ownerC', // same as the post owner
    body: 'Following up on my own post',
    moderation_status: 'visible',
    created_at: FieldValue.serverTimestamp(),
  });

  // Give the trigger time to run (or not run), then confirm nothing was created.
  await new Promise((resolve) => setTimeout(resolve, 2000));
  const snap = await db.collection('notifications')
    .where('related_post_id', '==', 'postB')
    .get();
  assert.equal(snap.empty, true);
});

test('a vote create then delete correctly increments then decrements upvote_count', async () => {
  const postRef = db.doc('communityPosts/postC');
  await postRef.set({
    user_id: 'ownerD',
    title: 'Networking issue', body: 'x', disease_tag: 'unknown',
    barangay: 'X', municipality: 'Y', province: 'Bukidnon',
    verification_status: 'unverified', moderation_status: 'visible',
    upvote_count: 0, created_at: FieldValue.serverTimestamp(),
  });

  const voteRef = postRef.collection('votes').doc('voterE');
  await voteRef.set({ created_at: FieldValue.serverTimestamp() });

  await waitFor(async () => {
    const fresh = await postRef.get();
    return fresh.data().upvote_count === 1 ? fresh : null;
  });

  await voteRef.delete();

  await waitFor(async () => {
    const fresh = await postRef.get();
    return fresh.data().upvote_count === 0 ? fresh : null;
  });
});

test('an area-scoped notification is routed to the topic-send path, not silently ignored', async () => {
  // Before the resolveNotificationTarget fix, an area_scope-only notification had no
  // recipient_user_id, so onNotificationCreate did nothing at all — it stayed "pending" forever,
  // a silent bug. Now it should at least be attempted and end up in a terminal, visible state.
  // (No real FCM credentials exist in this sandbox, so the actual send call fails — that's fine;
  // the point is it no longer sits unprocessed.)
  const notifRef = await db.collection('notifications').add({
    area_scope: 'barangay_test_area',
    type: 'nearby_report',
    title: 'Nearby report',
    message: 'A nearby farm reported common rust',
    delivery_status: 'pending',
    created_at: FieldValue.serverTimestamp(),
  });

  await waitFor(async () => {
    const fresh = await notifRef.get();
    return fresh.data().delivery_status !== 'pending' ? fresh : null;
  });

  const finalState = (await notifRef.get()).data();
  assert.notEqual(finalState.delivery_status, 'pending');
});
