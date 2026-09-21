// CORNGUARD — Firestore security rules test suite
//
// Verifies firebase/security/firestore.rules actually enforces
// firebase/security/access-control-matrix.md against the Firestore emulator.
// Run: firebase emulators:exec --only firestore "npm test" (from firebase/)
// or, with the emulator already running: npm test (from firebase/tests/)

import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc,
  setDoc,
  getDoc,
  updateDoc,
  collection,
} from 'firebase/firestore';

const PROJECT_ID = 'cornguard-rules-test';
let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync('../security/firestore.rules', 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

function farmerCtx(uid) {
  return testEnv.authenticatedContext(uid, { role: 'farmer' });
}
function adminCtx(uid) {
  return testEnv.authenticatedContext(uid, { role: 'admin' });
}
function anonCtx() {
  return testEnv.unauthenticatedContext();
}

// ---------- users/{userId} ----------

test('a farmer can create their own user doc with role=farmer', async () => {
  const db = farmerCtx('alice').firestore();
  await assertSucceeds(
    setDoc(doc(db, 'users/alice'), {
      display_name: 'Alice',
      role: 'farmer',
      account_status: 'active',
      barangay: 'X',
      municipality: 'Y',
      province: 'Bukidnon',
      farm_ids: [],
    })
  );
});

test('a user CANNOT self-assign role=admin on signup', async () => {
  const db = farmerCtx('mallory').firestore();
  await assertFails(
    setDoc(doc(db, 'users/mallory'), {
      display_name: 'Mallory',
      role: 'admin', // <- the exact attack this rule exists to block
      account_status: 'active',
      barangay: 'X',
      municipality: 'Y',
      province: 'Bukidnon',
      farm_ids: [],
    })
  );
});

test('a user cannot read another user\'s private profile', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users/bob'), {
      display_name: 'Bob',
      role: 'farmer',
      account_status: 'active',
      barangay: 'X',
      municipality: 'Y',
      province: 'Bukidnon',
      farm_ids: [],
    });
  });
  const db = farmerCtx('alice').firestore();
  await assertFails(getDoc(doc(db, 'users/bob')));
});

test('a user cannot elevate their own role via update', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users/carol'), {
      display_name: 'Carol',
      role: 'farmer',
      account_status: 'active',
      barangay: 'X',
      municipality: 'Y',
      province: 'Bukidnon',
      farm_ids: [],
    });
  });
  const db = farmerCtx('carol').firestore();
  await assertFails(updateDoc(doc(db, 'users/carol'), { role: 'admin' }));
});

test('a user CAN update their own display_name', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users/dave'), {
      display_name: 'Dave',
      role: 'farmer',
      account_status: 'active',
      barangay: 'X',
      municipality: 'Y',
      province: 'Bukidnon',
      farm_ids: [],
    });
  });
  const db = farmerCtx('dave').firestore();
  await assertSucceeds(updateDoc(doc(db, 'users/dave'), { display_name: 'Dave R.' }));
});

// ---------- farms/{farmId} ----------

test('a farmer can create a farm they own', async () => {
  const db = farmerCtx('erin').firestore();
  await assertSucceeds(
    setDoc(doc(db, 'farms/farm1'), {
      owner_user_id: 'erin',
      farm_name_or_label: 'Erin Farm',
      barangay: 'X', municipality: 'Y', province: 'Bukidnon',
    })
  );
});

test('a farmer cannot create a farm claiming someone else as owner', async () => {
  const db = farmerCtx('erin').firestore();
  await assertFails(
    setDoc(doc(db, 'farms/farm2'), {
      owner_user_id: 'someone-else',
      farm_name_or_label: 'Fake Farm',
      barangay: 'X', municipality: 'Y', province: 'Bukidnon',
    })
  );
});

// ---------- diagnosisRecordsCloud/{recordId} ----------

test('a farmer can share a scan as unverified', async () => {
  const db = farmerCtx('frank').firestore();
  await assertSucceeds(
    setDoc(doc(db, 'diagnosisRecordsCloud/rec1'), {
      user_id: 'frank',
      disease_code: 'common_rust',
      confidence: 0.9,
      captured_at: new Date().toISOString(),
      barangay: 'X', municipality: 'Y', province: 'Bukidnon',
      model_version: 'v1',
      verification_status: 'unverified',
      source: 'ai_scan',
    })
  );
});

