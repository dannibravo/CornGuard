package com.cornguard.app.data.repository

import com.cornguard.app.data.model.AuthResult
import com.cornguard.app.data.model.AuthUser
import kotlinx.coroutines.flow.Flow

/**
 * Account creation/sign-in boundary. Mirrors firebase/repositories/auth-repository-interface.md
 * exactly — see that file for the full contract and explicit non-goals.
 *
 * Scope note: only email/password is implemented for Sprint 1, matching the sign-in provider
 * actually enabled in the dev Firebase project. Phone auth (also named in the interface doc)
 * needs the Phone provider enabled plus an Activity-bound reCAPTCHA/verification-code UI flow
 * designed together with Ligue — deferred rather than half-built.
 *
 * Online-only: every method can throw on no connectivity or an expired session. Callers must
 * check ConnectivityObserver before invoking this rather than let a raw network exception surface
 * as an unhandled crash (claude/15_CLAUDE.md Error Handling: "no internet, auth expiration").
 */
interface AuthRepository {
    suspend fun registerWithEmail(email: String, password: String): AuthResult
    suspend fun signInWithEmail(email: String, password: String): AuthResult
    suspend fun signOut()
    suspend fun sendPasswordReset(email: String)

    /** Null if no user is currently signed in. */
    fun getCurrentUser(): AuthUser?

    /** Emits the current user immediately, then again on every sign-in/sign-out. */
    fun observeAuthState(): Flow<AuthUser?>
}
