package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.core.net.toUri
import com.illiamelnyk.studio.BuildConfig
import com.illiamelnyk.studio.core.data.network.CapabilitiesDto
import com.illiamelnyk.studio.core.data.network.CatalogDto
import com.illiamelnyk.studio.core.data.network.CreateTrackDto
import com.illiamelnyk.studio.core.data.network.PlaylistDto
import com.illiamelnyk.studio.core.data.network.ProfileDto
import com.illiamelnyk.studio.core.data.network.ProfileUpdateDto
import com.illiamelnyk.studio.core.data.network.QuotaDto
import com.illiamelnyk.studio.core.data.network.RecordingDto
import com.illiamelnyk.studio.core.data.network.RetrofitStudioTransport
import com.illiamelnyk.studio.core.data.network.TrackDto
import com.illiamelnyk.studio.core.data.network.TracksPageDto
import com.illiamelnyk.studio.core.data.network.awaitBoundedBody
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request

internal class ApiFailure(val error: AppError, val serverCode: String? = null) : IOException()

open class StudioApi(
    private val context: Context,
    val configuration: ApiConfiguration = ApiConfiguration.fromBuild(
        BuildConfig.API_BASE_URL,
        BuildConfig.API_AUTH_MODE,
        BuildConfig.DEBUG,
        BuildConfig.API_ALLOW_LAN_DEVELOPMENT,
        BuildConfig.FLAVOR,
    ),
    private val bearerTokens: BearerTokenProvider = UnavailableBearerTokenProvider,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    callFactory: Call.Factory? = null,
) {
    open val isAvailable: Boolean = configuration.isAvailable
    val baseUrl: String = configuration.baseUrl.trimEnd('/')
    private val transport by lazy { RetrofitStudioTransport(baseUrl, callFactory) }
    private val resolvedTokens = java.util.concurrent.ConcurrentHashMap<String, FirebaseBearerToken>()

    open suspend fun preflightGeneration(token: String, generation: GenerationRequest, variants: Int) {
        loadGenerationCapabilities(token).rejection(generation, variants)?.let { throw ApiFailure(it) }
    }

    internal suspend fun loadGenerationCapabilities(token: String): GenerationCapabilities = try {
        GenerationCapabilities.from(getCapabilities(token))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ApiFailure) {
        throw failure
    } catch (_: Exception) {
        throw ApiFailure(AppError.Server)
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
        } catch (missing: FirebaseIdentityMissing) {
            throw ApiFailure(AppError.Unauthorized, "FIREBASE_IDENTITY_MISSING".takeIf { missing.firebaseUid == owner })
        } catch (expired: FirebaseReauthenticationRequired) {
            throw ApiFailure(AppError.Unauthorized, "FIREBASE_REAUTH_REQUIRED".takeIf { expired.firebaseUid == owner })
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

    internal suspend fun confirmFirebaseIdentity(token: String?) {
        if (token?.startsWith("firebase:") != true) return
        val owner = token.removePrefix("firebase:")
        try {
            bearerTokens.confirmIdentity(owner)
        } catch (missing: FirebaseIdentityMissing) {
            throw ApiFailure(AppError.Unauthorized, "FIREBASE_IDENTITY_MISSING".takeIf { missing.firebaseUid == owner })
        } catch (expired: FirebaseReauthenticationRequired) {
            throw ApiFailure(AppError.Unauthorized, "FIREBASE_REAUTH_REQUIRED".takeIf { expired.firebaseUid == owner })
        } catch (_: IOException) {
            throw ApiFailure(AppError.Network)
        }
    }

    internal suspend fun clearClientSession(token: String?) = withContext(NonCancellable) {
        try {
            if (token?.startsWith("firebase:") == true) bearerTokens.clearSession(token.removePrefix("firebase:"))
        } finally {
            forgetCredentials()
        }
    }

    internal open suspend fun getProfile(token: String): ProfileDto = transport.profile(authorization(token))
    internal open suspend fun updateProfile(token: String, update: ProfileUpdateDto): ProfileDto =
        transport.updateProfile(authorization(token), update)
    internal open suspend fun deleteAccount(token: String) = transport.deleteAccount(authorization(token))
    internal open suspend fun getQuota(token: String): QuotaDto = transport.quota(authorization(token))
    internal open suspend fun getCapabilities(token: String): CapabilitiesDto =
        transport.capabilities(authorization(token))
    internal open suspend fun getCatalog(token: String): CatalogDto = transport.catalog(authorization(token))
    internal open suspend fun listTracks(token: String, cursor: String? = null): TracksPageDto =
        transport.tracks(authorization(token), cursor)
    internal open suspend fun getTrack(token: String, id: com.illiamelnyk.studio.core.domain.track.TrackId): TrackDto =
        transport.track(authorization(token), id.value)
    internal open suspend fun createTracks(
        token: String,
        payload: CreateTrackDto,
        idempotencyKey: String,
    ): List<TrackDto> = transport.createTracks(authorization(token), idempotencyKey, payload)
    internal open suspend fun renameTrack(
        token: String,
        id: com.illiamelnyk.studio.core.domain.track.TrackId,
        title: String,
    ) {
        val bearer = authorization(token)
        if (id.value.startsWith("recording-")) {
            transport.renameRecording(bearer, id.value.removePrefix("recording-"), title)
        } else {
            transport.renameTrack(bearer, id.value, title)
        }
    }
    internal open suspend fun deleteTrack(token: String, id: com.illiamelnyk.studio.core.domain.track.TrackId) {
        val bearer = authorization(token)
        if (id.value.startsWith("recording-")) {
            transport.deleteRecording(bearer, id.value.removePrefix("recording-"))
        } else {
            transport.deleteTrack(bearer, id.value)
        }
    }
    internal open suspend fun listLikes(token: String): List<String> = transport.likes(authorization(token))
    internal open suspend fun setLiked(
        token: String,
        id: com.illiamelnyk.studio.core.domain.track.TrackId,
        liked: Boolean,
    ) = transport.setLiked(authorization(token), id.value, liked)
    internal open suspend fun listPlaylists(token: String): List<PlaylistDto> =
        transport.playlists(authorization(token))
    internal open suspend fun createPlaylist(token: String, name: String): PlaylistDto =
        transport.createPlaylist(authorization(token), name)
    internal open suspend fun setPlaylistTrack(
        token: String,
        playlistId: String,
        trackId: com.illiamelnyk.studio.core.domain.track.TrackId,
        included: Boolean,
    ) = transport.setPlaylistTrack(authorization(token), playlistId, trackId.value, included)
    internal open suspend fun listRecordings(token: String): List<RecordingDto> =
        transport.recordings(authorization(token))

    open suspend fun upload(uri: String, token: String): String = withContext(Dispatchers.IO) {
        val bearer = authorization(token)
        val bytes = readAudio(uri, bearer)
        if (bytes.isEmpty() || bytes.size > MAX_UPLOAD_BYTES) throw ApiFailure(AppError.InvalidReference)
        val type = audioContentType(bytes, context.contentResolver.getType(uri.toUri()) ?: audioTypeFromPath(uri))
        if (type !in SUPPORTED_AUDIO_TYPES) throw ApiFailure(AppError.InvalidReference)
        transport.upload(bytes, type, bearer)
    }

    internal open suspend fun putRecording(
        id: String,
        uri: String,
        title: String,
        durationMs: Long,
        token: String,
    ): RecordingDto = withContext(Dispatchers.IO) {
        val bearer = authorization(token)
        val bytes = readAudio(uri, bearer)
        if (bytes.isEmpty() || bytes.size > MAX_UPLOAD_BYTES) throw ApiFailure(AppError.InvalidReference)
        val type = audioContentType(bytes, context.contentResolver.getType(uri.toUri()) ?: audioTypeFromPath(uri))
        if (type !in setOf("audio/mp4", "audio/wav", "audio/mpeg")) throw ApiFailure(AppError.InvalidReference)
        transport.recording(id, bytes, type, title, durationMs, bearer)
    }

    private suspend fun readAudio(uri: String, bearer: String): ByteArray {
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
        try {
            val request = Request.Builder().url(uri).header("Authorization", "Bearer " + bearer).build()
            return transport.calls.newCall(request).awaitBoundedBody(MAX_UPLOAD_BYTES) { response ->
                if (response.code != 200) throw transport.statusFailure(response.code, null)
                if (response.body == null) throw ApiFailure(AppError.InvalidReference)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ApiFailure) {
            throw failure
        } catch (_: SocketTimeoutException) {
            throw ApiFailure(AppError.Timeout)
        } catch (_: IOException) {
            throw ApiFailure(AppError.Network)
        }
    }

    private companion object {
        const val MAX_UPLOAD_BYTES = 20 * 1024 * 1024
        val SUPPORTED_AUDIO_TYPES =
            setOf("audio/wav", "audio/mp4", "audio/mpeg", "audio/aac", "audio/ogg", "audio/webm")
    }
}
