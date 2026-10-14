package com.illiamelnyk.studio.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * Where the user describes a track: a box with a 48 dp minimum height that grows with the text, a
 * row of actions under the text with the send button at the end, and the [options] row above the
 * box (a reference, the quality, the style, the duration) while [expanded].
 *
 * It is stateless: the caller owns [value] and decides when it is [expanded], typically while it has
 * focus or text. The send button is disabled until [sendEnabled], and when the text is over
 * [characterLimit]; a counter shows how much is left.
 *
 * It doesn't move out of the keyboard's way by itself: put it in a container with `imePadding()`
 * (the activity resizes with the keyboard, edge to edge), so the insets are applied once.
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    expanded: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
    sendEnabled: Boolean = value.isNotBlank(),
    characterLimit: Int? = null,
    interactionSource: MutableInteractionSource? = null,
    options: (@Composable () -> Unit)? = null,
    leadingActions: @Composable RowScope.() -> Unit = {},
    trailingActions: @Composable RowScope.() -> Unit = {},
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val motion = StudioTheme.motion
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val overLimit = characterLimit != null && value.length > characterLimit
    val shape = StudioTheme.shapes.xl
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
        if (options != null) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(motion.duration.medium, easing = motion.easing.emphasized)) +
                    fadeIn(tween(motion.duration.medium, easing = motion.easing.standard)),
                exit = shrinkVertically(tween(motion.duration.medium, easing = motion.easing.emphasized)) +
                    fadeOut(tween(motion.duration.short, easing = motion.easing.standard)),
            ) { options() }
        }
        Column(
            modifier = Modifier
                // The box grows line by line as the text wraps.
                .animateContentSize(tween(motion.duration.medium, easing = motion.easing.standard))
                .fillMaxWidth()
                .heightIn(min = dimens.size.component.composerMin)
                .background(colors.surface.level2, shape)
                .border(
                    dimens.stroke.hairline,
                    if (focused) colors.outline.strong else colors.outline.subtle,
                    shape,
                )
                .padding(dimens.spacing.s),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spacing.xxs),
                textStyle = StudioTheme.typography.bodyLarge.copy(color = colors.content.primary),
                maxLines = ComposerDefaults.MAX_LINES,
                interactionSource = source,
                cursorBrush = SolidColor(colors.content.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            StudioText(placeholder, StudioTheme.typography.bodyLarge, color = colors.content.tertiary)
                        }
                        innerTextField()
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingActions()
                Spacer(Modifier.weight(1f))
                if (characterLimit != null) {
                    StudioText(
                        text = stringResource(R.string.ui_character_count, value.length, characterLimit),
                        style = StudioTheme.typography.bodySmall,
                        color = if (overLimit) colors.error.content else colors.content.tertiary,
                    )
                }
                trailingActions()
                StudioIconButton(
                    icon = StudioIcons.ArrowUpward,
                    contentDescription = stringResource(R.string.ui_send),
                    onClick = onSend,
                    style = StudioIconButtonStyle.Primary,
                    size = StudioIconButtonSize.Small,
                    enabled = sendEnabled && !overLimit,
                )
            }
        }
    }
}

object ComposerDefaults {
    /** The box grows up to this many lines of text, then scrolls. */
    const val MAX_LINES = 6
}

@PreviewLightDark
@Composable
private fun ComposerPreview() {
    PreviewSurface {
        var prompt by remember { mutableStateOf("A slow lo-fi beat for a rainy morning") }
        Composer(
            value = prompt,
            onValueChange = { prompt = it },
            onSend = {},
            expanded = true,
            placeholder = "Describe your track…",
            characterLimit = 2_000,
            options = {
                Row(horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs)) {
                    StudioChip("Styles", onClick = {}, leadingIcon = StudioIcons.AutoAwesome)
                    StudioChip("2:00", onClick = {}, leadingIcon = StudioIcons.Schedule)
                }
            },
            leadingActions = {
                StudioIconButton(
                    StudioIcons.Add,
                    "Attach",
                    onClick = {},
                    style = StudioIconButtonStyle.Surface,
                    size = StudioIconButtonSize.Small,
                )
            },
        )
    }
}
