# CORNGUARD — DEVELOPMENT PLAN

This plan converts the manuscript's month-level Agile timeline into developer-oriented implementation sprints. Exact weekly dates should be placed in the team's task board; the sequence below must preserve the manuscript's dependencies: model work begins before final mobile inference integration, Android development runs October–December, the community module is developed November–December, system/UAT testing runs December–January, and final documentation continues through February.

## Sprint 0 — Requirements Freeze and Development Setup

### Objectives

- Repository and branch workflow ready.
- Android project ready.
- Firebase development project ready.
- Local database foundation ready.
- Model repository/notebook structure ready.
- Data/integration contracts drafted.
- Decision gates identified.

### Ligue

- Create Android Studio project structure.
- Establish package/module conventions.
- Create base navigation and reusable UI components.
- Prepare camera, gallery, location, notification permission strategy.
- Create SQLite abstraction/repository skeleton.
- Add placeholder model-service interface without hardcoding preprocessing.
- Prepare build variants for development and release where practical.

### Panes

- Create/configure Firebase development environment.
- Prepare Authentication configuration.
- Draft cloud logical schema and security rules.
- Prepare Storage folder/path rules.
- Prepare FCM setup plan.
- Define cloud repository interfaces independent of final Firestore/Realtime Database decision.
- Draft GIS/location service interface without locking an unapproved map provider.

### Acenas

- Prepare dataset inventory and provenance sheet.
- Verify class labels and file integrity.
- Prepare Colab training notebook structure.
- Define reproducible split/seed process pending final split decision.
- Define model metadata/versioning format.
- Establish baseline MobileNetV2 transfer-learning pipeline.

### Shared Output

- Repository runs on all developer machines.
- `develop` branch protected by team practice.
- Decision-gate list reviewed.
- Initial contracts approved for Sprint 1.

---

## Sprint 1 — Application Foundation, User Profile, Dataset Baseline

### Objectives

- Android shell is usable.
- Firebase authentication works in development.
- User/farm/location data model is ready at contract level.
- ML dataset baseline is reproducible.

### Ligue

- Build Home screen from approved design.
- Build authentication screens only as required by approved UX.
- Build profile/farm setup UI after fields are confirmed.
- Implement navigation to Scan, History, Map, Community.
- Implement offline-capable local repository foundation.
- Add clear connectivity-state handling for online-only features.

### Panes

- Implement Firebase Authentication service.
- Implement user profile repository.
- Implement provisional Farm repository after D-03 is approved.
- Implement security rules for user-owned profile/farm data.
- Create cloud data mocks/test fixtures for mobile integration.

### Acenas

- Clean and validate public dataset.
- Document class counts.
- Implement image loading/resizing.
- Implement approved normalization.
- Implement training-only augmentation.
- Implement class-weight calculation.
- Train baseline MobileNetV2 transfer-learning model.
- Record baseline metrics.

### Dependencies

- Ligue should not bind final farm fields until D-03 is resolved.
- Acenas must freeze model input preprocessing before Sprint 2 final integration.

### Sprint Output

- Authentication/profile foundation works online.
- Home/navigation works.
- Local persistence layer exists.
- Reproducible baseline model training exists.

---

## Sprint 2 — Offline Disease Detection, Treatment, and Local History

### Objectives

- End-to-end offline scan flow works.
- TFLite model runs on Android.
- Treatment guidance and scan history work offline.

### Acenas

- Fine-tune MobileNetV2.
- Evaluate accuracy, precision, recall, F1, and confusion matrix.
- Export TFLite candidate.
- Publish exact class order, input tensor shape/type, normalization, and output semantics.
- Compare Python reference inference with TFLite output.
- Produce model version metadata.

### Ligue

- Build Camera Capture screen.
- Implement gallery upload.
- Implement image rotation/orientation handling.
- Implement exact preprocessing from ML contract.
- Integrate TFLite interpreter.
- Build Diagnosis Result screen.
- Build Treatment Recommendation screen.
- Implement local disease-reference lookup.
- Build Scan History screen.
- Save diagnosis records to SQLite with timestamp, label, confidence, model version, and location if available.
- Test all core scan features with airplane mode/no network.

### Panes

- Define cloud diagnosis-sharing contract.
- Prepare location metadata contract.
- Prepare cloud storage upload service for report images.
- Prepare optional cloud diagnosis repository without forcing every scan to upload.
- Review privacy boundaries for exact farm coordinates.

### Integration Tests

- Python/TFLite/Android label agreement.
- Known test images produce expected class mapping.
- No Firebase dependency in scan path.
- Local history survives app restart.
- Treatment screen remains available offline.

### Sprint Output

- Offline diagnostic MVP.

---

## Sprint 3 — Community Module and Cloud Reporting

### Objectives

- Online community workflow works.
- Completed scans can pre-populate a report.
- Cloud data and image uploads are secure.

### Panes

- Implement selected Firebase database service after D-01 resolution.
- Implement community post repository.
- Implement threaded comments/replies.
- Implement optional upvote storage if retained in final scope.
- Implement Cloud Storage upload rules and metadata.
- Implement location-aware feed query strategy.
- Implement moderation status fields.
- Implement Firebase security rules and required indexes.

### Ligue

- Build Community/Notification screen according to approved design.
- Build Create/Post Report flow using approved UI.
- Integrate manual report creation.
- Integrate pre-population from scan result, image, and approximate location.
- Handle upload progress, failure, retry, and offline messaging.
- Show report verification state where applicable.
- Cache/re-display previously loaded community content using the selected Firebase/client mechanism.

