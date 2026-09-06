# CORNGUARD — TESTING AND ACCEPTANCE PLAN

Testing must validate both the AI classifier and the integrated mobile system. Model accuracy alone is not enough, and a usable app with an unvalidated disease model is also not enough.

## Testing Layers

1. Model evaluation
2. Unit/module testing
3. Integration testing
4. Offline functionality testing
5. Mobile performance/device testing
6. Firebase/security testing
7. GIS/location testing
8. Notification testing
9. Live field validation
10. Farmer UAT
11. Agricultural expert disease-result validation
12. IT expert evaluation
13. Regression testing

---

# 1. Model Evaluation

Owner: Acenas

Required outputs:

- accuracy
- precision
- recall
- F1-score
- confusion matrix
- per-class results
- test-set composition
- model version

Acceptance target:

- accuracy >= 90%
- F1 >= 0.88 if retained in final NFR

The final test split must be resolved before final metrics are claimed.

## Model Test Cases

- each disease class has correctly labeled test samples
- healthy class tested
- low-confidence/ambiguous samples observed
- real field images tested separately
- conversion to TFLite does not materially change prediction behavior

---

# 2. Android Unit / Module Testing

Owner: Ligue

Test:

- camera result handling
- gallery input handling
- image orientation
- preprocessing wrapper
- model output mapping
- result formatting
- local database create/read/update/delete where allowed
- treatment lookup
- history sorting
- permission handling
- connectivity-state behavior

---

# 3. Firebase / Cloud Module Testing

Owner: Panes

Test:

- registration/login
- authenticated profile access
- unauthorized access rejection
- community post create/read/update/delete rules
- comment/reply behavior
- image upload
- failed upload handling
- moderation permissions
- admin role protection
- location-aware query behavior
- FCM token lifecycle
- notification creation/delivery
- security rules against unauthorized writes

Use a development Firebase project for destructive tests.

---

# 4. Offline Functionality Testing

Critical acceptance requirement.

With network disabled:

- app launches
- scan screen opens
- camera/gallery input can be processed
- model returns diagnosis
- confidence is displayed
- treatment guidance opens
- history is saved
- previous history is readable

Expected online-only behavior:

- community live refresh unavailable
- new cloud post cannot be published unless supported by the selected Firebase offline-sync behavior and approved workflow
- live map/report updates unavailable
- push notifications unavailable

UI must explain unavailable online features without blocking the offline core.

---

# 5. Integration Testing

## Scan -> Local History

Verify a completed scan creates exactly one valid local history record with correct:

- label
- confidence
- timestamp
- model version
- location if available

## Scan -> Treatment

Verify class maps to correct DiseaseReference content.

## Scan -> Community Draft

Verify approved scan fields pre-populate the report correctly:

- disease type
- image
- approximate location
- linked local diagnosis

The user must not accidentally publish merely by opening the share action.

## Cloud Report -> GIS

Verify a location-valid report appears in the correct map/aggregation dataset according to verification rules.

## Report -> Notification

Verify notification is generated only when the configured test/approved rule conditions are satisfied.

---

# 6. Performance Testing

Because the manuscript states both <3s and <2s targets, use <2.0 seconds as the engineering pass target until corrected.

Measure:

- preprocessing time
- TFLite inference time
- result-render time
- end-to-end scan result time
- memory use where practical
- APK size
- app stability during repeated scans

Test on at least:

- a mid-range Android device
- a lower-end device representative of target farmers if available

Record device model, Android version, RAM, and processor class.

---

# 7. APK / Device Acceptance

Check:

- clean install
- permission prompts
- first launch
- offline first launch behavior if applicable
- camera availability
- storage/gallery compatibility
- Android 8.0+ baseline
- low storage handling
- repeated scan stability

The main manuscript NFR states APK <50 MB. Treat this as the package-size target unless formally revised.

---

# 8. GIS and Location Testing

Owner: Panes with Ligue

Test:

