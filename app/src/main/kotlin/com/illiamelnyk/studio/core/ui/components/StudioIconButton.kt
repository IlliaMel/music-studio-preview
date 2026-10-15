package com.illiamelnyk.studio.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioColors
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

enum class StudioIconButtonStyle {
    /** Just the icon, in the primary content color. */
    Plain,

    /** Just the icon, in the secondary content color: actions of a row, such as "more". */
    Muted,

    /** On a raised surface circle. */
    Surface,

    /** On the inverse surface, for the main action of a bar, such as sending a prompt. */
    Primary,

    /** On cover art or its color. */
    OnMedia,

    /** On glass, such as the mini player. */
    OnGlass,
}

enum class StudioIconButtonSize {
    /** A 40 dp circle with a 24 dp icon. */
    Medium,

    /** A 32 dp circle with a 20 dp icon. */
    Small,
}

/**
 * A round button with only an icon, so [contentDescription] is required: it is what TalkBack reads.
 * The touch area is 48 dp whatever the size.
 */
@Composable
fun StudioIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: StudioIconButtonStyle = StudioIconButtonStyle.Plain,
    size: StudioIconButtonSize = StudioIconButtonSize.Medium,
    enabled: Boolean = true,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val (buttonSize, iconSize) = when (size) {
        StudioIconButtonSize.Medium -> dimens.size.component.iconButton to dimens.size.icon.l
        StudioIconButtonSize.Small -> dimens.size.component.iconButtonSmall to dimens.size.icon.m
    }
    Box(
        // clickable comes before the container so that the press scale shrinks the whole circle.
        modifier = modifier
            .minimumTouchTarget()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .size(buttonSize)
            .background(style.containerColor(colors, enabled), StudioTheme.shapes.full),
        contentAlignment = Alignment.Center,
    ) {
        StudioIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = if (enabled) style.contentColor(colors) else colors.content.disabled,
            size = iconSize,
        )
    }
}

private fun StudioIconButtonStyle.containerColor(colors: StudioColors, enabled: Boolean): Color = when (this) {
    StudioIconButtonStyle.Plain,
    StudioIconButtonStyle.Muted,
    StudioIconButtonStyle.OnMedia,
    StudioIconButtonStyle.OnGlass,
    -> Color.Transparent

    StudioIconButtonStyle.Surface -> if (enabled) colors.surface.level4 else colors.surface.level3

    StudioIconButtonStyle.Primary -> if (enabled) colors.inverse.surface else colors.surface.level3
}

private fun StudioIconButtonStyle.contentColor(colors: StudioColors): Color = when (this) {
    StudioIconButtonStyle.Plain, StudioIconButtonStyle.Surface -> colors.content.primary
    StudioIconButtonStyle.Muted -> colors.content.secondary
    StudioIconButtonStyle.Primary -> colors.inverse.content
    StudioIconButtonStyle.OnMedia -> colors.overlay.onMediaPrimary
    StudioIconButtonStyle.OnGlass -> colors.overlay.onGlass
}

@PreviewLightDark
@Composable
private fun StudioIconButtonPreview() {
    PreviewSurface {
        Row {
            StudioIconButton(StudioIcons.Search, "Search", onClick = {})
            StudioIconButton(StudioIcons.Tune, "Settings", onClick = {}, style = StudioIconButtonStyle.Surface)
            StudioIconButton(
                StudioIcons.Add,
                "Add",
                onClick = {},
                style = StudioIconButtonStyle.Surface,
                size = StudioIconButtonSize.Small,
            )
            StudioIconButton(StudioIcons.ArrowUpward, "Send", onClick = {}, style = StudioIconButtonStyle.Primary)
            StudioIconButton(StudioIcons.MoreHoriz, "More", onClick = {}, style = StudioIconButtonStyle.Muted)
            StudioIconButton(
                StudioIcons.ArrowUpward,
                "Send",
                onClick = {},
                style = StudioIconButtonStyle.Primary,
                enabled = false,
            )
        }
    }
}
