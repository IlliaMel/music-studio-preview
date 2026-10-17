package com.illiamelnyk.studio.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A 32 dp chip for an action, such as "Styles", or for an input the user added, such as a reference
 * track: then it shows [leadingAvatar] and, with [onRemove], a remove button.
 *
 * Chips that toggle a filter or pick one of several values are [StudioFilterChip]s.
 *
 * @param leadingAvatar a 24 dp round image, `size.avatar.xs`.
 */
@Composable
fun StudioChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
    leadingAvatar: (@Composable () -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    comingSoon: Boolean = false,
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.dimens.spacing
    val isInput = leadingAvatar != null
    ChipContainer(
        modifier = modifier
            .minimumTouchTarget()
            .clickable(role = Role.Button, onClick = onClick),
        containerColor = colors.surface.level2,
        outlined = true,
        // An avatar sits 4 dp from the edge, like it is 4 dp from the top and the bottom.
        startPadding = if (isInput) spacing.xxs else spacing.s,
        gap = if (isInput) spacing.xs else spacing.xxs,
    ) {
        leadingAvatar?.invoke()
        if (leadingIcon != null) {
            StudioIcon(
                leadingIcon,
                contentDescription = null,
                tint = colors.content.secondary,
                size = StudioTheme.dimens.size.icon.s,
            )
        }
        StudioText(
            text = text,
            style = StudioTheme.typography.bodyMedium,
            color = if (isInput) colors.content.secondary else colors.content.primary,
            maxLines = 1,
            modifier = if (onRemove != null) Modifier.weight(1f, fill = false) else Modifier,
        )
        if (comingSoon) StudioSoonBadge()
        if (onRemove != null) {
            StudioIcon(
                icon = StudioIcons.Close,
                contentDescription = stringResource(R.string.ui_remove, text),
                modifier = Modifier.clickable(role = Role.Button, onClick = onRemove),
                tint = colors.content.secondary,
                size = StudioTheme.dimens.size.icon.s,
            )
        }
    }
}

/**
 * A 32 dp chip that is either selected or not: a filter ("All", "Liked") or one value of a few
 * (a duration). Selected, it turns into the inverse pill. TalkBack reads its selection state.
 */
@Composable
fun StudioFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
) {
    val colors = StudioTheme.colors
    val motion = StudioTheme.motion
    val spacing = StudioTheme.dimens.spacing
    val animationSpec = tween<Color>(motion.duration.short, easing = motion.easing.standard)
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.inverse.surface else colors.surface.level3,
        animationSpec = animationSpec,
        label = "chipContainer",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.inverse.content else colors.content.primary,
        animationSpec = animationSpec,
        label = "chipText",
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) colors.inverse.content else colors.content.secondary,
        animationSpec = animationSpec,
        label = "chipIcon",
    )
    ChipContainer(
        // Like Material's filter chip: a selectable with the checkbox role.
        modifier = modifier
            .minimumTouchTarget()
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick),
        containerColor = containerColor,
        startPadding = spacing.s,
        gap = spacing.xxs,
    ) {
        if (leadingIcon != null) {
            StudioIcon(leadingIcon, contentDescription = null, tint = iconColor, size = StudioTheme.dimens.size.icon.s)
        }
        StudioText(text, StudioTheme.typography.labelLarge, color = textColor, maxLines = 1)
    }
}

/**
 * The pill of both chips. [modifier] carries the click handling: it comes before the container so that
 * the press scale shrinks the whole chip.
 */
@Composable
private fun ChipContainer(
    containerColor: Color,
    startPadding: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier
            .heightIn(min = dimens.size.component.chip)
            .background(containerColor, StudioTheme.shapes.full)
            .then(
                if (outlined) {
                    Modifier.border(dimens.stroke.hairline, StudioTheme.colors.outline.subtle, StudioTheme.shapes.full)
                } else {
                    Modifier
                },
            )
            .padding(start = startPadding, end = dimens.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun StudioChipPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs)) {
            StudioFilterChip("All", selected = true, onClick = {})
            StudioFilterChip("Yours", selected = false, onClick = {})
            StudioFilterChip("Liked", selected = false, onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs)) {
            StudioChip("Muse", onClick = {}, leadingIcon = StudioIcons.Forum)
            StudioChip("Styles", onClick = {}, leadingIcon = StudioIcons.AutoAwesome)
        }
        StudioChip(
            text = "Light Through Clouds",
            onClick = {},
            leadingAvatar = {
                Cover(
                    seed = "Light Through Clouds",
                    size = StudioTheme.dimens.size.avatar.xs,
                    shape = StudioTheme.shapes.full,
                )
            },
            onRemove = {},
        )
    }
}
