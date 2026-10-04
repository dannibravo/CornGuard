package com.cornguard.app.data.remote.convex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal client for Convex's HTTP function API (`POST <deployment>/api/action`). Used only for
 * the auth handshake (`auth:signIn` / `auth:signOut`), which must run before — or independently
 * of — the authenticated WebSocket client in [ConvexProvider].
 */
internal class ConvexHttp(private val deploymentUrl: String) {

    class CallException(message: String, val serverMessage: String) : Exception(message)

    suspend fun action(path: String, args: JSONObject, bearerToken: String? = null): Any? =
        withContext(Dispatchers.IO) {
            val connection = URL("$deploymentUrl/api/action").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Content-Type", "application/json")
                bearerToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }

                val body = JSONObject().put("path", path).put("args", args).put("format", "json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }

                val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
                val response = JSONObject(stream.bufferedReader().use { it.readText() })
                if (response.optString("status") != "success") {
                    val serverMessage = response.optString("errorMessage")
                    val data = response.opt("errorData")?.toString()
                    throw CallException(data ?: serverMessage.lineSequence().first(), serverMessage)
                }
                response.opt("value")
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}
