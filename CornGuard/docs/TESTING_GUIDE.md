# CornGuard — Testing Guide

This guide explains how to test **every feature** of CornGuard:

- 📱 **Mobile app** — the CornGuard Android app farmers use to scan corn leaves, see outbreaks and talk to other farmers.
- 💻 **Web** — the CornGuard Admin website, used by admins (for example a DA technician) to review scans, approve outbreak alerts, moderate posts and manage users.

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
1. Ask the project owner for the file `app-debug.apk` (found in `android/app/build/outputs/apk/debug/`).
2. Copy it to the phone and tap it.
3. If Android asks, allow "Install unknown apps" for the file manager you used, then tap **Install**.
4. Open **CornGuard** from the app list. The icon is a **white shield with a yellow corn cob** on a green background.

**On the Android emulator (computer)**
1. Start the emulator (Android Studio → Device Manager → ▶ Play).
2. In a terminal, run:
   `adb install -r --abi armeabi-v7a android/app/build/outputs/apk/debug/app-debug.apk`
3. Open **CornGuard** from the app list.

> Note: the emulator cold-boots each time but keeps installed apps. If CornGuard ever disappears or shows an old icon, install it again with step 2.

### 2.3 Run the admin website 💻

1. Open a terminal in the project's `admin-web` folder.
2. Make sure a file named `.env.local` exists with one line: `VITE_CONVEX_URL=https://<deployment>.convex.cloud` (the project owner can give you this line).
3. Run `npm install` (first time only), then `npm run dev`.
4. Open **http://localhost:5173** in your browser.

### 2.4 Test accounts

| Account | Email | Password | Used for |
|---|---|---|---|
| Demo farmer | `demo@cornguard.app` | `CornGuard-Demo-2026` | Main mobile app account (profile: Poblacion, Valencia, Bukidnon) |
| Helper farmer 2 | `demo2@cornguard.app` | `CornGuard-Demo-2026` | Second farmer for outbreak/comment tests |
| Helper farmer 3 | `demo3@cornguard.app` | `CornGuard-Demo-2026` | Third farmer for outbreak tests |
| Admin | `admin@cornguard.app` | **Ask the project owner** (not written here for security) | Admin website |

You may also create new accounts during testing (see M-AUTH tests). Use made-up emails such as `tester1@example.com`.

### 2.5 Demo data (optional, for map and outbreak tests)

The project owner (or a tester with the code) can load demo outbreak data from the `backend` folder:

