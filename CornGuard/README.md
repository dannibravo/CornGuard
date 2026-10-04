# CornGuard

Corn leaf disease detection for farmers in Bukidnon: scan a leaf with your phone (works offline), get the disease and
treatment guidance, share reports with nearby farmers, and see outbreaks on a map. Admins review reports and approve
outbreak alerts on a website.

| Folder | What it is | Docs |
|---|---|---|
| `android/` | The CornGuard Android app (Kotlin, on-device TFLite model) | [android/README.md](android/README.md) |
| `backend/` | Convex backend: accounts, scans, community, outbreak severity, push notifications | [backend/README.md](backend/README.md) |
| `admin-web/` | CornGuard Admin website (React + Convex) | [admin-web/README.md](admin-web/README.md) |
| `ml/` | Model training notebook | [ml/README.md](ml/README.md) |
| `docs/` | Testing guide and project documents | [docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md) |
| `claude/` | Team process and planning docs | |

Third-party data and libraries (barangay boundaries, Leaflet, OpenStreetMap) are credited in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Download the app (APK)

The installable app is published on this repository's **Releases** page, not in the code
(latest: **Releases → [v0.2.0-debug](https://github.com/dannibravo/CornGuard/releases/tag/v0.2.0-debug) → `CornGuard-0.2.0-debug.apk`**).

To install on a phone (Android 8.0 or newer):

1. Download the `.apk` file on the phone (or copy it over from a computer).
2. Tap it. If Android asks, allow **Install unknown apps** for your browser or file manager.
3. Tap **Install**, then open **CornGuard**.

This is a **debug build** for testing. It connects to the project's development backend; scanning works offline, while
sign-in, Community, the Outbreak Map and notifications need internet. Test accounts are listed in
[docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md).

### Publishing a new APK (maintainers)

1. Build it: in `android/`, run `./gradlew assembleDebug` (output: `android/app/build/outputs/apk/debug/app-debug.apk`).
2. On GitHub: **Releases → Draft a new release**, choose a tag in the team's `vX.Y.Z-debug` style (e.g. `v0.2.1-debug`), attach the APK
   (renamed to `CornGuard-<version>-debug.apk`) and click **Publish release**.
   With the GitHub CLI instead:
   `gh release create v0.2.1-debug CornGuard-0.2.1-debug.apk --title "CornGuard v0.2.1 (debug)" --notes "Debug build for testing"`

APK files are git-ignored on purpose: committing a ~46 MB binary for every build would make the repository huge.
