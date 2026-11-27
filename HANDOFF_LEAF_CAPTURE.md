# CornGuard — Session Handoff: UI check → phone testing → model check → guided leaf capture

Session date: 2026-11-27. Committed on `feature/geo-minify` at the user's request. Paths under `.local-test/`,
`backend/.env.local`, `android/local.properties` and the scratchpad exist only on the author's PC.
Follows `HANDOFF_UI_REDESIGN.md` (local-only; that redesign is now **merged**, see §3).

**Start the next session with:**
> Read `HANDOFF_LEAF_CAPTURE.md` in the repo root. Ask me the questions in §1 before writing any code, then
> follow §9.

---

## 1. Ask the user FIRST (open decisions — never assume)

1. **Scope of guided capture.** Decided: farmers photograph **one leaf, guided by an outline on the camera**
   (`docs/MULTI_PHOTO_LEAF_CAPTURE_PLAN.md` Part A). Not decided: Part B (3 photos per leaf + aggregation).
   Recommended: build Part A first (plan steps 1–3), keep Part B for later.
2. **Gallery path** (plan §8 Q3): keep with the outline-fit step, or remove gallery for diagnosis?
   Field test (§5) shows non-close-up photos get confidently wrong answers, so an unguided gallery photo is risky.
3. **Auto-verify / alerts.** Healthy whole-plant photos came out as Northern Leaf Blight at **76–94 %**, above the
   0.60 auto-verify threshold (`backend/convex/diagnosisRecords.ts` ≈l.16-21). Guided capture reduces this but is not
   validated. Ask whether to require admin review before scans drive alerts (handoff §5 of the UI doc, D-07). Do not
   change without approval.
4. **Uncommitted work on `feature/geo-minify`** (§6): commit as-is, split (crash fix vs. geo files), or drop?
   The user commits; commits carry **no Claude attribution lines** (memory rule).
5. **Release** (asked earlier, unanswered): version for the next APK (proposed **0.2.6**, versionCode 11) and whether
   it should wait for guided capture.
6. **Pill labels** "Gray Spot" / "Blight" still need confirmation with farmers / Municipal Agriculture Office.

## 2. How the user wants to work (observed rules)

- Short, direct requests ("test.", "proceed"); expects Claude to act, then report plainly what works and what doesn't.
- Investigate and verify before claiming; distinguish verified facts from assumptions; show evidence (screenshots,
  numbers).
- Asked for "accept login for testing only" → solved with a **real local backend**, not an auth bypass. Keep that
  approach; never add fake-auth shortcuts.
- **Decision this session: no model retraining and no new photo sourcing for now.** Fix input quality instead
  (guided one-leaf capture). Don't propose retraining again unless asked.
- Root `CLAUDE.md` rules still apply (offline scan path never depends on the backend; no invented features; no guessed
  model contracts; thresholds are development-only; tests before "done").

## 3. Session timeline

| # | Request | Result |
|---|---|---|
| 1 | Check whether the UI redesign handoff is implemented; if not, proceed | Already merged into `develop` (`79ed28c`, merge `52d49f9`). All spec items verified in code. Only missing item: GeoJSON size → done (rounded to 6 dp + minified, 424→133 KB, 44.5→25.8 KB gz). Simplification rejected: same file feeds `BarangayResolver` point-in-polygon + backend centroids |
| 2 | Is it applied on mobile? | In code yes; version still 0.2.5.2, so released APK is the old look. Debug APK 55.6 MB (> 50 MB budget; `corn_gate.tflite` 8.9 MB + x86/x86_64 native libs) |
| 3 | Release as APK or adb test? | Release needs version + `gh` (not installed) → not done. Test APK built |
| 4 | "test." | Installed on phone (Realme RMX3890, Android 15). **Crash on launch** (Convex `channel closed`) → fixed in `ConvexBackend.observe()` |
| 5–7 | Sample account; "couldn't reach the server"; "make it accept for testing" | No real Convex URL on this PC → set up **anonymous local Convex backend** + `adb reverse` (§7). Signed in as demo farmer; heatmap, bottom sheet, pills verified on the phone |
| 8–10 | Does the model identify healthy corn? Need 4K healthy photos? | Healthy is a class. Advised: 4K unnecessary (224 px input); all 4 classes needed for any retraining |
| 11 | Model finds disease but not healthy — recheck with internet photos | §5 test. Root cause: framing/domain, not an app bug |
| 12 | Skip retraining; guide farmers to photograph one leaf; hand off | This file |

## 4. Verified state of the redesign (do not redo)

