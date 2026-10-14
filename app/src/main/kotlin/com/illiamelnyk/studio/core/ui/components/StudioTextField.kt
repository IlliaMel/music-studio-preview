package com.illiamelnyk.studio.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A 48 dp single-line text field in `bodyLarge`: [placeholder] while empty, an optional leading
 * icon, and a stronger border while focused. With [errorText] the border turns to the error color,
 * the text shows under the field, and TalkBack reads it as the field's error.
 *
 * The field doesn't keep the keyboard out of the way: put it in a container with `imePadding()`
 * (the activity resizes with the keyboard, edge to edge).
 */
@Composable
fun StudioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    StudioField(
        value = value,
        onValueChange = onValueChange,
        shape = StudioTheme.shapes.m,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        enabled = enabled,
        errorText = errorText,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        trailing = trailing,
    )
}

/**
 * A pill-shaped search field with a search icon. The keyboard shows a search action that calls
 * [onSearch], and a clear button appears once there is a query.
 */
@Composable
fun StudioSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    StudioField(
        value = query,
        onValueChange = onQueryChange,
        shape = StudioTheme.shapes.full,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = StudioIcons.Search,
        enabled = enabled,
        errorText = null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        interactionSource = interactionSource,
        trailing = if (query.isNotEmpty()) {
            {
                StudioIconButton(
                    icon = StudioIcons.Close,
                    contentDescription = stringResource(R.string.ui_clear),
                    onClick = { onQueryChange("") },
                    style = StudioIconButtonStyle.Muted,
                    size = StudioIconButtonSize.Small,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun StudioField(
    value: String,
    onValueChange: (String) -> Unit,
    shape: Shape,
    placeholder: String?,
    @DrawableRes leadingIcon: Int?,
    enabled: Boolean,
    errorText: String?,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    interactionSource: MutableInteractionSource?,
    trailing: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val borderColor = when {
        errorText != null -> colors.error.content
        focused -> colors.outline.strong
        else -> colors.outline.subtle
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxs)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (errorText != null) error(errorText) },
            enabled = enabled,
            textStyle = StudioTheme.typography.bodyLarge.copy(
                color = if (enabled) colors.content.primary else colors.content.disabled,
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
            interactionSource = source,
            cursorBrush = SolidColor(colors.content.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .heightIn(min = dimens.size.component.textField)
                        .background(colors.surface.level2, shape)
                        .border(dimens.stroke.hairline, borderColor, shape)
                        .padding(horizontal = dimens.spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        StudioIcon(leadingIcon, contentDescription = null, tint = colors.content.secondary)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            StudioText(
                                text = placeholder,
                                style = StudioTheme.typography.bodyLarge,
                                color = colors.content.tertiary,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                    trailing?.invoke()
                }
            },
        )
        if (errorText != null) {
            StudioText(
                text = errorText,
                style = StudioTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = dimens.spacing.m),
                color = colors.error.content,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun StudioTextFieldPreview() {
    PreviewSurface {
        StudioTextField("", onValueChange = {}, placeholder = "Enter your email address")
        StudioTextField("illia@", onValueChange = {}, errorText = "Enter a full email address")
        StudioSearchField("", onQueryChange = {}, onSearch = {}, placeholder = "Search sounds")
    }
}
