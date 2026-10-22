package com.illiamelnyk.studio.core.data.network

import com.illiamelnyk.studio.core.data.local.ApiFailure
import com.illiamelnyk.studio.core.domain.AppError
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Direct typed HTTP calls with shared error classification, cancellation and transport policy. */
internal class RetrofitStudioTransport(baseUrl: String, callFactory: Call.Factory? = null) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    internal val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    // Reads may go again over a fresh connection. The server closes idle keep-alive connections after
    // a few seconds, so the first read after a pause, such as the capabilities on resume, met a closed
    // pooled connection and failed as a network error. Writes keep no retry: a paid request is sent once.
    private val reads = client.newBuilder().retryOnConnectionFailure(true).build()
    internal val calls: Call.Factory = callFactory ?: Call.Factory { request ->
        (if (request.method == "GET") reads else client).newCall(request)
    }
    private val service = Retrofit.Builder()
        .baseUrl(baseUrl.trimEnd('/') + "/")
        .callFactory(calls)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(StudioHttpService::class.java)

    suspend fun profile(bearer: String) = classified { service.profile(auth(bearer)).checked() }
    suspend fun updateProfile(bearer: String, update: ProfileUpdateDto) =
        classified { service.updateProfile(auth(bearer), update).checked() }
    suspend fun deleteAccount(bearer: String) = classified { service.deleteAccount(auth(bearer)).unit() }
    suspend fun quota(bearer: String) = classified { service.quota(auth(bearer)).checked() }
    suspend fun capabilities(bearer: String) = classified { service.capabilities(auth(bearer)).checked() }
    suspend fun catalog(bearer: String) = classified { service.catalog(auth(bearer)).checked() }
    suspend fun tracks(bearer: String, cursor: String?) =
        classified { service.tracks(auth(bearer), 50, cursor).checked() }
    suspend fun track(bearer: String, id: String) = classified { service.track(auth(bearer), id).checked() }
    suspend fun createTracks(bearer: String, key: String, payload: CreateTrackDto) =
        classified { service.createTracks(auth(bearer), key, payload).checked().tracks }
    suspend fun renameTrack(bearer: String, id: String, title: String) =
        classified { service.renameTrack(auth(bearer), id, TitleDto(title)).checked() }
    suspend fun deleteTrack(bearer: String, id: String) = classified { service.deleteTrack(auth(bearer), id).unit() }
    suspend fun likes(bearer: String) = classified { service.likes(auth(bearer)).checked().trackIds }
    suspend fun setLiked(bearer: String, id: String, liked: Boolean) =
        classified { if (liked) service.like(auth(bearer), id).unit() else service.unlike(auth(bearer), id).unit() }
    suspend fun playlists(bearer: String) = classified { service.playlists(auth(bearer)).checked().playlists }
    suspend fun createPlaylist(bearer: String, name: String) =
        classified { service.createPlaylist(auth(bearer), PlaylistNameDto(name)).checked() }
    suspend fun setPlaylistTrack(bearer: String, playlistId: String, trackId: String, included: Boolean) = classified {
        if (included) {
            service.addPlaylistTrack(auth(bearer), playlistId, trackId).unit()
        } else {
            service.removePlaylistTrack(auth(bearer), playlistId, trackId).unit()
        }
    }
    suspend fun recordings(bearer: String) = classified { service.recordings(auth(bearer)).checked().recordings }
    suspend fun renameRecording(bearer: String, id: String, title: String) =
        classified { service.renameRecording(auth(bearer), id, TitleDto(title)).checked() }
    suspend fun deleteRecording(bearer: String, id: String) =
        classified { service.deleteRecording(auth(bearer), id).unit() }
    private fun auth(bearer: String) = "Bearer " + bearer

    suspend fun upload(bytes: ByteArray, type: String, bearer: String): String = classified {
        service.upload("Bearer $bearer", bytes.toRequestBody(type.toMediaType())).checked().id
    }

    suspend fun recording(
        id: String,
        bytes: ByteArray,
        type: String,
        title: String,
        duration: Long,
        bearer: String,
    ): RecordingDto = classified {
        service.putRecording(
            "Bearer $bearer",
            id,
            android.net.Uri.encode(title),
            duration,
            bytes.toRequestBody(type.toMediaType()),
        ).checked()
    }

    private fun Response<Unit>.unit() {
        if (!isSuccessful) throw failure(this)
    }

    private fun <T> Response<T>.checked(): T {
        if (!isSuccessful) throw failure(this)
        return body() ?: throw ApiFailure(AppError.Server)
    }

    internal fun failure(response: Response<*>): ApiFailure {
        val error = response.errorBody()?.use { body ->
            runCatching {
                val source = body.source()
                source.request(64 * 1024L + 1)
                if (source.buffer.size > 64 * 1024L) return@runCatching null
                json.decodeFromString<ErrorEnvelopeDto>(source.readUtf8()).error
            }.getOrNull()
        }
        return statusFailure(response.code(), error)
    }

    internal fun statusFailure(status: Int, error: ApiErrorDto?): ApiFailure = ApiFailure(
        when (status) {
            401 -> AppError.Unauthorized

            403 -> if (error?.code == "GENERATION_NOT_ALLOWED") AppError.GenerationNotAllowed else AppError.Unauthorized

            429 -> when (error?.code) {
                "SERVICE_DAILY_LIMIT" -> AppError.ServiceDailyLimit
                "DAILY_LIMIT" -> AppError.QuotaExceeded
                "QUOTA_EXCEEDED", "PROVIDER_AUTH", "PROVIDER_UNAVAILABLE" -> AppError.ProviderUnavailable
                else -> AppError.RateLimited
            }

            else -> serverAppError(error?.code.orEmpty(), error?.promptSuggestion)
        },
        error?.code,
    )

    private suspend fun <T> classified(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ApiFailure) {
        throw failure
    } catch (_: SocketTimeoutException) {
        throw ApiFailure(AppError.Timeout)
    } catch (_: SerializationException) {
        throw ApiFailure(AppError.Server)
    } catch (_: IOException) {
        throw ApiFailure(AppError.Network)
    } catch (_: IllegalArgumentException) {
        throw ApiFailure(AppError.Server)
    }
}
