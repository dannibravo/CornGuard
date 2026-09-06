# CORNGUARD — MASTER DEVELOPMENT CONTEXT

## Project Overview

**Project Name:** CORNGUARD

CORNGUARD is an Android mobile application intended for smallholder corn farmers in Bukidnon. Its primary purpose is to provide real-time corn leaf disease detection using a lightweight MobileNetV2 Convolutional Neural Network deployed with TensorFlow Lite. The system also supports treatment guidance, local diagnosis history, GIS-based disease monitoring, heatmap visualization, community reporting/discussion, and location-based alerts.

The development documentation must remain aligned with the capstone manuscript and the approved proposal-defense recommendations.

## Confirmed Target Diseases

The model classifies four output classes:

- Common Rust
- Gray Leaf Spot
- Northern Leaf Blight
- Healthy

The system is limited to visible corn leaf symptoms. It does not diagnose root, stem, or internal diseases outside the trained classes.

## Core Users

### Farmer
Primary user of the Android application.

Farmer capabilities include:

- Register/login when online.
- Maintain profile and farm location information.
- Capture a corn leaf image using the camera.
- Select an existing image from the gallery.
- Run offline AI disease detection.
- View disease label and confidence score.
- View treatment and prevention guidance.
- Save and review scan history locally.
- View GIS disease map and heatmap when map data is available.
- View nearby disease reports.
- Create community reports/posts when online.
- Pre-populate a report from a completed scan.
- Reply to community discussions.
- Upvote helpful posts if this feature remains in the approved final scope.
- Receive location-based outbreak notifications when online.

### System Administrator
Confirmed by the architecture and use-case sections.

Administrator responsibilities include:

- Manage user accounts.
- Moderate community content.
- Monitor diagnosis reports and notifications.
- Maintain disease reference/treatment data.
- Maintain system reference/configuration data.
- Support outbreak-monitoring configuration once validation rules are approved.

**Important:** The manuscript does not clearly specify whether the administrator uses a web dashboard, a dedicated mobile interface, or Firebase tooling. Do not invent a new admin platform without an approved UI/use-case update.

### Agricultural Technician — Conditional/Gated Role
The community implementation section describes an optional Agricultural Technician role that can verify posts and provide expert replies, while the architecture/use-case sections list only Farmer and Admin. Because this is inconsistent, the technician role must not be treated as finalized until the requirement is formally confirmed. See `03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md`.

## System Architecture

CORNGUARD follows a hybrid architecture with two major application domains.

### 1. Offline AI Detection Domain

Runs on the Android device and must function without internet connectivity.

Components:

- Camera/gallery image input
- Image preprocessing
- TensorFlow Lite model inference
- Disease classification
- Confidence score output
- Local treatment recommendation lookup
- SQLite diagnosis history
- Local model/reference metadata

The AI module must not call Firebase to perform classification.

### 2. Online Community and Monitoring Domain

Requires network connectivity for live cloud operations.

Components:

- Firebase Authentication
- Firebase cloud database service
- Firebase Cloud Storage for uploaded images
- Firebase Cloud Messaging for push notifications
- Community posts and threaded replies
- Location-aware feed filtering
- Disease report synchronization
- GIS disease occurrence data
- Heatmap/outbreak data
- Admin moderation/monitoring operations

## Data Architecture

The manuscript identifies the following entities or logical data groups:

- User
- DiagnosisRecord
- DiseaseReference
- Location
- CommunityPost
- Comment
- Notification

The proposal-defense minutes also require farm-related data to be explicitly represented and linked to farm owners and diagnosis records. Therefore the development schema must reserve a **Farm** entity/structure, but its final fields and cardinality must be approved before database freeze.

## Offline Data

The following must be locally available:

- TensorFlow Lite model
- Model labels / model metadata
- Disease treatment/prevention reference content
- Diagnosis scan history
- Data required to render previous local results

Previously loaded community content may be cached by Firebase/client persistence, but this cache must not be confused with the guaranteed offline diagnosis history.

## Online Data

The online data layer stores or synchronizes:

- User profiles
- Farm information
- Shared/cloud diagnosis records
- Community posts
- Comments/replies
- Votes/upvotes if retained
- Uploaded post images
- Geolocation metadata
- Notifications
- Moderation/verification status
- Outbreak-monitoring configuration and aggregated data when approved

## AI / Machine Learning Scope

Model development uses:

- Python
- TensorFlow / Keras
- MobileNetV2 transfer learning
- OpenCV
- NumPy
- scikit-learn for class-weight calculation and metrics as needed
- Google Colab for training
- TensorFlow Lite for Android deployment

Target dataset described in the manuscript:

- Common Rust: 1,306 images
- Gray Leaf Spot: 574 images
- Northern Leaf Blight: 1,146 images
- Healthy: 1,162 images
- Total listed public dataset: 4,188 images
- Additional locally captured Bukidnon images are intended to improve real-field relevance.

Training augmentation described by the study includes horizontal flip, vertical flip, ±15° rotation, ±10% zoom, and brightness adjustment from 80% to 120%, applied only to the training set.

## Model Quality Requirements

Confirmed targets include:

- Minimum model accuracy: 90%.
- Minimum F1-score: 0.88 is stated in the detailed NFR table.
- Confusion matrix required.
- Precision and recall required.
- Android/TFLite performance evaluation required.
- Offline inference required.

The manuscript gives both a `< 3 seconds` response target and a stricter `< 2.0 seconds` NFR. Development should target the stricter value until the manuscript is harmonized because satisfying <2 seconds also satisfies <3 seconds.

## Mobile Application UX Rules

The approved manuscript wireframes describe these main screens:

1. Home
2. Camera Capture
3. Diagnosis Result
4. Treatment Recommendation
5. GIS Disease Map / Heat Map
6. Community Notification / Forum
7. Scan History

UI design is intended to be simple, visual, icon-driven, and suitable for users with limited digital literacy. The camera screen should be reachable within three taps from Home.

Do not redesign or invent major screens unless the team has an approved design change.

## Disease Scan Workflow

1. Farmer opens Scan Corn Leaf.
2. Farmer captures or selects an image.
3. App validates/loads image.
4. App preprocesses image to the exact model input contract.
5. TFLite model runs locally.
6. App receives four-class probabilities.
7. App selects the classification result and confidence value.
8. App displays result.
9. App retrieves local treatment/prevention guidance.
10. App records the scan in local history.
11. If location permission is available, location metadata can be attached to the scan.
12. If online and the farmer chooses to share, a community report may be pre-populated with detection result, image, and approximate location.
13. Cloud reporting and alert logic then follow the approved validation rules.

## Community Workflow

1. Online user opens the community module.
2. Feed prioritizes geographically relevant posts (barangay/municipality/province) and can be filtered by disease type.
3. User can create a post manually or from a completed scan.
4. Post may contain title, body, disease tag, image, location metadata, timestamp, and user reference.
5. Other users may reply; upvoting is included in the manuscript community design.
6. Verification/moderation status must be visible where expert/admin validation is implemented.
7. Notifications may be generated for nearby disease reports/outbreak conditions according to approved rules.

## Outbreak and Alert Principle

The panel required safeguards against false reports and unnecessary panic. Therefore:

- Do not hardcode an outbreak threshold without expert validation.
- Do not label a single unverified community post as a confirmed outbreak.
- Keep outbreak rule parameters configurable.
- Store verification status and evidence needed for validation.
- Development/test environments may use test rule values, but production values require agricultural expert/adviser approval.

## Development Method

CORNGUARD uses Agile within the study's SDLC framework. Major modules can be developed in parallel but must converge through documented contracts and integration tests.

Repository workflow:

`feature/* -> develop -> main`

No developer should work directly on `main` or `develop`.

## Developer Ownership

### Ligue — Android Mobile & Integration Lead
Owns Android structure, navigation, UI, local data, device capabilities, TFLite client integration, and final APK integration.

### Panes — Firebase, Community & GIS Services Lead
Owns Firebase configuration, authentication, cloud data, media storage, community services, location-based cloud queries, notifications, security rules, and GIS/outbreak service layer.

### Acenas — AI/ML, Dataset & Model Validation Lead
Owns dataset preparation, model training, evaluation, TFLite export, model metadata, model/device benchmarking, and technical disease-model validation evidence.

## Development Priority

When source documents conflict, do not silently select a requirement. Use this priority:

1. Approved panel-required corrections/recommendations that explicitly change the proposed system.
2. Approved/current study objectives and scope.
3. Software Requirements Specification / functional and non-functional requirements.
4. System architecture, use cases, ERD, and DFD.
5. UI wireframes.
6. Developer engineering decision only after documented team approval.

Any unresolved contradiction must be added to the decision-gate file before implementation proceeds.