### Acenas

- Provide confidence interpretation guidance for report metadata.
- Produce model limitations text for community/report context.
- Support testing with known positive/negative samples.
- Work with Panes to ensure model confidence is never treated as expert confirmation by itself.

### Sprint Output

- Farmer can create and discuss disease reports while online.
- Offline detection remains unaffected when community services fail.

---

## Sprint 4 — GIS Mapping, Heatmap, Nearby Reports, and Notification Rules

### Objectives

- Disease occurrence data can be displayed geographically.
- Nearby reports work.
- Heatmap visualization works with test data.
- Notification pipeline works with development rules.

### Panes

- Implement selected map/GIS provider integration service after D-10 is resolved.
- Implement location storage/query logic.
- Implement geographic hierarchy fields: province, municipality/city, barangay, coordinates.
- Implement nearby-report query contract.
- Implement heatmap data aggregation.
- Implement FCM device token registration.
- Implement notification routing by approved geographic area.
- Implement configurable outbreak-rule engine/data structure.
- Keep production outbreak trigger disabled until D-07 is approved.

### Ligue

- Build/integrate GIS Disease Map / Heat Map screen.
- Display map markers/heat layer from Panes's service contract.
- Add nearby reports panel.
- Implement location permission UX and denied-permission fallback.
- Display notification cards/history in approved community/notification screen.
- Clearly label unverified vs verified reports if validation is enabled.

### Acenas

- Assist in defining which detection metadata is technically meaningful for aggregation.
- Validate confidence handling across model versions.
- Provide test datasets for simulated outbreak clusters.
- Participate in expert consultation for threshold definition.

### Sprint Output

- GIS and notification features function with development/test data.
- Production outbreak thresholds remain configuration-controlled.

---

## Sprint 5 — Admin/Moderation Services, Reference Data, and Integration Hardening

### Objectives

- Administrator permissions work.
- Community moderation/reference maintenance is functional.
- All major modules are integrated.

### Panes

- Implement admin authorization checks.
- Implement moderation operations.
- Implement user-management operations allowed by the approved scope.
- Implement disease-reference update path if approved.
- Implement notification monitoring/logging.
- Harden Firebase security rules.
- Add cloud validation and index tests.

### Ligue

- Integrate approved admin UI only if its platform/design has been formally confirmed.
- Otherwise, focus on Android integration, user-facing error states, loading states, accessibility, and performance.
- Polish Home, Scan, Result, Treatment, History, Map, and Community flows.
- Verify no online-only dependency leaks into offline scan path.

### Acenas

- Freeze release-candidate model.
- Produce model card/technical report.
- Benchmark TFLite on target Android devices.
- Verify accuracy/F1 gates.
- Validate field-image behavior and identify common failure modes.

### Sprint Output

- Integrated release candidate ready for formal testing.

---

## Sprint 6 — System Testing, Field Validation, UAT, and Bug Fixing

### Objectives

- Validate model and system under real conditions.
- Complete farmer UAT and IT expert evaluation.
- Complete agricultural disease-result validation.
- Fix critical defects.

### All Developers

- Execute unit, integration, offline, performance, security, and usability tests.
- Conduct real farm environment testing in Bukidnon.
- Record defects with reproducible steps.
- Prioritize critical and high-impact defects.
- Re-test every fixed defect.

### Ligue

- Own Android functional test execution and usability observation support.
- Track crashes, navigation issues, camera failures, storage failures, and device compatibility.
- Prepare APK builds for controlled test sessions.

### Panes

- Own cloud/community/GIS/security testing.
- Validate account permissions, data access, uploads, map/report correctness, notification delivery, and moderation.
- Verify privacy and security rules before field testing with real user data.

### Acenas

- Own model evaluation and field-result comparison.
- Prepare confusion matrix and class metrics.
- Coordinate agricultural expert validation of sample diagnoses.
- Measure on-device inference speed and accuracy on field images.

### Required Evaluation Groups

- Target 30 farmer respondents for live/UAT testing as described in the manuscript.
- Minimum 3 IT expert evaluators as described in the manuscript.
- Agricultural disease validators must also be included as required by the panel; this is separate from general farmer usability and IT review.

### Sprint Output

- Test evidence package.
- UAT results.
- Expert validation evidence.
- Release-blocking bug list reduced to zero.

---

## Sprint 7 — Release, Documentation, and Handover

### Objectives

- Stable application build.
- Final model and data contracts frozen.
- Documentation complete.
- Demo/defense environment reproducible.

### Ligue

- Produce signed release APK/AAB according to school deployment needs.
- Verify clean-install and upgrade behavior.
- Prepare demo device and offline demo dataset.
- Document Android build steps.

### Panes

- Freeze Firebase production rules/configuration.
- Verify indexes/storage policies/notification settings.
- Export or document cloud configuration required for handover.
- Prepare admin/moderation demo data.

### Acenas

- Freeze TFLite release model and metadata.
- Archive training notebook, dataset manifest, metrics, and model checksum.
- Prepare model evaluation figures/tables for final documentation.

### Shared

- Merge approved `develop` to `main`.
- Tag the release.
- Complete final paper and technical appendix.
- Prepare defense demo accounts and sample cases.
- Document known limitations and future work.
