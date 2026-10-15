package com.illiamelnyk.studio.feature.player.presentation.mini

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.host.PlayerHostScope
import com.illiamelnyk.studio.feature.player.presentation.host.sharedPlayerCover
import com.illiamelnyk.studio.feature.player.presentation.mini.components.MiniPlayerPill
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The slot for the mini player above the bottom bar or the bottom of the screen. It slides in while
 * something is loaded and [visible] is set, and opens the full player of [host] when tapped or
 * dragged up.
 *
 * While the full player is open the pill fades out but keeps its place, so the screens under the
 * player don't move; its cover is where the full player's cover grows from and shrinks back to.
 *
 * Only the progress line reads the position, in its draw phase, so the ticks of the clock redraw
 * that line and recompose nothing.
 *
 * @param visible `false` to keep the mini player out, whatever plays.
 */
@Composable
fun MiniPlayerHost(
    host: PlayerHostScope,
    visible: Boolean,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = koinViewModel(),
) {
    val drafts: ComposerDraftStore = koinInject()
    val playback: PlaybackController = koinInject()
    val playbackState by playback.state.collectAsStateWithLifecycle()
    val state = viewModel.state.collectAsStateWithLifecycle()
    val track by remember { derivedStateOf { state.value.track } }
    val isPlaying by remember { derivedStateOf { state.value.isPlaying } }
    val isStation by remember { derivedStateOf { state.value.isStation } }
    val hasNext by remember { derivedStateOf { state.value.hasNext } }
    val motion = StudioTheme.motion
    AnimatedVisibility(
        visible = visible && track != null,
        modifier = modifier,
        enter = slideInVertically(tween(motion.duration.medium, easing = motion.easing.standard)) { it / 2 } +
            fadeIn(tween(motion.duration.medium)),
        exit = slideOutVertically(tween(motion.duration.medium, easing = motion.easing.standard)) { it / 2 } +
            fadeOut(tween(motion.duration.short)),
    ) {
        Box(Modifier.fillMaxWidth().height(StudioTheme.dimens.size.component.miniPlayer)) {
            // Fades out in the first part of opening the full player and back in at the end of closing
            // it; the cover flies meanwhile. Linear: the player's transition eases as a whole.
            host.transition.AnimatedVisibility(
                visible = { expanded -> !expanded },
                enter = fadeIn(
                    tween(motion.duration.short, motion.duration.long - motion.duration.short, LinearEasing),
                ),
                exit = fadeOut(tween(motion.duration.short, easing = LinearEasing)),
            ) {
                // After the queue is cleared there is nothing left to show while the bar slides out.
                val shownTrack = track ?: return@AnimatedVisibility
                MiniPlayerPill(
                    track = shownTrack,
                    isPlaying = isPlaying,
                    isStation = isStation,
                    progress = { state.value.progress },
                    onOpen = host.sheet::expand,
                    onPlayPause = { viewModel.onIntent(MiniPlayerIntent.PlayPauseClicked) },
                    onNext = if (!isStation) ({ viewModel.onIntent(MiniPlayerIntent.NextClicked) }) else null,
                    nextEnabled = hasNext,
                    onRemix = if (!isStation && playbackState.current?.remixTrackId == null) {
                        { playback.state.value.current?.let(drafts::remix) }
                    } else {
                        null
                    },
                    coverModifier = Modifier.sharedPlayerCover(host, this),
                    pillModifier = Modifier.draggable(
                        state = host.sheet.expandDrag,
                        orientation = Orientation.Vertical,
                        onDragStarted = { host.sheet.startDrag(expanded = true) },
                        // Dragging up opens: velocities upwards are negative.
                        onDragStopped = { velocity -> host.sheet.endDrag(-velocity) },
                    ),
                )
            }
        }
    }
}
