package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * The 56 dp glass pill of what is playing, over the bottom of the screen: the rounded cover, the
 * title and a subtitle, then play/pause and next side by side. Like ElevenMusic's, it has no
 * progress line. Tapping the pill opens the player; the whole pill is meant to be the shared element
 * of that transition. A [live] station shows a stop button.
 *
 * It blurs the [LocalGlassBackdrop] behind it.
 */
@Composable
fun MiniPlayer(
    title: String,
    subtitle: String,
    coverSeed: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    onNext: (() -> Unit)? = null,
    live: Boolean = false,
    nextEnabled: Boolean = true,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val shape = StudioTheme.shapes.full
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.size.component.miniPlayer)
            .studioGlass(LocalGlassBackdrop.current, GlassDefaults.style(shape))
            .clip(shape)
            .clickable(
                interactionSource = null,
                indication = studioIndication(shape),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(start = dimens.spacing.s, end = dimens.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(coverSeed, dimens.size.cover.xs, shape = StudioTheme.shapes.s)
        Column(
            Modifier
                .weight(1f)
                .padding(start = dimens.spacing.xxs),
        ) {
            StudioText(title, StudioTheme.typography.titleSmall, color = colors.overlay.onGlass, maxLines = 1)
            StudioText(subtitle, StudioTheme.typography.bodySmall, color = colors.content.secondary, maxLines = 1)
        }
        // Touching targets with no gap, as close as 48 dp targets get to the reference's controls.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (icon, description) = when {
                live -> StudioIcons.StopFilled to stringResource(R.string.ui_stop)
                isPlaying -> StudioIcons.PauseFilled to stringResource(R.string.ui_pause)
                else -> StudioIcons.PlayArrowFilled to stringResource(R.string.ui_play)
            }
            StudioIconButton(
                icon = icon,
                contentDescription = description,
                onClick = onPlayPause,
                style = StudioIconButtonStyle.OnGlass,
                size = StudioIconButtonSize.Large,
            )
            if (onNext != null && !live) {
                StudioIconButton(
                    icon = StudioIcons.FastForwardFilled,
                    contentDescription = stringResource(R.string.ui_next),
                    onClick = onNext,
                    style = StudioIconButtonStyle.OnGlass,
                    size = StudioIconButtonSize.Large,
                    enabled = nextEnabled,
                )
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
            onClick = {},
            onPlayPause = {},
            onNext = {},
        )
        MiniPlayer(
            title = "Late Night Lo-fi · station",
            subtitle = "Rainy Window",
            coverSeed = "Late Night Lo-fi",
            isPlaying = true,
            onClick = {},
            onPlayPause = {},
            live = true,
        )
    }
}
