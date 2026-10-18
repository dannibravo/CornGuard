# Android Emulator Setup Guide (No Android Studio)

Complete walkthrough of setting up an Android emulator from scratch using command-line tools, then installing and
testing the CornGuard app on it. Adapted from the caps 3 project's guide (originally written for Expo).
For what to test once the app is running, see [TESTING_GUIDE.md](TESTING_GUIDE.md).

## 1. Download Command-Line Tools

1. Go to https://developer.android.com/studio#command-line-tools-only
2. Download `commandlinetools-win-*.zip`
3. Extract to `C:\AndroidSDK\cmdline-tools` (create the folder first)

The final structure must be:
```
C:\AndroidSDK\cmdline-tools\latest\bin\sdkmanager.bat
```

If after extraction you have `C:\AndroidSDK\cmdline-tools\cmdline-tools\`, move the inner folder:
```powershell
Move-Item "C:\AndroidSDK\cmdline-tools\cmdline-tools" "C:\AndroidSDK\cmdline-tools\latest"
```

## 2. Enable Hardware Acceleration (AMD Ryzen)

Run **as Administrator** in PowerShell:

```powershell
Enable-WindowsOptionalFeature -Online -FeatureName "Microsoft-Hyper-V" -All
Enable-WindowsOptionalFeature -Online -FeatureName "HypervisorPlatform"
```

**Reboot** after.

> On Intel CPUs, use Intel HAXM instead. On AMD, WHPX (Windows Hypervisor Platform) is required.

## 3. Install SDK Packages

All at once (recommended):

```powershell
& "C:\AndroidSDK\cmdline-tools\latest\bin\sdkmanager.bat" `
    "system-images;android-30;google_apis_playstore;x86" `
    "emulator" `
    "platform-tools" `
    "platforms;android-30"
```

- `system-images;android-30;google_apis_playstore;x86` — Android 11 with Google Play Store (~2.5 GB). Includes Google Play Services, which CornGuard needs for push notifications (FCM).
- `emulator` — The emulator binary.
- `platform-tools` — `adb` and `fastboot`.
- `platforms;android-30` — SDK platform for building.

> **Why not `default` image?** The AOSP `default` image has no Google Play Services, so CornGuard can't receive outbreak notifications on it.

### Package sizes (approx)

| Package | Size |
|---|---|
| `system-images;android-30;google_apis_playstore;x86` | ~2.5 GB |
| `system-images;android-28;default;x86` | ~500 MB |
| `emulator` | ~200 MB |
| `platform-tools` | ~10 MB |

### Where files land

```
C:\AndroidSDK\
├── cmdline-tools\latest\bin\   — sdkmanager, avdmanager
├── emulator\                   — emulator.exe
├── platform-tools\             — adb.exe, fastboot.exe
├── platforms\android-30\       — Android SDK platform
├── system-images\android-30\google_apis_playstore\x86\   — system image
└── licenses\                   — accepted licenses
```

## 4. Accept Licenses

```powershell
& "C:\AndroidSDK\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
```

Type `y` to accept all.

## 5. Set Environment Variables

Permanent (current user):

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\AndroidSDK", "User")

$path = [Environment]::GetEnvironmentVariable("Path","User")
[Environment]::SetEnvironmentVariable("Path", $path + ";C:\AndroidSDK\platform-tools;C:\AndroidSDK\emulator", "User")
```

For the current session only:

```powershell
$env:ANDROID_HOME = "C:\AndroidSDK"
$env:Path += ";C:\AndroidSDK\platform-tools;C:\AndroidSDK\emulator"
```

**Restart your terminal** after setting permanent variables.

## 6. Create an AVD (Android Virtual Device)

```powershell
echo no | & "C:\AndroidSDK\cmdline-tools\latest\bin\avdmanager.bat" create avd `
    -n Pixel_9 -k "system-images;android-30;google_apis_playstore;x86" -d pixel
