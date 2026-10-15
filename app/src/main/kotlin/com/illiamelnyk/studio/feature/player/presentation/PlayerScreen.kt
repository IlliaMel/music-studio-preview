package com.illiamelnyk.studio.feature.player.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerActions
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerArtwork
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerBackdrop
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerComingSoonSheet
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerLayout
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerQueueContent
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerTopBar
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerTrackInfo
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerTransport

/**
 * The full player on its cover's colors: the top bar, the cover or the lyrics, the track, the
 * controls and the actions, and the sheets over them. [clock] is read only by the parts that show
 * the position, while they draw. Nothing is drawn while nothing is loaded.
 *
 * @param coverModifier an optional decoration for the artwork.
 * @param bodyModifier for the player's body under the sheets, such as the drag that closes it.
 */
@Composable
fun PlayerScreen(
    state: PlayerContent,
    clock: () -> PlaybackClock,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    bodyModifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val track = state.track ?: return
    PlayerBackdrop(track.coverSeed, modifier) {
        PlayerLayout(
            modifier = bodyModifier.padding(contentPadding),
            stationMode = state.isStation,
            queueMode = state.sheet == PlayerSheet.Queue,
            topBar = {
                PlayerTopBar(
                    showActions = !state.isStation,
                    canRemix = state.canRemix,
                    onCollapse = { onIntent(PlayerIntent.CollapseClicked) },
                    onShare = { onIntent(PlayerIntent.ShareClicked) },
                    onAddTo = { onIntent(PlayerIntent.AddToClicked) },
                    onComingSoon = { onIntent(PlayerIntent.ComingSoonClicked(it)) },
                )
            },
            artwork = { artworkModifier ->
                Artwork(state, clock, onIntent, artworkModifier, coverModifier)
            },
        ) { compactHeight ->
            TrackInfoSlot(visible = state.sheet != PlayerSheet.Queue, reserveSpace = !compactHeight) {
                PlayerTrackInfo(
                    track = track,
                    showLyrics = state.showLyrics && state.hasLyrics && !state.isStation,
                    onLyricsClick = if (state.hasLyrics &&
                        !state.isStation
                    ) {
                        ({ if (state.sheet != PlayerSheet.Queue) onIntent(PlayerIntent.LyricsClicked) })
                    } else {
                        null
                    },
                    onQueueClick = if (!state.isStation) {
                        { if (state.sheet != PlayerSheet.Queue) onIntent(PlayerIntent.QueueClicked) }
                    } else {
                        null
                    },
                )
            }
            Transport(state, clock, onIntent)
            if (!state.isStation) {
                PlayerActions(
                    isLiked = state.isLiked,
                    canRemix = state.canRemix,
                    onRemixClick = { onIntent(PlayerIntent.ComingSoonClicked(SoonFeature.Remix)) },
                    onAddToClick = { onIntent(PlayerIntent.AddToClicked) },
                    onLikeClick = { onIntent(PlayerIntent.LikeClicked) },
                )
            }
        }
        Sheets(state, onIntent)
    }
}

@Composable
private fun Artwork(
    state: PlayerContent,
    clock: () -> PlaybackClock,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val motion = StudioTheme.motion
    Crossfade(
        targetState = state.sheet == PlayerSheet.Queue,
        modifier = modifier,
        animationSpec = tween(motion.duration.medium),
        label = "playerQueue",
    ) { queueShown ->
        if (queueShown) {
            PlayerQueueContent(
                track = track, upNext = state.upNext, shuffle = state.shuffle, repeat = state.repeat,
                onShuffle = { onIntent(PlayerIntent.ShuffleChanged(!state.shuffle)) },
                onRepeat = { onIntent(PlayerIntent.RepeatChanged(!state.repeat)) },
                onAutoplay = { onIntent(PlayerIntent.AutoplayChanged(!state.autoplay)) },
                autoplay = state.autoplay,
                onClear = { onIntent(PlayerIntent.ClearUpNextClicked) },
                onTrackClick = { onIntent(PlayerIntent.UpNextTrackClicked(it)) },
                onMove = { from, to -> onIntent(PlayerIntent.QueueItemMoved(from, to)) },
                onClose = { onIntent(PlayerIntent.QueueClicked) },
                onLyrics = if (state.hasLyrics) ({ onIntent(PlayerIntent.LyricsClicked) }) else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PlayerArtwork(
                coverSeed = track.coverSeed,
                trackKey = track.id.value,
                showLyrics = state.showLyrics && state.hasLyrics && !state.isStation,
                lyrics = state.lyrics,
                positionMs = { clock().positionMs },
                onLyricLineClick = { onIntent(PlayerIntent.LyricLineClicked(it)) },
                modifier = Modifier.fillMaxSize(),
                coverModifier = coverModifier,
            )
        }
    }
}

/** The seek bar and the transport, with their callbacks as intents. */
@Composable
private fun Transport(state: PlayerContent, clock: () -> PlaybackClock, onIntent: (PlayerIntent) -> Unit) {
    PlayerTransport(
        isStation = state.isStation,
        isPlaying = state.isPlaying,
        hasNext = state.hasNext,
        showModes = state.sheet != PlayerSheet.Queue,
        shuffle = state.shuffle,
        repeat = state.repeat,
        clock = clock,
        onSeek = { onIntent(PlayerIntent.SeekRequested(it)) },
        onPlayPause = { onIntent(PlayerIntent.PlayPauseClicked) },
        onStop = { onIntent(PlayerIntent.StopClicked) },
        onPrevious = { onIntent(PlayerIntent.PreviousClicked) },
        onNext = { onIntent(PlayerIntent.NextClicked) },
        onShuffleChange = { onIntent(PlayerIntent.ShuffleChanged(it)) },
        onRepeatChange = { onIntent(PlayerIntent.RepeatChanged(it)) },
    )
}

/** The queue and the coming-soon sheets, with their callbacks as intents. */
@Composable
private fun Sheets(state: PlayerContent, onIntent: (PlayerIntent) -> Unit) {
    val onDismiss = { onIntent(PlayerIntent.SheetDismissed) }
    PlayerComingSoonSheet(
        feature = (state.sheet as? PlayerSheet.ComingSoon)?.feature,
        onWantIt = { onIntent(PlayerIntent.WantItClicked) },
        onDismissRequest = onDismiss,
    )
}

@PreviewLightDark
@Composable
private fun PlayerScreenPreview(@PreviewParameter(PlayerPreviewData.States::class) state: PlayerContent) {
    // The player always uses the dark scheme; see PlayerHost.
    StudioTheme(darkTheme = true) { PlayerScreen(state = state, clock = { PlayerPreviewData.clock }, onIntent = {}) }
}

@Composable
private fun TrackInfoSlot(visible: Boolean, reserveSpace: Boolean, content: @Composable () -> Unit) {
    val opacity = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(StudioTheme.motion.duration.medium),
        label = "playerTrackInfo",
    )
    Box(
        Modifier.layout { measurable, constraints ->
            val child = measurable.measure(constraints)
            layout(child.width, if (visible || reserveSpace) child.height else 0) {
                if (opacity.value > 0f) child.place(0, 0)
            }
        }.graphicsLayer { alpha = opacity.value }
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
    ) { content() }
}
