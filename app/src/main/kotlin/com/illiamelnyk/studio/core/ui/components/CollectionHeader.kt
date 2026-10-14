package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.ProvideGlassBackdrop
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.glassBackdrop
import com.illiamelnyk.studio.core.ui.foundation.rememberGlassBackdrop
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The header of a mix, a collection or an album: its cover over a background shaded from the
 * cover's colors, the title, a line of [meta] and the [actions], usually glass buttons such as Play
 * and Shuffle, which blur the background behind them.
 */
@Composable
fun CollectionHeader(
    title: String,
    meta: String,
    coverSeed: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val backdrop = rememberGlassBackdrop()
    Box(modifier.fillMaxWidth()) {
        // A sibling under the content, so the glass buttons can blur it.
        Box(
            Modifier
                .matchParentSize()
                .coverBackdrop(coverSeed)
                .glassBackdrop(backdrop),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = dimens.spacing.xl,
                    start = dimens.spacing.m,
                    end = dimens.spacing.m,
                    bottom = dimens.spacing.m,
                ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Cover(
                seed = coverSeed,
                size = dimens.size.cover.l,
                modifier = Modifier.dropShadow(
                    StudioTheme.shapes.m,
                    StudioTheme.effects.elevation.artwork.toShadow(),
                ),
            )
            StudioText(
                text = title,
                style = StudioTheme.typography.titleLarge,
                modifier = Modifier.padding(top = dimens.spacing.xs),
                color = colors.overlay.onMediaPrimary,
                textAlign = TextAlign.Center,
            )
            StudioText(meta, StudioTheme.typography.bodySmall, color = colors.overlay.onMediaSecondary)
            ProvideGlassBackdrop(backdrop) {
                Row(
                    modifier = Modifier.padding(top = dimens.spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
                    content = actions,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun CollectionHeaderPreview() {
    PreviewSurface {
        CollectionHeader("Ambient", "Ambient Mix · 111 tracks · 6:00:39", "Ambient") {
            StudioButton("Play", onClick = {}, style = StudioButtonStyle.Glass, icon = StudioIcons.PlayArrowFilled)
            StudioButton("Shuffle", onClick = {}, style = StudioButtonStyle.Glass, icon = StudioIcons.Shuffle)
        }
    }
}