```

- `-n Pixel_9` — Name of your AVD.
- `-k` — System image to use.
- `-d pixel` — Device profile (screen size, density).

### Recommended AVD tweaks

Edit `C:\Users\<you>\.android\avd\Pixel_9.avd\config.ini`:

```ini
hw.ramSize=4G          # More RAM = smoother
hw.gpu.enabled=yes     # Enable GPU acceleration
PlayStore.enabled=yes  # Enable Play Store
```

## 7. Launch the Emulator

```powershell
& "C:\AndroidSDK\emulator\emulator.exe" -avd Pixel_9 -gpu auto
```

- `-avd Pixel_9` — Which AVD to run.
- `-gpu auto` — Use GPU acceleration.

### List available AVDs

```powershell
& "C:\AndroidSDK\emulator\emulator.exe" -list-avds
```

## 8. Verify ADB Connection

```powershell
& "C:\AndroidSDK\platform-tools\adb.exe" devices
```

Expected output:

```
List of devices attached
emulator-5554   device
```

## 9. Install and Run CornGuard

The emulator image is x86, while CornGuard's on-device AI model uses the ARM build of TensorFlow Lite, so install
with `--abi armeabi-v7a` (Android 11 emulators translate ARM code):

```powershell
# The APK from the GitHub release (Releases -> latest -> CornGuard-<version>-debug.apk)
adb install -r --abi armeabi-v7a "C:\path\to\CornGuard-0.2.1-debug.apk"

# Or one you built yourself (in the project's android folder: .\gradlew assembleDebug)
adb install -r --abi armeabi-v7a android\app\build\outputs\apk\debug\app-debug.apk
```

Then open **CornGuard** from the app list (the debug build's package is `com.cornguard.app.debug`).

> **Building it yourself:** use JDK 17 (Android Studio's bundled one works). A newer JDK set in `JAVA_HOME`
> (e.g. JDK 26) makes the Gradle build fail.

### Photos for scanning

Copy test leaf photos into the emulator's gallery (Git Bash: prefix with `MSYS_NO_PATHCONV=1` so the `/sdcard` path
isn't rewritten):

```powershell
adb push "C:\path\to\Acenas_Dataset\data\Blight\Corn_Blight (1).jpg" /sdcard/Pictures/CornGuard/blight_01.jpg
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/CornGuard/blight_01.jpg
```

In CornGuard: **Scan -> Gallery -> Pictures -> CornGuard**.

### GPS location for scans

The emulator has no real GPS; give it a position before scanning:

- **Extended controls (⋯) -> Location -> Single points:** type latitude and longitude, then click **Set Location**.
  "Save point" only adds it to the list — it doesn't move the emulator.
- Or from the command line (note: **longitude first**, then latitude):

  ```powershell
  adb emu geo fix 125.09 7.91   # Poblacion, City of Valencia
  ```

The scan's result sheet should then show the barangay (e.g. "Poblacion, City of Valencia") instead of
"GPS unavailable". A cold boot resets the position.

## 10. Common ADB Commands

```powershell
# Push file to emulator
adb push "C:\path\to\file.png" /sdcard/Download/

# Pull file from emulator
adb pull /sdcard/Download/file.png "$env:USERPROFILE\Desktop\"

# List files on emulator storage
adb shell ls /sdcard/Download/

# Take screenshot (saves to emulator gallery)
# Use the emulator side-panel camera icon instead

# Open a shell on the emulator
adb shell

# Install an APK directly (CornGuard: add --abi armeabi-v7a, see section 9)
adb install -r --abi armeabi-v7a app.apk

# Uninstall a package
adb uninstall com.cornguard.app.debug

