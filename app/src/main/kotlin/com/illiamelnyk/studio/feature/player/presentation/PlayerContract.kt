package com.illiamelnyk.studio.feature.player.presentation

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.ui.components.LyricLine
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The full player, in two parts so that playback ticking never recomposes the screen: [content]
 * changes when the track, a control or a panel does; [clock] changes four times a second and is
 * read only where the seek bar and the lyrics draw.
 */
@Immutable
data class PlayerState(val content: PlayerContent = PlayerContent(), val clock: PlaybackClock = PlaybackClock())

/** Where playback is in the current track. */
@Immutable
data class PlaybackClock(val positionMs: Long = 0, val durationMs: Long = 0) {
    /** How far into the track, from 0 to 1. */
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Everything the full player shows except the clock.
 *
 * @param track `null` when nothing is loaded.
 * @param isStation a station plays without end: it shows LIVE and a stop button, nothing to seek,
 *   like, share or queue.
 * @param repeat whether the queue repeats; the controls have one switch for it, so repeating one
 *   track shows as on too.
 * @param showLyrics the lyrics in place of the cover, when the track has them.
 * @param sheet the sheet open over the player, if any.
 */
@Immutable
data class PlayerContent(
    val track: NowPlayingUi? = null,
    val isPlaying: Boolean = false,
    val isStation: Boolean = false,
    val isLiked: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
    val lyrics: LyricsUi = LyricsUi.Loading,
    val showLyrics: Boolean = false,
    val upNext: ImmutableList<TrackUi> = persistentListOf(),
    val sheet: PlayerSheet? = null,
) {
    /** Only synced lyrics are shown, and the lyrics button is there only when there are some. */
    val hasLyrics: Boolean get() = lyrics is LyricsUi.Synced
}

/** The lyrics of the current track. */
@Immutable
sealed interface LyricsUi {
    data object Loading : LyricsUi

    /** An instrumental, or a song whose lyrics aren't available. */
    data object None : LyricsUi

    /** Lines with the time of every word, for the synced view. */
    data class Synced(val lines: ImmutableList<LyricLine>) : LyricsUi
}

/** A sheet over the player. */
@Immutable
sealed interface PlayerSheet {
    data object Queue : PlayerSheet

    data class ComingSoon(val feature: SoonFeature) : PlayerSheet
}

/** What the player offers before the app can do it. */
enum class SoonFeature {
    /** A new version of the track through the Music API. */
    Remix,

    /** Playlists live in Library, which the player can't reach yet. */
    AddToPlaylist,

    /** Saving the audio file: needs real audio. */
    Download,

    /** More tracks like these once the queue ends: needs recommendations from the catalog. */
    Autoplay,
}

sealed interface PlayerIntent {
    data object CollapseClicked : PlayerIntent

    data object PlayPauseClicked : PlayerIntent

    /** Stops a station; there is nothing to resume afterwards. */
    data object StopClicked : PlayerIntent

    data object NextClicked : PlayerIntent

    data object PreviousClicked : PlayerIntent

    data class ShuffleChanged(val enabled: Boolean) : PlayerIntent

    data class RepeatChanged(val enabled: Boolean) : PlayerIntent

    /** The seek bar was dragged or tapped at [fraction] of the track. */
    data class SeekRequested(val fraction: Float) : PlayerIntent

    /** A line of the lyrics was tapped: playback goes to where it starts. */
    data class LyricLineClicked(val startMs: Long) : PlayerIntent

    /** Shows the lyrics in place of the cover, or the cover again. */
    data object LyricsClicked : PlayerIntent

    data object QueueClicked : PlayerIntent

    /** A track of "Playing next" was tapped, the [position]th after the current one, from 0. */
    data class UpNextTrackClicked(val position: Int) : PlayerIntent

    /** "Clear" in the queue: only the current track stays. */
    data object ClearUpNextClicked : PlayerIntent

    data object SheetDismissed : PlayerIntent

    data object LikeClicked : PlayerIntent

    data object ShareClicked : PlayerIntent

    data class ComingSoonClicked(val feature: SoonFeature) : PlayerIntent

    /** "I want this" on a coming-soon sheet. */
    data object WantItClicked : PlayerIntent
}

sealed interface PlayerEffect {
    /** Back to the mini player. */
    data object Collapse : PlayerEffect

    /**
     * Opens the system share sheet with [text], and with the track's MP3 at [audio] when there is
     * one: a `content://` URI, such as a FileProvider gives for a downloaded file.
     */
    data class Share(val text: UiText, val audio: Uri? = null) : PlayerEffect

    data class ShowToast(val toast: PlayerToastUi) : PlayerEffect
}

/** A message the player shows for a moment. */
@Immutable
data class PlayerToastUi(val title: UiText, val message: UiText, val kind: StudioToastKind = StudioToastKind.Info)
