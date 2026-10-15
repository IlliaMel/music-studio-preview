package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.credits.Credits
import com.illiamelnyk.studio.core.domain.playlist.Playlist
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.track.Track
import java.time.LocalDate
import java.time.ZoneOffset

internal data class AccountSnapshot(
    val session: Session,
    val token: String?,
    val tracks: List<Track> = emptyList(),
    val likes: Set<String> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val credits: Credits = Credits(25, 25),
    val creditDay: String = utcDay(),
)

internal fun utcDay(): String = LocalDate.now(ZoneOffset.UTC).toString()