test('a farmer cannot mark their own shared scan as verified', async () => {
  const db = farmerCtx('gina').firestore();
  await assertFails(
    setDoc(doc(db, 'diagnosisRecordsCloud/rec2'), {
      user_id: 'gina',
      disease_code: 'healthy',
      confidence: 0.9,
      captured_at: new Date().toISOString(),
      barangay: 'X', municipality: 'Y', province: 'Bukidnon',
      model_version: 'v1',
      verification_status: 'verified', // <- self-verification attempt
      source: 'ai_scan',
    })
  );
});

test('another farmer cannot read someone else\'s private diagnosis record', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'diagnosisRecordsCloud/rec3'), {
      user_id: 'henry',
      disease_code: 'healthy',
      confidence: 0.9,
      captured_at: new Date().toISOString(),
      barangay: 'X', municipality: 'Y', province: 'Bukidnon',
      model_version: 'v1',
      verification_status: 'unverified',
      source: 'ai_scan',
    });
  });
  const db = farmerCtx('intruder').firestore();
  await assertFails(getDoc(doc(db, 'diagnosisRecordsCloud/rec3')));
});

// ---------- communityPosts/{postId} ----------

test('a farmer can create a community post with upvote_count=0', async () => {
  const db = farmerCtx('julia').firestore();
  await assertSucceeds(
    setDoc(doc(db, 'communityPosts/post1'), {
      user_id: 'julia',
      title: 'Rust on my leaves',
      body: 'Seeing orange spots',
      disease_tag: 'common_rust',
      barangay: 'X', municipality: 'Y', province: 'Bukidnon',
      verification_status: 'unverified',
      moderation_status: 'visible',
      upvote_count: 0,
      created_at: new Date().toISOString(),
    })
  );
});

test('a farmer cannot fake a nonzero upvote_count on create', async () => {
  const db = farmerCtx('kevin').firestore();
  await assertFails(
    setDoc(doc(db, 'communityPosts/post2'), {
      user_id: 'kevin',
      title: 'Fake popular post',
      body: 'x',
      disease_tag: 'unknown',
      barangay: 'X', municipality: 'Y', province: 'Bukidnon',
      verification_status: 'unverified',
      moderation_status: 'visible',
      upvote_count: 999, // <- the exact abuse this rule blocks
      created_at: new Date().toISOString(),
    })
  );
});

test('any signed-in farmer can read the community feed', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'communityPosts/post3'), {
      user_id: 'laura', title: 'x', body: 'x', disease_tag: 'unknown',
      barangay: 'X', municipality: 'Y', province: 'Bukidnon',
      verification_status: 'unverified', moderation_status: 'visible',
      upvote_count: 0, created_at: new Date().toISOString(),
    });
  });
  const db = farmerCtx('reader').firestore();
  await assertSucceeds(getDoc(doc(db, 'communityPosts/post3')));
});

// ---------- notifications/{id} — backend-only ----------

test('a client can NEVER create a notification directly', async () => {
  const db = farmerCtx('mona').firestore();
  await assertFails(
    setDoc(doc(db, 'notifications/n1'), {
      recipient_user_id: 'mona',
      type: 'community_reply',
      title: 'x', message: 'x',
      created_at: new Date().toISOString(),
      delivery_status: 'pending',
    })
  );
});

// ---------- outbreakRules/{id} — admin-only, not publicly readable ----------

test('a regular farmer cannot read outbreak rule thresholds', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'outbreakRules/rule1'), {
      disease_code: 'common_rust', active: false, version: '0.1',
    });
  });
  const db = farmerCtx('nosy').firestore();
  await assertFails(getDoc(doc(db, 'outbreakRules/rule1')));
});

test('an admin CAN read outbreak rule thresholds', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'outbreakRules/rule2'), {
      disease_code: 'common_rust', active: false, version: '0.1',
    });
  });
  const db = adminCtx('admin1').firestore();
  await assertSucceeds(getDoc(doc(db, 'outbreakRules/rule2')));
});

// ---------- default deny ----------

test('an unauthenticated client cannot read anything', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'communityPosts/post4'), {
      user_id: 'x', title: 'x', body: 'x', disease_tag: 'unknown',
      barangay: 'X', municipality: 'Y', province: 'Bukidnon',
      verification_status: 'unverified', moderation_status: 'visible',
      upvote_count: 0, created_at: new Date().toISOString(),
    });
  });
  const db = anonCtx().firestore();
  await assertFails(getDoc(doc(db, 'communityPosts/post4')));
});
