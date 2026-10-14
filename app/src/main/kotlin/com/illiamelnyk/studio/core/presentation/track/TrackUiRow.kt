package com.illiamelnyk.studio.core.presentation.track

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.ui.components.TrackRow
import com.illiamelnyk.studio.core.ui.components.TrackRowState

/**
 * A [TrackRow] for a [TrackUi]: picks the row's state from the track's status and resolves its
 * texts.
 *
 * @param isPlaying shows the equalizer; only a ready track can be playing.
 */
@Composable
fun TrackUiRow(
    track: TrackUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onMore: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    val status = track.status
    TrackRow(
        title = track.title,
        subtitle = track.subtitle.asString(),
        coverSeed = track.coverSeed,
        onClick = onClick,
        modifier = modifier,
        state = when (status) {
            TrackUi.Status.Ready -> if (isPlaying) TrackRowState.Playing else TrackRowState.Idle
            TrackUi.Status.Generating -> TrackRowState.Generating
            is TrackUi.Status.Failed -> TrackRowState.Failed(status.message.asString())
        },
        onMore = onMore,
        onRetry = onRetry,
    )
}
