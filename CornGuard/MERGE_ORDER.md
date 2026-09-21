# CORNGUARD — Open Branch Stack (temporary status note)

**Delete this file once all three branches below are merged into `develop`.** It exists only to
make the current dependency order clear to whoever reviews next — it is not project
documentation and should not be treated as one of the permanent `claude/*.md` docs.

## Current state (as of this note)

Three feature branches are open and unmerged, and they depend on each other in this order:

```
develop
  └─ feature/firebase-setup      (Panes)   — independent, no dependency on the other two
  └─ feature/android-foundation  (Ligue)   — independent, no dependency on the other two
       └─ feature/auth-service   (Panes)   — branched from feature/android-foundation,
                                              NOT from develop
```

## Why `feature/auth-service` isn't branched from `develop`

Normally every feature branch comes from `develop` (`claude/11_OFFICIAL_GITHUB_WORKFLOW.md`). This
one doesn't, because the Android project (`android/`) only exists on `feature/android-foundation`
— it isn't in `develop` yet. `feature/auth-service` adds the Firebase Auth SDK and the cloud
repository implementations on top of Ligue's Android module, which meant branching from his
branch to have that code to build against at all.

## Required merge order

1. **`feature/firebase-setup` → `develop`** first, or independently at any time — nothing else
   depends on it being merged first, but it's the schema/rules everything else assumes exist.
2. **`feature/android-foundation` → `develop`** — must merge before step 3 makes sense.
3. **`feature/auth-service` → `develop`** — do this last. Reviewing it in isolation without
   `feature/android-foundation` already in `develop` will show a much larger diff than it actually
   is (it'll look like it's introducing the whole Android module, not just the Auth/Firestore
   pieces) — review it as a diff against `feature/android-foundation`, not against `develop`,
   until #2 has landed.

## What's in each branch

- **`feature/firebase-setup`**: cloud logical schema, security rules (Firestore rules tested
  against the emulator, 26 passing), GIS service interface, FCM plan, all 4 repository interface
  docs, Cloud Functions for admin-claim granting and community-reply notifications (28 tests
  passing total), dev fixture/seed scripts, a live `cornguard-dev` Firebase project already
  provisioned (Firestore + Auth enabled; Storage and Cloud Functions deployment both deferred —
  need the Blaze plan).
- **`feature/android-foundation`**: Android Studio project scaffold, Room-backed local
  history/disease-reference repositories, navigation shell, permissions strategy — offline-only,
  no Firebase dependency.
- **`feature/auth-service`**: Firebase Auth (email/password) + Firestore-backed User/Farm
  repositories, wired into `ServiceLocator`. Verified with a real `./gradlew
  :app:compileDebugKotlin` and `:app:testDebugUnitTest` run, both passing.