Merged on `develop`: earth tokens light/dark + Inter (Android + admin web); backend `heatIntensity` in
`barangayStats.getAllStats` + tests; Android map heat layer, no pulse, `preferCanvas`, tiles from zoom 12, maxZoom 16;
bottom sheet 96/286/330 dp + All·Rust·Gray Spot·Blight pills + `setBottomInset` bridge; admin map toggles, gradient
legend with Moderate 2× / Severe 3× ticks, intensity meter, lazy map route.
Checks run this session: backend `npm run test:rules` 22/22 + typecheck; admin-web `npm run build`;
Android `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug` — all pass.
On-device observation: expanded sheet text is longer than the mock (privacy note below the fold); it scrolls
(`NestedScrollView`), not a bug. Optional: let the sheet height fit content.

## 5. Model check (2026-11-27) — evidence for guided capture

Ran the app's exact scan path on PC (`ai-edge-litert`): corn gate (whole image → 224, raw 0–255, p(corn) ≥ 0.5),
then disease model (centre square crop → 224 bilinear, x/127.5−1). 45 internet photos, labels checked visually by
Claude (not expert-verified). v3 = `ml/cornguard_v3_model.tflite` for comparison.

| Set | v4 (app) | v3 |
|---|---|---|
| Lab-style (PlantVillage), all classes | **22/22** (healthy 10/10) | 22/22 |
| Field, diseased (Commons) | 10/14 | 12/14 |
| Field, healthy (Commons + iNaturalist) | **3/9** | 2/9 |

Field-healthy breakdown (v4):
- One leaf filling the frame (3 photos) → **Healthy, all correct** (57–87 %).
- Whole plant / field scene (4) → **Northern Leaf Blight 76–94 %**; corn gate rejected 3 of them as not-corn.
- Leaf with glare / water / shadows (2) → Common Rust 45–52 %.

Conclusions:
- App pipeline, label order and Healthy class are correct (lab set perfect). Not a code bug.
- Failure is framing/domain: training data are 100 % close-up public leaf photos (`ml/v1_training_audit`).
- This directly answers the plan's §3.4 warning ("check that the training images look like the cropped input"):
  **close-up one-leaf input matches training and works** → supports outline guide + crop-to-outline.
- Caveats: 9 field-healthy photos only; PlantVillage probably overlaps the training set (pipeline check, not accuracy).
- Glare/shadow cases suggest the capture checks should include light/glare, not just blur and darkness.

Saved durably (git-excluded): `/home/fablords/CornGuard/.local-test/leaf-model-check-2026-11-27/`
(`photos/` 45 labelled images `<code>__<field|lab>__<id>.jpg`, `results.txt`, `scripts/run_models.py`, source URL
lists `urls.tsv`/`urls2.tsv` with Commons titles / iNaturalist obs IDs + licences). Re-run:
`python -I scripts/run_models.py photos <repo>/CornGuard/android/app/src/main/assets [other.tflite]` in a venv with
`ai-edge-litert pillow numpy` (Python 3.14 system has no TFLite runtime; scratch venv may be gone).
Use these photos to validate crop-to-outline (plan §6.2) before enabling it by default.

## 6. Repo state at hand-off

Branch **`feature/geo-minify`** (from `develop` @ `015b215`), all **uncommitted**:

| File | Change |
|---|---|
| `CornGuard/android/app/src/main/assets/geo/bukidnon-barangays.json`, `CornGuard/admin-web/src/assets/bukidnon-barangays.json` | 6-dp rounding + minify (identical copies). Centroids regenerate within 1e-6°, `barangayCentroids.ts` left unchanged |
| `.../data/remote/convex/ConvexBackend.kt` | `observe()` adds `.catch { emit(fallback) }` — fixes launch crash when Convex is unreachable (uncaught `ClientException` from treatment-guide sync in `ServiceLocator.init`) |
| `CornGuard/android/app/src/debug/AndroidManifest.xml`, `.../debug/res/xml/network_security_config.xml` | **Debug-only** cleartext to 127.0.0.1/localhost for the local backend. Release unaffected |
| `.../androidTest/.../model/InternetLeafImagesTest.kt` | Reporting test: gate + classifier on photos pushed to `/sdcard/Android/data/com.cornguard.app.debug/files/leaftest/` (log tag `LeafTest`). Compiles; **not yet run on the phone** (phone disconnected) |
| `CornGuard/backend/convex/_generated/api.d.ts` | Codegen churn from the running local `convex dev` — revert with `git checkout` before committing |

