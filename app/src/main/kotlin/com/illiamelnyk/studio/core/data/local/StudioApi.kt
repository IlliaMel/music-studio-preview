package com.illiamelnyk.studio.core.data.local

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.net.toUri
import com.illiamelnyk.studio.core.domain.AppError
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class ApiFailure(val error: AppError) : IOException()

open class StudioApi(private val context: Context) {
    open val isAvailable: Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val baseUrl: String = "http://10.0.2.2:8787"

    open suspend fun request(
        method: String,
        path: String,
        token: String,
        body: JSONObject? = null,
        key: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        if (!isAvailable) throw ApiFailure(AppError.Unauthorized)
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer " + token)
            connection.setRequestProperty("Accept", "application/json")
            key?.let { connection.setRequestProperty("Idempotency-Key", it) }
            body?.let {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { stream -> stream.write(it.toString().toByteArray(Charsets.UTF_8)) }
            }
            response(connection)
        } catch (error: ApiFailure) {
            throw error
        } catch (_: SocketTimeoutException) {
            throw ApiFailure(AppError.Timeout)
        } catch (_: IOException) {
            throw ApiFailure(AppError.Network)
        } finally {
            connection.disconnect()
        }
    }

    open suspend fun upload(uri: String, token: String): String = withContext(Dispatchers.IO) {
        if (!isAvailable) throw ApiFailure(AppError.Unauthorized)
        val parsed = uri.toUri()
        val bytes = context.contentResolver.openInputStream(parsed)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (total <= MAX_UPLOAD_BYTES) {
                val count = input.read(buffer, 0, minOf(buffer.size, MAX_UPLOAD_BYTES + 1 - total))
                if (count < 0) break
                output.write(buffer, 0, count)
                total += count
            }
            output.toByteArray()
        } ?: throw ApiFailure(AppError.InvalidPrompt(null))
        if (bytes.isEmpty() || bytes.size > MAX_UPLOAD_BYTES) throw ApiFailure(AppError.InvalidPrompt(null))
        val fallback = context.contentResolver.getType(parsed) ?: audioTypeFromPath(uri)
        val type = audioContentType(bytes, fallback)
        if (type !in SUPPORTED_AUDIO_TYPES) throw ApiFailure(AppError.InvalidPrompt(null))
        val connection = URL(baseUrl + "/v1/uploads").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer " + token)
            connection.setRequestProperty("Content-Type", type)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            response(connection).getString("id")
        } catch (error: ApiFailure) {
            throw error
        } catch (_: SocketTimeoutException) {
            throw ApiFailure(AppError.Timeout)
        } catch (_: IOException) {
            throw ApiFailure(AppError.Network)
        } finally {
            connection.disconnect()
        }
    }

    private fun response(connection: HttpURLConnection): JSONObject {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use {
            it.readText()
        }.orEmpty()
        val json = if (text.isBlank()) JSONObject() else JSONObject(text)
        if (status !in 200..299) {
            val error = json.optJSONObject("error") ?: json
            throw ApiFailure(
                when (status) {
                    401, 403 -> AppError.Unauthorized

                    429 -> {
                        if (error.optString("code") in listOf("QUOTA_EXCEEDED", "DAILY_LIMIT")) {
                            AppError.QuotaExceeded
                        } else {
                            AppError.RateLimited
                        }
                    }

                    else -> apiError(error.optString("code"), error.stringOrNull("promptSuggestion"))
                },
            )
        }
        return json
    }

    private companion object {
        const val MAX_UPLOAD_BYTES = 20 * 1024 * 1024
        val SUPPORTED_AUDIO_TYPES = setOf(
            "audio/wav",
            "audio/mp4",
            "audio/mpeg",
            "audio/aac",
            "audio/ogg",
            "audio/webm",
        )
    }
}
