# CornGuard Admin (website)

Adapted from caps 3's `convex-prototype/admin-web`, with the same Vite + React + Leaflet stack, components and
styles, and connected to CornGuard's Convex backend (`../backend`).

## Pages
| Page | What it's for | Backend functions (`backend/convex/admin.ts` unless noted) |
|---|---|---|
| **Dashboard** | Counts of what needs attention, including **clusters without a baseline** (5+ farms in an area where neither it nor its neighbours have enough data; no alert was sent) | `stats` |
| **Outbreaks** | Log of the **automatic** alerts: a barangay reaching Moderate (2× its weekly baseline) sends an awareness notice, reaching Severe (3×) a potential-outbreak warning (never while it is still collecting data for its baseline), to farmers in that barangay and the ones bordering it (not repeated within 14 days). Each alert shows **why it fired** (e.g. "5 farms vs usual 2.5/week, Moderate at 5"), as of the moment it fired. **False alarm** marks one as reviewed (and cancels it in its first minute). Above the log: **clusters in areas without a baseline**, for review. Also: the push delivery log. There is no manual alert: every alert starts from verified reports. | `listOutbreaks`, `dismissOutbreak`, `recentNotifications` |
| **Review → Leaf scans** | Check shared scans' photos; Verify or Reject. Only verified scans count toward outbreaks. | `listRecords`, `setRecordVerification` |
| **Review → Posts** | Authenticity signals: photo, linked scan (and whether it matches the disease tag), author's history, upvotes and comments. Verify or reject; hide, remove or restore. | `listPosts`, `setPostVerification`, `setPostModeration` |
| **Review → Comments** | Hide, remove or restore | `listComments`, `setCommentModeration` |
| **Users** | Suspend or reactivate (a suspended account can't post or share, gets no alerts, and its scans stop counting), view a user's scans. Nothing is permanently deleted. There is no "make admin" button. | `listUsers`, `setAccountStatus`, `listRecords` |
| **Outbreak Map** | The same heatmap as the app, plus (admin only) a **teal outline** on barangays that have a baseline, and a collapsible **Baselines** panel beside the map: every barangay, A to Z, each expanding to its usual farms per week per disease and the farm counts for Moderate and Severe (or its data-gathering progress), with **Show on map** to fly there. A **Map / Satellite** icon button (bottom-left) switches the basemap to Esri imagery; the choice is remembered per browser | `barangayStats.getAllStats` (`includeBaselines: true`), `diagnosisRecords.mapReports` |

caps 3's **Groups** page and its **Messages** tab were removed; CornGuard has no group chat.

## Sign-in
Admins sign in with a normal CornGuard account (Convex Auth email and password) that has the **admin** role. Every
admin function checks that role on the server.

caps 3 compared a username and password that were built into the JavaScript bundle, so anyone could read them. That
was removed.

- **First admin:** create an account in the app or by signing up, then from `backend/` run:
  `npx convex run admin:bootstrapFirstAdmin "{email:'you@example.com'}"`
- **More admins:** the same command with the new admin's email. Granting admin is deliberately not possible from
  the website.
- **Non-admin accounts** see an "Admins only" screen.

## Run
```bash
cd admin-web
npm install
# .env.local (git-ignored):  VITE_CONVEX_URL=https://<deployment>.convex.cloud   (same as backend/.env.local)
npm run dev        # http://localhost:5173
npm run build      # type-check + production build in dist/
```

## Tested
`e2e-screenshots/` holds screenshots from an automated browser run against the dev deployment. The run covered:
- a signed-out visitor is redirected to the login page
- a wrong password shows an error
- a non-admin is refused
- the admin dashboard, scan, post, comment and user lists, and the map all load
- a barangay reaching Moderate, then Severe, sends one automatic push each to the emulator