Not in git: `CornGuard/android/local.properties` now `convex.url=http://127.0.0.1:3210` (backup of the old
placeholder `https://127.0.0.1:9` was in the scratchpad; just edit the line back). `backend/.env.local` (anonymous local
deployment). Root `android/` and `ml/` leftovers: never commit.
Known stale test: `EmulatorLeafImagesTest` expects the v3 SHA (`DFE14E…`) but the app ships v4 → fails its model check.

## 7. Local test environment (no cloud Convex URL on this PC)

1. `cd CornGuard/backend && CONVEX_AGENT_MODE=anonymous npx convex dev` (keep running; http://127.0.0.1:3210,
   site 3211). Auth env already set on the local deployment (JWT_PRIVATE_KEY, JWKS, SITE_URL).
2. Data already seeded by `node scripts/seed-map-demo.mjs`: accounts `demo@` … `demo5@cornguard.app`, password
   `demo1234`; Valencia Poblacion Severe (Blight), Malaybalay Brgy 1 Moderate (Rust), Maramag North Poblacion Mild (GLS).
3. Phone on USB: `adb reverse tcp:3210 tcp:3210 && adb reverse tcp:3211 tcp:3211`.
4. Build: `cd CornGuard/android && ANDROID_HOME=$HOME/Android/Sdk bash ./gradlew :app:assembleDebug`
   (`gradlew` is not executable; `local.properties` has no `sdk.dir`). Install `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
5. For a shareable APK, point `convex.url` back to the real dev deployment (held by the teammate who runs
   `npx convex dev`; the v0.2.5.2 release was cut by Marc) — never ship the local URL.

## 8. Guided one-leaf capture — what to build (from the existing plan, adjusted by §5)

Source of truth: `CornGuard/docs/MULTI_PHOTO_LEAF_CAPTURE_PLAN.md` (status "Proposal, not approved"; user has now
approved the **one-leaf guided capture** direction). Part A, plan steps 1–3:

1. **`feature/camerax-capture`** — replace the system-camera `TakePicture` intent in `ScanFragment.launchCamera()`
   with an in-app CameraX `LeafCaptureFragment` (`PreviewView` + `ImageAnalysis` + `ImageCapture`). Offline only.
   New deps (camera-core/camera2/lifecycle/view) → check APK size (already 55.6 MB debug).
2. **`feature/leaf-outline-overlay`** — `LeafOutlineOverlayView` (vector leaf path, dimmed scrim, turns
   `cg_accent_green` when checks pass), preview→sensor coordinate mapping, **crop to outline before gate + classifier**
   (keep the uncropped original for history). Validate crop with §5 photos first (plan §6.2).
3. **`feature/capture-quality-gate`** — live checks on the outline crop, 2–3/s: corn gate ≥ 0.5, sharpness (Laplacian
   variance), brightness ~50–220, steady 3 frames; add a **glare/over-exposure** check (§5). Shutter disabled until all
   pass; one plain hint at a time ("Move closer so the leaf fills the outline", "Hold still", "Too dark", "Point at a
   corn leaf"). Thresholds in `assets/capture_config.json`, marked development-only.
   Gallery: outline-fit screen (plan §3.5) unless the user removes gallery (§1 Q2).

Use the approved design system for the camera UI (handoff UI §6: earth tokens, Inter, Android body ≥ 15 sp, 48 dp
targets, wording "AI detection", never "diagnosis"). Strings English only for now (Bisaya pending).
Contracts: crop + capture checks change the scan input → update `claude/09_ML_MODEL_CONTRACT.md` and tell the team
(`claude/04_DEVELOPMENT_RULES.md` #3). Multi-photo (Part B) adds DB v4 + Convex fields — only if approved.

## 9. Next steps

1. Ask §1 questions. Resolve `feature/geo-minify` first (user commits; revert `api.d.ts`).
2. When the phone is connected: push `.local-test/.../photos/` to the `leaftest` folder and run
   `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.cornguard.app.model.InternetLeafImagesTest`,
   then `adb logcat -s LeafTest` — confirm on-device numbers match §5.
3. Branch `feature/camerax-capture` from `develop`; build plan steps 1–3 (§8), stopping for approval between steps.
4. Re-run the §5 set through crop-to-outline; then take real Bukidnon leaf photos with the new camera on the phone.
5. Verify (CLAUDE.md): unit tests, `compileDebugKotlin`, airplane-mode scan (no backend needed), low-end phone preview
   smoothness, APK size.

Related: `HANDOFF_UI_REDESIGN.md` (redesign done; ask the user before deleting it), memory notes
`active-branch-is-develop-v2`, `local-test-backend`, `no-retraining-guided-capture`.
Delete this file when the user confirms guided capture is merged or says they're done with it.
