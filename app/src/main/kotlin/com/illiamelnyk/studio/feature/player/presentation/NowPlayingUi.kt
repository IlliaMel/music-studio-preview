package com.illiamelnyk.studio.feature.player.presentation

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId

/**
 * What the players show of what plays: a track's title and artist, or a station's name and the
 * track it plays now.
 *
 * @param id the track that plays.
 * @param artistVerified shows the verified mark after the artist. [Track] doesn't say yet whether its
 *   artist is verified, so tracks map to `false` until it does.
 */
@Immutable
data class NowPlayingUi(
    val id: TrackId,
    val title: String,
    val subtitle: String,
    val coverSeed: String,
    val artistVerified: Boolean = false,
)

fun Track.toNowPlaying(): NowPlayingUi =
    NowPlayingUi(id = id, title = title, subtitle = artist, coverSeed = coverSeed.value)

/** What plays: the current track, or the station that plays it; `null` when nothing is loaded. */
fun PlaybackState.toNowPlaying(): NowPlayingUi? {
    val track = current ?: return null
    val station = stationName ?: return track.toNowPlaying()
    return track.toNowPlaying().copy(title = station, subtitle = track.title)
}
