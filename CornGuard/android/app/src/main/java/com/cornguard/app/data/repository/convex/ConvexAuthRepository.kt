package com.cornguard.app.data.repository.convex

import android.content.Context
import com.cornguard.app.data.model.AuthResult
import com.cornguard.app.data.model.AuthUser
import com.cornguard.app.data.remote.convex.ConvexAuthProvider
import com.cornguard.app.data.remote.convex.ConvexBackend
import com.cornguard.app.data.remote.convex.ConvexHttp
import com.cornguard.app.data.remote.convex.UserDto
import com.cornguard.app.data.repository.AuthRepository
import dev.convex.android.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Email/password auth via Convex Auth (see [ConvexAuthProvider]). The signed-in user's id and
 * email are cached in private SharedPreferences so [getCurrentUser] answers synchronously right
 * after a cold start, while [restoreSession] re-establishes the backend session in the background.
 */
class ConvexAuthRepository(
    context: Context,
    private val backend: ConvexBackend
) : AuthRepository {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val currentUser = MutableStateFlow(loadCachedUser())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // A session that can no longer be restored (refresh token rejected) signs the user out.
        scope.launch {
            backend.client.authState.collect { state ->
                if (state is AuthState.Unauthenticated<*> && !backend.authProvider.hasSavedSession()) {
                    setUser(null)
                }
            }
        }
    }

    override suspend fun registerWithEmail(email: String, password: String): AuthResult {
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw IllegalArgumentException("Password must be at least $MIN_PASSWORD_LENGTH characters.")
        }
        return AuthResult(authenticate(email, password, ConvexAuthProvider.Flow.SIGN_UP), isNewUser = true)
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        AuthResult(authenticate(email, password, ConvexAuthProvider.Flow.SIGN_IN), isNewUser = false)

    override suspend fun signOut() {
        backend.authProvider.signOutRemote()
        backend.client.logout(appContext)
        setUser(null)
    }

    override suspend fun sendPasswordReset(email: String) {
        throw UnsupportedOperationException(
            "Password reset needs an email provider configured for Convex Auth; it isn't set up yet."
        )
    }

    override fun getCurrentUser(): AuthUser? = currentUser.value

    override fun observeAuthState(): Flow<AuthUser?> = currentUser.asStateFlow()

    /** Called once at app start; keeps the cached user if the device is merely offline. */
    suspend fun restoreSession() {
        if (!backend.authProvider.hasSavedSession()) {
            setUser(null)
            return
        }
        // Off the main thread: the SDK's native calls can block (see ConvexBackend.queryOnce).
        withContext(Dispatchers.IO) { backend.client.loginFromCache() }
        if (!backend.authProvider.hasSavedSession()) setUser(null)
    }

    private suspend fun authenticate(
        email: String,
        password: String,
        flow: ConvexAuthProvider.Flow
    ): AuthUser = withContext(Dispatchers.IO) {
        backend.authProvider.prepare(email, password, flow)
        backend.client.login(appContext).getOrElse { throw IllegalStateException(friendlyMessage(it, flow), it) }
        val viewer = withTimeout(VIEWER_TIMEOUT_MS) {
            backend.client.subscribe<UserDto?>("users:viewer")
                .map { it.getOrNull() }
                .filterNotNull()
                .first()
        }
        val user = AuthUser(uid = viewer.userId, email = viewer.email, phoneNumber = null)
        setUser(user)
        user
    }

    private fun friendlyMessage(error: Throwable, flow: ConvexAuthProvider.Flow): String = when {
        error is IOException || error.cause is IOException ->
            "Couldn't reach the server. Check your connection and try again."
        error !is ConvexHttp.CallException -> error.message ?: GENERIC_ERROR
        flow == ConvexAuthProvider.Flow.SIGN_IN -> "Incorrect email or password."
        else -> "Couldn't create the account. This email may already be registered."
    }

    private fun setUser(user: AuthUser?) {
        currentUser.value = user
        prefs.edit().apply {
            if (user == null) clear() else putString(KEY_UID, user.uid).putString(KEY_EMAIL, user.email)
        }.apply()
    }

    private fun loadCachedUser(): AuthUser? {
        val uid = prefs.getString(KEY_UID, null) ?: return null
        return AuthUser(uid = uid, email = prefs.getString(KEY_EMAIL, null), phoneNumber = null)
    }

    private companion object {
        const val PREFS = "convex_auth_user"
        const val KEY_UID = "uid"
        const val KEY_EMAIL = "email"
        const val MIN_PASSWORD_LENGTH = 8
        const val VIEWER_TIMEOUT_MS = 15_000L
        const val GENERIC_ERROR = "Something went wrong. Please try again."
    }
}