- valid latitude/longitude ranges
- denied location permission
- stale/low-accuracy GPS
- wrong or missing barangay/municipality metadata
- correct map placement
- correct nearby-report filtering
- correct administrative-area filtering
- heatmap point weighting
- duplicate record handling
- exact coordinate privacy/display behavior

Field-test location in multiple farm environments.

---

# 9. Outbreak/Alert Testing

Do not use unapproved production thresholds.

Use test configuration to verify:

- single unverified report does not become confirmed outbreak unless approved rule explicitly allows it
- confidence threshold behavior
- repeated report aggregation
- geographic boundary behavior
- time-window expiration
- notification cooldown
- verified/rejected status effect
- false-report suppression

Every production alert rule must be traceable to expert/adviser approval.

---

# 10. Live Field Validation

The panel explicitly requires real corn farm testing.

Record:

- farm/barangay environment
- date/time
- lighting/weather conditions
- device used
- actual leaf condition
- expert/qualified validator diagnosis
- CORNGUARD result
- confidence
- correct/incorrect
- notes

Do not use only PlantVillage/Kaggle test images as evidence of field suitability.

---

# 11. Farmer UAT

The manuscript targets approximately 30 farmer respondents in Bukidnon.

Each farmer should perform structured tasks without development-team assistance where possible:

- capture a corn leaf image
- obtain diagnosis
- read treatment guidance
- open history
- open GIS disease map
- create a community report

Record:

- task completion
- time on task
- errors
- confusion points
- comments

Then administer the approved usability questionnaire/SUS-style instrument.

---

# 12. Agricultural Expert Validation

This is separate from farmer UAT and IT evaluation.

Qualified validators should assess whether selected disease results/ground-truth labels are accurate.

Possible validators according to the panel:

- agricultural experts
- agricultural technicians
- experienced corn farmers with appropriate expertise
- relevant agricultural office/Department of Agriculture personnel where available

Required evidence:

- validator qualification/role
- samples reviewed
- reference diagnosis
- app diagnosis
- agreement/disagreement
- action taken for model/data refinement

---

# 13. IT Expert Evaluation

The manuscript requires at least three IT evaluators with relevant background.

Evaluate:

- functionality
- reliability
- usability
- efficiency
- maintainability
- offline operation
- architecture/integration completeness
- GIS correctness
- community integration
- adherence to system requirements

Use the approved ISO/IEC 25010-aligned instrument.

---

# 14. Defect Workflow

1. Tester records issue.
2. Assign severity and module owner.
3. Owner reproduces issue.
4. Create `fix/*` branch.
5. Implement fix.
6. Add/update test.
7. PR to `develop`.
8. Reviewer verifies fix.
9. Original tester re-tests.
10. Close defect only after evidence passes.

## Severity Guide

### Critical

- wrong disease label caused by code/class mapping
- app cannot perform offline diagnosis
- security rule exposes private data
- application consistently crashes in core flow
- production outbreak alerts trigger incorrectly at large scale

### High

- history not saved
- community report links wrong location/disease
- unauthorized action possible
- map displays wrong report region
- model performance fails required threshold

### Medium

- non-blocking UX issue
- recoverable upload failure
- incorrect non-critical formatting

### Low

- cosmetic issue with no workflow impact

---

# Release Acceptance Checklist

Before `develop -> main`:

- [ ] Model metrics pass.
- [ ] Model preprocessing/class order frozen.
- [ ] Android parity test passes.
- [ ] Offline scan passes.
- [ ] Local history passes.
- [ ] Treatment guidance passes.
- [ ] Firebase auth/security passes.
- [ ] Community integration passes.
- [ ] GIS/map tests pass.
- [ ] Notification tests pass.
- [ ] Production alert rules approved or alerts safely disabled.
- [ ] Field validation completed.
- [ ] Farmer UAT completed.
- [ ] Agricultural validation completed.
- [ ] IT expert evaluation completed.
- [ ] No critical bugs remain.
- [ ] Known limitations documented.
