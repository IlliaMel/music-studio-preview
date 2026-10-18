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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentWithReceiverOf
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
import com.illiamelnyk.studio.core.ui.foundation.rememberStudioEditor
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
 * [characterLimit]; a counter shows how much is left. Set [showSend] to false for a collapsed toolbar.
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
    showSend: Boolean = true,
    additionalEditor: (@Composable () -> Unit)? = null,
) {
    val leading = remember(leadingActions) { movableContentWithReceiverOf<RowScope> { leadingActions() } }
    val trailing = remember(trailingActions) { movableContentWithReceiverOf<RowScope> { trailingActions() } }
    val editor = rememberStudioEditor(value, onValueChange)
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val motion = StudioTheme.motion
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val overLimit = characterLimit != null && value.length > characterLimit
    val shape = if (expanded) StudioTheme.shapes.xl else StudioTheme.shapes.full
    Column(
        modifier.fillMaxWidth().animateContentSize(
            animationSpec = tween(motion.duration.medium, easing = motion.easing.standard),
            alignment = Alignment.BottomStart,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        if (expanded) options?.invoke()
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f)
                    .fillMaxWidth()
                    .heightIn(min = if (expanded) dimens.size.component.composerMin else dimens.size.touchTarget)
                    .background(colors.surface.level2, shape)
                    .border(
                        dimens.stroke.hairline,
                        if (focused) colors.outline.strong else colors.outline.subtle,
                        shape,
                    )
                    .padding(
                        horizontal = if (expanded) dimens.spacing.s else dimens.spacing.xxs,
                        vertical = if (expanded) dimens.spacing.s else dimens.spacing.none,
                    ),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
            ) {
                BasicTextField(
                    state = editor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spacing.xxs),
                    textStyle = StudioTheme.typography.bodyLarge.copy(color = colors.content.primary),
                    lineLimits = TextFieldLineLimits.MultiLine(
                        maxHeightInLines = if (expanded) ComposerDefaults.MAX_LINES else 1,
                    ),
                    interactionSource = source,
                    cursorBrush = SolidColor(colors.content.primary),
                    decorator = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!expanded) leading()
                            Box(Modifier.weight(1f)) {
                                if (editor.text.isEmpty()) {
                                    StudioText(
                                        placeholder,
                                        StudioTheme.typography.bodyLarge,
                                        color = colors.content.tertiary,
                                        maxLines = if (expanded) ComposerDefaults.MAX_LINES else 1,
                                    )
                                }
                                innerTextField()
                            }
                        }
                    },
                )
                if (expanded) additionalEditor?.invoke()
                if (expanded || showSend) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (expanded) Arrangement.SpaceBetween else Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (expanded) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) { leading() }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (characterLimit != null) {
                                StudioText(
                                    text = stringResource(R.string.ui_character_count, value.length, characterLimit),
                                    style = StudioTheme.typography.bodySmall,
                                    color = if (overLimit) colors.error.content else colors.content.tertiary,
                                )
                            }
                            if (expanded) trailing()
                            if (showSend) {
                                StudioIconButton(
                                    icon = StudioIcons.ArrowUpward,
                                    contentDescription = stringResource(R.string.ui_send),
                                    onClick = {
                                        onValueChange(editor.text.toString())
                                        onSend()
                                    },
                                    style = StudioIconButtonStyle.Primary,
                                    size = StudioIconButtonSize.Small,
                                    enabled = sendEnabled && !overLimit,
                                )
                            }
                        }
                    }
                }
            }
            if (!expanded) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
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

@PreviewLightDark
@Composable
private fun ComposerCollapsedPreview() {
    PreviewSurface {
        Composer(
            value = "",
            onValueChange = {},
            onSend = {},
            expanded = false,
            placeholder = "Describe your track...",
            showSend = false,
            leadingActions = {
                StudioIconButton(StudioIcons.Add, "Attach", onClick = {}, size = StudioIconButtonSize.Small)
            },
            trailingActions = {
                StudioIconButton(StudioIcons.Tune, "Settings", onClick = {}, size = StudioIconButtonSize.Small)
            },
        )
    }
}
