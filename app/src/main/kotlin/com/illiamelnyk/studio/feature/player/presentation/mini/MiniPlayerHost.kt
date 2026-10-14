package com.illiamelnyk.studio.feature.player.presentation.mini

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.mini.components.MiniPlayerBar
import org.koin.compose.viewmodel.koinViewModel

/**
 * The slot for the mini player above the bottom bar or the bottom of the screen. Until the kit has
 * its glass MiniPlayer, a plain bar of kit pieces stands in. It slides in while something is
 * loaded and [visible] is set.
 *
 * Only the progress line reads the position, in its draw phase, so the ticks of the clock redraw
 * that line and recompose nothing.
 *
 * @param visible `false` where the mini player would duplicate the screen, such as the full player.
 * @param onOpenPlayer opens the full player.
 */
@Composable
fun MiniPlayerHost(
    visible: Boolean,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = koinViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle()
    val track by remember { derivedStateOf { state.value.track } }
    val isPlaying by remember { derivedStateOf { state.value.isPlaying } }
    val motion = StudioTheme.motion
    AnimatedVisibility(
        visible = visible && track != null,
        modifier = modifier,
        enter = slideInVertically(tween(motion.duration.medium, easing = motion.easing.standard)) { it / 2 } +
            fadeIn(tween(motion.duration.medium)),
        exit = slideOutVertically(tween(motion.duration.medium, easing = motion.easing.standard)) { it / 2 } +
            fadeOut(tween(motion.duration.short)),
    ) {
        // After the queue is cleared there is nothing left to show while the bar slides out.
        val shownTrack = track
        if (shownTrack != null) {
            MiniPlayerBar(
                track = shownTrack,
                isPlaying = isPlaying,
                progress = { state.value.progress },
                onClick = onOpenPlayer,
                onPlayPauseClick = { viewModel.onIntent(MiniPlayerIntent.PlayPauseClicked) },
            )
        }
    }
}