- Add demo reports: `node scripts/seed-map-demo.mjs` — creates one **Severe** outbreak (Poblacion, City of Valencia), one **Moderate** (Barangay 1, City of Malaybalay) and one **Mild** (North Poblacion, Maramag).
- Remove them again: `node scripts/seed-map-demo.mjs --clear`

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
1. Email: `demo@cornguard.app`. Password: `CornGuard-Demo-2026`.
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
**Explanation:** Location is optional; the app never makes the farmer wait for a GPS fix.
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
**Steps:** Scan leaves in these situations (on the emulator, set location with `adb emu geo fix 125.09 7.91` for Valencia):
1. GPS inside Bukidnon. 2. GPS outside Bukidnon. 3. No location permission / GPS off.
**Expected result:** 1: a green pin with "Barangay, Municipality" (for example "Poblacion, City of Valencia"). 2: an orange warning "Outside Bukidnon". 3: an orange warning "GPS unavailable". In cases 2 and 3 an underlined "Set location manually" link appears.
**Explanation:** The barangay decides where a shared scan appears on the Outbreak Map.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-RES-04 · Set location manually (barangay picker)
**Platform:** 📱 Mobile app
**Steps:**
1. On a result without location, tap "Set location manually".
2. In "Select Barangay", type `lumbo` in the search box.
3. Tap "Lumbo — City of Valencia".
**Expected result:** The picker lists all Bukidnon barangays with their municipality. Typing filters the list. After tapping, the picker closes, the location line turns green ("Lumbo, City of Valencia") and the link changes to "Change location".
**Explanation:** Lets farmers fix the location when GPS fails, so the report lands in the right barangay.
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
- A share at **85% confidence or higher** is verified automatically and appears as a dot on the Outbreak Map. Below 85% it waits for an admin to verify it (see W-REV-03).
- A farmer can share at most **30 scans per day**; the 31st is refused with an error (hard to trigger by hand).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-RES-08 · Share to Community — no internet
**Platform:** 📱 Mobile app
**Steps:** 1. Signed in, turn on Airplane mode. 2. Scan a leaf and tap "Share to Community".
**Expected result:** Message: "Couldn't share this result. Check your connection and try again." The scan stays in History as "On device"; you can share it later from History.
**Explanation:** Sharing needs internet; nothing is lost when it fails.
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
**Expected result:** A dark header "Outbreak Heatmap" with a round back button. A street map of northern Mindanao with Bukidnon's barangay boundaries drawn as outlines. Coloured dots: red (Severe), amber (Moderate), green (Mild), each with a coloured ring (blue = Northern Leaf Blight, pink = Common Rust, violet = Gray Leaf Spot). A legend at the bottom right, zoom "+"/"−" buttons at the bottom left, and a "Reports near you" panel at the bottom.
**Explanation:** Shows where verified reports are and how serious each area is.
**Error/edge cases:** Bigger dots = more severe, so the map is readable without relying on colour alone.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-03 · Zoom, pan and back
**Platform:** 📱 Mobile app
**Steps:** 1. Tap "+" twice, then "−". 2. Drag the map with one finger; pinch to zoom. 3. Tap the back button.
**Expected result:** The map zooms and moves smoothly; back returns to the Dashboard.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-04 · Collapsible legend
**Platform:** 📱 Mobile app
**Steps:** 1. Tap the legend box. 2. Tap "Legend ▸".
**Expected result:** Tap 1 shrinks it to a small "Legend ▸" button (uncovering the map). Tap 2 expands it again ("Severity" and "Disease (ring)" lists).
**Explanation:** On a phone the full legend can cover dots.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-05 · Dot popup
**Platform:** 📱 Mobile app
**Steps:** 1. Tap a dot.
**Expected result:** A dark popup: disease name, "Barangay, Municipality", "Severity: SEVERE/MODERATE/MILD" in the matching colour, "Confidence: NN%" and "Reported: <date>". Tapping elsewhere or the popup's "×" closes it.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-06 · Barangay popup
**Platform:** 📱 Mobile app
**Steps:** 1. Tap inside a barangay outline that has a dot. 2. Tap a barangay with no reports.
**Expected result:** 1: barangay and municipality name, then for each active disease "Active Disease", "Severity", "Farms Affected" and "Total Reports". 2: the name plus "No active disease reports".
**Explanation:** Shows the outbreak status of a whole barangay.
**Error/edge cases:** Barangays with the same name in different towns (e.g. "Poblacion") show their own separate data.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### M-MAP-07 · "Reports near you" panel
**Platform:** 📱 Mobile app
**Steps:** 1. Tap the "Reports near you" header (or drag it up). 2. Scroll. 3. Tap the header again (or drag it down).
**Expected result:** The panel rises and shows "Showing <your barangay, municipality>", a "VERIFIED OUTBREAKS" list and a "NEARBY REPORTS" list (or their empty messages: "No verified outbreaks near you yet…" / "No community reports in your area yet."). Tapping again lowers it.
**Explanation:** A text summary of reports in the farmer's own barangay.
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

#### M-SET-05 · My Farms
**Platform:** 📱 Mobile app
**Steps:** 1. Under "MY FARMS", type `North field` in "Add a farm, e.g. North field". 2. Tap "Add".
**Expected result:** The text box clears and "North field" appears in the list. Before adding any farm the list says "No farms added yet."
**Explanation:** Farmers can name their fields.
**Error/edge cases:** Tapping "Add" with an empty box does nothing. The button is disabled while saving (no duplicates from double taps).
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

