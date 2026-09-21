// Pure unit tests for firebase/functions/logic.mjs — no emulator required.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  assertCanPromoteToAdmin,
  buildCommentNotification,
  buildFcmPayload,
} from '../functions/logic.mjs';

// ---------- assertCanPromoteToAdmin ----------

test('rejects an unauthenticated caller', () => {
  assert.throws(
    () => assertCanPromoteToAdmin(null, 'someUid'),
    (err) => err.code === 'unauthenticated'
  );
});

test('rejects a caller without the admin claim', () => {
  assert.throws(
    () => assertCanPromoteToAdmin({ token: { role: 'farmer' } }, 'someUid'),
    (err) => err.code === 'permission-denied'
  );
});

test('rejects a missing targetUid', () => {
  assert.throws(
    () => assertCanPromoteToAdmin({ token: { role: 'admin' } }, undefined),
    (err) => err.code === 'invalid-argument'
  );
});

test('allows an admin caller with a valid targetUid', () => {
  assert.doesNotThrow(() =>
    assertCanPromoteToAdmin({ token: { role: 'admin' } }, 'someUid')
  );
});

// ---------- buildCommentNotification ----------

test('builds a notification when a different user comments', () => {
  const payload = buildCommentNotification(
    { user_id: 'owner' },
    { user_id: 'commenter', body: 'Nice catch, try fungicide X' },
    'post1'
  );
  assert.equal(payload.recipient_user_id, 'owner');
  assert.equal(payload.type, 'community_reply');
  assert.equal(payload.related_post_id, 'post1');
  assert.equal(payload.delivery_status, 'pending');
});

test('does NOT notify when the post owner comments on their own post', () => {
  const payload = buildCommentNotification(
    { user_id: 'owner' },
    { user_id: 'owner', body: 'Update: resolved it' },
    'post1'
  );
  assert.equal(payload, null);
});

test('truncates a long comment body to 140 chars in the notification message', () => {
  const longBody = 'x'.repeat(500);
  const payload = buildCommentNotification({ user_id: 'owner' }, { user_id: 'other', body: longBody }, 'post1');
  assert.equal(payload.message.length, 140);
});

// ---------- buildFcmPayload ----------

test('builds an FCM payload for a per-user notification', () => {
  const payload = buildFcmPayload(
    { recipient_user_id: 'owner', type: 'community_reply', title: 't', message: 'm' },
    'notif1'
  );
  assert.equal(payload.notification_id, 'notif1');
  assert.equal(payload.type, 'community_reply');
});

test('returns null for an area-scoped notification (no recipient_user_id)', () => {
  const payload = buildFcmPayload({ area_scope: 'barangay:x', type: 'outbreak_alert' }, 'notif2');
  assert.equal(payload, null);
});
