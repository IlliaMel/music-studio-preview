package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The title of a section, in `titleMedium`. With [onTitleClick] the title gets a chevron and opens
 * the whole section; [action] goes at the end, such as a "Show more" text button.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val titleModifier = if (onTitleClick != null) {
            Modifier
                .minimumTouchTarget()
                .clickable(
                    interactionSource = null,
                    indication = studioIndication(StudioTheme.shapes.s),
                    role = Role.Button,
                    onClick = onTitleClick,
                )
        } else {
            Modifier
        }
        Row(
            modifier = titleModifier,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioText(title, StudioTheme.typography.titleMedium, maxLines = 1)
            if (onTitleClick != null) {
                StudioIcon(
                    icon = StudioIcons.ChevronRight,
                    contentDescription = null,
                    tint = StudioTheme.colors.content.secondary,
                    size = dimens.size.icon.m,
                )
            }
        }
        action?.invoke()
    }
}

@PreviewLightDark
@Composable
private fun SectionHeaderPreview() {
    PreviewSurface {
        SectionHeader("Trending Now", onTitleClick = {})
        SectionHeader("New this week") {
            StudioButton("Show more", onClick = {}, style = StudioButtonStyle.Text, size = StudioButtonSize.Small)
        }
    }
}
