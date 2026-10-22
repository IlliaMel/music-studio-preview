package com.illiamelnyk.studio.core.data.local

import android.content.Context
import android.net.Uri
import com.illiamelnyk.studio.core.data.network.CapabilitiesDto
import com.illiamelnyk.studio.core.data.network.CatalogDto
import com.illiamelnyk.studio.core.data.network.CreateTrackDto
import com.illiamelnyk.studio.core.data.network.LikesDto
import com.illiamelnyk.studio.core.data.network.PlaylistDto
import com.illiamelnyk.studio.core.data.network.ProfileDto
import com.illiamelnyk.studio.core.data.network.ProfileUpdateDto
import com.illiamelnyk.studio.core.data.network.QuotaDto
import com.illiamelnyk.studio.core.data.network.RecordingDto
import com.illiamelnyk.studio.core.data.network.TrackDto
import com.illiamelnyk.studio.core.data.network.TracksPageDto
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.feature.listen.data.CatalogDocument
import kotlinx.serialization.json.Json
import org.json.JSONObject

/** Test-fixture adapter only: legacy test documents implement the typed API without a production router. */
internal open class JsonFixtureStudioApi(
    context: Context,
    configuration: ApiConfiguration = ApiConfiguration("http://10.0.2.2:8787", ApiAuthMode.Development, true),
    bearerTokens: BearerTokenProvider = UnavailableBearerTokenProvider,
) : StudioApi(context, configuration, bearerTokens) {
    override val isAvailable: Boolean = true
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    protected open suspend fun requestFixture(
        method: String,
        path: String,
        token: String,
        body: JSONObject? = null,
        key: String? = null,
    ): JSONObject = error("No fixture for $method $path")
    private inline fun <reified T> decode(document: JSONObject): T = json.decodeFromString(document.toString())
    private inline fun <reified T> document(value: T): JSONObject =
        JSONObject(json.encodeToString(kotlinx.serialization.serializer<T>(), value))
    internal override suspend fun getProfile(token: String): ProfileDto = decode(requestFixture("GET", "/v1/me", token))
    internal override suspend fun updateProfile(token: String, update: ProfileUpdateDto): ProfileDto =
        decode(requestFixture("PATCH", "/v1/me", token, document(update)))
    internal override suspend fun deleteAccount(token: String) {
        requestFixture("DELETE", "/v1/me", token)
    }
    internal override suspend fun getQuota(token: String): QuotaDto =
        decode(requestFixture("GET", "/v1/me/quota", token))
    internal override suspend fun getCapabilities(token: String): CapabilitiesDto =
        decode(requestFixture("GET", "/v1/capabilities", token))
    internal override suspend fun getCatalog(token: String): CatalogDto {
        val response = requestFixture("GET", "/v1/catalog", token)
        return try {
            CatalogDocument.decode(response.toString())
        } catch (
            _: IllegalArgumentException,
        ) {
            throw ApiFailure(AppError.Server)
        }
    }
    internal override suspend fun listTracks(token: String, cursor: String?): TracksPageDto = decode(
        requestFixture(
            "GET",
            "/v1/tracks?limit=50" + (cursor?.let { "&cursor=" + Uri.encode(it) } ?: ""),
            token,
        ),
    )
    internal override suspend fun getTrack(token: String, id: TrackId): TrackDto = decode(
        requestFixture(
            "GET",
            "/v1/tracks/" + Uri.encode(id.value),
            token,
        ),
    )
    internal override suspend fun createTracks(
        token: String,
        payload: CreateTrackDto,
        idempotencyKey: String,
    ): List<TrackDto> {
        val response = requestFixture("POST", "/v1/tracks", token, document(payload), idempotencyKey)
        return response.optJSONArray("tracks")?.objects()?.map { decode<TrackDto>(it) } ?: listOf(decode(response))
    }
    internal override suspend fun renameTrack(token: String, id: TrackId, title: String) {
        requestFixture("PATCH", trackResourcePath(id), token, JSONObject().put("title", title))
    }
    internal override suspend fun deleteTrack(token: String, id: TrackId) {
        requestFixture("DELETE", trackResourcePath(id), token)
    }
    internal override suspend fun listLikes(token: String): List<String> =
        decode<LikesDto>(requestFixture("GET", "/v1/likes", token)).trackIds
    internal override suspend fun setLiked(token: String, id: TrackId, liked: Boolean) {
        requestFixture(
            if (liked) "PUT" else "DELETE",
            "/v1/likes/" + Uri.encode(id.value),
            token,
        )
    }
    private fun playlistFixture(document: JSONObject): PlaylistDto = decode(
        JSONObject(document.toString()).apply {
            if (!has("createdAt")) put("createdAt", "2026-10-16T00:00:00Z")
        },
    )
    internal override suspend fun listPlaylists(token: String): List<PlaylistDto> =
        requestFixture("GET", "/v1/playlists", token).array("playlists").objects().map(::playlistFixture)
    internal override suspend fun createPlaylist(token: String, name: String): PlaylistDto =
        playlistFixture(requestFixture("POST", "/v1/playlists", token, JSONObject().put("name", name)))
    internal override suspend fun setPlaylistTrack(
        token: String,
        playlistId: String,
        trackId: TrackId,
        included: Boolean,
    ) {
        requestFixture(
            if (included) "PUT" else "DELETE",
            "/v1/playlists/" + Uri.encode(playlistId) + "/tracks/" + Uri.encode(trackId.value),
            token,
        )
    }
    private fun recordingFixture(document: JSONObject): RecordingDto = decode(
        JSONObject(document.toString()).apply {
            if (!has("updatedAt")) put("updatedAt", getString("createdAt"))
        },
    )
    internal override suspend fun listRecordings(token: String): List<RecordingDto> =
        requestFixture("GET", "/v1/recordings", token).array("recordings").objects().map(::recordingFixture)
    internal override suspend fun putRecording(
        id: String,
        uri: String,
        title: String,
        durationMs: Long,
        token: String,
    ): RecordingDto = recordingFixture(putRecordingFixture(id, uri, title, durationMs, token))
    protected open suspend fun putRecordingFixture(
        id: String,
        uri: String,
        title: String,
        durationMs: Long,
        token: String,
    ): JSONObject = error("No recording fixture")
}

/** JSON appears only at this compatibility-fixture boundary, never in production capability parsing. */
internal fun GenerationCapabilities.Companion.parse(document: JSONObject): GenerationCapabilities =
    GenerationCapabilities.from(
        Json {
            ignoreUnknownKeys = true
        }.decodeFromString(CapabilitiesDto.serializer(), document.toString()),
    )
