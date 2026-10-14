package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A track in a carousel: the 144 dp cover with the title and a subtitle under it, about 2.5 cards
 * on a phone screen.
 *
 * @param badges marks after the subtitle, such as a [VerifiedMark].
 */
@Composable
fun TrackCard(
    title: String,
    subtitle: String,
    coverSeed: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    generating: Boolean = false,
    badges: (@Composable RowScope.() -> Unit)? = null,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier
            .clickable(
                interactionSource = null,
                indication = studioIndication(StudioTheme.shapes.m),
                role = Role.Button,
                onClick = onClick,
            )
            .width(dimens.size.cover.l),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        Cover(coverSeed, dimens.size.cover.l, generating = generating)
        Column {
            StudioText(title, StudioTheme.typography.titleSmall, maxLines = 1)
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudioText(
                    text = subtitle,
                    style = StudioTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f, fill = false),
                    color = StudioTheme.colors.content.secondary,
                    maxLines = 1,
                )
                badges?.invoke(this)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun TrackCardPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.layout.itemGap)) {
            TrackCard(
                title = "Light Through Clouds",
                subtitle = "DEREK",
                coverSeed = "Light Through Clouds",
                onClick = {},
                badges = { VerifiedMark() },
            )
            TrackCard("Lo-fi rainy morning", "Composing…", "Lo-fi rainy morning", onClick = {}, generating = true)
        }
    }
}
