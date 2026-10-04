package com.cornguard.app.data.remote.convex

import android.content.Context
import dev.convex.android.ConvexClientWithAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

typealias CornGuardConvexClient = ConvexClientWithAuth<ConvexSession>

/**
 * The single Convex connection for the app process (one WebSocket), plus its auth provider and
 * the image uploader. Created lazily by ServiceLocator the first time an online feature is used.
 */
class ConvexBackend(context: Context, deploymentUrl: String) {

    init {
        require(deploymentUrl.isNotBlank()) {
            "Convex isn't configured: set convex.url=https://<deployment>.convex.cloud in android/local.properties"
        }
    }

    val authProvider = ConvexAuthProvider(context, deploymentUrl)
    val client: CornGuardConvexClient = ConvexClientWithAuth(deploymentUrl, authProvider)
    val uploader = ConvexFileUploader(context, client)
}

/**
 * Builds a Convex argument map. Null values are dropped (Convex `v.optional` fields must be
 * absent, not null) and every number is sent as a Double, because the Kotlin client encodes
 * Int/Long as int64, which the backend's `v.number()` (float64) validators reject.
 */
internal fun convexArgs(vararg pairs: Pair<String, Any?>): Map<String, Any?> = buildMap {
    for ((key, value) in pairs) {
        when (value) {
            null -> Unit
            is Int -> put(key, value.toDouble())
            is Long -> put(key, value.toDouble())
            is Float -> put(key, value.toDouble())
            else -> put(key, value)
        }
    }
}

/** Runs a mutation whose result isn't needed (the backend returns null for these). */
internal suspend fun CornGuardConvexClient.execute(name: String, args: Map<String, Any?> = emptyMap()) {
    mutation<JsonElement?>(name, args)
}

/**
 * One-shot read of a query: the first result of its live subscription.
 *
 * Subscriptions always run on [Dispatchers.IO]: the SDK's `subscribe` polls its native future
 * with a blocking call, which can wait for seconds while the client reconnects or re-authenticates.
 * On the main thread that froze the UI (ANR) — and with it any WebView, whose browser side shares
 * the UI thread.
 */
internal suspend inline fun <reified T> CornGuardConvexClient.queryOnce(
    name: String,
    args: Map<String, Any?> = emptyMap()
): T = withContext(Dispatchers.IO) { subscribe<T>(name, args).first().getOrThrow() }

/**
 * Live query as a Flow. Like the Firestore snapshot listeners this replaces, a failed update
 * emits [fallback] instead of terminating the collector. Subscribes on [Dispatchers.IO] (see
 * [queryOnce]); values are still delivered in the collector's context.
 */
internal inline fun <reified T> CornGuardConvexClient.observe(
    name: String,
    args: Map<String, Any?>,
    fallback: T
): Flow<T> = subscribe<T>(name, args).map { it.getOrDefault(fallback) }.flowOn(Dispatchers.IO)

/** Server messages from `ConvexError` arrive JSON-quoted; strip that for display. */
internal fun Throwable.readableConvexMessage(): String {
    val raw = message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    return raw.removeSurrounding("\"").ifBlank { "Something went wrong. Please try again." }
}
