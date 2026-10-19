package com.illiamelnyk.studio.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.illiamelnyk.studio.core.ui.foundation.GlassDefaults
import com.illiamelnyk.studio.core.ui.foundation.LocalGlassBackdrop
import com.illiamelnyk.studio.core.ui.foundation.ProvideContentColor
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.foundation.studioGlass
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioColors
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

enum class StudioButtonStyle {
    /** The one main action of a screen: the inverse pill. */
    Primary,

    /** Every other action. */
    Secondary,

    /**
     * On cover art or its color, such as the player: frosted glass over the [LocalGlassBackdrop], or
     * the plain glass tint without one.
     */
    Glass,

    /** A low-emphasis action without a container, such as "Show more". */
    Text,

    /** A destructive action, confirmed in a dialog. */
    Danger,
}

enum class StudioButtonSize {
    /** 52 dp: the main action of a screen. */
    Large,

    /** 40 dp: the default. */
    Medium,

    /** 32 dp: inside rows, sheets and dense layouts. */
    Small,
}

/**
 * A pill-shaped button with a text and an optional leading icon.
 *
 * While [loading], a spinner replaces the icon and the button ignores clicks; [enabled] = `false`
 * also greys it out. Smaller than 48 dp, it still has a 48 dp touch area.
 */
@Composable
fun StudioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: StudioButtonStyle = StudioButtonStyle.Primary,
    size: StudioButtonSize = StudioButtonSize.Medium,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    comingSoon: Boolean = false,
) {
    val colors = StudioTheme.colors
    val shape = StudioTheme.shapes.full
    val metrics = buttonMetrics(size, style)
    val container = if (enabled) style.containerColor(colors) else style.disabledContainerColor(colors)
    val content = if (enabled) style.contentColor(colors) else colors.content.disabled
    val surface = if (enabled && style == StudioButtonStyle.Glass) {
        Modifier.studioGlass(LocalGlassBackdrop.current, GlassDefaults.style(shape))
    } else {
        Modifier.background(container, shape)
    }
    Row(
        // clickable comes before the container so that the press scale shrinks the whole pill.
        modifier = modifier
            .minimumTouchTarget()
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            // One node for TalkBack: the button reports the progress, the spinner inside stays silent.
            .semantics { if (loading) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            .heightIn(min = metrics.height)
            .then(surface)
            .padding(horizontal = metrics.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideContentColor(content) {
            when {
                loading -> StudioSpinner(
                    modifier = Modifier.clearAndSetSemantics {},
                    trackColor = style.spinnerTrackColor(colors),
                )

                icon != null -> StudioIcon(icon, contentDescription = null, size = metrics.iconSize)
            }
            StudioSoonAnchor(comingSoon) {
                StudioText(text, metrics.textStyle, maxLines = 1)
            }
        }
    }
}

private class ButtonMetrics(
    val height: Dp,
    val horizontalPadding: Dp,
    val gap: Dp,
    val iconSize: Dp,
    val textStyle: TextStyle,
)

@Composable
@ReadOnlyComposable
private fun buttonMetrics(size: StudioButtonSize, style: StudioButtonStyle): ButtonMetrics {
    val dimens = StudioTheme.dimens
    val typography = StudioTheme.typography
    val metrics = when (size) {
        StudioButtonSize.Large -> ButtonMetrics(
            height = dimens.size.component.buttonLarge,
            horizontalPadding = if (style == StudioButtonStyle.Glass) dimens.spacing.m else dimens.spacing.xl,
            gap = dimens.spacing.xs,
            iconSize = dimens.size.icon.m,
            textStyle = typography.labelExtraLarge,
        )

        StudioButtonSize.Medium -> ButtonMetrics(
            height = dimens.size.component.button,
            horizontalPadding = dimens.spacing.m,
            gap = dimens.spacing.xs,
            iconSize = dimens.size.icon.m,
            textStyle = typography.labelLarge,
        )

        StudioButtonSize.Small -> ButtonMetrics(
            height = dimens.size.control.s,
            horizontalPadding = dimens.spacing.s,
            gap = dimens.spacing.xxs,
            iconSize = dimens.size.icon.s,
            textStyle = typography.labelMedium,
        )
    }
    // Without a container the text needs less room around it, whatever the size.
    return if (style == StudioButtonStyle.Text) {
        ButtonMetrics(metrics.height, dimens.spacing.xs, metrics.gap, metrics.iconSize, metrics.textStyle)
    } else {
        metrics
    }
}

private fun StudioButtonStyle.containerColor(colors: StudioColors): Color = when (this) {
    StudioButtonStyle.Primary -> colors.inverse.surface
    StudioButtonStyle.Secondary -> colors.surface.level3
    StudioButtonStyle.Glass -> colors.overlay.glass
    StudioButtonStyle.Text -> Color.Transparent
    StudioButtonStyle.Danger -> colors.error.content
}

private fun StudioButtonStyle.disabledContainerColor(colors: StudioColors): Color =
    if (this == StudioButtonStyle.Text) Color.Transparent else colors.surface.level3

/** The kit's `surface.level4` ring only stands out from the arc next to `level3` and the background. */
private fun StudioButtonStyle.spinnerTrackColor(colors: StudioColors): Color = when (this) {
    StudioButtonStyle.Secondary, StudioButtonStyle.Text -> colors.surface.level4
    StudioButtonStyle.Primary, StudioButtonStyle.Glass, StudioButtonStyle.Danger -> Color.Transparent
}

private fun StudioButtonStyle.contentColor(colors: StudioColors): Color = when (this) {
    StudioButtonStyle.Primary -> colors.inverse.content
    StudioButtonStyle.Secondary -> colors.content.primary
    StudioButtonStyle.Glass -> colors.overlay.onGlass
    StudioButtonStyle.Text -> colors.content.secondary
    StudioButtonStyle.Danger -> colors.error.onError
}

@PreviewLightDark
@Composable
private fun StudioButtonPreview() {
    PreviewSurface {
        StudioButton("Generate", onClick = {}, size = StudioButtonSize.Large)
        StudioButton("Shuffle", onClick = {}, style = StudioButtonStyle.Secondary, icon = StudioIcons.Shuffle)
        StudioButton("Use tool", onClick = {}, enabled = false)
        StudioButton("Creating", onClick = {}, loading = true)
        StudioButton(
            "New playlist",
            onClick = {},
            style = StudioButtonStyle.Secondary,
            size = StudioButtonSize.Small,
            icon = StudioIcons.Add,
        )
        StudioButton("Show more", onClick = {}, style = StudioButtonStyle.Text)
        StudioButton("Delete", onClick = {}, style = StudioButtonStyle.Danger)
    }
}