# Open CornGuard
adb shell monkey -p com.cornguard.app.debug -c android.intent.category.LAUNCHER 1
```

## 11. Clean Up / Uninstall

Remove everything:

```powershell
Remove-Item -Recurse -Force "C:\AndroidSDK"
Remove-Item -Recurse -Force "$env:USERPROFILE\.android"
```

Remove only a specific system image:

```powershell
& "C:\AndroidSDK\cmdline-tools\latest\bin\sdkmanager.bat" --uninstall "system-images;android-28;default;x86"
```

Remove only an AVD:

```powershell
& "C:\AndroidSDK\cmdline-tools\latest\bin\avdmanager.bat" delete avd -n Pixel_9
```

## 12. Troubleshooting

Try these in order — from the gentlest fix to the most drastic. `-wipe-data` (last) erases everything on the emulator.

### Prevent it: always cold boot (recommended)

By default, closing the emulator saves a **Quick Boot snapshot** and the next start restores it. If the emulator was frozen
when it closed, it comes back **still frozen** — "restarting" it doesn't help (`adb shell cat /proc/uptime` shows it never
rebooted). Make every start a fresh boot instead. Close the emulator, then edit
`C:\Users\<you>\.android\avd\Pixel_9.avd\config.ini`:

```ini
fastboot.forceColdBoot=yes   # always boot fresh, never restore a snapshot
fastboot.forceFastBoot=no    # don't use Quick Boot
```

Booting takes about a minute instead of a few seconds; installed apps, files and sign-ins are kept. To cold boot just
once without changing the config:

```powershell
& "C:\AndroidSDK\emulator\emulator.exe" -avd Pixel_9 -no-snapshot
```

Afterwards you can free ~4 GB by deleting the old snapshot (only while the emulator is closed — it's locked while running):

```powershell
Remove-Item -Recurse -Force "$env:USERPROFILE\.android\avd\Pixel_9.avd\snapshots\default_boot"
```

### Wait after booting

Right after boot, Android starts many apps at once. Opening an app (or tapping around) in the first 30–60 seconds can
freeze the system UI — that's what causes the missing bars below. Wait on the home screen until it's responsive.

### Status bar / Home button missing, or a white screen

The system UI froze during boot. The emulator isn't broken. First check that Android still responds — pressing Home
should bring up the launcher:

```powershell
adb shell input keyevent KEYCODE_HOME
```

If the bars are still missing, reboot Android inside the emulator (keeps all apps and data), then wait 30–60 seconds:

```powershell
adb reboot
adb wait-for-device
```

### Emulator frozen / not responding

Close it from the command line (force-kill it if that doesn't work), then start it with a cold boot:

```powershell
adb emu kill
# if it's still running after ~30 s:
taskkill /F /IM qemu-system-x86_64.exe

& "C:\AndroidSDK\emulator\emulator.exe" -avd Pixel_9 -no-snapshot
```

### `adb devices` shows `unauthorized`

The emulator is showing an **"Allow USB debugging?"** prompt — click **Allow** (tick "Always allow"). If no prompt
appears, restart adb:

```powershell
adb kill-server
adb start-server
adb devices
```

### Fake GPS location (Extended controls → Location)

- **Save point** only adds a point to the list; it doesn't move the emulator. Select the point and click
  **Set Location** to actually send it.
- A cold boot resets the location — click **Set Location** again after every restart.
- From the command line (note: **longitude first**, then latitude):

  ```powershell
  adb emu geo fix 125.053248 7.925762
  ```

### App shows a white screen where a map/web page should be

The app's WebView renderer crashed (often low memory on the emulator). CornGuard (v0.2.1+) handles this — the map
screen reloads by itself. If it ever stays white, go back and reopen the screen, or restart the app:

```powershell
adb shell am force-stop com.cornguard.app.debug
```

### Last resort: wipe the emulator

Only if nothing above works. **This erases all apps, files and sign-ins** on the emulator (the AVD itself is kept):

```powershell
& "C:\AndroidSDK\emulator\emulator.exe" -list-avds
& "C:\AndroidSDK\emulator\emulator.exe" -avd Pixel_9 -wipe-data
```

## 13. Version History (this setup)

| Component | Version |
|---|---|
| Android API | 30 (Android 11) |
| System image | google_apis_playstore x86 |
| AVD name | Pixel_9 |
| Device profile | pixel (1080×1920, 420dpi) |
| RAM | 4 GB |
| GPU | Enabled (auto) |
| Boot mode | Cold boot every start (`fastboot.forceColdBoot=yes`) |
| SDK location | `C:\AndroidSDK` |
| AVD location | `%USERPROFILE%\.android\avd` |
