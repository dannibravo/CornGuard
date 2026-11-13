# CornGuard — Testing Guide

This guide explains how to test **every feature** of CornGuard:

- 📱 **Mobile app** — the CornGuard Android app farmers use to scan corn leaves, see outbreaks and talk to other farmers.
- 💻 **Web** — the CornGuard Admin website, used by admins (for example a DA technician) to review scans, follow the automatic outbreak alerts, moderate posts and manage users.

You do not need to know how the app works. Follow the steps exactly, compare what you see with the **Expected result**, and tick **Pass** or **Fail**.

---

## Contents

1. [How to use this guide](#1-how-to-use-this-guide)
2. [Setup (do this first)](#2-setup-do-this-first)
3. [Mobile app tests 📱](#3-mobile-app-tests-)
4. [Web admin tests 💻](#4-web-admin-tests-)
5. [End-to-end scenarios 📱 + 💻](#5-end-to-end-scenarios---)
6. [Appendix: messages, rules, glossary, results sheet](#6-appendix)

---

## 1. How to use this guide

- Every test has an **ID**. IDs starting with **M-** are mobile app tests; IDs starting with **W-** are web tests; **E-** are end-to-end tests that use both.
- Every test says which **Platform** it applies to: 📱 Mobile app or 💻 Web.
- Text in "quotes" is exactly what you should see or tap on screen.
- Do the sections in order the first time — later tests sometimes rely on something done earlier (for example, being signed in).
- **When a test fails:** tick ☐ Fail, take a screenshot, and write down (1) the test ID, (2) what you did, (3) what you expected, (4) what actually happened. Then continue with the next test.
- "Tap" means touch on the phone. "Click" means mouse click on the website.

---

## 2. Setup (do this first)

### 2.1 What you need

| Item | Why |
|---|---|
| An Android phone (Android 8 or newer) **or** the Android emulator on a computer | To run the mobile app |
| A computer with Google Chrome (or Edge/Firefox) and **Node.js 18+** | To run the admin website |
| Internet connection | Sign-in, Community, Map, sharing and notifications need it (scanning does not) |
| A few corn leaf photos (healthy and diseased) | For scanning. The project's dataset folder `Acenas_Dataset/data/` has photos of Blight, Common Rust, Gray Leaf Spot and Healthy leaves |

### 2.2 Install the mobile app 📱

**On a real phone**
1. Download **`CornGuard-0.2.1-debug.apk`** from the GitHub release:
   https://github.com/dannibravo/CornGuard/releases/tag/v0.2.1-debug (or ask the project owner for the file).
2. Copy it to the phone (or download it on the phone) and tap it.
3. If Android asks, allow "Install unknown apps" for the file manager you used, then tap **Install**.
4. Open **CornGuard** from the app list. The icon is a **white shield with a yellow corn cob** on a green background.

**On the Android emulator (computer)**

Setting up an emulator from scratch, and fixing a frozen one: see [ANDROID_EMULATOR_SETUP.md](ANDROID_EMULATOR_SETUP.md).

1. Start the emulator (Android Studio → Device Manager → ▶ Play).
2. In a terminal, run (use the downloaded APK, or the one you built):
   `adb install -r --abi armeabi-v7a CornGuard-0.2.1-debug.apk`
3. Open **CornGuard** from the app list.

> Notes for the emulator:
> - It cold-boots each time but keeps installed apps. If CornGuard ever disappears or shows an old icon, install it again with step 2.
> - After it boots, wait **30–60 seconds** on the home screen before opening CornGuard. Opening apps while Android is still starting can freeze the status bar and Home button; if that happens, run `adb reboot`.
> - To give the emulator a GPS position: emulator toolbar **⋯ → Location**, type latitude and longitude, click **Set Location** ("Save point" alone does not move it). Or run `adb emu geo fix <longitude> <latitude>`, e.g. `adb emu geo fix 125.09 7.91` for Valencia. A cold boot resets the position.

### 2.3 Run the admin website 💻

1. Open a terminal in the project's `admin-web` folder.
2. Make sure a file named `.env.local` exists with one line: `VITE_CONVEX_URL=https://<deployment>.convex.cloud` (the project owner can give you this line).
3. Run `npm install` (first time only), then `npm run dev`.
4. Open **http://localhost:5173** in your browser.

### 2.4 Test accounts

| Account | Email | Password | Used for |
|---|---|---|---|
| Demo farmer | `demo@cornguard.app` | `demo1234` | Main mobile app account (profile: Poblacion, Valencia, Bukidnon) |
| Helper farmer 2 | `demo2@cornguard.app` | `demo1234` | Second farmer for outbreak/comment tests |
| Helper farmer 3 | `demo3@cornguard.app` | `demo1234` | Third farmer for outbreak tests |
| Admin | `admin@cornguard.app` | **Ask the project owner** (not written here for security) | Admin website |

You may also create new accounts during testing (see M-AUTH tests). Use made-up emails such as `tester1@example.com`.

### 2.5 Demo data (optional, for map and outbreak tests)

The project owner (or a tester with the code) can load demo outbreak data from the `backend` folder:

- Add demo reports: `node scripts/seed-map-demo.mjs` — first gives each demo barangay 6 weeks of past healthy scans (so it has left "collecting data"), then creates one **Severe** outbreak (Poblacion, City of Valencia, 5 farms), one **Moderate** (Barangay 1, City of Malaybalay, 3 farms) and one **Mild** (North Poblacion, Maramag).
- Remove them again: `node scripts/seed-map-demo.mjs --clear`
- Give any barangay past data by hand (alert tests): `node scripts/alert-scenarios.mjs history "<barangay>" "<municipality>" [weeks] [healthy per week] [disease] [farms per week]`, then share scans as demo accounts 1–6 with `node scripts/alert-scenarios.mjs scan <1-6> "<barangay>" "<municipality>" [disease]`.

---

## 3. Mobile app tests 📱

### 3.1 Navigation and app-wide

#### M-NAV-01 · App opens to the Dashboard
**Platform:** 📱 Mobile app
**What to test:** The first screen shown when the app starts.
**Steps:**
1. Close CornGuard completely (swipe it away from recent apps).
2. Open CornGuard from the app list.
**Expected result:** The **Dashboard** appears with "Welcome back," at the top, a name below it, and three cards: "Scan Corn", "Community" and "Outbreak Map". A bottom bar shows "Dashboard", "History" and "Settings", with "Dashboard" highlighted.
**Explanation:** The Dashboard is the home screen; from here farmers reach every main feature.
**Error/edge cases:** The app must open even with no internet and without being signed in.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-NAV-02 · Bottom bar navigation
**Platform:** 📱 Mobile app
**What to test:** The three bottom bar buttons.
**Steps:**
1. Tap "History". 2. Tap "Settings". 3. Tap "Dashboard".
**Expected result:** Each tap opens that screen ("Detection History", "Profile & Settings", the Dashboard) and the tapped button becomes highlighted.
**Explanation:** The bottom bar is the main way to move between the three top-level screens.
**Error/edge cases:** Tapping the already-selected button again should not crash or open a duplicate screen. Tapping quickly between buttons should always end on the last one tapped.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-NAV-03 · Screens opened from the Dashboard keep "Dashboard" highlighted
**Platform:** 📱 Mobile app
**What to test:** Navigation to Scan, Community and Map.
**Steps:**
1. On the Dashboard, tap the "Scan Corn" card, look at the bottom bar, then tap the back arrow (top left).
2. Repeat with the "Community" card and the "Outbreak Map" card.
**Expected result:** Each card opens its screen (Scan Corn Leaf / Community / Outbreak Heatmap). The bottom bar still highlights "Dashboard". The back arrow returns to the Dashboard.
**Explanation:** These screens belong to the Dashboard section, so the bar should show you are still in that section.
**Error/edge cases:** The phone's own Back button (or back gesture) should do the same as the on-screen back arrow.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-NAV-04 · Offline banner
**Platform:** 📱 Mobile app
**What to test:** The warning shown when there is no internet.
**Steps:**
1. Turn on Airplane mode (or turn off Wi-Fi and mobile data).
2. Look at the app (any screen).
3. Turn the internet back on.
**Expected result:** While offline, a banner appears: "You're offline. Scanning and history still work. Community, map, and notifications need internet." It disappears by itself when the internet returns.
**Explanation:** Farmers in the field often lose signal; the banner tells them what still works.
**Error/edge cases:** Turning the internet on and off several times should show/hide the banner each time without freezing the app.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-NAV-05 · New app icon
**Platform:** 📱 Mobile app
**What to test:** The launcher icon.
**Steps:**
1. Go to the phone's home screen and open the app list.
2. Find CornGuard.
**Expected result:** The icon shows a white shield with a yellow corn cob and green husk leaves on a green background. The whole shield is visible (not cut off), whether the phone shows round or square icons.
**Explanation:** The icon was redesigned so it clearly shows "corn" + "guard" instead of a plain oval.
**Error/edge cases:** On Android 13+ with "Themed icons" turned on (long-press home screen → Wallpaper & style), the icon should switch to a single-colour version of the shield.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-NAV-06 · Light and dark theme
**Platform:** 📱 Mobile app
**What to test:** The app follows the phone's light/dark setting.
**Steps:**
1. Turn on Dark theme in the phone settings (Settings → Display → Dark theme).
2. Open CornGuard and visit Dashboard, History, Settings, Scan and Community.
3. Turn Dark theme off and check again.
**Expected result:** In dark theme the backgrounds become dark and text light; in light theme the opposite. All text stays readable on every screen.
**Explanation:** The app uses the system theme so it is comfortable to use in sunlight and at night.
**Error/edge cases:** Switching the theme while the app is open should redraw the app without losing what you were doing.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-NAV-07 · Rotation and app restart keep you signed in
**Platform:** 📱 Mobile app
**What to test:** The app keeps its state.
**Steps:**
1. Sign in with the demo farmer (see M-AUTH-06).
2. Rotate the phone (if auto-rotate is on), then rotate back.
3. Close the app completely and open it again.
**Expected result:** Nothing is lost when rotating. After reopening, you are still signed in (Settings still shows "Demo Farmer").
**Explanation:** The sign-in session is saved on the phone so farmers do not have to sign in every time.
**Error/edge cases:** Reopening the app with no internet should still show you as signed in (online screens simply show the offline banner).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 3.2 Dashboard

#### M-HOME-01 · Greeting shows the user's name
**Platform:** 📱 Mobile app
**What to test:** The name under "Welcome back,".
**Steps:**
1. While signed out, open the Dashboard and read the name.
2. Sign in as the demo farmer and return to the Dashboard.
**Expected result:** Signed out: "Farmer". Signed in: "Demo Farmer".
**Explanation:** The greeting personalises the app using the profile name.
**Error/edge cases:** If the profile name is changed (M-SET-04), the Dashboard should show the new name the next time it is opened.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-HOME-02 · "Scan Corn" card
**Platform:** 📱 Mobile app
**What to test:** The card that opens the scanner.
**Steps:** 1. Tap the "Scan Corn" card ("Identify diseases instantly").
**Expected result:** The "Scan Corn Leaf" screen opens with "Ready to scan", "Open Camera" and "Pick from Gallery".
**Explanation:** Scanning is the main feature, so it has the biggest card.
**Error/edge cases:** Works signed out and offline. Double-tapping the card should open only one scan screen.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-HOME-03 · "Community" card
**Platform:** 📱 Mobile app
**What to test:** The card that opens the community feed.
**Steps:** 1. Tap the "Community" card ("Share reports and insights with other farmers").
**Expected result:** The "Community" screen opens.
**Explanation:** Lets farmers read and share reports with farmers nearby.
**Error/edge cases:** When signed out, the screen asks you to sign in (see M-COM-01).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-HOME-04 · "Outbreak Map" card
**Platform:** 📱 Mobile app
**What to test:** The card that opens the outbreak map.
**Steps:** 1. Tap the "Outbreak Map" card ("View active crop disease outbreaks in Bukidnon").
**Expected result:** The "Outbreak Heatmap" screen opens showing a map of Bukidnon.
**Explanation:** Shows where diseases are being reported.
**Error/edge cases:** When signed out, a sign-in card appears over the map (see M-MAP-01).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 3.3 Sign in and sign up

The sign-in screen opens from any "Sign In" button (Settings, Community, Outbreak Map).

#### M-AUTH-01 · Open the sign-in screen
**Platform:** 📱 Mobile app
**What to test:** Reaching the sign-in screen.
**Steps:**
1. Make sure you are signed out (Settings → "Log Out" if needed).
2. Tap "Settings", then tap "Sign In".
**Expected result:** A screen titled "Welcome back" appears with an explanation, an "Email" box, a "Password (at least 8 characters)" box with an eye button, a green "Sign In" button, "New here? Create an account" and "Not now".
**Explanation:** Accounts are optional; scanning and treatment guides always work without one.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-AUTH-02 · "Not now" closes the sign-in screen
**Platform:** 📱 Mobile app
**Steps:** 1. On the sign-in screen, tap "Not now".
**Expected result:** You return to the screen you came from, still signed out.
**Explanation:** Farmers can skip signing in and keep using offline features.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-AUTH-03 · Switch to "Create Account" and back
**Platform:** 📱 Mobile app
**Steps:**
1. Tap "New here? Create an account".
2. Tap "Already have an account? Sign in".
**Expected result:** Step 1: the title becomes "Create Account", extra boxes appear ("Full name", "Barangay", "Municipality / City", "Province") and the button says "Create Account". Step 2: back to "Welcome back" with only Email and Password.
**Explanation:** One screen handles both signing in and registering.
**Error/edge cases:** Text already typed in Email/Password should stay when switching.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-AUTH-04 · Show / hide password (eye button)
**Platform:** 📱 Mobile app (both Sign In and Create Account)
**Steps:**
1. Type `Test12345` in the password box. Note it shows as dots.
2. Tap the eye icon at the right of the password box.
3. Tap it again.
**Expected result:** Step 2: the password becomes readable ("Test12345") in the normal font and the icon changes to a crossed-out eye. Step 3: it turns back into dots and the icon returns to a plain eye. The cursor stays at the end of the text.
**Explanation:** Lets users check they typed the password correctly, which avoids failed sign-ins.
**Error/edge cases:** Works with an empty password box (no crash). Works on both Sign In and Create Account. With a screen reader (TalkBack) on, the button is read as "Show password" / "Hide password".
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-AUTH-05 · Empty fields are rejected
**Platform:** 📱 Mobile app
**Steps:**
1. On "Welcome back", leave Email and Password empty and tap "Sign In".
2. Switch to "Create Account", fill only Email and Password, and tap "Create Account".
**Expected result:** Both times a red message appears: "Please fill in all required fields." Nothing else happens.
**Explanation:** The app checks required fields before contacting the server.
**Error/edge cases:** Spaces only in a field count as empty.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-AUTH-06 · Sign in with a correct account
**Platform:** 📱 Mobile app
**Steps:**
1. Email: `demo@cornguard.app`. Password: `demo1234`.
2. Tap "Sign In".
**Expected result:** A loading spinner shows briefly, then the sign-in screen closes. Settings shows "Demo Farmer", "Poblacion, Valencia" and "demo@cornguard.app".
**Explanation:** Signing in unlocks sharing, Community and the Outbreak Map, and registers the phone for alerts.
**Error/edge cases:** Tapping "Sign In" several times quickly must not sign in twice or crash (the button is disabled while loading).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-AUTH-07 · Wrong password
**Platform:** 📱 Mobile app
**Steps:** 1. Email `demo@cornguard.app`, password `wrongpass123`, tap "Sign In".
**Expected result:** Message: "Incorrect email or password." You stay on the sign-in screen.
**Explanation:** The server rejects wrong passwords; the message does not reveal which part was wrong (for security).
**Error/edge cases:** An email that has no account shows the same message.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-AUTH-08 · Create a new account
**Platform:** 📱 Mobile app
**Steps:**
1. Switch to "Create Account".
2. Fill: Full name `Test Farmer`, Email `tester1@example.com` (use a new email each time), Password `TestPass123`, Barangay `Lumbo`, Municipality / City `Valencia`, Province `Bukidnon`.
3. Tap "Create Account".
**Expected result:** The screen closes and Settings shows "Test Farmer", "Lumbo, Valencia, Bukidnon" and the email.
**Explanation:** Registration creates the account and the farmer profile in one step.
**Error/edge cases:** See M-AUTH-09 and M-AUTH-10.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-AUTH-09 · Password shorter than 8 characters / email already used
**Platform:** 📱 Mobile app
**Steps:**
1. Create Account with all fields filled but password `abc`.
2. Create Account again using `demo@cornguard.app` with a different password.
**Expected result:** Both show: "Couldn't create the account. This email may already be registered." No account is created.
**Explanation:** Passwords need at least 8 characters, and each email can only have one account.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-AUTH-10 · Sign in without internet
**Platform:** 📱 Mobile app
**Steps:** 1. Turn on Airplane mode. 2. Try to sign in with the demo account.
**Expected result:** Message: "Couldn't reach the server. Check your connection and try again."
**Explanation:** Signing in needs the server; the app explains the cause instead of failing silently.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 3.4 Scanning a leaf

#### M-SCAN-01 · Scan screen layout
**Platform:** 📱 Mobile app
**Steps:** 1. Dashboard → "Scan Corn".
**Expected result:** Title "Scan Corn Leaf", "Ready to scan", "Take a photo or choose from gallery", buttons "Open Camera" and "Pick from Gallery", and a back arrow.
**Explanation:** Two ways to get a leaf photo into the scanner.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SCAN-02 · Camera permission — allow
**Platform:** 📱 Mobile app
**Steps:**
1. Tap "Open Camera". If Android asks for camera permission, tap "While using the app".
2. Take a photo of a corn leaf (or any object) and confirm it.
**Expected result:** The camera opens. After confirming the photo, "Analyzing Leaf…" shows with a spinner, then the result sheet slides up (see 3.5).
**Explanation:** The app needs the camera to photograph leaves.
**Error/edge cases:** Pressing Back/cancel inside the camera returns to the scan screen with no result and no error.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SCAN-03 · Camera permission — deny
**Platform:** 📱 Mobile app
**Steps:**
1. In phone Settings → Apps → CornGuard → Permissions, set Camera to "Don't allow" (or deny when asked).
2. Tap "Open Camera" and deny the request.
**Expected result:** Message on the scan screen: "Camera permission is needed to take a photo." The camera does not open.
**Explanation:** The app cannot use the camera without permission and tells the user why.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SCAN-04 · Pick from Gallery (and deny case)
**Platform:** 📱 Mobile app
**Steps:**
1. Tap "Pick from Gallery". Allow access if asked.
2. Choose a corn leaf photo.
3. Repeat, but deny the permission when asked (on phones that ask).
**Expected result:** Steps 1–2: "Analyzing Leaf…" then the result sheet. Step 3: "Gallery permission is needed to choose a photo."
**Explanation:** Farmers can scan photos taken earlier.
**Error/edge cases:** Closing the picker without choosing returns to the scan screen with no error.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SCAN-05 · Unreadable image
**Platform:** 📱 Mobile app
**Steps:** 1. "Pick from Gallery" and choose a broken or unsupported file (for example a 0-byte image).
**Expected result:** Message: "That photo couldn't be read. Please try another one." No result is saved.
**Explanation:** The scanner only works on real images.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SCAN-06 · Scanning works offline
**Platform:** 📱 Mobile app
**Steps:** 1. Turn on Airplane mode. 2. Scan a leaf photo from the gallery.
**Expected result:** The result appears normally (the disease model runs on the phone).
**Explanation:** Diagnosis must work in the field without signal.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SCAN-07 · Correct diagnosis on sample photos
**Platform:** 📱 Mobile app
**Steps:** 1. Scan at least two photos each of Blight, Common Rust, Gray Leaf Spot and Healthy from `Acenas_Dataset/data/`.
**Expected result:** Most photos get the matching disease name: Northern Leaf Blight (for "Blight"), Common Rust, Gray Leaf Spot, Healthy. Write down any wrong results and the confidence shown.
**Explanation:** Checks the AI model's accuracy. It is about 96% accurate on dataset photos, so an occasional mistake (most often Gray Leaf Spot vs Northern Leaf Blight) is expected.
**Error/edge cases:** A photo that is not a corn leaf will still get one of the four answers, usually with low confidence.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SCAN-08 · Location permission request
**Platform:** 📱 Mobile app
**Steps:**
1. Make sure location permission for CornGuard is not yet granted.
2. Scan a leaf.
**Expected result:** The result appears right away. Android also asks for location permission. Since location was not available for this scan, its location line shows "GPS unavailable" (see M-RES-03). After allowing, later scans include the location.
**Explanation:** Location is optional. With permission, the app uses a recent position if the phone has one, otherwise it asks for a fresh GPS fix and waits at most about 8 seconds (GPS works without internet). It never asks for permission in the middle of a scan.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 3.5 Result sheet

The result sheet slides up after every scan, and when a History item is tapped.

#### M-RES-01 · Result details
**Platform:** 📱 Mobile app
**Steps:** 1. Scan any leaf.
**Expected result:** The sheet shows: the photo thumbnail, the disease name in large text, "Confidence: NN.N%", a location line, a "Recommended Action" box with a short treatment preview and "View Full Details →", "Scanned <date and time> · <model version>", a green "Share to Community" button and "✓ Saved to History".
**Explanation:** Gives the farmer the diagnosis, how sure the model is, and what to do next.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-RES-02 · Close the sheet
**Platform:** 📱 Mobile app
**Steps:** 1. Tap the "X" at the top right of the sheet. 2. Scan again and this time swipe the sheet down.
**Expected result:** The sheet closes both ways and the scan screen is visible.
**Explanation:** The scan is already saved, so closing loses nothing.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-RES-03 · Location line
**Platform:** 📱 Mobile app
**Steps:** Scan leaves in these situations (on the emulator, set the position as in section 2.2 — e.g. `adb emu geo fix 125.09 7.91` for Valencia):
1. GPS inside Bukidnon. 2. GPS outside Bukidnon. 3. No location permission / GPS off. 4. Set two different points in the same barangay and scan once at each.
**Expected result:** 1: a green pin with "Barangay, Municipality" (for example "Poblacion, City of Valencia"). 2: an orange warning "Outside Bukidnon". 3: an orange warning "GPS unavailable". In cases 2 and 3 an underlined "Set location manually" link appears. 4: both show the same barangay; once shared, they appear as two separate markers at their own points.
**Explanation:** The barangay decides which area a shared scan counts toward; the GPS point decides exactly where its marker is drawn.
**Error/edge cases:** On a real phone you can test other places with a mock-location app (Developer options → "Select mock location app"); turn it off afterwards.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-RES-04 · Set location manually (barangay picker)
**Platform:** 📱 Mobile app
**Steps:**
1. On a result without location, tap "Set location manually".
2. In "Select Barangay", type `lumbo` in the search box.
3. Tap "Lumbo — City of Valencia".
**Expected result:** The picker lists all Bukidnon barangays with their municipality. Typing filters the list. After tapping, the picker closes, the location line turns green ("Lumbo, City of Valencia") and the link changes to "Change location".
**Explanation:** Lets farmers fix the location when GPS fails, so the report lands in the right barangay. A manually picked location uses the barangay's centre point, so several manual scans in one barangay sit on top of each other on the map.
**Error/edge cases:** Typing something that matches nothing (e.g. `zzzz`) shows "No barangay matches your search." Tapping the "X" closes the picker without changing anything. Search also matches municipality names (try `maramag`).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-RES-05 · View Full Details
**Platform:** 📱 Mobile app
**Steps:** 1. Tap "View Full Details →". 2. Scroll down. 3. Tap the close button.
**Expected result:** A "Full Details" sheet opens with sections "Symptoms", "Treatment", "Causes", "Duration", "Prevention" and "Source", the note "Available offline — no internet needed" and a disclaimer saying the guidance should be confirmed with the local DA office. Closing returns to the result.
**Explanation:** Detailed treatment guidance, stored on the phone so it works offline.
**Error/edge cases:** Works in Airplane mode. If a disease has no stored guidance, the sheet says "No reference content is available for this disease on this device yet."
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-RES-06 · Share to Community — signed out
**Platform:** 📱 Mobile app
**Steps:** 1. Sign out. 2. Scan a leaf. 3. Tap "Share to Community".
**Expected result:** Message: "Sign in to share this result with the community." Nothing is shared.
**Explanation:** Shared reports must belong to an account so they can be verified.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-RES-07 · Share to Community — success
**Platform:** 📱 Mobile app
**Steps:** 1. Sign in as the demo farmer. 2. Scan a leaf (with a Bukidnon location). 3. Tap "Share to Community".
**Expected result:** A short loading state, then the button changes to "Shared to Community" and can no longer be tapped. The "Set/Change location" link disappears. In History, this scan now has a "Shared" badge.
**Explanation:** Sharing sends the scan (photo, disease, confidence, location) to the server so it can count toward outbreak detection.
**Error/edge cases:**
- Tapping the button many times quickly shares only once.
- A share at **60% confidence or higher** is verified automatically and appears as a dot on the Outbreak Map. Below 60% it waits for an admin to verify it (see W-REV-03).
- A farmer can share at most **30 scans per day**; the 31st is refused with an error (hard to trigger by hand).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-RES-08 · Share to Community — no internet
**Platform:** 📱 Mobile app
**Steps:** 1. Signed in, turn on Airplane mode. 2. Scan a leaf and tap "Share to Community".
**Expected result:** Message: "Couldn't share this result. Check your connection and try again." The scan stays in History as "On device"; you can share it later from History.
**Explanation:** Sharing needs internet; nothing is lost when it fails.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-RES-09 · Share without a location
**Platform:** 📱 Mobile app
**Steps:** 1. Signed in, scan a leaf with location off (the line says "GPS unavailable"). 2. Tap "Share to Community" without setting a location. 3. Tap "Set location manually", pick a barangay, and tap "Share to Community" again.
**Expected result:** Step 2: message "Set the location (barangay) first, so this report can appear on the Outbreak Map." and nothing is shared. Step 3: the share succeeds as in M-RES-07.
**Explanation:** Outbreaks are counted per barangay, so a report without one couldn't count anywhere. The server refuses such shares too.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-RES-10 · Share from one of my farms
**Platform:** 📱 Mobile app
**Steps:** 1. Add a farm in Sugod, City of Valencia (M-SET-05); keep your Settings barangay somewhere else. 2. Scan a leaf. 3. On the result sheet, check "Which farm is this from?" and pick the farm. 4. Tap "Share to Community".
**Expected result:** Step 3: the button shows "North field · Sugod ▾" and the location line shows "Sugod, City of Valencia" (no "Change in Settings" link). Choosing "Not from a saved farm (use my Settings barangay)" switches the line back to the Settings barangay. Step 4: the report appears on the Outbreak Map in Sugod, at the farm pin. The next scan's sheet starts with the same farm selected.
**Explanation:** A scan counts where the farm is, not where the farmer lives. A farmer still counts once per barangay, however many of his farms are there.
**Error/edge cases:** With no saved farms the sheet shows "Add your farms in Settings to file scans by farm." and shares under the Settings barangay as before.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 3.6 History

#### M-HIST-01 · Empty history
**Platform:** 📱 Mobile app
**Steps:** 1. On a fresh install (before any scans), tap "History".
**Expected result:** "Detection History" with the message "No detections yet. Go to the Scanner tab to analyze your first corn leaf!"
**Explanation:** Explains what will appear here.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-HIST-02 · History list items
**Platform:** 📱 Mobile app
**Steps:** 1. Scan two or three leaves. 2. Open "History".
**Expected result:** Newest first. Each item shows the photo, a coloured disease badge, a "Shared" or "On device" badge, the date and time, "Confidence: NN.N%", a short treatment preview and a delete (trash) icon.
**Explanation:** Every scan is saved on the phone automatically, even offline and signed out.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-HIST-03 · Reopen a past result
**Platform:** 📱 Mobile app
**Steps:** 1. Tap a History item (not the trash icon).
**Expected result:** The result sheet opens for that scan, the same as right after scanning (M-RES-01). If it was not shared yet, "Share to Community" is available.
**Explanation:** Farmers can revisit or share old scans.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-HIST-04 · Delete a scan — confirmation dialog
**Platform:** 📱 Mobile app
**Steps:**
1. Tap the trash icon on an item.
2. Tap "Cancel".
3. Tap the trash icon again and tap "Delete".
**Expected result:** A dialog "Delete this scan?" — "It will be removed from this device. Anything already shared to the community stays shared." Cancel keeps the item. Delete removes it from the list.
**Explanation:** The confirmation prevents accidental deletion.
**Error/edge cases:** Deleting a "Shared" scan removes it from the phone only; the shared copy (and its map dot) stays.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-HIST-05 · Each account has its own history (shared phone)
**Platform:** 📱 Mobile app
**Steps:**
1. Sign in as `demo@cornguard.app` and scan two leaves. Open History.
2. Sign out (Settings → Log out). Scan one leaf while signed out. Open History.
3. Sign in as `demo3@cornguard.app`. Open History.
4. Sign out, sign in as `demo@cornguard.app` again. Open History.
5. Sign out, sign in as `demo3@cornguard.app`, and share the guest scan from step 2 (with a location set).
**Expected result:** 1: demo sees its two scans. 2: signed out, History shows only the guest scan, not demo's. 3: demo3 sees only the guest scan (taken over on sign-in), none of demo's. 4: demo sees only its own two scans again. 5: the scan shares normally as demo3, and it never appears in demo's History.
**Explanation:** Several farmers can share one phone. Each sees and shares only their own scans, so one scan can't be counted as several farms. Scans taken while signed out go to whoever signs in next.
**Error/edge cases:** Already-shared scans saved by an older app version go back to their owner when that owner signs in (matched with the server).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 3.7 Community

#### M-COM-01 · Signed-out prompt
**Platform:** 📱 Mobile app
**Steps:** 1. Sign out. 2. Dashboard → "Community".
**Expected result:** A message "Sign in to read and share community reports." with a "Sign In" button. No feed and no "+" button. Tapping "Sign In" opens the sign-in screen.
**Explanation:** The community is only for registered farmers.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-COM-02 · Feed loads
**Platform:** 📱 Mobile app
**Steps:** 1. Sign in. 2. Open "Community".
**Expected result:** A loading indicator, then a list of posts. Each post shows the author name, how long ago and the barangay ("2h · Poblacion"), an optional disease badge, the text, an optional photo, a heart with a number, a speech bubble with a number, and up to a few comment previews.
**Explanation:** The feed shows recent reports from farmers.
**Error/edge cases:** With no posts: "No posts yet. Tap + to share what's happening on your farm." Offline or server error: "Couldn't load the community feed. Check your connection and try again."
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-COM-03 · Pull to refresh
**Platform:** 📱 Mobile app
**Steps:** 1. On the feed, drag the list down from the top and release.
**Expected result:** A green spinner appears and the feed reloads (new posts from others appear).
**Explanation:** Lets farmers check for new reports.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-COM-04 · Create a post
**Platform:** 📱 Mobile app
**Steps:**
1. Tap the "+" button.
2. Type `Rust spots appearing on my lower leaves` in "What's happening in your farm?".
3. Under "Disease (optional)", tap "Common Rust".
4. Tap "Add Photo" and pick a leaf photo.
5. Tap "Post".
**Expected result:** The "Create Post" sheet opens with the keyboard ready. The chosen photo shows as a preview and the button changes to "Change Photo". After "Post", a spinner shows, the sheet closes and the new post appears at the top of the feed with the Common Rust badge and photo.
**Explanation:** Farmers share what they see so others nearby can prepare.
**Error/edge cases:**
- "Post" is greyed out until some text is typed; spaces only do not count. If a post is somehow sent empty: "Write something before posting."
- "None" is selected by default (no disease badge).
- A photo is optional.
- The "X" closes the sheet without posting.
- Posting offline shows an error in the sheet and keeps your text.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-COM-05 · Like and unlike a post
**Platform:** 📱 Mobile app
**Steps:** 1. Tap the heart on a post. 2. Tap it again.
**Expected result:** First tap: the heart fills (red) and the number goes up by 1 immediately. Second tap: the heart empties and the number goes back down.
**Explanation:** Likes show agreement ("same on my farm").
**Error/edge cases:** Tapping the heart many times quickly should end with the correct count (one like per account at most).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-COM-06 · Comments sheet
**Platform:** 📱 Mobile app
**Steps:**
1. Tap the speech bubble (or a comment preview, or "View all N comments").
2. Type `Same on my farm in Lumbo` in "Write a comment…" and tap the send button.
3. Close the sheet with the "X".
**Expected result:** The "Comments" sheet lists existing comments (or "No comments yet. Be the first to reply."). After sending, the text box clears and the comment appears in the list. After closing, the post's comment count and previews update in the feed.
**Explanation:** Farmers can discuss a report.
**Error/edge cases:** Tapping send with an empty or spaces-only box does nothing. The send button is disabled while sending (no double comments). The post's author gets a notification (see M-NOTIF-02).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-COM-07 · "View all N comments"
**Platform:** 📱 Mobile app
**Steps:** 1. Find a post with more comments than previews shown (add comments from several accounts if needed). 2. Tap "View all N comments".
**Expected result:** The link shows the total, e.g. "View all 5 comments", and opens the full comments sheet.
**Explanation:** The feed shows only a few previews to stay compact.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 3.8 Outbreak Map

#### M-MAP-01 · Signed-out card
**Platform:** 📱 Mobile app
**Steps:** 1. Sign out. 2. Dashboard → "Outbreak Map".
**Expected result:** The map is visible behind a card: "Sign in to see verified outbreaks and community reports for your barangay." with a "Sign In" button. No report dots are shown.
**Explanation:** Report dots are farm locations, so only signed-in users see them.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-02 · Map loads with boundaries and dots
**Platform:** 📱 Mobile app
**Steps:** 1. Sign in. 2. Open the Outbreak Map (load demo data first, section 2.5).
**Expected result:** A dark header "Outbreak Heatmap" with a round back button. A street map of northern Mindanao with Bukidnon's barangay boundaries drawn as outlines. Markers coloured by severity — red (Severe), amber (Moderate), green (Mild) — with a white border; the shape and letter show the disease (circle "B" = Northern Leaf Blight, square "R" = Common Rust, diamond "G" = Gray Leaf Spot). Severe markers pulse softly. A legend at the bottom right, zoom "+"/"−" buttons at the bottom left, and a "Reports near you" panel at the bottom.
**Explanation:** Shows where verified reports are and how serious each area is.
**Error/edge cases:** Bigger markers = more severe, and the disease is shown by shape, so the map is readable without relying on colour alone. Where markers overlap, Severe is drawn on top.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-03 · Zoom, pan and back
**Platform:** 📱 Mobile app
**Steps:** 1. Tap "+" twice, then "−". 2. Drag the map with one finger; pinch to zoom. 3. Tap the back button.
**Expected result:** The map zooms and moves smoothly; back returns to the Dashboard.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-04 · Collapsible legend
**Platform:** 📱 Mobile app
**Steps:** 1. Tap the legend box. 2. Tap "Legend ▸".
**Expected result:** Tap 1 shrinks it to a small "Legend ▸" button (uncovering the map). Tap 2 expands it again: "Outbreak level" (Severe Outbreak, Moderate Threat, Mild Detection, Reported 2–4 weeks ago) and "Disease (shape)".
**Explanation:** On a phone the full legend can cover dots.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-05 · Dot popup
**Platform:** 📱 Mobile app
**Steps:** 1. Tap a dot.
**Expected result:** A dark popup: disease name, "Barangay, Municipality", "Outbreak level: SEVERE/MODERATE/MILD" in the matching colour, "Confidence: NN%" and "Reported: <date>". For a faded grey marker the severity line reads "Reported N days ago · no longer counted in the outbreak level". Tapping elsewhere or the popup's "×" closes it.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-06 · Barangay popup
**Platform:** 📱 Mobile app
**Steps:** 1. Tap inside a barangay outline that has a dot. 2. Tap a barangay with no reports.
**Expected result:** 1: barangay and municipality name, then for each active disease "Active Disease", "Outbreak level", "Usual: N farms / week" (with "(provisional)" during the first 12 weeks of data), "Farms (7 days)" with "· last week N", and "Total Reports". In a barangay still collecting data, "Outbreak level" is replaced by "Outbreak status: UNDETERMINED — Collecting data for this area's baseline (X of 4 weeks, Y of 10 earlier scans)". 2: the name plus "No active disease reports".
**Explanation:** Shows the outbreak status of a whole barangay.
**Error/edge cases:** Barangays with the same name in different towns (e.g. "Poblacion") show their own separate data.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-07 · "Reports near you" panel
**Platform:** 📱 Mobile app
**Steps:** 1. Tap the "Reports near you" header (or drag it up). 2. Scroll. 3. Tap the header again (or drag it down).
**Expected result:** The panel rises and shows "Showing <your barangay, municipality>", a "VERIFIED OUTBREAKS" list and a "NEARBY REPORTS" list (or their empty messages: "No disease reports near you in the last 4 weeks." / "No community reports in your area yet."). Tapping again lowers it.
**Explanation:** A text summary of the last 4 weeks of reports in the farmer's own barangay — in their own town only (many towns have a "Poblacion"). Healthy scans aren't listed.
**Error/edge cases:** With no barangay in the profile: "Add your barangay to your profile to see nearby reports."
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-08 · Map offline
**Platform:** 📱 Mobile app
**Steps:** 1. Open the map once with internet. 2. Turn on Airplane mode and reopen the map.
**Expected result:** The barangay outlines and legend still draw (they are stored in the app); the street map background may be blank grey.
**Explanation:** Boundaries are bundled with the app; only the street map needs internet.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-09 · Live update
**Platform:** 📱 Mobile app
**Steps:** 1. Keep the map open on the phone. 2. On another device, share a confident scan in Bukidnon (or run the demo script).
**Expected result:** The new dot appears within a few seconds without leaving the screen.
**Explanation:** The map receives updates live from the server.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-09B · My farm pins
**Platform:** 📱 Mobile app
**Steps:** 1. Add a farm (M-SET-05). 2. Open the Outbreak Map. 3. Tap the green farm pin.
**Expected result:** The farm appears as a green pin with a house, drawn above the report markers and not affected by the filters. The legend shows "Your farm (only you see it)". The popup shows "Your farm: North field", its barangay, "Outbreak level here" (each disease's level in that barangay, or "No active disease reports in this barangay") and "Only you can see this farm."
**Explanation:** Farmers can see at a glance whether reports are near their farms.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-10 · Old reports fade, then disappear
**Platform:** 📱 Mobile app (+ 💻 Web, same behaviour)
**Steps:**
1. Note a verified report on the map and its record id (website → Review → Leaf scans → Verified; or ask the project owner).
2. On a computer with the project, in `backend/`, run `npx convex run devAccounts:backdateRecord "{recordId:'<id>', daysAgo:20}"`.
3. Reopen the map. Then run the command again with `daysAgo:35` and reopen the map.
**Expected result:** After step 2 the report is a small, faded grey marker (same shape and letter); its popup says "Reported 20 days ago · no longer counted in the outbreak level", and the barangay's severity no longer counts it. After step 3 the marker is gone.
**Explanation:** The outbreak level counts the last 7 days. Reports 7–28 days old stay visible but faded, so an outbreak doesn't vanish overnight if farmers stop scanning; after 28 days they disappear. During a long outbreak, new reports keep the area coloured.
**Error/edge cases:** Open maps update when data changes and at least once a day (the nightly recalculation at 2 am).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-11 · Healthy scans aren't shown
**Platform:** 📱 Mobile app
**Steps:** 1. Scan a healthy leaf (e.g. `healthy_01.jpg`) with a location in Bukidnon and share it. 2. Open the map.
**Expected result:** No marker appears for it, and it isn't listed in "Reports near you".
**Explanation:** The map shows disease reports only.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 3.9 Settings (Profile & Settings)

#### M-SET-01 · Signed-out card
**Platform:** 📱 Mobile app
**Steps:** 1. Sign out. 2. Tap "Settings".
**Expected result:** "You're not signed in" with "Sign in to manage your profile and farms. Scanning and history work without an account." and a "Sign In" button.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SET-02 · Profile information
**Platform:** 📱 Mobile app
**Steps:** 1. Sign in as the demo farmer. 2. Tap "Settings".
**Expected result:** A circle with the first letter of the name ("D"), "Demo Farmer", "Poblacion, Valencia", "demo@cornguard.app", and an edit (pencil) button.
**Error/edge cases:** A profile with no barangay/municipality shows "Location not set".
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SET-03 · Edit profile — open and cancel
**Platform:** 📱 Mobile app
**Steps:** 1. Tap the edit (pencil) button. 2. Change the name. 3. Tap "Cancel".
**Expected result:** An "Edit Profile" sheet opens with the current Name, Barangay, Municipality / City, Province and Mobile number (optional) filled in. The letter in the circle changes as you edit the name. "Cancel" closes it and nothing changes.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SET-04 · Edit profile — save
**Platform:** 📱 Mobile app
**Steps:** 1. Open Edit Profile. 2. Change Name to `Demo Farmer 1` and Mobile number to `09171234567`. 3. Tap "Save".
**Expected result:** A spinner, then the sheet closes and Settings shows the new name. The Dashboard greeting also shows it. (Change it back to `Demo Farmer` afterwards.)
**Explanation:** Farmers keep their profile up to date; the barangay decides which outbreak alerts they receive.
**Error/edge cases:** Empty name → "Please fill in all required fields." and nothing is saved. Offline → "Couldn't save your profile. Check your connection and try again."
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SET-05 · My Farms — add a farm with a pin
**Platform:** 📱 Mobile app
**Steps:** 1. Settings → under "MY FARMS" tap "Add farm". 2. On "Add a farm", tap the map inside Bukidnon (e.g. in Sugod, City of Valencia). 3. Type `North field` in "Farm name". 4. Tap "Save farm".
**Expected result:** Step 2: a green farm pin appears and the line below shows the barangay in green ("Sugod, City of Valencia"). "Save farm" is enabled only when there is a pin inside Bukidnon and a name. Step 4: back in Settings, "North field" is listed with "Sugod, City of Valencia". Before adding any farm the list says "No farms added yet." The note reads "Only you can see your farms. A farm's location is shared only with a scan you share from it."
**Explanation:** Farmers mark their farms privately so a scan can be filed at the right farm and they can see outbreaks near their farms.
**Error/edge cases:** "Use my location" puts the pin at the phone's location (asks for location permission first; if denied or unavailable, a message suggests tapping the map). Offline → "Couldn't save the farm. Check your connection and try again."
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SET-05B · My Farms — pin outside Bukidnon
**Platform:** 📱 Mobile app
**Steps:** 1. Settings → "Add farm". 2. Pan out and tap outside the Bukidnon boundaries (e.g. Cagayan de Oro).
**Expected result:** The line turns orange: "Outside Bukidnon. Pick a spot inside the map's boundaries." and "Save farm" stays disabled.
**Explanation:** Farms only work inside the outbreak map's area; the server refuses them too.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SET-05C · My Farms — remove a farm
**Platform:** 📱 Mobile app
**Steps:** 1. Tap the bin icon on a farm. 2. Tap "Remove" in "Remove North field?".
**Expected result:** The farm disappears from the list and from the Outbreak Map. The dialog says "Scans you already shared from this farm stay on the map."
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SET-05D · My Farms are private
**Platform:** 📱 Mobile app
**Steps:** 1. As farmer A, add a farm. 2. Sign in as farmer B and open Settings and the Outbreak Map.
**Expected result:** B sees none of A's farms anywhere.
**Explanation:** Farm locations are visible only to their owner (the server never returns another user's farms).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SET-06 · About CornGuard
**Platform:** 📱 Mobile app
**Steps:** 1. Tap "About CornGuard". 2. Tap "OK".
**Expected result:** A dialog showing "CornGuard <version>", "Disease model: cornguard_mobilenetv2_v3" and a short description. "OK" closes it.
**Explanation:** Shows which app and AI model version is installed (useful for bug reports).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-SET-07 · Log Out
**Platform:** 📱 Mobile app
**Steps:** 1. Tap "Log Out".
**Expected result:** You are signed out immediately: Settings shows "You're not signed in", the Dashboard says "Farmer", and Community/Map ask you to sign in. Scan history stays on the phone.
**Explanation:** Logging out also stops alerts for that account on this phone.
**Error/edge cases:** Logging out offline still works (the phone forgets the session).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 3.10 Notifications

#### M-NOTIF-01 · Automatic alerts: Moderate (awareness) and Severe (potential-outbreak warning)
**Platform:** 📱 Mobile app + 💻 Web
**Steps:**
1. On the phone, sign in as the demo farmer (profile barangay Poblacion, Valencia), then go to the phone's home screen.
2. Give Poblacion past data (section 2.5): `node scripts/alert-scenarios.mjs history Poblacion "City of Valencia"` (6 weeks of healthy scans: baseline 0 for every disease).
3. Share confident (80%+) scans of one disease in Poblacion from **three** demo accounts (`scan 1`, `scan 3`, `scan 4` …), so the barangay reaches **Moderate**. Wait about a minute and check the phone.
4. Share from **two more** accounts (five farms in total) so it reaches **Severe**. Wait about a minute and check the phone again.
5. Tap each notification.
**Expected result:**
- After step 3: "Northern Leaf Blight cases rising in Poblacion — More corn farms than usual in Poblacion, City of Valencia, near you, have reported Northern Leaf Blight this week. Check your field and the outbreak map." It uses the "Disease Awareness" channel (normal priority, amber).
- After step 4: "Potential outbreak: Northern Leaf Blight near you — Reports of Northern Leaf Blight in Poblacion, City of Valencia, close to your farm, are far above the usual level for this area. Inspect your crops and take precautions. See the outbreak map for details." It pops up (high priority, red, "Outbreak Alerts" channel).
- Tapping either opens CornGuard on the Outbreak Map. In the app's notification inbox the awareness notice has an amber warning icon and the warning a red one.
**Explanation:** Alerts are sent automatically, with no admin step, once the barangay has a baseline. Only verified scans count toward a level, so the evidence is checked before the level is reached.
**Error/edge cases:**
- **Without step 2** (a barangay with no history), the same scans leave its outbreak status **Undetermined** and **no notification** is sent (M-NOTIF-04).
- Where a disease is normally reported, more farms are needed: with a baseline of 2 farms a week, Moderate needs 4 farms and Severe 6.
- Farmers in barangays bordering Poblacion (e.g. Lumbo), including border barangays of a neighbouring town, also get both; farmers farther away do not.
- A logged-out phone does not get them.
- More scans at the same level send nothing new. The same level isn't repeated for that barangay and disease within 14 days, even if the level dips and rises again.
- Going down a level (e.g. Severe → Moderate) sends nothing.
- If several diseases reach the same level in the same barangay within a minute, one combined alert is sent ("Northern Leaf Blight and Gray Leaf Spot…").
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-NOTIF-04 · No alerts while a barangay is collecting data
**Platform:** 📱 Mobile app
**Steps:**
1. On the phone, sign in as the demo farmer (Poblacion, Valencia) and go to the home screen.
2. Pick a barangay bordering Poblacion that has no reports yet (e.g. Sugod, City of Valencia). Share confident scans of one disease there from five demo accounts (`node scripts/alert-scenarios.mjs scan <1-5> Sugod "City of Valencia"`).
3. Wait two minutes. Open the Outbreak Map and tap the new markers and the Sugod outline.
**Expected result:** No notification arrives. The popups say "Outbreak status: UNDETERMINED — Collecting data for this area's baseline (0 of 4 weeks, … of 10 earlier scans)"; the markers use the small Mild style.
**Explanation:** Without at least 4 weeks and 10 verified scans of history, nothing is known about what is normal in that barangay, so no outbreak is claimed.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-NOTIF-02 · Community reply notification
**Platform:** 📱 Mobile app
**Steps:** 1. Phone A: sign in as `demo@cornguard.app` and create a post. 2. Phone B (or after logging out): sign in as `demo2@cornguard.app` and comment on that post. 3. Check phone A.
**Expected result:** Phone A receives "New reply on your post" with the comment text. Tapping opens the app.
**Explanation:** Authors learn when someone replies.
**Error/edge cases:** Commenting on your own post sends no notification.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-NOTIF-03 · Notification settings
**Platform:** 📱 Mobile app
**Steps:** 1. Phone Settings → Apps → CornGuard → Notifications.
**Expected result:** Two categories: "Outbreak Alerts" and "Community & Outbreak Alerts". Turning a category off stops those notifications. On Android 13+, the app asks for notification permission; denying it means no notifications at all.
**Explanation:** Farmers control which alerts they receive.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

## 4. Web admin tests 💻

### 4.1 Login and access

#### W-LOGIN-01 · Signed-out visitors are sent to the login page
**Platform:** 💻 Web
**Steps:** 1. Open http://localhost:5173/ in a private/incognito window.
**Expected result:** The address changes to /login and shows "CornGuard Admin", "Sign in with your CornGuard admin account", "Email", "Password", a green "Sign In" button and "Only accounts with the admin role can use this site."
**Explanation:** Every admin page is protected.
**Error/edge cases:** Typing an admin address directly (e.g. /users) also redirects to /login.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-LOGIN-02 · Empty fields
**Platform:** 💻 Web
**Steps:** 1. Click "Sign In" with both fields empty. 2. Type an email without "@" and click "Sign In".
**Expected result:** The browser shows its own small warning ("Please fill out this field" / "Please include an '@'…") and nothing is sent.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-LOGIN-03 · Wrong password
**Platform:** 💻 Web
**Steps:** 1. Email `admin@cornguard.app`, password `wrong-password-1`, click "Sign In".
**Expected result:** The button shows "Signing in…", then a red message "Incorrect email or password."
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-LOGIN-04 · Non-admin account is refused
**Platform:** 💻 Web
**Steps:** 1. Sign in with `demo@cornguard.app` / `demo1234`. 2. Click "Sign out".
**Expected result:** A lock screen "Admins only" — "demo@cornguard.app is not an admin. Admin access is granted by the CornGuard team." — with a "Sign out" button that returns to the login page.
**Explanation:** Farmers' accounts work in the app but cannot use admin tools. The server also blocks them, not just the page.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-LOGIN-05 · Admin signs in
**Platform:** 💻 Web
**Steps:** 1. Sign in with the admin account.
**Expected result:** The Dashboard opens, with the sidebar on the left ("CornGuard Admin", Dashboard, Outbreaks, Review, Users, Outbreak Map) and the admin's name and email at the bottom.
**Error/edge cases:** Clicking "Sign In" repeatedly does not cause errors (the button is disabled while signing in).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-LOGIN-06 · Stay signed in after refresh; Logout
**Platform:** 💻 Web
**Steps:** 1. While signed in, press F5 (refresh). 2. Click "🚪 Logout".
**Expected result:** After refresh you are still signed in on the same page. After Logout you are back at the login page; pressing the browser Back button does not show admin data.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-LOGIN-07 · Suspended admin is refused
**Platform:** 💻 Web
**Steps:** 1. Make a second admin account (W-USR-04), then suspend it while signed in as the main admin (W-USR-03). 2. Sign in with the suspended account.
**Expected result:** The "Admins only" screen says the account "is suspended".
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 4.2 Layout and Dashboard

#### W-NAV-01 · Sidebar links and badges
**Platform:** 💻 Web
**Steps:** 1. Click each sidebar link in turn.
**Expected result:** Each opens its page and is highlighted in the sidebar; the page title at the top changes ("Dashboard", "Outbreak Alerts", "Review & Moderation", "User Management", "Outbreak Map"). An orange number badge appears next to "Review" (scans awaiting review) when there is something to do. "Outbreaks" has no badge: alerts are sent automatically, so nothing waits there.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-DASH-01 · Dashboard cards
**Platform:** 💻 Web
**Steps:** 1. Open the Dashboard. 2. Click each card that is a link.
**Expected result:** Eight cards: "Alerts sent (14 days)", "Scans awaiting review", "Unverified posts", "Severe barangay / disease", "Users", "Shared scans", "Verified scans", "Suspended accounts". "Scans awaiting review" gets an orange outline when its number is above 0. Clickable cards open Outbreaks, Review, Map or Users.
**Explanation:** A quick view of what needs attention.
**Error/edge cases:** Numbers update by themselves when data changes (e.g. when an automatic alert goes out, the first card goes up by 1).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-DASH-02 · "How alerts work" box
**Platform:** 💻 Web
**Steps:** 1. Read the "How alerts work" box. 2. Click the "Outbreaks" and "Review" links in it.
**Expected result:** It explains that alerts go out automatically (Moderate = awareness notice, Severe = outbreak warning) to farmers in the barangay and the bordering ones, and that a level isn't repeated within 14 days. The links open those pages.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 4.3 Outbreaks

#### W-OUT-01 · Automatic alert log
**Platform:** 💻 Web
**Steps:** 1. Load demo data (section 2.5), or follow E-01. 2. Open "Outbreaks".
**Expected result:** "Automatic alerts" lists one row per alert: Sent time, Level (amber MODERATE or red SEVERE), Area "Poblacion, City of Valencia", Disease(s), Why it fired, Level now, Notified (number of farmers), Status ("SENDING" for its first minute, then green "SENT"), and a "False alarm" button. A barangay that went Mild → Moderate → Severe has two rows, one per level.
**Explanation:** Alerts go out by themselves; this page is the record of what was sent and to how many farmers.
**Error/edge cases:** If the level drops after the alert (e.g. scans rejected), "Level now" shows the lower level.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-OUT-02 · Mark an alert as a false alarm
**Platform:** 💻 Web
**Steps:** 1. On a row, click "False alarm". 2. Confirm with "Mark false alarm".
**Expected result:** A dialog "Mark as false alarm?". For a SENT alert it says farmers won't be messaged again and suggests rejecting the scans behind it in Review. For an alert still SENDING (its first minute) it says the alert will be cancelled, and no phone receives it. After confirming, the row shows grey "FALSE ALARM" and your name as the reviewer, and the button disappears.
**Explanation:** For false alarms (e.g. wrong photos). The same level isn't re-sent for that barangay and disease within 14 days.
**Error/edge cases:** "Cancel" in the dialog (or Esc, or clicking outside it) changes nothing.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-OUT-03 · Tabs and delivery log
**Platform:** 💻 Web
**Steps:** 1. Click "All", "Severe", "Moderate", "False alarms". 2. Scroll to "Push delivery log".
**Expected result:** Each tab filters the list. With nothing to show: "No alerts yet…". The delivery log lists each push with time, type ("disease_awareness" for Moderate, "outbreak_alert" for Severe), title and a badge: green "sent", red "failed" (e.g. the phone had no app installed), orange "pending".
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-OUT-04 · Why an alert fired
**Platform:** 💻 Web
**Steps:** 1. Seed a baseline, e.g. from `backend/`: `npx convex run devAccounts:seedAreaHistory "{barangay:'Casisang', municipality:'City of Malaybalay (Capital)', weeks:4, healthyPerWeek:0, clear:true, diseaseTotals:{gray_leaf_spot:10}}"` (usual 2.5 farms/week). 2. Have 5 demo accounts report Gray Leaf Spot there (`node scripts/alert-scenarios.mjs scan 3 "Casisang" "City of Malaybalay (Capital)" gray_leaf_spot`, then 4, 5, 6, 7). 3. Open "Outbreaks".
**Expected result:** The new MODERATE row's "Why it fired" reads "Gray Leaf Spot: **5 farms** vs usual 2.5/week (Moderate at 5, provisional baseline)". Rows from before this feature, and test alerts, show "—".
**Explanation:** These are the numbers at the moment the alert fired. They don't change afterwards, unlike "Level now", which follows the rolling 7-day window.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-OUT-05 · Borrowed baseline (nearby areas)
**Platform:** 💻 Web
**Steps:** 1. From `backend/`, give two neighbours of Barangay 8 (Malaybalay) history: `npx convex run devAccounts:seedAreaHistory "{barangay:'Casisang', municipality:'City of Malaybalay (Capital)', weeks:4, healthyPerWeek:1, clear:true, diseaseTotals:{gray_leaf_spot:10}}"`, and the same for `'Barangay 1'`. 2. Barangay 8 itself has no history. Have 5 demo accounts report Gray Leaf Spot there (`node scripts/alert-scenarios.mjs scan 3 "Barangay 8" "City of Malaybalay (Capital)" gray_leaf_spot`, then 4–7). 3. Open "Outbreaks" and "Outbreak Map".
**Expected result:** After 3–4 farms the level is Mild (not Undetermined). At 5 farms a MODERATE alert appears; "Why it fired" reads "Gray Leaf Spot: **5 farms** vs usual 2.5/week from nearby areas (Moderate at 5, provisional baseline)". On the map, Barangay 8's "Usual levels" says "baseline from nearby areas (2 barangays)".
**Explanation:** A barangay without enough data of its own borrows the average of its bordering barangays.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-OUT-06 · Cluster in an area without a baseline
**Platform:** 💻 Web + 📱 App
**Steps:** 1. Pick a barangay where neither it nor its neighbours have any scans (e.g. Adtuyon, Pangantucan). 2. Have 5 demo accounts report the same disease there. 3. Open the Dashboard and "Outbreaks".
**Expected result:** No alert is created and no phone is notified; the area stays Undetermined on the map. The Dashboard tile "Clusters without a baseline" shows 1 (amber outline), and "Outbreaks" shows the card "Clusters in areas without a baseline (no alert sent)" with Adtuyon, Pangantucan, the disease and 5 farms.
**Explanation:** Without any baseline there is no basis for an automatic warning, but a cluster this large is shown to admins for review.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 4.4 Review (scans, posts, comments)

#### W-REV-01 · Review page tabs
**Platform:** 💻 Web
**Steps:** 1. Open "Review". 2. Click "Leaf scans", "Posts", "Comments".
**Expected result:** Each tab shows its own list. "Leaf scans" is selected first.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-REV-02 · Scan list and photo zoom
**Platform:** 💻 Web
**Steps:** 1. In "Leaf scans", click "Awaiting review", "Verified", "Rejected", "All". 2. Click a scan photo. 3. Click outside the big photo.
**Expected result:** Columns: Photo, Model says, Confidence, Farmer, Area, Scanned, Status, Actions. The photo opens full size and closes when you click outside it. Scans with a barangay but no GPS point show "(no GPS)"; scans with no location at all (older app versions) show a red "No location" badge; farmers who are suspended show a red "suspended" badge.
**Error/edge cases:** Clicking a column header with an arrow sorts the list (click again to reverse).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-REV-03 · Verify / Reject a scan
**Platform:** 💻 Web (+ 📱 map)
**Steps:** 1. On an "Awaiting review" scan with a Bukidnon location, click "Verify". 2. On a verified scan, click "Reject".
**Expected result:** The status badge changes immediately (VERIFIED green / REJECTED red) and the scan moves to the matching tab. A verified scan appears as a dot on the Outbreak Map; a rejected one disappears from the map and its barangay's severity is recalculated.
**Explanation:** Only verified scans count toward outbreaks, so admins remove wrong or fake ones.
**Error/edge cases:** Clicking "Verify" twice quickly causes no error. If the action fails (e.g. internet lost), a red message appears above the list. For a scan with a red "No location" badge, "Verify" is greyed out (hover: "No location: this scan can't be placed on the map") — such scans can only be rejected.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-REV-04 · Post authenticity signals
**Platform:** 💻 Web
**Steps:** 1. Open "Posts" and look at a post.
**Expected result:** Each row shows the photo, the text with its disease tag, area and date; "Backed by scan" (a linked scan's photo, disease and confidence, with "matches tag" or "tag mismatch", and "other user's scan" if it belongs to someone else — or "no linked scan"); the author's history ("N scans · N verified · N rejected", "N posts · N hidden/removed"); likes 👍 and comments 💬; and status badges.
**Explanation:** These clues help decide whether a post is genuine.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-REV-05 · Verify / Reject / Hide / Remove / Restore a post
**Platform:** 💻 Web (+ 📱 Community)
**Steps:** 1. Click "Verify" on a post. 2. Click "Hide". 3. Check the app's Community feed. 4. Click "Restore". 5. Click "Remove", then "Restore".
**Expected result:** Status badges update immediately. Hidden or removed posts disappear from the app's feed (after a refresh) but the author can still see their own post; Restore brings it back.
**Explanation:** Moderation keeps the community trustworthy without permanently deleting anything.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-REV-06 · Moderate comments
**Platform:** 💻 Web
**Steps:** 1. Open "Comments". 2. Click "Hide" on a comment, then "Restore". 3. Click "Remove", then "Restore".
**Expected result:** Columns: Author, On post, Comment, Posted, Status, Actions. The status badge changes each time; hidden/removed comments no longer appear in the app.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 4.5 Users

#### W-USR-01 · User list
**Platform:** 💻 Web
**Steps:** 1. Open "Users". 2. Click the "Name", "Scans" and "Joined" column headers.
**Expected result:** "All Users (N)" with Name and email, Role (farmer/admin), Status (active/suspended), Location, App push ("📱 yes" if the phone can receive alerts), Scans (shared/verified/rejected), Posts, Joined, Actions. Clicking headers sorts.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-USR-02 · View a user's scans
**Platform:** 💻 Web
**Steps:** 1. Click "Scans" on the demo farmer's row. 2. Click "×" or outside the pop-up.
**Expected result:** A pop-up "<name>'s shared scans (N)" lists their scans with photo, disease, confidence, area, date and status. It closes with "×" or clicking outside.
**Error/edge cases:** A user with no scans: "This user hasn't shared any scans."
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-USR-03 · Suspend and reactivate
**Platform:** 💻 Web (+ 📱)
**Steps:** 1. Click "Suspend" on a test user (e.g. demo3), then "Suspend" in the dialog. 2. In the app, signed in as that user, try to create a post or share a scan. 3. Back on the website, click "Reactivate" and confirm.
**Expected result:** The dialog warns what suspension does. After suspending, the status becomes "suspended"; in the app the user's post/share fails with an error; their markers disappear from the Outbreak Map, their scans stop counting toward severity, and they get no outbreak alerts. After reactivating, everything works again.
**Error/edge cases:** There is no Suspend button on your own row (you cannot suspend yourself). Nothing is permanently deleted.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-USR-04 · No way to grant admin from the website
**Platform:** 💻 Web
**Steps:** 1. Look at the Actions column for a farmer account in Users.
**Expected result:** Only "Scans" and "Suspend"/"Reactivate" are shown. There is no "Make admin" button; admin access is granted from the terminal only (see admin-web/README.md).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 4.6 Outbreak Map (web) and general

#### W-MAP-01 · Web map
**Platform:** 💻 Web
**Steps:** 1. Open "Outbreak Map". 2. Click a dot and a barangay. 3. Read the card below the map.
**Expected result:** The same map as the app (boundaries, severity-coloured markers with disease shapes, faded grey markers for 1–4-week-old reports, legend, popups) with "Individual verified reports across Bukidnon — N plotted". Below: "How the outbreak level is assigned" explaining the collecting-data period (Undetermined), the weekly baseline, Severe (3×), Moderate (2×) and Mild, and the cut-off examples.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-MAP-02 · Usual levels (admin only)
**Platform:** 💻 Web + 📱 App
**Steps:** 1. Seed a baseline with no reports this week (W-OUT-04 step 1, e.g. `diseaseTotals:{northern_leaf_blight:15, gray_leaf_spot:10, common_rust:12}`). 2. On the web "Outbreak Map", find Casisang (search) and click it. 3. Clear the search. 4. Open the same barangay in the app's map.
**Expected result:** On the web, Casisang has a **teal outline** (legend: "Baseline set"). Its popup says "No active disease reports this week", then "Usual levels (baseline from 4 weeks, provisional)" with, per disease, the usual farms/week and "this week 0 · Moderate at N · Severe at N" (here NLB 3.8 · 8 · 12, GLS 2.5 · 5 · 8, Common Rust 3.0 · 6 · 9). After clearing the search the outline is still teal. In the **app**, the same barangay has a plain outline and only "No active disease reports".
**Explanation:** Admins check alerts against the usual level; farmers' map stays simple.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-GEN-01 · Loading, empty and error states
**Platform:** 💻 Web
**Steps:** 1. Open each page right after signing in. 2. Look at a list that has no items (e.g. Rejected scans when none are rejected). 3. Turn off the computer's internet and click an action button (e.g. Verify).
**Expected result:** "Loading…" shows briefly. Empty lists show "📭 No Data" with an explanation. A failed action shows a red error message; nothing changes.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-GEN-02 · Narrow window
**Platform:** 💻 Web
**Steps:** 1. Make the browser window narrow (about phone width).
**Expected result:** Pages remain usable; wide tables can be scrolled sideways.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

## 5. End-to-end scenarios 📱 + 💻

#### E-01 · From scans to automatic alerts
**Platform:** 📱 Mobile app + 💻 Web
**Steps:**
1. Phone: sign in as `demo@cornguard.app`, stay signed in, leave the phone on the home screen.
2. Website or app: open the map popup for Poblacion, City of Valencia. If it has no history yet, its status is "Undetermined · Collecting data". Give it past data: `node scripts/alert-scenarios.mjs history Poblacion "City of Valencia"`. The popup now shows a level and "Usual: 0.0 farms / week (provisional)".
3. From three demo accounts (demo2, demo3, demo4), share one confident (80%+) scan each of the same disease in Poblacion. Wait about a minute. Phone: the awareness notice arrives ("… cases rising in Poblacion").
4. From two more accounts (demo5, demo6), share one more each (five farms in total). Wait about a minute. (Shortcut for steps 2–4: run `node scripts/seed-map-demo.mjs`; it may send only the Severe warning if the level jumps straight from Mild.)
5. Phone: the potential-outbreak warning arrives; tap it. The Outbreak Map shows red, softly pulsing "B" markers in Valencia.
6. Website: Outbreaks lists a MODERATE and a SEVERE alert for Poblacion, both SENT, and the Dashboard's "Alerts sent (14 days)" counts them.
**Expected result:** Each step behaves as described. Nobody had to approve anything on the website.
**Explanation:** This is the core flow: history → baseline → verified scans this week compared with it → automatic alerts to farmers nearby.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### E-02 · Moderation round trip
**Platform:** 📱 + 💻
**Steps:** 1. App: create a post. 2. Website: Review → Posts → "Hide". 3. App (another account): refresh the feed. 4. Website: "Restore". 5. App: refresh.
**Expected result:** The post disappears at step 3 and comes back at step 5.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### E-03 · Rejecting scans lowers severity
**Platform:** 📱 + 💻
**Steps:** 1. With the demo Severe outbreak loaded, website: Review → Leaf scans → All → reject two of the Poblacion (Valencia) scans. 2. App: look at the Valencia dot and barangay popup.
**Expected result:** The outbreak level drops (Severe → Moderate or Mild); the remaining markers get smaller and change colour, and the rejected scans' markers disappear. On the website's Outbreaks page, "Level now" for that alert shows the lower level. No new notification is sent when a level goes down.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### E-04 · Suspended user
**Platform:** 📱 + 💻
**Steps:** 1. Website: suspend `demo2`. 2. App as demo2: try to post and to share a scan. 3. Website: reactivate.
**Expected result:** Step 2 fails with an error message; after step 3 it works again.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

## 6. Appendix

### 6.1 Messages and where they appear

| Message | Platform | Where | When |
|---|---|---|---|
| "Please fill in all required fields." | 📱 | Sign in / Create account / Edit profile | A required field is empty |
| "Incorrect email or password." | 📱 💻 | Sign in | Wrong email or password |
| "Couldn't create the account. This email may already be registered." | 📱 | Create account | Email already used or password under 8 characters |
| "Couldn't reach the server. Check your connection and try again." | 📱 | Sign in | No internet |
| "Camera permission is needed to take a photo." | 📱 | Scan | Camera permission denied |
| "Gallery permission is needed to choose a photo." | 📱 | Scan | Gallery permission denied |
| "That photo couldn't be read. Please try another one." | 📱 | Scan | Broken/unsupported image |
| "Sign in to share this result with the community." | 📱 | Result sheet | Sharing while signed out |
| "Couldn't share this result. Check your connection and try again." | 📱 | Result sheet | Sharing fails |
| "Set the location (barangay) first, so this report can appear on the Outbreak Map." | 📱 | Result sheet | Sharing a scan that has no location |
| "No disease reports near you in the last 4 weeks." | 📱 | Map → Reports near you | Nothing reported in your barangay recently |
| "No barangay matches your search." | 📱 | Select Barangay | Search finds nothing |
| "Delete this scan?" | 📱 | History | Trash icon tapped |
| "Write something before posting." | 📱 | Create Post | Empty post |
| "Couldn't load the community feed. Check your connection and try again." | 📱 | Community | Feed fails to load |
| "Couldn't save your profile. Check your connection and try again." | 📱 | Edit Profile | Save fails |
| "You're offline. Scanning and history still work…" | 📱 | Any screen | No internet |
| "Admins only" | 💻 | After sign in | Account is not an admin, or is suspended |
| "Mark as false alarm?" | 💻 | Outbreaks | Before marking an automatic alert as a false alarm |
| "No location" (red badge; Verify greyed out) | 💻 | Review → Leaf scans | A scan has no barangay |
| "This scan has no location, so it can't be placed on the map. Reject it instead." | 💻 | Review → Leaf scans | Verifying a scan with no location (if attempted) |

### 6.2 Outbreak level rules

Only **verified** scans from the last **7 days** in the same barangay count. Each scan adds weight by confidence (evidence strength): 80%+ = 1.0, 60–79% = 0.6, below 60% = 0.3. The level is the **outbreak level in the area**, not how badly a leaf is affected.

Following the WHO and DA-BPI definitions of an outbreak (disease above its normal level):
- **Collecting data:** until a barangay has at least **4 weeks** since its first verified scan **and 10 verified scans** of any kind (healthy included), its outbreak status is **Undetermined** and no alerts are sent.
- **Baseline:** after that, the farms reporting a disease this week are compared with the barangay's **baseline**: the average farms reporting it per week over up to 12 earlier weeks (weeks with no scans at all are skipped). It is "provisional" until it has 12 weeks of data.

Method and sources: [OUTBREAK_DETECTION_METHOD.md](OUTBREAK_DETECTION_METHOD.md).

| Level | Rule | Map dot |
|---|---|---|
| **Severe** | At least **3×** the baseline **and** at least 5 different farms, weighted score 4 or more | Large red marker, softly pulsing — reaching it sends an automatic potential-outbreak warning |
| **Moderate** | At least **2×** the baseline **and** at least 3 different farms, weighted score 2 or more | Medium amber marker — reaching it sends an automatic awareness notice |
| **Mild** | Verified reports below those levels | Small green marker |
| **Undetermined** | The barangay is still collecting data | Small green marker; popup says "Outbreak status: UNDETERMINED" |
| *(1–4 weeks old)* | No longer counted | Small faded grey marker; gone after 4 weeks |

| Baseline (farms / week) | 0–1.5 | 2 | 2.5 | 3 | 5 |
|---|---|---|---|---|---|
| Moderate at | 3 | 4 | 5 | 6 | 10 |
| Severe at | 5 | 6 | 8 | 9 | 15 |

One farmer's repeat scans can never raise the level, because Moderate and Severe both need several farms.

Marker shape and letter show the disease: circle "B" Northern Leaf Blight, square "R" Common Rust, diamond "G" Gray Leaf Spot. Healthy scans and scans from suspended accounts never count and are never shown.
Alerts are sent automatically when a level goes **up** (Moderate → awareness notice, Severe → potential-outbreak warning), never while a barangay is collecting data, to farmers in that barangay and the ones bordering it, and the same level isn't repeated there for that disease within 14 days. There is no manual alert. These thresholds are development values, pending validation by agricultural experts (D-07).

### 6.3 Glossary

- **Barangay** — the smallest local government area in the Philippines (a village or district). Bukidnon has 464.
- **Verified** — a shared scan accepted as real: automatically at 60%+ confidence with a photo, or by an admin.
- **Awareness notice / Outbreak warning** — the automatic push sent when a barangay reaches Moderate / Severe for a disease.
- **Push notification** — a message that appears on the phone even when the app is closed.
- **Admin** — a trusted account (e.g. DA technician) that can use the CornGuard Admin website.

### 6.4 Results summary

| Section | Tests | Passed | Failed | Tester | Date |
|---|---|---|---|---|---|
| 3.1 Navigation (M-NAV) | 7 | | | | |
| 3.2 Dashboard (M-HOME) | 4 | | | | |
| 3.3 Sign in / sign up (M-AUTH) | 10 | | | | |
| 3.4 Scanning (M-SCAN) | 8 | | | | |
| 3.5 Result sheet (M-RES) | 9 | | | | |
| 3.6 History (M-HIST) | 5 | | | | |
| 3.7 Community (M-COM) | 7 | | | | |
| 3.8 Outbreak Map (M-MAP) | 11 | | | | |
| 3.9 Settings (M-SET) | 7 | | | | |
| 3.10 Notifications (M-NOTIF) | 4 | | | | |
| 4.1 Web login (W-LOGIN) | 7 | | | | |
| 4.2 Layout & Dashboard (W-NAV, W-DASH) | 3 | | | | |
| 4.3 Outbreaks (W-OUT) | 6 | | | | |
| 4.4 Review (W-REV) | 6 | | | | |
| 4.5 Users (W-USR) | 4 | | | | |
| 4.6 Web map & general (W-MAP, W-GEN) | 4 | | | | |
| 5 End-to-end (E) | 4 | | | | |
| **Total** | **102** | | | | |
