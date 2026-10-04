# CornGuard Admin (website)

Adapted from caps 3's `convex-prototype/admin-web`, with the same Vite + React + Leaflet stack, components and
styles, and connected to CornGuard's Convex backend (`../backend`).

## Pages
| Page | What it's for | Backend functions (`backend/convex/admin.ts` unless noted) |
|---|---|---|
| **Dashboard** | Counts of what needs attention | `stats` |
| **Outbreaks** | Severe barangays wait here as *pending*. **Approve** notifies farmers in that barangay and the ones bordering it; **Dismiss** notifies nobody. Also: raise an alert manually, see history and the push delivery log. | `listOutbreaks`, `approveOutbreak`, `dismissOutbreak`, `raiseAlert`, `recentNotifications` |
| **Review → Leaf scans** | Check shared scans' photos; Verify or Reject. Only verified scans count toward outbreaks. | `listRecords`, `setRecordVerification` |
| **Review → Posts** | Authenticity signals: photo, linked scan (and whether it matches the disease tag), author's history, upvotes and comments. Verify or reject; hide, remove or restore. | `listPosts`, `setPostVerification`, `setPostModeration` |
| **Review → Comments** | Hide, remove or restore | `listComments`, `setCommentModeration` |
| **Users** | Suspend or reactivate (a suspended account can't post or share, gets no alerts, and its scans stop counting), make admin, view a user's scans. Nothing is permanently deleted. | `listUsers`, `setAccountStatus`, `promoteToAdmin`, `listRecords` |
| **Outbreak Map** | The same heatmap as the app | `barangayStats.getAllStats`, `diagnosisRecords.mapReports` |

caps 3's **Groups** page and its **Messages** tab were removed; CornGuard has no group chat.

## Sign-in
Admins sign in with a normal CornGuard account (Convex Auth email and password) that has the **admin** role. Every
admin function checks that role on the server.

caps 3 compared a username and password that were built into the JavaScript bundle, so anyone could read them. That
was removed.

- **First admin:** create an account in the app or by signing up, then from `backend/` run:
  `npx convex run admin:bootstrapFirstAdmin "{email:'you@example.com'}"`
- **More admins:** Users → **Make admin**.
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
- approving a pending outbreak sends exactly one push to the emulator
