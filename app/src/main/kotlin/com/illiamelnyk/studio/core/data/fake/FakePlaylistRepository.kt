package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playlist.Playlist
import com.illiamelnyk.studio.core.domain.playlist.PlaylistRepository
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Temporary in-memory playlists, replaced by the backend repository when connected. */
class FakePlaylistRepository : PlaylistRepository {
    private val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val nextId = AtomicInteger(1)

    override fun observeAll(): Flow<List<Playlist>> = playlists.asStateFlow()

    override suspend fun clear() {
        playlists.value = emptyList()
    }

    override suspend fun create(name: String): Outcome<Playlist> {
        val trimmed = name.trim()
        if (trimmed.length !in 1..MAX_NAME_LENGTH) return Outcome.Failure(AppError.InvalidPrompt(null))
        val playlist = Playlist("playlist-${nextId.getAndIncrement()}", trimmed)
        playlists.update { it + playlist }
        return Outcome.Success(playlist)
    }

    override suspend fun addTrack(playlistId: String, track: Track): Outcome<Unit> = edit(playlistId) { playlist ->
        if (playlist.tracks.any { it.id == track.id }) playlist else playlist.copy(tracks = playlist.tracks + track)
    }

    override suspend fun removeTrack(playlistId: String, trackId: TrackId): Outcome<Unit> = edit(playlistId) { playlist ->
        playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId })
    }

    private fun edit(id: String, transform: (Playlist) -> Playlist): Outcome<Unit> {
        if (playlists.value.none { it.id == id }) return Outcome.Failure(AppError.Unknown)
        playlists.update { list -> list.map { if (it.id == id) transform(it) else it } }
        return Outcome.Success(Unit)
    }

    private companion object {
        const val MAX_NAME_LENGTH = 80
    }
}
