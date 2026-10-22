package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The player's seek bar on cover color: an 8 dp track with a drag-only thumb in a 48 dp touch area, and
 * the [elapsed] and [remaining] times under it. Drag the thumb or tap the track to seek; the bar
 * follows the finger and calls [onSeek] when it lifts.
 *
 * [progress] (0 to 1) is read while drawing, so playback ticking doesn't recompose the caller.
 * TalkBack reads the position and can set it.
 */
@Composable
fun SeekBar(
    progress: () -> Float,
    onSeek: (fraction: Float) -> Unit,
    elapsed: String,
    remaining: String,
    modifier: Modifier = Modifier,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val paintedTrack = dimens.size.component.seekTrack * 2
    val shapeRadius = paintedTrack / 2
    val layoutDirection = LocalLayoutDirection.current
    val currentOnSeek by rememberUpdatedState(onSeek)
    // Where the finger is while dragging; the bar shows it instead of the playback position.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val timeStyle = StudioTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(dimens.size.touchTarget)
                .pointerInput(layoutDirection) {
                    detectTapGestures { offset -> currentOnSeek(fractionAt(offset.x, size.width, layoutDirection)) }
                }
                .pointerInput(layoutDirection) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFraction = fractionAt(offset.x, size.width, layoutDirection) },
                        onDragEnd = {
                            dragFraction?.let(currentOnSeek)
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        dragFraction = fractionAt(change.position.x, size.width, layoutDirection)
                    }
                }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
                    setProgress { target ->
                        currentOnSeek(target.coerceIn(0f, 1f))
                        true
                    }
                }
                .drawBehind {
                    val fraction = (dragFraction ?: progress()).coerceIn(0f, 1f)
                    val track = paintedTrack.toPx()
                    val radius = CornerRadius(shapeRadius.toPx())
                    val top = (size.height - track) / 2
                    val filled = size.width * fraction
                    val filledLeft = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - filled
                    drawRoundRect(colors.overlay.onMediaTrack, Offset(0f, top), Size(size.width, track), radius)
                    drawRoundRect(colors.overlay.onMediaPrimary, Offset(filledLeft, top), Size(filled, track), radius)
                    val thumbX = if (layoutDirection == LayoutDirection.Ltr) filled else size.width - filled
                    if (dragFraction != null) {
                        drawCircle(
                            color = colors.overlay.onMediaPrimary,
                            radius = dimens.size.component.seekThumb.toPx() / 2,
                            center = Offset(
                                thumbX.coerceIn(
                                    dimens.size.component.seekThumb.toPx() / 2,
                                    maxOf(
                                        dimens.size.component.seekThumb.toPx() / 2,
                                        size.width - dimens.size.component.seekThumb.toPx() / 2,
                                    ),
                                ),
                                size.height / 2,
                            ),
                        )
                    }
                },
        )
        Row(Modifier.fillMaxWidth().offset(y = -dimens.spacing.m), horizontalArrangement = Arrangement.SpaceBetween) {
            StudioText(elapsed, timeStyle, color = colors.overlay.onMediaSecondary)
            StudioText(remaining, timeStyle, color = colors.overlay.onMediaSecondary)
        }
    }
}

private fun fractionAt(x: Float, width: Int, layoutDirection: LayoutDirection): Float {
    val fraction = (x / width).coerceIn(0f, 1f)
    return if (layoutDirection == LayoutDirection.Ltr) fraction else 1f - fraction
}

@PreviewLightDark
@Composable
private fun SeekBarPreview() {
    PreviewSurface {
        SeekBar(progress = { 0.58f }, onSeek = {}, elapsed = "02:12", remaining = "-01:47")
    }
}
