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

Both Panes branches now cover the full Sprint 0–5 Panes scope from `claude/05_DEVELOPMENT_PLAN.md`
— see `firebase/README.md` and `android/README.md` on their respective branches for the complete,
current breakdown. Summary:

- **`feature/firebase-setup`**: cloud logical schema, security rules (36 rules/logic tests + 3
  integration tests passing against the emulator, including Sprint 5's admin
  verification/moderation/user-management paths), all 6 repository interface docs (including
  `admin-repository-interface.md`), GIS service interface, FCM plan, Firestore composite indexes
  for the location-aware feed, Cloud Functions (admin-claim granting, community-reply
  notifications with real FCM delivery — per-user token *and* area-scoped topic routing, and a
  real `upvote_count`-maintaining trigger), dev fixture/seed scripts. A live `cornguard-dev`
  Firebase project is provisioned: Firestore (database + rules + indexes) is deployed; Storage and
  Cloud Functions deployment are both deferred — need the Blaze plan.
- **`feature/android-foundation`**: Android Studio project scaffold, Room-backed local
  history/disease-reference repositories, navigation shell, permissions strategy — offline-only,
  no Firebase dependency.
- **`feature/auth-service`**: every Kotlin repository for Sprint 1–5 — Auth (email/password),
  User/Farm, Diagnosis-sharing (with Storage image upload), Community (posts/comments/upvotes),
  GIS (nearby reports, heatmap aggregation, FCM device tokens/topics), and Admin (verification,
  moderation, user management), all wired into `ServiceLocator`. Verified with a real `./gradlew
  :app:compileDebugKotlin` and `:app:testDebugUnitTest` run after every addition, all passing.

## A note on scope, for whoever reviews this

Several decision gates (`claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`) that would normally
require all three developers were instead continued on pragmatically by Panes alone, because Ligue
and Acenas were unavailable during this stretch of work — D-01 (Firestore), D-10 (a GIS provider
lean, though no map-SDK code is actually bound anywhere). Each is recorded transparently in its own
decision-gate section, not silently assumed. D-07 (outbreak thresholds) and D-02 (Technician role)
were **not** worked around this way — they require expert/team input no amount of solo continuation
substitutes for, and the code (`getHeatmapAggregates`, `outbreakRules`) stays correctly inert
until those resolve for real.
