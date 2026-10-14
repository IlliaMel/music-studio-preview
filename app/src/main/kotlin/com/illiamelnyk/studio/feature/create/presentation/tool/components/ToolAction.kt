package com.illiamelnyk.studio.feature.create.presentation.tool.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonSize
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/** "Use tool" at the bottom of the screen; while it can't be used, [hint] above it says why. */
@Composable
fun ToolAction(
    canUse: Boolean,
    hint: UiText?,
    isSubmitting: Boolean,
    onUseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimens.layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hint != null) {
            StudioText(
                text = hint.asString(),
                style = StudioTheme.typography.bodySmall,
                color = StudioTheme.colors.content.secondary,
                textAlign = TextAlign.Center,
            )
        }
        StudioButton(
            text = stringResource(R.string.tool_use),
            onClick = onUseClick,
            modifier = Modifier.fillMaxWidth(),
            size = StudioButtonSize.Large,
            enabled = canUse || isSubmitting,
            loading = isSubmitting,
        )
    }
}

@PreviewLightDark
@Composable
private fun ToolActionPreview() {
    PreviewScreen {
        Column {
            ToolAction(canUse = true, hint = null, isSubmitting = false, onUseClick = {})
            ToolAction(canUse = false, hint = null, isSubmitting = true, onUseClick = {})
            ToolAction(
                canUse = false,
                hint = UiText.Resource(R.string.tool_fill_slots),
                isSubmitting = false,
                onUseClick = {},
            )
        }
    }
}
