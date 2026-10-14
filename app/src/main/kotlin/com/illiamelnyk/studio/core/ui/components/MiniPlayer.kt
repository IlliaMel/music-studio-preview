package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.GlassDefaults
import com.illiamelnyk.studio.core.ui.foundation.LocalGlassBackdrop
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.studioGlass
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The 56 dp glass pill of what is playing, over the bottom of the screen: the round cover, the title
 * and a subtitle, play/pause and next, and a 2 dp progress line along the bottom edge. Tapping the
 * pill opens the player; the whole pill is meant to be the shared element of that transition.
 *
 * [progress] (0 to 1) is read while drawing, so playback ticking doesn't recompose the caller. A
 * [live] station shows a stop button and no progress.
 *
 * It blurs the [LocalGlassBackdrop] behind it.
 */
@Composable
fun MiniPlayer(
    title: String,
    subtitle: String,
    coverSeed: String,
    isPlaying: Boolean,
    progress: () -> Float,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    onNext: (() -> Unit)? = null,
    live: Boolean = false,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val shape = StudioTheme.shapes.full
    Box(
        modifier = modifier
            .clickable(
                interactionSource = null,
                indication = studioIndication(shape),
                role = Role.Button,
                onClick = onClick,
            )
            .fillMaxWidth()
            .height(dimens.size.component.miniPlayer)
            .studioGlass(LocalGlassBackdrop.current, GlassDefaults.style(shape)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(coverSeed, dimens.size.cover.xs, shape = shape)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = dimens.spacing.xxs),
            ) {
                StudioText(title, StudioTheme.typography.titleSmall, color = colors.overlay.onGlass, maxLines = 1)
                StudioText(subtitle, StudioTheme.typography.bodySmall, color = colors.content.secondary, maxLines = 1)
            }
            val (icon, description) = when {
                live -> StudioIcons.StopFilled to stringResource(R.string.ui_stop)
                isPlaying -> StudioIcons.PauseFilled to stringResource(R.string.ui_pause)
                else -> StudioIcons.PlayArrowFilled to stringResource(R.string.ui_play)
            }
            StudioIconButton(icon, description, onClick = onPlayPause, style = StudioIconButtonStyle.OnGlass)
            if (onNext != null && !live) {
                StudioIconButton(
                    icon = StudioIcons.FastForwardFilled,
                    contentDescription = stringResource(R.string.ui_next),
                    onClick = onNext,
                    style = StudioIconButtonStyle.OnGlass,
                )
            }
        }
        if (!live) {
            val track = colors.outline.strong
            val fill = colors.overlay.onGlass
            Canvas(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = dimens.spacing.xl)
                    .fillMaxWidth()
                    .height(dimens.spacing.hairline),
            ) {
                drawRect(track)
                drawRect(fill, size = Size(size.width * progress().coerceIn(0f, 1f), size.height))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun MiniPlayerPreview() {
    PreviewSurface {
        MiniPlayer(
            title = "Night Drive Through Kyiv",
            subtitle = "Studio",
            coverSeed = "Night Drive Through Kyiv",
            isPlaying = true,
            progress = { 0.4f },
            onClick = {},
            onPlayPause = {},
            onNext = {},
        )
        MiniPlayer(
            title = "Late Night Lo-fi · station",
            subtitle = "Rainy Window",
            coverSeed = "Late Night Lo-fi",
            isPlaying = true,
            progress = { 0f },
            onClick = {},
            onPlayPause = {},
            live = true,
        )
    }
}
