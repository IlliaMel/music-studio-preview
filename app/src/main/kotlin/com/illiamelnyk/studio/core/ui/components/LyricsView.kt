package com.illiamelnyk.studio.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

/** A line of lyrics: when it starts, and its words with the time each one is sung. */
@Immutable
data class LyricLine(val startMs: Long, val words: List<LyricWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
}

@Immutable
data class LyricWord(val text: String, val startMs: Long)

/**
 * Synced lyrics on cover color, in `titleLarge`: the line being sung is bright, with the words sung
 * so far in the primary media color and the rest dimmed, and the list follows it, keeping it on the
 * upper third of the view, between edges that fade out. Tapping a line seeks to its start.
 *
 * [positionMs] is read through derived state, so playback ticking recomposes only when the line or
 * the word changes. Scrolling by hand stops following and shows a "Follow along" pill; tapping it
 * goes back to the line being sung and follows again.
 *
 * @param lines in order of [LyricLine.startMs], which also keys the items, so start times are unique.
 * @param followState whether the list follows the line being sung; hoist it to read or reset that.
 */
@Composable
fun LyricsView(
    lines: List<LyricLine>,
    positionMs: () -> Long,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    followState: LyricsFollowState = rememberLyricsFollowState(),
) {
    val motion = StudioTheme.motion
    val currentPositionMs by rememberUpdatedState(positionMs)
    val currentLine by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.startMs <= currentPositionMs() } }
    }

    LaunchedEffect(state, followState) {
        state.interactionSource.interactions.filterIsInstance<DragInteraction.Start>().collect {
            followState.stopFollowing()
        }
    }
    LaunchedEffect(state, followState) {
        val spec = tween<Float>(motion.duration.long, easing = motion.easing.emphasized)
        snapshotFlow { currentLine to followState.isFollowing }.collectLatest { (line, following) ->
            if (line >= 0 && following) state.scrollToLine(line, spec)
        }
    }

    val dimens = StudioTheme.dimens
    BoxWithConstraints(modifier) {
        // The current line rests on the upper third; the padding lets the first and last lines get there.
        val focusLine = maxHeight * LyricsDefaults.FOCUS_LINE
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight)
                .fadingEdges(dimens.spacing.huge),
            state = state,
            contentPadding = PaddingValues(top = focusLine, bottom = maxHeight - focusLine),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        ) {
            itemsIndexed(lines, key = { _, line -> line.startMs }) { index, line ->
                LyricLineText(
                    line = line,
                    current = index == currentLine,
                    positionMs = { currentPositionMs() },
                    onClick = { onSeek(line.startMs) },
                )
            }
        }
        FollowAlongPill(
            visible = !followState.isFollowing && currentLine >= 0,
            onClick = followState::followAlong,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dimens.spacing.m),
        )
    }
}

/**
 * Whether a [LyricsView] follows the line being sung. Scrolling the lyrics by hand stops following;
 * [followAlong] starts again.
 */
@Stable
class LyricsFollowState(following: Boolean = true) {
    var isFollowing: Boolean by mutableStateOf(following)
        private set

    /** Goes back to the line being sung and keeps it in view. */
    fun followAlong() {
        isFollowing = true
    }

    internal fun stopFollowing() {
        isFollowing = false
    }

    companion object {
        /** Keeps whether the lyrics follow through a configuration change and process death. */
        val Saver: Saver<LyricsFollowState, Boolean> = Saver(save = { it.isFollowing }, restore = ::LyricsFollowState)
    }
}

@Composable
fun rememberLyricsFollowState(): LyricsFollowState = rememberSaveable(saver = LyricsFollowState.Saver) {
    LyricsFollowState()
}

object LyricsDefaults {
    /** Where the current line rests, as a fraction of the view's height from the top. */
    const val FOCUS_LINE = 1f / 3
}

/** The pill that brings a [LyricsView] back to the line being sung, in the main action's style. */
@Composable
private fun FollowAlongPill(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = StudioTheme.motion
    val spec = tween<Float>(motion.duration.short, easing = motion.easing.standard)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(spec) + scaleIn(spec, motion.pressScale),
        exit = fadeOut(spec) + scaleOut(spec, motion.pressScale),
    ) {
        StudioButton(
            text = stringResource(R.string.ui_follow_along),
            onClick = onClick,
            size = StudioButtonSize.Small,
        )
    }
}

@Composable
private fun LyricLineText(line: LyricLine, current: Boolean, positionMs: () -> Long, onClick: () -> Unit) {
    val colors = StudioTheme.colors
    val text = if (current) {
        val sungWords by remember(line) { derivedStateOf { line.words.count { it.startMs <= positionMs() } } }
        remember(line, sungWords, colors) {
            val sung = colors.overlay.onMediaPrimary
            val ahead = colors.overlay.onMediaSecondary
            buildAnnotatedString {
                line.words.forEachIndexed { index, word ->
                    if (index > 0) append(' ')
                    withStyle(SpanStyle(color = if (index < sungWords) sung else ahead)) { append(word.text) }
                }
            }
        }
    } else {
        AnnotatedString(line.text)
    }
    BasicText(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = null,
                indication = studioIndication(StudioTheme.shapes.s),
                role = Role.Button,
                onClick = onClick,
            ),
        style = StudioTheme.typography.titleLarge.copy(color = colors.overlay.onMediaSecondary),
    )
}

/** Fades the content out over [length] at the top and bottom edges, so lines don't end in a hard cut. */
private fun Modifier.fadingEdges(length: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = (length.toPx() / size.height).coerceAtMost(0.5f)
        // A mask: only the alpha of these colors counts.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                edge to Color.Black,
                1f - edge to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Brings [index] to the top of the content area: smoothly when it is on screen, otherwise directly there. */
private suspend fun LazyListState.scrollToLine(index: Int, spec: AnimationSpec<Float>) {
    val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (visible != null) {
        animateScrollBy(visible.offset.toFloat(), spec)
    } else {
        animateScrollToItem(index)
    }
}

@PreviewLightDark
@Composable
private fun LyricsViewPreview() {
    val lines = listOf(
        LyricLine(0, listOf(LyricWord("Radio", 0), LyricWord("is", 400), LyricWord("playing", 700))),
        LyricLine(3_000, listOf(LyricWord("We're", 3_000), LyricWord("chasing", 3_300), LyricWord("the", 3_800))),
        LyricLine(6_000, listOf(LyricWord("Hold", 6_000), LyricWord("on", 6_400))),
    )
    PreviewSurface {
        LyricsView(
            lines = lines,
            positionMs = { 3_500 },
            onSeek = {},
            modifier = Modifier.height(StudioTheme.dimens.size.cover.l),
        )
        // Scrolled by hand: the pill brings the line being sung back.
        LyricsView(
            lines = lines,
            positionMs = { 3_500 },
            onSeek = {},
            modifier = Modifier.height(StudioTheme.dimens.size.cover.l),
            followState = remember { LyricsFollowState(following = false) },
        )
    }
}
