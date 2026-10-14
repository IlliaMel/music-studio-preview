package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A 56 dp settings row: the [label], and at the end either the current [value] with a chevron when
 * the row opens a choice ([onClick]), or a [trailing] element such as a badge or a switch.
 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = null,
            indication = studioIndication(StudioTheme.shapes.s),
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .then(clickModifier)
            .fillMaxWidth()
            .heightIn(min = dimens.size.component.listRow),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioText(label, StudioTheme.typography.bodyMedium, Modifier.weight(1f))
        when {
            trailing != null -> trailing()

            value != null || onClick != null -> Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (value != null) {
                    StudioText(value, StudioTheme.typography.bodyMedium, color = colors.content.secondary, maxLines = 1)
                }
                if (onClick != null) {
                    StudioIcon(
                        icon = StudioIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.content.secondary,
                        size = dimens.size.icon.m,
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingRowPreview() {
    PreviewSurface {
        SettingRow("Default model", value = "Music v2.5", onClick = {})
        SettingRow("Backend") { StudioBadge("Mock", style = StudioBadgeStyle.Demo) }
    }
}