#### M-NOTIF-01 · Outbreak alert (arrives only after admin approval)
**Platform:** 📱 Mobile app + 💻 Web
**Steps:**
1. On the phone, sign in as the demo farmer (profile barangay Poblacion, Valencia), then go to the phone's home screen.
2. Create a Severe outbreak in Poblacion, City of Valencia (run the demo script, section 2.5, or follow E-01).
3. Wait 30 seconds and check the phone — **no** outbreak notification yet.
4. On the website, approve the outbreak (W-OUT-02).
5. Check the phone; then tap the notification.
**Expected result:** After step 4 a notification arrives: "Northern Leaf Blight outbreak reported nearby — Multiple corn farms in Poblacion, City of Valencia have confirmed Northern Leaf Blight. Check the outbreak map." It pops up (high priority) with a leaf icon. Tapping it opens CornGuard on the Outbreak Map.
**Explanation:** Severe outbreaks are checked by an admin first so farmers never get false alarms.
**Error/edge cases:**
- Farmers in neighbouring barangays (e.g. Lumbo) also get it; farmers far away do not.
- A logged-out phone does not get it.
- If several diseases turned Severe in the same barangay, one combined alert is sent ("Northern Leaf Blight and Gray Leaf Spot outbreak…").
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
**Steps:** 1. Sign in with `demo@cornguard.app` / `CornGuard-Demo-2026`. 2. Click "Sign out".
**Expected result:** A lock screen "Admins only" — "demo@cornguard.app is not an admin. Ask an existing admin to promote it in Users." — with a "Sign out" button that returns to the login page.
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
**Expected result:** Each opens its page and is highlighted in the sidebar; the page title at the top changes ("Dashboard", "Outbreak Alerts", "Review & Moderation", "User Management", "Outbreak Map"). Orange number badges appear next to "Outbreaks" (pending outbreaks) and "Review" (scans awaiting review) when there is something to do.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-DASH-01 · Dashboard cards
**Platform:** 💻 Web
**Steps:** 1. Open the Dashboard. 2. Click each card that is a link.
**Expected result:** Eight cards: "Outbreaks awaiting review", "Scans awaiting review", "Unverified posts", "Severe barangay / disease", "Users", "Shared scans", "Verified scans", "Suspended accounts". The first two get an orange outline when their number is above 0. Clickable cards open Outbreaks, Review, Map or Users.
**Explanation:** A quick view of what needs attention.
**Error/edge cases:** Numbers update by themselves when data changes (e.g. after approving an outbreak the first card drops to 0).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-DASH-02 · "How alerts work" box
**Platform:** 💻 Web
**Steps:** 1. Read the "How alerts work" box. 2. Click the "Outbreaks" and "Review" links in it.
**Expected result:** It explains the Severe rule and that farmers are notified only after approval. The links open those pages.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 4.3 Outbreaks

