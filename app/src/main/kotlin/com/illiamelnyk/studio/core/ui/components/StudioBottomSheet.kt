package com.illiamelnyk.studio.core.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * A modal sheet that rises from the bottom over a scrim, without Material: drag it down, tap the
 * scrim or go back to dismiss it. While the back gesture is in progress the sheet shrinks a little,
 * as a preview of leaving.
 *
 * It draws inside the window it is composed in, so place it last in a full-screen container, such as
 * the screen's root `Box`. The sheet runs behind the navigation bar and keeps its content above it.
 *
 * @param visible whether the sheet should be open; it animates in and out, and stays composed while
 *   it leaves.
 * @param onDismissRequest called when the user dismisses the sheet; set [visible] to `false` then.
 */
@Composable
fun StudioBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    showCloseButton: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = StudioTheme.motion
    val state = remember { AnchoredDraggableState(SheetValue.Hidden) }
    val backProgress = remember { Animatable(0f) }
    val currentVisible by rememberUpdatedState(visible)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val spec = tween<Float>(motion.duration.long, easing = motion.easing.emphasized)
    // Still on screen while it moves out, however that ends: the scrim must not outlive the sheet.
    val onScreen by remember(state) {
        derivedStateOf {
            val hidden = state.anchors.positionOf(SheetValue.Hidden)
            !hidden.isNaN() && !state.offset.isNaN() && state.offset < hidden
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            backProgress.snapTo(0f)
            // The anchors exist once the sheet has been measured.
            snapshotFlow { state.anchors.hasPositionFor(SheetValue.Expanded) }.first { it }
            state.animateTo(SheetValue.Expanded, spec)
        } else if (onScreen) {
            state.animateTo(SheetValue.Hidden, spec)
        }
    }
    // A drag that settles at the bottom dismisses the sheet. The settled value changes only once
    // the finger has let go, unlike the current value, which follows the finger.
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.drop(1).collect { value ->
            if (value == SheetValue.Hidden && currentVisible) currentOnDismissRequest()
        }
    }
    PredictiveBackHandler(enabled = visible) { progress ->
        try {
            progress.collect { event -> backProgress.snapTo(event.progress) }
            currentOnDismissRequest()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                backProgress.animateTo(0f, tween(motion.duration.short, easing = motion.easing.standard))
            }
            throw cancelled
        }
    }

    if (!visible && !onScreen) return
    Box(modifier.fillMaxSize()) {
        Scrim(
            // How far the sheet is open, from 0 to 1, read while drawing.
            openFraction = {
                val hidden = state.anchors.positionOf(SheetValue.Hidden)
                if (hidden.isNaN() || hidden == 0f) 0f else 1f - state.requireOffset() / hidden
            },
            onDismissRequest = onDismissRequest,
        )
        SheetSurface(
            state = state,
            visible = visible,
            backProgress = { backProgress.value },
            onDismissRequest = onDismissRequest,
            showCloseButton = showCloseButton,
            content = content,
        )
    }
}

private enum class SheetValue { Hidden, Expanded }

@Composable
private fun Scrim(openFraction: () -> Float, onDismissRequest: () -> Unit) {
    val scrim = StudioTheme.colors.overlay.scrim
    val description = stringResource(R.string.ui_close_sheet)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(scrim, alpha = openFraction().coerceIn(0f, 1f)) }
            .pointerInput(Unit) { detectTapGestures { onDismissRequest() } }
            .semantics {
                contentDescription = description
                onClick {
                    onDismissRequest()
                    true
                }
            },
    )
}

@Composable
private fun BoxScope.SheetSurface(
    state: AnchoredDraggableState<SheetValue>,
    visible: Boolean,
    backProgress: () -> Float,
    onDismissRequest: () -> Unit,
    showCloseButton: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val motion = StudioTheme.motion
    val shape = StudioTheme.shapes.xxl.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
    val navigationBars = WindowInsets.navigationBars
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            // Never under the status bar, however tall the content is.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = dimens.spacing.xxs)
            .fillMaxWidth()
            .onSizeChanged { size ->
                state.updateAnchors(
                    DraggableAnchors {
                        SheetValue.Hidden at size.height.toFloat()
                        SheetValue.Expanded at 0f
                    },
                    newTarget = if (visible) SheetValue.Expanded else SheetValue.Hidden,
                )
            }
            // Read while laying out, so the sheet moves without recomposing.
            .offset { IntOffset(0, state.offset.takeUnless { it.isNaN() }?.roundToInt() ?: 0) }
            .graphicsLayer {
                // The back gesture previews leaving: the sheet shrinks towards the bottom edge.
                val scale = 1f - (1f - motion.pressScale) * backProgress()
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .anchoredDraggable(
                state = state,
                reverseDirection = false,
                orientation = Orientation.Vertical,
                flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                    state = state,
                    animationSpec = tween(motion.duration.medium, easing = motion.easing.standard),
                ),
            )
            .semantics {
                dismiss {
                    onDismissRequest()
                    true
                }
            }
            .drawBehind {
                val height = navigationBars.getBottom(density).toFloat().coerceAtMost(size.height)
                if (height > 0f) {
                    drawRect(
                        colors.surface.level1,
                        topLeft = Offset(0f, size.height - height),
                        size = Size(size.width, height),
                    )
                }
            }
            .padding(horizontal = dimens.spacing.xs)
            .clip(shape)
            .background(colors.surface.level1)
            .navigationBarsPadding()
            .padding(
                start = dimens.spacing.m,
                end = dimens.spacing.m,
                top = dimens.spacing.xs,
                bottom = dimens.spacing.m,
            ),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(dimens.size.component.sheetHandleWidth, dimens.size.component.sheetHandleHeight)
                    .background(colors.outline.strong, StudioTheme.shapes.full),
            )
            content()
        }
        if (showCloseButton) {
            StudioIconButton(
                StudioIcons.Close,
                stringResource(R.string.ui_close),
                onDismissRequest,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = dimens.spacing.xs),
                style = StudioIconButtonStyle.Surface,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun StudioBottomSheetPreview() {
    PreviewSurface {
        Box(Modifier.size(StudioTheme.dimens.size.component.stationWidth)) {
            StudioBottomSheet(visible = true, onDismissRequest = {}) {
                ComingSoonSheetContent(
                    title = "Voice to Song",
                    description = "Hum or sing a melody and get a full track around it.",
                    steps = listOf("Record up to 30 s", "`upload` the file", "Pass it as `conditioning_ref`"),
                    actionLabel = "I want this",
                    onAction = {},
                )
            }
        }
    }
}
