package com.illiamelnyk.studio.core.domain.playlist

import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import kotlinx.coroutines.flow.Flow

/** A local demo playlist, ordered as tracks were added. */
data class Playlist(val id: String, val name: String, val tracks: List<Track> = emptyList())

/** Shared by Library, collection menus and the player's Add To sheet. */
interface PlaylistRepository {
    fun observeAll(): Flow<List<Playlist>>

    /** A trimmed name must contain 1 to 80 characters. */
    suspend fun create(name: String): Outcome<Playlist>

    /** Adding an existing member is idempotent. */
    suspend fun addTrack(playlistId: String, track: Track): Outcome<Unit>

    suspend fun removeTrack(playlistId: String, trackId: TrackId): Outcome<Unit>
}
