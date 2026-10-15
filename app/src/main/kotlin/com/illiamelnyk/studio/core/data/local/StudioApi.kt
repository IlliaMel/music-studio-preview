package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.core.net.toUri
import com.illiamelnyk.studio.BuildConfig
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class ApiFailure(val error: AppError, val serverCode: String? = null) : IOException()

open class StudioApi(
    private val context: Context,
    val configuration: ApiConfiguration = ApiConfiguration.fromBuild(
        BuildConfig.API_BASE_URL,
        BuildConfig.API_AUTH_MODE,
        BuildConfig.DEBUG,
    ),
    private val bearerTokens: BearerTokenProvider = UnavailableBearerTokenProvider,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    open val isAvailable: Boolean = configuration.isAvailable
    val baseUrl: String = configuration.baseUrl.trimEnd('/')
    private val resolvedTokens = java.util.concurrent.ConcurrentHashMap<String, FirebaseBearerToken>()

    open suspend fun preflightGeneration(token: String, generation: GenerationRequest, variants: Int) {
        val capabilities = try {
            GenerationCapabilities.parse(request("GET", "/v1/capabilities", token))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ApiFailure) {
            throw failure
        } catch (_: Exception) {
            throw ApiFailure(AppError.Server)
        }
        capabilities.rejection(generation, variants)?.let { throw ApiFailure(it) }
    }

    internal suspend fun authorization(token: String): String {
        if (!isAvailable) throw ApiFailure(AppError.Unauthorized)
        if (configuration.authMode == ApiAuthMode.Development) {
            if (!configuration.debug ||
                !Regex("dev:[a-z0-9-]{1,32}").matches(token)
            ) {
                throw ApiFailure(AppError.Unauthorized)
            }
            return token
        }
        if (configuration.authMode != ApiAuthMode.Firebase ||
            !token.startsWith("firebase:")
        ) {
            throw ApiFailure(AppError.Unauthorized)
        }
        val owner = token.removePrefix("firebase:")
        if (owner.isBlank() || owner.length > 128) throw ApiFailure(AppError.Unauthorized)
        resolvedTokens.remove(owner)
        val bearer = try {
            bearerTokens.getToken(owner)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            throw ApiFailure(AppError.Network)
        }
        if (bearer == null || bearer.firebaseUid != owner || bearer.expiresAtMillis <= nowMillis() + 30_000 ||
            bearer.value.count { it == '.' } != 2 || bearer.value.any(Char::isWhitespace)
        ) {
            resolvedTokens.remove(owner)
            throw ApiFailure(AppError.Unauthorized)
        }
        resolvedTokens[owner] = bearer
        return bearer.value
    }

    internal fun audioHeaders(token: String): Map<String, String> {
        if (!isAvailable) return emptyMap()
        val bearer = when (configuration.authMode) {
            ApiAuthMode.Development -> token.takeIf { configuration.debug && Regex("dev:[a-z0-9-]{1,32}").matches(it) }

            ApiAuthMode.Firebase -> resolvedTokens[token.removePrefix("firebase:")]
                ?.takeIf { token == "firebase:" + it.firebaseUid && it.expiresAtMillis > nowMillis() + 30_000 }?.value

            ApiAuthMode.Disabled -> null
        }
        return bearer?.let { mapOf("Authorization" to "Bearer " + it) }.orEmpty()
    }

    internal fun forgetCredentials() {
        resolvedTokens.clear()
    }

    open suspend fun request(
        method: String,
        path: String,
        token: String,
        body: JSONObject? = null,
        key: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val bearer = authorization(token)
        val connection = openConnection(baseUrl + path)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer " + bearer)
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
        val bearer = authorization(token)
        val parsed = uri.toUri()
        val bytes = readAudio(uri, bearer)
        if (bytes.isEmpty() || bytes.size > MAX_UPLOAD_BYTES) throw ApiFailure(AppError.InvalidPrompt(null))
        val fallback = context.contentResolver.getType(parsed) ?: audioTypeFromPath(uri)
        val type = audioContentType(bytes, fallback)
        if (type !in SUPPORTED_AUDIO_TYPES) throw ApiFailure(AppError.InvalidPrompt(null))
        val connection = openConnection(baseUrl + "/v1/uploads")
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer " + bearer)
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

    open suspend fun putRecording(id: String, uri: String, title: String, durationMs: Long, token: String): JSONObject =
        withContext(Dispatchers.IO) {
            val bearer = authorization(token)
            val parsed = uri.toUri()
            val bytes = readAudio(uri, bearer)
            if (bytes.isEmpty() || bytes.size > MAX_UPLOAD_BYTES) throw ApiFailure(AppError.InvalidReference)
            val type = audioContentType(bytes, context.contentResolver.getType(parsed) ?: audioTypeFromPath(uri))
            if (type !in setOf("audio/mp4", "audio/wav", "audio/mpeg")) throw ApiFailure(AppError.InvalidReference)
            val connection = openConnection(baseUrl + "/v1/recordings/" + id)
            try {
                connection.requestMethod = "PUT"
                connection.connectTimeout = 5_000
                connection.readTimeout = 30_000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer " + bearer)
                connection.setRequestProperty("Content-Type", type)
                connection.setRequestProperty("X-Recording-Title", android.net.Uri.encode(title))
                connection.setRequestProperty("X-Recording-Duration-Ms", durationMs.toString())
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                response(connection)
            } catch (failure: ApiFailure) {
                throw failure
            } catch (_: SocketTimeoutException) {
                throw ApiFailure(AppError.Timeout)
            } catch (_: IOException) {
                throw ApiFailure(AppError.Network)
            } finally {
                connection.disconnect()
            }
        }

    private fun readAudio(uri: String, bearer: String): ByteArray {
        fun bounded(input: java.io.InputStream): ByteArray = input.use {
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
        }
        val source = java.net.URI(uri)
        if (source.scheme !in setOf("http", "https")) {
            return bounded(
                context.contentResolver.openInputStream(uri.toUri()) ?: throw ApiFailure(AppError.InvalidReference),
            )
        }
        val origin = java.net.URI(baseUrl)
        if (source.scheme != origin.scheme || source.host != origin.host || source.port != origin.port ||
            source.rawUserInfo != null || source.rawQuery != null || source.rawFragment != null ||
            !Regex("/v1/recordings/[0-9a-f-]{36}/audio").matches(source.rawPath.orEmpty())
        ) {
            throw ApiFailure(AppError.InvalidReference)
        }
        val connection = openConnection(uri)
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer " + bearer)
            if (connection.responseCode != 200) {
                response(connection)
                throw ApiFailure(AppError.InvalidReference)
            }
            return bounded(connection.inputStream)
        } catch (failure: ApiFailure) {
            throw failure
        } catch (_: SocketTimeoutException) {
            throw ApiFailure(AppError.Timeout)
        } catch (_: IOException) {
            throw ApiFailure(AppError.Network)
        } finally {
            connection.disconnect()
        }
    }

    protected open fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply { instanceFollowRedirects = false }

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
                serverCode = error.stringOrNull("code"),
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