#### W-OUT-01 · Pending outbreak card
**Platform:** 💻 Web
**Steps:** 1. Load demo data (section 2.5). 2. Open "Outbreaks".
**Expected result:** Under "Awaiting review (1)" a card "Poblacion, City of Valencia", "Declared <date>", "Would notify N farmer(s) with the app", a table (Disease, Severity now = SEVERE, Farms 3, Reports 4, Score 4.0), a "Note" box, "Approve & notify farmers" and "Dismiss".
**Explanation:** The admin sees the evidence before deciding.
**Error/edge cases:** If severity drops after declaring (e.g. scans rejected), the card shows "Severity has dropped since this was declared… consider dismissing."
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-OUT-02 · Approve an outbreak
**Platform:** 💻 Web (+ 📱 to check the alert)
**Steps:** 1. Type `Confirmed by DA technician` in the note box. 2. Click "Approve & notify farmers". 3. In the dialog, click "Send alert".
**Expected result:** A dialog "Send outbreak alert?" says how many farmers will be notified and about which disease. After "Send alert" the card leaves the pending list and appears in "History" with status "SENT", the number notified, "Reviewed by" and your note. Signed-in farmers in that barangay and the neighbouring ones get the push (M-NOTIF-01). The "Push delivery log" shows "outbreak_alert" rows with "sent".
**Error/edge cases:** "Cancel" in the dialog (or pressing Esc, or clicking outside it) sends nothing. An outbreak cannot be approved twice.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-OUT-03 · Dismiss an outbreak
**Platform:** 💻 Web
**Steps:** 1. With a pending outbreak, click "Dismiss". 2. Confirm with "Dismiss".
**Expected result:** A dialog "Dismiss outbreak?" — "Nobody will be notified…". After confirming, it moves to History as "DISMISSED". No phone receives a notification.
**Explanation:** For false alarms (e.g. wrong photos).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-OUT-04 · Raise an alert manually
**Platform:** 💻 Web (+ 📱)
**Steps:**
1. In "Raise an alert manually", choose Municipality "City of Valencia".
2. Choose Barangay "Poblacion".
3. Tick "Common Rust".
4. Optionally type a message, e.g. `Inspect lower leaves this week.`
5. Click "Send alert", then "Send alert" in the dialog.
**Expected result:** The Barangay list is disabled until a municipality is chosen, then lists only that town's barangays. "Send alert" stays disabled until municipality, barangay and at least one disease are chosen. After confirming, "Alert sent for Poblacion, City of Valencia." appears, the form clears, History shows a "manual" "SENT" row, and farmers there get the push (your message if typed, otherwise the standard one).
**Error/edge cases:** Changing the municipality clears the chosen barangay. Several diseases can be ticked (one combined alert).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-OUT-05 · History tabs and delivery log
**Platform:** 💻 Web
**Steps:** 1. In "History", click "All", "Sent", "Dismissed". 2. Scroll to "Push delivery log".
**Expected result:** Each tab filters the list. With nothing to show: "No Data — No reviewed outbreaks yet." The delivery log lists each push with time, type, title and a badge: green "sent", red "failed" (e.g. the phone had no app installed), orange "pending".
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
**Expected result:** Columns: Photo, Model says, Confidence, Farmer, Area, Scanned, Status, Actions. The photo opens full size and closes when you click outside it. Scans without GPS show "(no GPS)"; farmers who are suspended show a red "suspended" badge.
**Error/edge cases:** Clicking a column header with an arrow sorts the list (click again to reverse).
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-REV-03 · Verify / Reject a scan
**Platform:** 💻 Web (+ 📱 map)
**Steps:** 1. On an "Awaiting review" scan with a Bukidnon location, click "Verify". 2. On a verified scan, click "Reject".
**Expected result:** The status badge changes immediately (VERIFIED green / REJECTED red) and the scan moves to the matching tab. A verified scan appears as a dot on the Outbreak Map; a rejected one disappears from the map and its barangay's severity is recalculated.
**Explanation:** Only verified scans count toward outbreaks, so admins remove wrong or fake ones.
**Error/edge cases:** Clicking "Verify" twice quickly causes no error. If the action fails (e.g. internet lost), a red message appears above the list.
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
**Expected result:** The dialog warns what suspension does. After suspending, the status becomes "suspended"; in the app the user's post/share fails with an error; their scans stop counting on the map and they get no outbreak alerts. After reactivating, everything works again.
**Error/edge cases:** There is no Suspend button on your own row (you cannot suspend yourself). Nothing is permanently deleted.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### W-USR-04 · Make admin
**Platform:** 💻 Web
**Steps:** 1. Click "Make admin" on a test account you created, then confirm. 2. Log out and sign in to the website with that account.
**Expected result:** The role badge changes to "admin" and the "Make admin" button disappears for that row. The account can now use the website.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

---

### 4.6 Outbreak Map (web) and general

#### W-MAP-01 · Web map
**Platform:** 💻 Web
**Steps:** 1. Open "Outbreak Map". 2. Click a dot and a barangay. 3. Read the card below the map.
**Expected result:** The same map as the app (boundaries, coloured dots with rings, legend, popups) with "Individual verified reports across Bukidnon — N plotted". Below: "How severity is assigned" explaining Severe, Moderate and Mild.
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

#### E-01 · From scans to an approved outbreak alert
**Platform:** 📱 Mobile app + 💻 Web
**Steps:**
1. Phone: sign in as `demo@cornguard.app`, stay signed in, leave the phone on the home screen.
2. Using three accounts (demo, demo2, demo3), share confident (85%+) scans of the same disease with a location in Poblacion, City of Valencia — four scans in total, at least one from each account. (Shortcut: run `node scripts/seed-map-demo.mjs`.)
3. Phone: open the Outbreak Map — a red dot appears in Valencia. No notification has arrived.
4. Website: the Dashboard shows "1 Outbreaks awaiting review". Open Outbreaks → Approve & notify farmers → Send alert.
5. Phone: the outbreak notification arrives; tap it.
**Expected result:** Each step behaves as described; the notification opens the Outbreak Map.
**Explanation:** This is the core flow: scans → automatic severity → admin check → farmers warned.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### E-02 · Moderation round trip
**Platform:** 📱 + 💻
**Steps:** 1. App: create a post. 2. Website: Review → Posts → "Hide". 3. App (another account): refresh the feed. 4. Website: "Restore". 5. App: refresh.
**Expected result:** The post disappears at step 3 and comes back at step 5.
**Result:** ☐ Pass ☐ Fail — Notes: ____________

