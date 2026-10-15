package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.presentation.track.TrackUiRow
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons

@Composable
internal fun QueueTrackRow(
    track: TrackUi,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rowHeight by remember { mutableIntStateOf(1) }
    val position by rememberUpdatedState(index)
    val total by rememberUpdatedState(count)
    val move by rememberUpdatedState(onMove)
    val description = stringResource(R.string.player_queue_reorder, track.title)
    val moveUp = stringResource(R.string.player_queue_move_up)
    val moveDown = stringResource(R.string.player_queue_move_down)
    Row(
        modifier.onSizeChanged { rowHeight = it.height.coerceAtLeast(1) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackUiRow(track, onClick, Modifier.weight(1f))
        Box(
            Modifier.minimumTouchTarget()
                .semantics {
                    contentDescription = description
                    customActions = buildList {
                        if (index > 0) {
                            add(
                                CustomAccessibilityAction(moveUp) {
                                    onMove(index, index - 1)
                                    true
                                },
                            )
                        }
                        if (index <
                            count - 1
                        ) {
                            add(
                                CustomAccessibilityAction(moveDown) {
                                    onMove(index, index + 1)
                                    true
                                },
                            )
                        }
                    }
                }
                .pointerInput(track.id) {
                    var distance = 0f
                    var draggingIndex = position
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            distance = 0f
                            draggingIndex = position
                        },
                        onDragCancel = { distance = 0f },
                        onDragEnd = { distance = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            distance += amount.y
                            val direction = if (distance > 0) 1 else -1
                            if (kotlin.math.abs(distance) >= rowHeight) {
                                val target = (draggingIndex + direction).coerceIn(0, total - 1)
                                if (target != draggingIndex) {
                                    move(draggingIndex, target)
                                    draggingIndex = target
                                }
                                distance = 0f
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            StudioIcon(StudioIcons.DragHandle, contentDescription = null)
        }
    }
}

@androidx.compose.ui.tooling.preview.PreviewLightDark
@Composable
private fun QueueTrackRowPreview() {
    com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface {
        QueueTrackRow(
            track = com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewData.upNext.first(),
            index = 1,
            count = 3,
            onClick = {},
            onMove = { _, _ -> },
        )
    }
}
