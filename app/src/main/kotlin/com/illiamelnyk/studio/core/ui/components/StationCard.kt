package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A radio station: a 320 × 180 dp (16:9) card of cover art with the station's [title] and [byline]
 * at the bottom, in the colors for text on media.
 */
@Suppress("ktlint:compose:modifier-clickable-order") // The press scale has to shrink the clipped container.
@Composable
fun StationCard(title: String, byline: String, coverSeed: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    val colors = StudioTheme.colors
    val shape = StudioTheme.shapes.l
    // Known before layout, so the first frame already asks for the sharp cover, not the list preview.
    val sidePx = with(LocalDensity.current) {
        maxOf(dimens.size.component.stationWidth, dimens.size.component.stationHeight).roundToPx()
    }
    Row(
        modifier = modifier
            .clickable(
                interactionSource = null,
                indication = studioIndication(shape),
                role = Role.Button,
                onClick = onClick,
            )
            .size(width = dimens.size.component.stationWidth, height = dimens.size.component.stationHeight)
            .clip(shape)
            .coverArt(coverSeed, sidePx = sidePx)
            .padding(dimens.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        StudioText(
            text = title,
            style = StudioTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            color = colors.overlay.onMediaPrimary,
            maxLines = 2,
        )
        StudioText(byline, StudioTheme.typography.bodySmall, color = colors.overlay.onMediaSecondary, maxLines = 1)
    }
}

@PreviewLightDark
@Composable
private fun StationCardPreview() {
    PreviewSurface {
        StationCard("Late Night Lo-fi", "Studio", "Late Night Lo-fi", onClick = {})
    }
}
