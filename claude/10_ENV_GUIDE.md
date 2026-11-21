# CORNGUARD — SHARED ENVIRONMENT AND CONFIGURATION GUIDE

CORNGUARD uses Android/Firebase rather than the three `.env` setup used by Engage. Configuration must therefore be adapted to Android Studio, Firebase, model assets, and any selected GIS provider.

## Environment Separation

At minimum maintain:

- development Firebase project/configuration
- production/release Firebase project/configuration

A separate staging project is recommended if the team can maintain it, but it is not required by the study.

## Android Configuration

Keep environment-specific values outside hardcoded Kotlin source where practical.

Possible locations:

- Gradle build config fields
- `local.properties`
- a non-committed local secrets properties file
- Firebase `google-services.json` per environment
- GitHub Actions repository secrets for CI-only values

## Firebase Configuration

Required services from the manuscript:

- Authentication
- Firestore or Realtime Database — final choice pending D-01
- Cloud Storage
- Cloud Messaging

### Rules

- Never commit Firebase Admin SDK service-account private keys.
- Firebase Android client configuration is not a substitute for security rules.
- Restrict access through Firebase Authentication, security rules, app/package restrictions, and other supported controls.
- Maintain Firebase security rules and indexes in version control where supported.

## GIS / Map Provider Configuration

The study requires GIS maps and heatmaps but does not clearly fix the provider.

Before adding a map key:

1. Resolve D-10.
2. Use provider-supported Android restrictions.
3. Never place a truly secret server credential in the APK.
4. Document any billing/quota constraints.

Suggested placeholder naming only after provider selection:

- `MAPS_API_KEY`

Do not commit an unrestricted key.

## Model Files

Release model assets should be treated as versioned application artifacts.

Expected files:

- `.tflite` model
- label map
- model metadata

If the repository becomes too large, use Git LFS or a controlled release-artifact workflow. Do not download an unversioned model at runtime for the core offline scan unless the approved architecture changes.

## Local Disease Reference Data

Treatment/prevention data must be packaged or synchronized in a way that preserves offline availability.

Every reference bundle should have a version. Do not overwrite treatment content without source tracking.

## Example Local Configuration Checklist

Each developer should be able to answer yes to:

- Android project builds.
- Correct Java/JDK/Android SDK is installed.
- Development Firebase config is present.
- Production secrets are not on a shared personal machine unnecessarily.
- TFLite model version is known.
- Label file matches model version.
- Map configuration is present only if GIS provider is approved.
- No service-account JSON is tracked by Git.

## Git Ignore Checklist

Review `.gitignore` for:

- IDE-specific local files that should not be shared
- local secret property files
- signing keystores
- service-account keys
- local dataset copies if too large/private
- temporary model outputs/checkpoints
- Python virtual environments
- notebook cache/output directories where appropriate

Do not ignore source files required to reproduce model training, schema rules, or builds.

## Release Signing

Android signing keys are highly sensitive.

- Do not commit release keystore files.
- Store keystore/passwords in a controlled team location.
- Document who is authorized to create release builds.

## Dataset and Field Images

Do not place large datasets directly into the main application source tree.

Maintain:

- dataset manifest in Git
- acquisition/validation notes in Git
- raw datasets in controlled external storage
- local farmer/field images according to research consent/privacy requirements

## Production Configuration Freeze

Before UAT/final deployment, freeze and record:

- Firebase project ID
- selected cloud database product
- security rules version
- Storage rules version
- FCM configuration
- GIS provider/configuration
- model version
- disease reference data version
- Android application ID/package
- minimum/target SDK
