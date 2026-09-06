# CORNGUARD — OFFICIAL GITHUB WORKFLOW

## Permanent Branches

- `main`
- `develop`

## Feature Branches

All implementation work happens in temporary feature branches:

- `feature/*`
- `fix/*`
- `docs/*`
- `test/*`

Examples:

- `feature/tflite-integration`
- `feature/community-data`
- `feature/gis-notifications`
- `fix/history-crash`
- `test/offline-scan`

## Never Work Directly On

- `main`
- `develop`

## Branch Flow

`feature/* -> develop -> main`

`develop` is the active integration branch.

`main` is the stable release/demo branch.

---

# PHASE 1 — BEFORE CODING

## Step 1 — Switch to develop

```bash
git checkout develop
```

## Step 2 — Pull latest changes

```bash
git pull origin develop
```

Do this before beginning work for the day.

## Step 3 — Create a task-specific branch

Example:

```bash
git checkout -b feature/tflite-integration
```

Branch names must describe the task, not the developer.

## Step 4 — Push branch once work begins

```bash
git push -u origin feature/tflite-integration
```

---

# PHASE 2 — DURING DEVELOPMENT

## Confirm branch

```bash
git branch
```

Do not continue if the active branch is `main` or `develop`.

## Commit small logical changes

Good examples:

```bash
git commit -m "feat: add camera image capture"
git commit -m "feat: save diagnosis to sqlite"
git commit -m "test: add model label mapping test"
git commit -m "fix: handle denied location permission"
```

Avoid a single giant commit containing unrelated UI, cloud, model, and documentation changes.

## Keep branch synchronized

Before major integration or before a PR:

```bash
git checkout develop
git pull origin develop
git checkout feature/your-feature
git merge develop
```

Resolve conflicts locally and run tests again.

---

# PHASE 3 — BEFORE PUSHING

Checklist:

- Project builds.
- Relevant tests pass.
- No secrets are committed.
- No private service-account key is committed.
- No release keystore is committed.
- No raw private farmer data is committed.
- No large accidental dataset/model checkpoints are committed.
- Android app still supports the required minimum device baseline.
- Offline scan still works if the feature touches shared app architecture.
- Contracts are updated if data/model shape changed.
- Decision-gate conflicts are not being silently resolved in code.

Push:

```bash
git push origin feature/your-feature
```

---

# PHASE 4 — PULL REQUEST

Create PR:

`feature/your-feature -> develop`

Do not create normal feature PRs directly to `main`.

## Pull Request Template

### Title

Short module-focused title.

Example:

`Offline TFLite Detection Integration`

### Description

Include:

**Completed**

- what was built

**Contract changes**

- data/model/config changes
- `None` if no contract changed

**Tested**

- devices/emulators
- online/offline state
- test cases

**Screenshots / evidence**

- UI or logs as appropriate

**Known issues**

- list remaining issues

**Decision gates affected**

- list any D-xx items

---

# PHASE 5 — CODE REVIEW

At least one peer reviews every PR.

A cross-module contract PR should have an affected owner review.

## Review focus

- Matches capstone requirements.
- Does not invent unsupported scope.
- No secret/privacy leak.
- Error handling exists.
- Offline behavior is preserved.
- UI follows approved flow.
- Cloud rules are safe.
- Model contract is respected.
- Location data is handled appropriately.
- Tests cover the changed behavior.

## Required owner review examples

- TFLite/preprocessing change -> Acenas and Ligue
- Firebase schema/security change -> Panes and consuming developer
- Shared Android architecture -> Ligue
- Outbreak logic -> Panes plus expert-approved configuration evidence
- Release merge -> all three developers

No developer should be the only reviewer of their own PR.

---

# PHASE 6 — MERGE

After approval:

`feature/* -> develop`

Delete the feature branch after successful merge unless there is a documented reason to keep it.

---

# PHASE 7 — CONTINUOUS INTEGRATION CHECK

Whenever major code reaches `develop`, all developers should update and verify their area.

```bash
git checkout develop
git pull origin develop
```

Verify:

### Ligue

- Android build runs.
- Navigation works.
- Offline scan still works.
- Local history still opens.

### Panes

- Firebase access works.
- Rules/indexes are valid.
- Community/GIS integration has no schema drift.

### Acenas

- Model assets and labels still match.
- Model contract is unchanged or intentionally versioned.
- Reference model test images still pass.

---

# PHASE 8 — RELEASE MERGE

Only merge `develop` into `main` when:

- sprint/release objectives are complete,
- critical integration tests pass,
- release model is identified,
- Firebase production rules are reviewed,
- no release-blocking bugs remain,
- all three developers approve the release.

Example release flow:

```bash
git checkout main
git pull origin main
git merge develop
git push origin main
```

Then create a release tag, for example:

```bash
git tag -a v0.1.0 -m "CORNGUARD release candidate"
git push origin v0.1.0
```

---

# DAILY DEVELOPER ROUTINE

## Start of day

```bash
git checkout develop
git pull origin develop
git checkout feature/my-task
git merge develop
```

## During work

Commit meaningful progress.

## End of day

- run relevant tests,
- commit work,
- push feature branch,
- update task board,
- document blockers.

Do not leave critical contract decisions only in verbal discussion.

---

# MODEL FILE WORKFLOW

Large training checkpoints should not be committed casually.

Commit or release only:

- approved `.tflite` model needed by the app,
- label file,
- metadata file,
- reproducibility scripts/notebooks,
- metrics/evaluation outputs needed for documentation.

Use Git LFS or release artifacts if model size becomes impractical for normal Git history.

---

# FIREBASE RULE WORKFLOW

Security rule changes require:

1. local/emulator or development-project testing,
2. peer review,
3. documented affected collections/nodes,
4. no production deployment directly from an unreviewed feature branch.
