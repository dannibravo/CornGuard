// CORNGUARD — dev Firestore seed/fixtures (Sprint 1, Panes)
//
// "Create cloud data mocks/test fixtures for mobile integration" (claude/05_DEVELOPMENT_PLAN.md,
// Sprint 1). Seeds cornguard-dev's live Firestore with realistic sample documents so Ligue can
// develop and test the Android app's Firebase calls against real-shaped data before any farmer
// ever touches the app.
//
// IMPORTANT: the `treatment_steps` / `prevention_steps` text below is explicit PLACEHOLDER
// content, not real agricultural guidance. Per claude/16_UI_UX_AND_DIAGRAM_REVISION_GUIDE.md,
// Claude must not invent real treatment/pesticide instructions — that content must come from an
// approved agricultural reference (claude/09_ML_MODEL_CONTRACT.md's "Treatment Guidance
// Boundary"). Do not ship this placeholder text to a real farmer-facing build.
//
// Run: node firebase/scripts/seed-dev-fixtures.mjs
// Requires `firebase login` (Application Default Credentials) for cornguard-dev.

import { initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';

initializeApp({ projectId: 'cornguard-dev' });
const db = getFirestore();

const now = Timestamp.now();

async function seedDiseaseReference() {
  // The 4 model classes from claude/09_ML_MODEL_CONTRACT.md — codes match DetectionResult.disease_code.
  const diseases = [
    { code: 'common_rust', name: 'Common Rust' },
    { code: 'gray_leaf_spot', name: 'Gray Leaf Spot' },
    { code: 'northern_leaf_blight', name: 'Northern Leaf Blight' },
    { code: 'healthy', name: 'Healthy' },
  ];

  const batch = db.batch();
  for (const d of diseases) {
    batch.set(db.doc(`diseaseReferenceCloud/${d.code}`), {
      display_name: d.name,
      symptoms: `PLACEHOLDER — symptom description for ${d.name} pending agricultural reference validation.`,
      treatment_steps: 'PLACEHOLDER — not real guidance. Must be replaced with content from an approved agricultural source before any real user sees it.',
      prevention_steps: 'PLACEHOLDER — not real guidance. Must be replaced with content from an approved agricultural source before any real user sees it.',
      source_reference: 'PLACEHOLDER — pending approved source',
      content_version: 'dev-fixture-0.1',
      published_at: now,
    });
  }
  await batch.commit();
  console.log(`Seeded ${diseases.length} diseaseReferenceCloud docs.`);
}

async function seedUsersAndFarms() {
  const users = [
    { uid: 'dev-fixture-user-1', display_name: 'Test Farmer One', barangay: 'Barangay A', municipality: 'Malaybalay', province: 'Bukidnon' },
    { uid: 'dev-fixture-user-2', display_name: 'Test Farmer Two', barangay: 'Barangay B', municipality: 'Valencia', province: 'Bukidnon' },
  ];

  for (const u of users) {
    await db.doc(`users/${u.uid}`).set({
      display_name: u.display_name,
      role: 'farmer',
      account_status: 'active',
      barangay: u.barangay,
      municipality: u.municipality,
      province: u.province,
      farm_ids: [`${u.uid}-farm-1`],
      created_at: now,
      updated_at: now,
    });

    await db.doc(`farms/${u.uid}-farm-1`).set({
      owner_user_id: u.uid,
      farm_name_or_label: `${u.display_name}'s Farm`,
      barangay: u.barangay,
      municipality: u.municipality,
      province: u.province,
      created_at: now,
      updated_at: now,
    });
  }
  console.log(`Seeded ${users.length} users + farms.`);
}

async function seedCommunityContent() {
  const postRef = db.doc('communityPosts/dev-fixture-post-1');
  await postRef.set({
    user_id: 'dev-fixture-user-1',
    title: 'Rust spots on my corn leaves',
    body: 'Noticed reddish-brown pustules on several plants this week. Anyone else seeing this in Barangay A?',
    disease_tag: 'common_rust',
    barangay: 'Barangay A',
    municipality: 'Malaybalay',
    province: 'Bukidnon',
    verification_status: 'unverified',
    moderation_status: 'visible',
    upvote_count: 0,
    created_at: now,
    updated_at: now,
  });

  await postRef.collection('comments').doc('dev-fixture-comment-1').set({
    user_id: 'dev-fixture-user-2',
    body: 'Seeing the same thing near Valencia. Keeping an eye on it too.',
    moderation_status: 'visible',
    created_at: now,
  });

  console.log('Seeded 1 community post + 1 comment.');
}

await seedDiseaseReference();
await seedUsersAndFarms();
await seedCommunityContent();
console.log('Done. All dev fixtures use ids prefixed "dev-fixture-" for easy identification/cleanup.');
