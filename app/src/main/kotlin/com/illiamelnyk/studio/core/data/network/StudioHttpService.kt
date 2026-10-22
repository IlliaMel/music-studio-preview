package com.illiamelnyk.studio.core.data.network

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Central, typed HTTP surface. Paths and verbs do not belong to feature repositories. */
internal interface StudioHttpService {
    @GET("v1/me")
    suspend fun profile(@Header("Authorization") auth: String): Response<ProfileDto>

    @PATCH("v1/me")
    suspend fun updateProfile(@Header("Authorization") auth: String, @Body body: ProfileUpdateDto): Response<ProfileDto>

    @DELETE("v1/me")
    suspend fun deleteAccount(@Header("Authorization") auth: String): Response<Unit>

    @GET("v1/me/quota")
    suspend fun quota(@Header("Authorization") auth: String): Response<QuotaDto>

    @GET("v1/capabilities")
    suspend fun capabilities(@Header("Authorization") auth: String): Response<CapabilitiesDto>

    @GET("v1/catalog")
    suspend fun catalog(@Header("Authorization") auth: String): Response<CatalogDto>

    @GET("v1/tracks")
    suspend fun tracks(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): Response<TracksPageDto>

    @GET("v1/tracks/{id}")
    suspend fun track(@Header("Authorization") auth: String, @Path("id") id: String): Response<TrackDto>

    @POST("v1/tracks")
    suspend fun createTracks(
        @Header("Authorization") auth: String,
        @Header("Idempotency-Key") key: String,
        @Body body: CreateTrackDto,
    ): Response<CreatedTracksDto>

    @PATCH("v1/tracks/{id}")
    suspend fun renameTrack(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: TitleDto,
    ): Response<TrackDto>

    @DELETE("v1/tracks/{id}")
    suspend fun deleteTrack(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>

    @GET("v1/likes")
    suspend fun likes(@Header("Authorization") auth: String): Response<LikesDto>

    @PUT("v1/likes/{id}")
    suspend fun like(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>

    @DELETE("v1/likes/{id}")
    suspend fun unlike(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>

    @GET("v1/playlists")
    suspend fun playlists(@Header("Authorization") auth: String): Response<PlaylistsDto>

    @POST("v1/playlists")
    suspend fun createPlaylist(
        @Header("Authorization") auth: String,
        @Body body: PlaylistNameDto,
    ): Response<PlaylistDto>

    @PATCH("v1/playlists/{id}")
    suspend fun renamePlaylist(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: PlaylistNameDto,
    ): Response<PlaylistDto>

    @DELETE("v1/playlists/{id}")
    suspend fun deletePlaylist(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>

    @PUT("v1/playlists/{playlist}/tracks/{track}")
    suspend fun addPlaylistTrack(
        @Header("Authorization") auth: String,
        @Path("playlist") playlist: String,
        @Path("track") track: String,
    ): Response<Unit>

    @DELETE("v1/playlists/{playlist}/tracks/{track}")
    suspend fun removePlaylistTrack(
        @Header("Authorization") auth: String,
        @Path("playlist") playlist: String,
        @Path("track") track: String,
    ): Response<Unit>

    @GET("v1/recordings")
    suspend fun recordings(@Header("Authorization") auth: String): Response<RecordingsDto>

    @PUT("v1/recordings/{id}")
    suspend fun putRecording(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Header("X-Recording-Title") title: String,
        @Header("X-Recording-Duration-Ms") duration: Long,
        @Body audio: RequestBody,
    ): Response<RecordingDto>

    @PATCH("v1/recordings/{id}")
    suspend fun renameRecording(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: TitleDto,
    ): Response<RecordingDto>

    @DELETE("v1/recordings/{id}")
    suspend fun deleteRecording(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>

    @POST("v1/uploads")
    suspend fun upload(@Header("Authorization") auth: String, @Body audio: RequestBody): Response<UploadDto>

    @DELETE("v1/uploads/{id}")
    suspend fun deleteUpload(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>
}
