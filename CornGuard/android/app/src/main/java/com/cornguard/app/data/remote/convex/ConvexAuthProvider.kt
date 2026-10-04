package com.cornguard.app.data.remote.convex

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dev.convex.android.AuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A Convex Auth session: short-lived JWT + long-lived, single-use refresh token. */
data class ConvexSession(val token: String, val refreshToken: String)

/**
 * [AuthProvider] for Convex Auth's Password provider. The library's `login(context)` takes no
 * credentials, so ConvexAuthRepository stages them with [prepare] immediately before calling it.
 *
 * Sessions persist across restarts via the refresh token (private SharedPreferences). Convex Auth
 * refresh tokens are single-use: presenting one again after it was exchanged (outside a short
 * grace window) is treated as token theft and ends the whole session. So every exchange runs under
 * one [mutex], always uses the latest stored token, and is persisted synchronously; and
 * `loginFromCache` — which the Convex client also calls on its own whenever the server asks for a
 * fresh token — reuses the current JWT while it's still valid instead of exchanging again.
 */
class ConvexAuthProvider(
    context: Context,
    deploymentUrl: String
) : AuthProvider<ConvexSession> {

    enum class Flow(val value: String) { SIGN_IN("signIn"), SIGN_UP("signUp") }

    private data class Credentials(val email: String, val password: String, val flow: Flow)

    private val http = ConvexHttp(deploymentUrl)
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var refreshJob: Job? = null

    @Volatile private var pending: Credentials? = null
    @Volatile private var session: ConvexSession? = null
    @Volatile private var sessionObtainedAt = 0L

    fun prepare(email: String, password: String, flow: Flow) {
        pending = Credentials(email.trim(), password, flow)
    }

    /** True while a refresh token is stored, i.e. a session can still be restored. */
    fun hasSavedSession(): Boolean = prefs.contains(KEY_REFRESH)

    /** Ends the session server-side (invalidates the refresh token). Best-effort. */
    suspend fun signOutRemote() {
        val token = session?.token ?: return
        runCatching { http.action("auth:signOut", JSONObject(), bearerToken = token) }
    }

    override suspend fun login(context: Context, onIdToken: (String?) -> Unit): Result<ConvexSession> =
        mutex.withLock {
            val credentials = pending ?: return@withLock Result.failure(IllegalStateException("No credentials staged"))
            pending = null
            runCatching {
                val params = JSONObject()
                    .put("email", credentials.email)
                    .put("password", credentials.password)
                    .put("flow", credentials.flow.value)
                signIn(JSONObject().put("provider", "password").put("params", params))
            }.onSuccess {
                save(it)
                scheduleRefresh(onIdToken)
                Log.i(TAG, "Signed in (${credentials.flow.value})")
            }
        }

    override suspend fun loginFromCache(onIdToken: (String?) -> Unit): Result<ConvexSession> =
        mutex.withLock {
            session?.takeIf { isFresh() }?.let { current ->
                return@withLock Result.success(current)
            }
            val result = exchangeRefreshToken()
            result.onSuccess { scheduleRefresh(onIdToken) }
            result
        }

    override suspend fun logout(context: Context): Result<Void?> {
        refreshJob?.cancel()
        mutex.withLock { clear("signed out") }
        return Result.success(null)
    }

    override fun extractIdToken(authResult: ConvexSession): String = authResult.token

    /** Exchanges the latest refresh token for a new session. Caller must hold [mutex]. */
    private suspend fun exchangeRefreshToken(): Result<ConvexSession> {
        val refreshToken = session?.refreshToken ?: prefs.getString(KEY_REFRESH, null)
            ?: return Result.failure(IllegalStateException("No saved session"))
        return runCatching { signIn(JSONObject().put("refreshToken", refreshToken)) }
            .onSuccess {
                save(it)
                Log.i(TAG, "Session refreshed")
            }
            .onFailure { error ->
                // Only a rejected token ends the session; a network failure keeps it for next time.
                if (error is ConvexHttp.CallException) clear("refresh rejected: ${error.message}")
                else Log.w(TAG, "Session refresh failed (will retry): ${error.message}")
            }
    }

    private suspend fun signIn(args: JSONObject): ConvexSession {
        val value = http.action("auth:signIn", args) as? JSONObject
        val tokens = value?.optJSONObject("tokens")
            ?: throw ConvexHttp.CallException("Sign-in did not return a session", value.toString())
        return ConvexSession(tokens.getString("token"), tokens.getString("refreshToken"))
    }

    private suspend fun save(newSession: ConvexSession) {
        session = newSession
        sessionObtainedAt = SystemClock.elapsedRealtime()
        // commit(), not apply(): a lost write would leave an already-used token on disk.
        withContext(Dispatchers.IO) { prefs.edit().putString(KEY_REFRESH, newSession.refreshToken).commit() }
    }

    private fun isFresh() = SystemClock.elapsedRealtime() - sessionObtainedAt < REFRESH_INTERVAL_MS

    private fun scheduleRefresh(onIdToken: (String?) -> Unit) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                val wait = REFRESH_INTERVAL_MS - (SystemClock.elapsedRealtime() - sessionObtainedAt)
                delay(wait.coerceAtLeast(RETRY_INTERVAL_MS))
                val outcome = mutex.withLock {
                    if (session == null) return@launch
                    if (isFresh()) null else exchangeRefreshToken() // someone else may have refreshed
                } ?: continue
                outcome.onSuccess { onIdToken(it.token) }
                outcome.onFailure { if (session == null) { onIdToken(null); return@launch } }
            }
        }
    }

    private fun clear(reason: String) {
        Log.i(TAG, "Session cleared: $reason")
        session = null
        sessionObtainedAt = 0L
        prefs.edit().clear().commit()
    }

    private companion object {
        const val TAG = "ConvexAuth"
        const val PREFS = "convex_auth"
        const val KEY_REFRESH = "refresh_token"
        const val REFRESH_INTERVAL_MS = 50L * 60 * 1000 // JWT lifetime is 60 min
        const val RETRY_INTERVAL_MS = 60L * 1000
    }
}
