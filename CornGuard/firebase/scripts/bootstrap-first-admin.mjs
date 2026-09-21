// CORNGUARD — one-time first-admin bootstrap (Sprint 1, Panes)
//
// firebase/functions/index.mjs's `promoteToAdmin` can only be called BY an existing admin —
// that's the whole point (security/firestore.rules never lets a client self-assign role=admin,
// and neither does that function). Which means the very first admin can't be created through the
// normal app flow at all. This script is the one deliberate, manual, out-of-band exception.
//
// Run it locally by a trusted operator, NOT as a deployed Cloud Function, NOT triggered by any
// client request:
//
//   node firebase/scripts/bootstrap-first-admin.mjs <uid>
//
// Requires Application Default Credentials for the target project — run `firebase login` first
// (or set GOOGLE_APPLICATION_CREDENTIALS to a service-account key you already have locally; never
// commit that key file, per claude/04_DEVELOPMENT_RULES.md #4).

import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';

const uid = process.argv[2];
if (!uid) {
  console.error('Usage: node bootstrap-first-admin.mjs <uid>');
  process.exit(1);
}

initializeApp();

const userRecord = await getAuth().getUser(uid).catch(() => null);
if (!userRecord) {
  console.error(`No Firebase Auth user found with uid "${uid}". Create the account first (it must sign up normally), then run this script.`);
  process.exit(1);
}

await getAuth().setCustomUserClaims(uid, { role: 'admin' });

const userDoc = getFirestore().doc(`users/${uid}`);
const existing = await userDoc.get();
if (existing.exists) {
  await userDoc.update({ role: 'admin' });
} else {
  console.warn(`users/${uid} does not exist yet in Firestore — set its role manually once the app creates it, or re-run this script's Firestore update after signup completes.`);
}

console.log(`Granted admin to uid=${uid} (display name: ${userRecord.displayName ?? 'n/a'}, email: ${userRecord.email ?? 'n/a'}).`);
console.log('The user must sign out and back in (or force-refresh their ID token) for the new claim to take effect client-side.');
