# CornGuard backend (Convex)

Database, email/password auth, image storage and push fan-out for the CornGuard Android app.
It replaces Firestore, Firebase Auth, Cloud Storage and Cloud Functions. Firebase Cloud
Messaging is still used to deliver pushes; its Android config is
`android/app/google-services.json` (Firebase project `cornguard-app`, used for FCM only).

## Layout

| File | What it does | Replaced |
|---|---|---|
| `convex/schema.ts` | Tables: users, farms, diagnosisRecords, communityPosts, comments, postVotes, deviceTokens, notifications, diseaseReferences | Firestore collections |
| `convex/auth.ts`, `auth.config.ts`, `http.ts` | Convex Auth, Password provider | Firebase Auth |
| `convex/lib/access.ts` | Signed-in / active / admin / owner checks | `firestore.rules` |
| `convex/users.ts` | Profile + farms | `users`, `farms` |
| `convex/storage.ts` | Upload URLs for images | Cloud Storage |
| `convex/diagnosisRecords.ts` | Shared scans, verified occurrences, heatmap aggregates | `diagnosisRecordsCloud` |
| `convex/posts.ts` | Posts, comments, upvotes, nearby reports; reply notifications | `communityPosts`, `onVoteWrite`, `onCommentCreate` |
| `convex/devices.ts` | FCM device tokens | `deviceTokens` |
| `convex/admin.ts` | Verification, moderation, account status, admin promotion, reference content | `promoteToAdmin`, admin writes |
| `convex/push.ts`, `pushData.ts` | Sends a notification through FCM HTTP v1 | `onNotificationCreate` |

## First-time setup

Requires Node 18+.

1. `cd backend` and run `npm install`.
2. `npx convex dev`. Log in and create a new project when prompted. This deploys the functions
   and keeps watching for changes. It writes the deployment URL to `backend/.env.local`
   (`CONVEX_URL=https://<name>.convex.cloud`).
3. In another terminal, run `npx @convex-dev/auth` and accept the prompts. It sets
   `JWT_PRIVATE_KEY`, `JWKS` and `SITE_URL` on the deployment.
4. For push notifications: in the Firebase console (project `cornguard-app`), go to Project
   settings → Service accounts → Generate new private key. Save the JSON file outside the repo,
   then run `npx convex env set FCM_SERVICE_ACCOUNT_JSON --from-file <path-to-key.json>`.
   Without it, everything else works and notifications are recorded with status `failed`.
   To rotate the key later, generate a new one, run the same command, then delete the old key
   in the console. Device tokens that FCM rejects as dead are switched off automatically.
5. Point the app at the deployment: add `convex.url=https://<name>.convex.cloud` to
   `android/local.properties`.
6. After registering your own account in the app, make it the first admin:
   `npx convex run admin:bootstrapFirstAdmin "{email:'you@example.com'}"`.

## Checking the deployment

`node scripts/smoke-test.mjs` runs about 30 end-to-end checks against the deployment in
`.env.local`: accounts, profile, farms, image upload, posts, comments, upvotes, permissions,
shared scans and admin actions. It creates test accounts and data, so only point it at a dev
deployment.

## Notes

- Passwords must be at least 8 characters (Convex Auth's Password provider default).
- Password reset isn't configured; it needs an email provider (e.g. Resend) added to `auth.ts`.
- Numbers are float64 (`v.number()`), so the Android client sends every number as a `Double`.
