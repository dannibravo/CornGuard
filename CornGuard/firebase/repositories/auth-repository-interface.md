# CORNGUARD — Auth Repository Interface (Draft, Sprint 0)

Provider-agnostic contract for account creation/sign-in, consumed by Ligue's Android
auth/profile screens (`claude/16_UI_UX_AND_DIAGRAM_REVISION_GUIDE.md`, section 5.3). Assumes
Firebase Authentication per `claude/02_PROJECT_CONTEXT.md`, but exposes no Firebase-SDK types
directly so the Android layer can wrap it cleanly.

Per the UI/UX guide, farmers register with **email or mobile number** — collect nothing beyond
what's needed (`04_DEVELOPMENT_RULES.md` and manuscript section 5.3: "Do not collect unnecessary
personal data").

## Value types

```
AuthUser {
  uid: string,
  email: string?,
  phoneNumber: string?
}

AuthResult {
  user: AuthUser,
  isNewUser: bool
}
```

## Operations

```
registerWithEmail(email: string, password: string) -> AuthResult
registerWithPhone(phoneNumber: string) -> VerificationSession   // starts OTP flow
confirmPhoneCode(session: VerificationSession, code: string) -> AuthResult

signInWithEmail(email: string, password: string) -> AuthResult
signInWithPhone(phoneNumber: string) -> VerificationSession
confirmSignInCode(session: VerificationSession, code: string) -> AuthResult

signOut() -> void
sendPasswordReset(email: string) -> void

getCurrentUser() -> AuthUser?
observeAuthState() -> Stream<AuthUser?>
```

## Explicit non-goals

- **Role is never set here.** Auth only establishes identity (`uid`). The `users/{uid}` document
  — including the fixed `role: "farmer"` at creation — is written by the **User/Farm repository**
  in a follow-up call right after a successful `registerWith*`, per
  `security/firestore.rules` (`create` requires `role == 'farmer'`). There is no client path to
  become `admin`; that is a backend-only operation via Admin SDK custom claims
  (`security/access-control-matrix.md`).
- No profile fields (`display_name`, `barangay`, etc.) are collected here — that's the User/Farm
  repository's `createUserProfile` call, kept as a separate step so a partially-completed
  registration doesn't leave an inconsistent `users` document.
- No `technician` registration path exists — reserved and disabled until D-02.
