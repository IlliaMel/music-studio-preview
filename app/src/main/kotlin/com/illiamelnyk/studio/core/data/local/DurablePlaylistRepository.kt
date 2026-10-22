package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playlist.Playlist
import com.illiamelnyk.studio.core.domain.playlist.PlaylistRepository
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Account-scoped PlaylistRepository implementation. All mutations share the owner transaction boundary. */
internal class DurablePlaylistRepository(private val runtime: StudioAccountRuntime) : PlaylistRepository {
    override fun observeAll(): Flow<List<Playlist>> = combine(runtime.state, runtime.publicCatalogTracks) {
            account,
            catalog,
        ->
        account?.playlists.orEmpty().map { playlist ->
            playlist.copy(
                tracks = playlist.tracks.map { track ->
                    account?.tracks?.find {
                        it.id == track.id
                    } ?: catalog.find { it.id == track.id }?.let(runtime::catalogTrack) ?: track
                },
            )
        }
    }.distinctUntilChanged()

    override suspend fun create(name: String): Outcome<Playlist> = runtime.operation {
        val account = runtime.requireAccount()
        if (name.trim().length !in 1..80) throw ApiFailure(AppError.InvalidPrompt(null))
        val playlist = if (account.token != null) {
            val json = runtime.api.createPlaylist(account.token, name.trim())
            Playlist(json.id, json.name)
        } else {
            Playlist(UUID.randomUUID().toString(), name.trim())
        }
        runtime.save(account.copy(playlists = account.playlists + playlist))
        playlist
    }

    override suspend fun addTrack(playlistId: String, track: Track): Outcome<Unit> =
        editPlaylist(playlistId, track.id, add = true)

    override suspend fun removeTrack(playlistId: String, trackId: TrackId): Outcome<Unit> =
        editPlaylist(playlistId, trackId, add = false)

    private suspend fun editPlaylist(id: String, trackId: TrackId, add: Boolean): Outcome<Unit> = runtime.operation {
        val account = runtime.requireAccount()
        if (account.playlists.none { it.id == id }) throw ApiFailure(AppError.Unknown)
        val current = if (add) {
            (
                account.tracks.find { it.id == trackId }
                    ?: runtime.publicCatalogTracks.value.find { it.id == trackId }?.let(runtime::catalogTrack)
                )
                ?.takeIf { it.status == TrackStatus.Ready }
                ?: throw ApiFailure(AppError.InvalidReference)
        } else {
            null
        }
        if (account.token != null && !trackId.value.startsWith("recording-")) {
            runtime.api.setPlaylistTrack(account.token, id, trackId, add)
        }
        runtime.save(
            account.copy(
                playlists = account.playlists.map { playlist ->
                    if (playlist.id != id) {
                        playlist
                    } else if (current != null) {
                        val members = playlist.tracks.map { if (it.id == trackId) current else it }
                        playlist.copy(tracks = if (members.any { it.id == trackId }) members else members + current)
                    } else {
                        playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId })
                    }
                },
            ),
        )
    }

    override suspend fun clear() {
        runtime.operation { runtime.save(runtime.requireAccount().copy(playlists = emptyList())) }
    }
}
