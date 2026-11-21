# CORNGUARD — PROJECT CONTEXT

## Overview

CORNGUARD is an AI-powered Android application for corn disease diagnosis and community-based disease monitoring in Bukidnon. It combines offline MobileNetV2/TensorFlow Lite inference with online Firebase-backed reporting, geographic monitoring, heatmaps, and notifications.

## Primary Goal

Provide farmers with a practical tool that can identify Common Rust, Gray Leaf Spot, Northern Leaf Blight, or Healthy corn leaves in real time, including in areas with unreliable internet connectivity, while also contributing disease occurrence information for community awareness and monitoring.

## Technology Stack

### Android Mobile

- Kotlin
- XML layouts
- Android Studio
- Android SDK
- Device camera / gallery
- SQLite for local scan history and local reference data

### Machine Learning

- Python
- TensorFlow / Keras
- MobileNetV2 transfer learning
- TensorFlow Lite
- OpenCV
- NumPy
- Google Colab

### Cloud / Community

- Firebase Authentication
- Firebase Firestore or Realtime Database — final choice must be frozen before cloud implementation
- Firebase Cloud Storage
- Firebase Cloud Messaging

### GIS

The study requires GIS mapping, heatmaps, geographic hierarchy, precise latitude/longitude, and nearby-report filtering. The exact map SDK/provider is not clearly fixed in the manuscript. Do not select or embed a provider-specific dependency until the team records the decision.

### Version Control

- Git
- GitHub

## Target Platform

- Android 8.0 (Oreo) or higher is stated in the hardware requirements.
- The detailed NFR table also references Android API Level 24 and above.
- Developers should preserve Android 8.0 compatibility as the safer baseline unless the requirement is formally changed.

## Core Modules

1. Authentication and User Profile
2. Farm and Location Profile
3. Camera / Gallery Input
4. Image Preprocessing
5. Offline TFLite Disease Detection
6. Diagnosis Result and Confidence
7. Treatment and Prevention Reference
8. Local Scan History
9. Cloud Diagnosis Sharing
10. Community Posts and Threads
11. GIS Disease Map
12. Heatmap / Disease Concentration View
13. Location-Aware Feed
14. Push Notifications
15. Moderation / Verification
16. System Reference Data

## Confirmed Screens from the Study

- Home
- Camera Capture
- Diagnosis Result
- Treatment Recommendation
- GIS Disease Map / Heat Map
- Community / Notification Forum
- Scan History

Do not create a new web dashboard or extra major screen solely for developer convenience unless the approved system design is updated.

## Offline-First Boundary

Must work without internet:

- Capture/select image
- Image preprocessing
- Disease classification
- Confidence display
- Treatment/prevention guidance
- Local diagnosis history

Requires online connectivity for live operations:

- Account registration/login if not already authenticated/cached
- Cloud sync
- Community posting/browsing live data
- Image upload
- Push notification receipt
- Live GIS outbreak data
- Live moderation/verification operations

## Roles

Confirmed:

- Farmer
- System Administrator

Conditional pending alignment:

- Agricultural Technician / Extension Worker verification role

## Non-Functional Targets

- Model accuracy: at least 90%.
- F1-score: at least 0.88 where required by the detailed NFR table.
- Target inference/result time: use <2.0 seconds as engineering target until source wording is harmonized.
- Earlier manuscript section also allows up to 3 seconds; <2 satisfies both.
- APK target: less than 50 MB is stated in the main NFR section.
- Camera path should be reachable within three taps from Home.
- Geotagged/user data must be protected in transit and at rest using the security capabilities of the selected Firebase/mobile stack.
- Application must remain usable on low- to mid-range Android devices.

## Source-Controlled Development Rule

The study's requirements are the authority. If a developer discovers a missing or conflicting requirement, create a documented decision before changing architecture, data shape, model contract, user flow, or platform scope.
