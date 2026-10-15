package com.illiamelnyk.studio.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/** What a [TrackRow] shows besides the title and the subtitle. */
@Immutable
sealed interface TrackRowState {
    /** A finished track. */
    data object Idle : TrackRowState

    /** The track that is playing: an equalizer before the subtitle. */
    data object Playing : TrackRowState

    /** A track that is still being composed: a living cover and a progress bar. */
    data object Generating : TrackRowState

    /** Generation failed: an error cover, and [message] in place of the subtitle. */
    data class Failed(val message: String) : TrackRowState
}

/**
 * A 64 dp row of a track list: a 48 dp cover, the title and a subtitle, and an action at the end:
 * "more" for a finished track ([onMore]) or "retry" for a failed one ([onRetry]).
 *
 * @param coverSeed what the cover art is generated from; see [Cover].
 * @param badges marks after the subtitle, such as a [VerifiedMark] or a [StudioBadge].
 */
@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    coverSeed: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: TrackRowState = TrackRowState.Idle,
    onMore: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    badges: (@Composable RowScope.() -> Unit)? = null,
    titleSuffix: (@Composable () -> Unit)? = null,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .fillMaxWidth()
            .heightIn(min = dimens.size.component.trackRow),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state is TrackRowState.Failed) {
            FailedCover()
        } else {
            Cover(coverSeed, dimens.size.cover.s, generating = state == TrackRowState.Generating)
        }
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudioText(title, StudioTheme.typography.titleSmall, Modifier.weight(1f, fill = false), maxLines = 1)
                titleSuffix?.invoke()
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state == TrackRowState.Playing) Equalizer()
                StudioText(
                    text = if (state is TrackRowState.Failed) state.message else subtitle,
                    style = StudioTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f, fill = false),
                    color = if (state is TrackRowState.Failed) colors.error.content else colors.content.secondary,
                    maxLines = if (state is TrackRowState.Failed) 2 else 1,
                )
                badges?.invoke(this)
            }
            if (state == TrackRowState.Generating) {
                StudioLinearProgress(Modifier.padding(top = dimens.spacing.xxs))
            }
        }
        val finished = state == TrackRowState.Idle || state == TrackRowState.Playing
        when {
            state is TrackRowState.Failed && onRetry != null -> StudioIconButton(
                icon = StudioIcons.Refresh,
                contentDescription = stringResource(R.string.ui_retry),
                onClick = onRetry,
                style = StudioIconButtonStyle.Muted,
            )

            finished && onMore != null -> StudioIconButton(
                icon = StudioIcons.MoreHoriz,
                contentDescription = stringResource(R.string.ui_more_options),
                onClick = onMore,
                style = StudioIconButtonStyle.Muted,
            )
        }
    }
}

@Composable
private fun FailedCover() {
    val colors = StudioTheme.colors
    Box(
        modifier = Modifier
            .size(StudioTheme.dimens.size.cover.s)
            .background(colors.error.surface, StudioTheme.shapes.s),
        contentAlignment = Alignment.Center,
    ) {
        StudioIcon(StudioIcons.Error, contentDescription = null, tint = colors.error.content)
    }
}

/** Three bars bouncing out of phase, 16 dp: the track is playing. Decorative: the subtitle says it. */
@Composable
private fun Equalizer() {
    val color = StudioTheme.colors.content.primary
    val dimens = StudioTheme.dimens
    val motion = StudioTheme.motion
    val time by rememberInfiniteTransition(label = "equalizer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(motion.loop.equalizer, easing = LinearEasing)),
        label = "time",
    )
    Canvas(Modifier.size(dimens.size.icon.s)) {
        val gap = dimens.spacing.hairline.toPx()
        val barWidth = (size.width - gap * (BAR_PHASES.size - 1)) / BAR_PHASES.size
        val radius = minOf(dimens.spacing.xxs.toPx(), barWidth / 2)
        BAR_PHASES.forEachIndexed { index, phase ->
            // Each bar grows from 30 % to full height and back once a period, like the kit's keyframes.
            val progress = (time + phase) % 1f
            val triangle = if (progress < 0.5f) progress * 2 else (1 - progress) * 2
            val height = size.height * (BAR_MIN + (1 - BAR_MIN) * motion.loopEasing.transform(triangle))
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(radius),
            )
        }
    }
}

/** Phases of the three bars, as the kit's animation delays of 0, -300 and -600 ms in a 1 s period. */
private val BAR_PHASES = floatArrayOf(0f, 0.3f, 0.6f)
private const val BAR_MIN = 0.3f

@PreviewLightDark
@Composable
private fun TrackRowPreview() {
    PreviewSurface {
        TrackRow(
            title = "Rust and Redemption",
            subtitle = "Enrique Flores",
            coverSeed = "Rust and Redemption",
            onClick = {},
            onMore = {},
            badges = {
                VerifiedMark()
                StudioBadge("v2")
            },
        )
        TrackRow(
            title = "Night Drive Through Kyiv",
            subtitle = "Playing · 2:00",
            coverSeed = "Night Drive Through Kyiv",
            onClick = {},
            state = TrackRowState.Playing,
            onMore = {},
        )
        TrackRow(
            title = "Lo-fi rainy morning",
            subtitle = "Composing… 0:42",
            coverSeed = "Lo-fi rainy morning",
            onClick = {},
            state = TrackRowState.Generating,
        )
        TrackRow(
            title = "Song in the style of…",
            subtitle = "",
            coverSeed = "Song in the style of…",
            onClick = {},
            state = TrackRowState.Failed("Prompt rejected · tap for a suggestion"),
            onRetry = {},
        )
    }
}
