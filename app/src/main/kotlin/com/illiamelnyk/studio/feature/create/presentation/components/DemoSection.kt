package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * Generates a track from a sample prompt until the composer replaces this: one prompt the service
 * accepts and one it rejects. [notice] says why the last attempt failed.
 */
@Composable
fun DemoSection(
    isSubmitting: Boolean,
    notice: UiText?,
    onGenerateClick: () -> Unit,
    onGenerateRejectedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier.padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        StudioButton(
            text = stringResource(R.string.create_generate_demo),
            onClick = onGenerateClick,
            icon = StudioIcons.AutoAwesome,
            loading = isSubmitting,
        )
        StudioButton(
            text = stringResource(R.string.create_generate_rejected),
            onClick = onGenerateRejectedClick,
            style = StudioButtonStyle.Text,
            enabled = !isSubmitting,
        )
        if (notice != null) {
            StudioText(notice.asString(), StudioTheme.typography.bodySmall, color = StudioTheme.colors.error.content)
        }
    }
}

@PreviewLightDark
@Composable
private fun DemoSectionPreview() {
    PreviewScreen {
        Column {
            DemoSection(isSubmitting = false, notice = null, onGenerateClick = {}, onGenerateRejectedClick = {})
            DemoSection(
                isSubmitting = true,
                notice = UiText.Resource(R.string.error_quota_exceeded),
                onGenerateClick = {},
                onGenerateRejectedClick = {},
            )
        }
    }
}