#### E-03 · Rejecting scans lowers severity
**Platform:** 📱 + 💻
**Steps:** 1. With the demo Severe outbreak loaded, website: Review → Leaf scans → All → reject two of the Poblacion (Valencia) scans. 2. App: look at the Valencia dot and barangay popup.
**Expected result:** The severity drops (Severe → Moderate or Mild) and the dot gets smaller and changes colour. A still-pending outbreak card on the website shows the "Severity has dropped…" hint.
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
| "No barangay matches your search." | 📱 | Select Barangay | Search finds nothing |
| "Delete this scan?" | 📱 | History | Trash icon tapped |
| "Write something before posting." | 📱 | Create Post | Empty post |
| "Couldn't load the community feed. Check your connection and try again." | 📱 | Community | Feed fails to load |
| "Couldn't save your profile. Check your connection and try again." | 📱 | Edit Profile | Save fails |
| "You're offline. Scanning and history still work…" | 📱 | Any screen | No internet |
| "Admins only" | 💻 | After sign in | Account is not an admin, or is suspended |
| "Send outbreak alert?" / "Dismiss outbreak?" / "Send this alert now?" | 💻 | Outbreaks | Before approving, dismissing or raising an alert |

### 6.2 Outbreak severity rules

Only **verified** scans from the last **14 days** in the same barangay count. Each scan adds weight by confidence: 90%+ = 1.0, 70–89% = 0.6, below 70% = 0.3.

| Level | Rule | Map dot |
|---|---|---|
| **Severe** | Weighted score 4 or more **and** 3 or more different farmers | Large red dot — creates a pending outbreak for admin review |
| **Moderate** | Weighted score 2 or more **or** 2 or more different farmers | Medium amber dot |
| **Mild** | Any verified report | Small green dot |

Healthy scans never count.

### 6.3 Glossary

- **Barangay** — the smallest local government area in the Philippines (a village or district). Bukidnon has 464.
- **Verified** — a shared scan accepted as real: automatically at 85%+ confidence with a photo, or by an admin.
- **Pending** — a Severe outbreak waiting for an admin to approve or dismiss it.
- **Push notification** — a message that appears on the phone even when the app is closed.
- **Admin** — a trusted account (e.g. DA technician) that can use the CornGuard Admin website.

### 6.4 Results summary

| Section | Tests | Passed | Failed | Tester | Date |
|---|---|---|---|---|---|
| 3.1 Navigation (M-NAV) | 7 | | | | |
| 3.2 Dashboard (M-HOME) | 4 | | | | |
| 3.3 Sign in / sign up (M-AUTH) | 10 | | | | |
| 3.4 Scanning (M-SCAN) | 8 | | | | |
| 3.5 Result sheet (M-RES) | 8 | | | | |
| 3.6 History (M-HIST) | 4 | | | | |
| 3.7 Community (M-COM) | 7 | | | | |
| 3.8 Outbreak Map (M-MAP) | 9 | | | | |
| 3.9 Settings (M-SET) | 7 | | | | |
| 3.10 Notifications (M-NOTIF) | 3 | | | | |
| 4.1 Web login (W-LOGIN) | 7 | | | | |
| 4.2 Layout & Dashboard (W-NAV, W-DASH) | 3 | | | | |
| 4.3 Outbreaks (W-OUT) | 5 | | | | |
| 4.4 Review (W-REV) | 6 | | | | |
| 4.5 Users (W-USR) | 4 | | | | |
| 4.6 Web map & general (W-MAP, W-GEN) | 3 | | | | |
| 5 End-to-end (E) | 4 | | | | |
| **Total** | **99** | | | | |
