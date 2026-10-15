package com.illiamelnyk.studio.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.illiamelnyk.studio.core.ui.foundation.FeedbackWindow
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A menu of actions that opens from its trigger, such as the "more" button of a row: place it next
 * to the trigger, in the same parent (a `Box` around both), and it anchors to that parent. It opens
 * below the trigger, or above when there isn't room, end-aligned and kept on screen; a tap outside
 * or the back gesture closes it through [onDismissRequest].
 *
 * Fill it with [ContextMenuItem]s and [ContextMenuDivider]s. Items don't close the menu: their
 * `onClick` does what the item says and sets [expanded] to `false`.
 */
// The root is the popup window; the modifier is for the menu inside it.
@Suppress("ktlint:compose:modifier-not-used-at-root")
@Composable
fun StudioContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return

    val dimens = StudioTheme.dimens
    val motion = StudioTheme.motion
    val shadow = StudioTheme.effects.elevation.menu
    val shape = StudioTheme.shapes.l
    val density = LocalDensity.current
    // The popup window has room around the menu for its shadow.
    val shadowRoom = shadow.blur + shadow.offsetY
    var opensUpwards by remember { mutableStateOf(false) }
    val positionProvider = remember(density, shadowRoom, dimens) {
        MenuPositionProvider(
            shadowRoom = with(density) { shadowRoom.roundToPx() },
            screenMargin = with(density) { dimens.layout.screenPadding.roundToPx() },
            gap = with(density) { dimens.spacing.xxs.roundToPx() },
            onPlaced = { upwards -> opensUpwards = upwards },
        )
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        // The shadow's room may run past the screen edges; clipping would push the menu inwards.
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        FeedbackWindow {
            // A tap in the shadow's room is a tap outside the menu.
            Box(
                Modifier
                    .pointerInput(Unit) { detectTapGestures { onDismissRequest() } }
                    .padding(shadowRoom),
            ) {
                val origin = TransformOrigin(1f, if (opensUpwards) 1f else 0f)
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = fadeIn(tween(motion.duration.short, easing = motion.easing.standard)) +
                        scaleIn(
                            tween(motion.duration.short, easing = motion.easing.standard),
                            motion.pressScale,
                            origin,
                        ),
                    exit = fadeOut(tween(motion.duration.short, easing = motion.easing.standard)) +
                        scaleOut(
                            tween(motion.duration.short, easing = motion.easing.standard),
                            motion.pressScale,
                            origin,
                        ),
                ) {
                    Column(
                        modifier = modifier
                            .dropShadow(shape, shadow.toShadow())
                            .width(IntrinsicSize.Max)
                            .clip(shape)
                            .background(StudioTheme.colors.surface.level1)
                            .border(dimens.stroke.hairline, StudioTheme.colors.outline.subtle, shape)
                            // Taps between the items stay in the menu.
                            .pointerInput(Unit) { detectTapGestures { } }
                            .padding(dimens.spacing.xs),
                        content = content,
                    )
                }
            }
        }
    }
}

/**
 * An item of a [StudioContextMenu]: a 40 dp row with an optional icon, the [label] and a [hint] at
 * the end, such as "Soon" or "5 left". A [danger] item, such as Delete, is in the error color.
 */
@Composable
fun ContextMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    hint: String? = null,
    danger: Boolean = false,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier
            .clickable(
                interactionSource = null,
                indication = studioIndication(StudioTheme.shapes.s),
                role = Role.Button,
                onClick = onClick,
            )
            .fillMaxWidth()
            .heightIn(min = dimens.size.control.m)
            .padding(horizontal = dimens.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            StudioIcon(
                icon = icon,
                contentDescription = null,
                tint = if (danger) colors.error.content else colors.content.secondary,
                size = dimens.size.icon.m,
            )
        }
        StudioText(
            text = label,
            style = StudioTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = if (danger) colors.error.content else colors.content.primary,
            maxLines = 1,
        )
        if (hint != null) {
            StudioText(hint, StudioTheme.typography.bodySmall, color = colors.content.tertiary, maxLines = 1)
        }
    }
}

/** A line between groups of items in a [StudioContextMenu]. */
@Composable
fun ContextMenuDivider(modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = dimens.spacing.xxs, horizontal = dimens.spacing.xs)
            .height(dimens.size.component.divider)
            .background(StudioTheme.colors.outline.subtle),
    )
}

/**
 * Places the menu under its anchor, end-aligned, or above it when it doesn't fit below, and keeps
 * it [screenMargin] away from the window edges. The popup content is the menu plus [shadowRoom] on
 * every side.
 */
private class MenuPositionProvider(
    private val shadowRoom: Int,
    private val screenMargin: Int,
    private val gap: Int,
    private val onPlaced: (opensUpwards: Boolean) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val menuWidth = popupContentSize.width - shadowRoom * 2
        val menuHeight = popupContentSize.height - shadowRoom * 2
        val endAligned = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.right - menuWidth
            LayoutDirection.Rtl -> anchorBounds.left
        }
        val x = endAligned.coerceIn(
            screenMargin,
            (windowSize.width - screenMargin - menuWidth).coerceAtLeast(screenMargin),
        )
        val below = anchorBounds.bottom + gap
        val fitsBelow = below + menuHeight <= windowSize.height - screenMargin
        val y = if (fitsBelow) below else (anchorBounds.top - gap - menuHeight).coerceAtLeast(screenMargin)
        onPlaced(!fitsBelow)
        return IntOffset(x - shadowRoom, y - shadowRoom)
    }
}

@PreviewLightDark
@Composable
private fun StudioContextMenuPreview() {
    PreviewSurface {
        Box {
            StudioIconButton(StudioIcons.MoreHoriz, "More", onClick = {})
            StudioContextMenu(expanded = true, onDismissRequest = {}) {
                ContextMenuItem("Remix", onClick = {}, icon = StudioIcons.AutoFixHigh, hint = "Soon")
                ContextMenuItem("Play next", onClick = {}, icon = StudioIcons.PlaylistPlay)
                ContextMenuItem("Download", onClick = {}, icon = StudioIcons.Download, hint = "5 left")
                ContextMenuDivider()
                ContextMenuItem("Delete", onClick = {}, icon = StudioIcons.Delete, danger = true)
            }
        }
    }
}
