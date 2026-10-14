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

/**
 * "Use tool" at the bottom of the screen. A tool that needs the user's recording can't be used
 * yet, and says so above the button; [notice] says why the last attempt failed.
 */
@Composable
fun ToolAction(
    needsRecording: Boolean,
    isSubmitting: Boolean,
    notice: UiText?,
    onUseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    val colors = StudioTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimens.layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val message = when {
            notice != null -> notice.asString()
            needsRecording -> stringResource(R.string.tool_needs_recording)
            else -> null
        }
        if (message != null) {
            StudioText(
                text = message,
                style = StudioTheme.typography.bodySmall,
                color = if (notice != null) colors.error.content else colors.content.secondary,
                textAlign = TextAlign.Center,
            )
        }
        StudioButton(
            text = stringResource(R.string.tool_use),
            onClick = onUseClick,
            modifier = Modifier.fillMaxWidth(),
            size = StudioButtonSize.Large,
            enabled = !needsRecording,
            loading = isSubmitting,
        )
    }
}

@PreviewLightDark
@Composable
private fun ToolActionPreview() {
    PreviewScreen {
        Column {
            ToolAction(needsRecording = false, isSubmitting = false, notice = null, onUseClick = {})
            ToolAction(needsRecording = true, isSubmitting = false, notice = null, onUseClick = {})
            ToolAction(
                needsRecording = false,
                isSubmitting = false,
                notice = UiText.Resource(R.string.error_quota_exceeded),
                onUseClick = {},
            )
        }
    }
}
