package com.cornguard.app.data.remote.convex

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uploads an image to Convex file storage: ask the backend for a one-time upload URL, POST the
 * bytes there, and return the resulting storage id (passed as `imageId` to the mutation that
 * creates the record or post). Accepts a plain file path or a `content://`/`file://` URI.
 */
class ConvexFileUploader(
    context: Context,
    private val client: CornGuardConvexClient
) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun uploadImage(pathOrUri: String): String? {
        if (pathOrUri.isBlank()) return null
        val bytes = withContext(Dispatchers.IO) { readBytes(pathOrUri) } ?: return null
        val uploadUrl = client.mutation<String>("storage:generateUploadUrl")
        return withContext(Dispatchers.IO) {
            val connection = URL(uploadUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "image/jpeg")
                connection.outputStream.use { it.write(bytes) }
                check(connection.responseCode in 200..299) { "Image upload failed (HTTP ${connection.responseCode})" }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).getString("storageId")
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun readBytes(pathOrUri: String): ByteArray? =
        if (pathOrUri.contains("://")) {
            resolver.openInputStream(Uri.parse(pathOrUri))?.use { it.readBytes() }
        } else {
            File(pathOrUri).takeIf { it.exists() }?.readBytes()
        }
}
