package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A tool on the Create screen: a 56 dp tile with a square picture and the tool's name. A tool that
 * is [comingSoon] gets a muted picture and a SOON badge, and still opens, to explain what is coming.
 */
@Suppress("ktlint:compose:modifier-clickable-order") // The press scale has to shrink the clipped container.
@Composable
fun ToolTile(
    name: String,
    imageSeed: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    comingSoon: Boolean = false,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val shape = StudioTheme.shapes.m
    Row(
        modifier = modifier
            .clickable(
                interactionSource = null,
                indication = studioIndication(shape),
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = dimens.size.component.toolTile)
            .clip(shape)
            .background(colors.surface.level3)
            .padding(end = dimens.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tile's corners clip the picture.
        Cover(imageSeed, dimens.size.cover.m, shape = RectangleShape, muted = comingSoon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
        ) {
            StudioText(
                text = name,
                style = StudioTheme.typography.titleSmall,
                color = if (comingSoon) colors.content.secondary else colors.content.primary,
                maxLines = 2,
            )
            if (comingSoon) StudioBadge(stringResource(R.string.ui_soon), style = StudioBadgeStyle.Soon)
        }
    }
}

@PreviewLightDark
@Composable
private fun ToolTilePreview() {
    PreviewSurface {
        ToolTile("Voice to Song", "Voice to Song", onClick = {})
        ToolTile("Unplugged", "Unplugged", onClick = {}, comingSoon = true)
    }
}
