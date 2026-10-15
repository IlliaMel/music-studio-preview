package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.PromptFix

/**
 * What can be done about a failed track, under its row: put the [fix] of a refused prompt in the
 * composer, or retry a generation that failed for another reason; and delete it either way.
 */
@Composable
fun FailedTrackActions(
    fix: PromptFix?,
    canRetry: Boolean,
    onFixClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            fix != null -> StudioButton(
                text = stringResource(
                    if (fix.isSuggestion) R.string.create_use_suggestion else R.string.create_edit_prompt,
                ),
                onClick = onFixClick,
                style = StudioButtonStyle.Secondary,
                size = StudioButtonSize.Small,
                icon = if (fix.isSuggestion) StudioIcons.AutoFixHigh else StudioIcons.Edit,
            )

            canRetry -> StudioButton(
                text = stringResource(R.string.ui_retry),
                onClick = onRetryClick,
                style = StudioButtonStyle.Secondary,
                size = StudioButtonSize.Small,
                icon = StudioIcons.Refresh,
            )
        }
        StudioButton(
            text = stringResource(R.string.create_menu_delete),
            onClick = onDeleteClick,
            style = StudioButtonStyle.Text,
            size = StudioButtonSize.Small,
        )
    }
}

@PreviewLightDark
@Composable
private fun FailedTrackActionsPreview() {
    PreviewScreen {
        Column {
            FailedTrackActions(
                fix = PromptFix("Indie rock with an original sound", isSuggestion = true),
                canRetry = false,
                onFixClick = {},
                onRetryClick = {},
                onDeleteClick = {},
            )
            FailedTrackActions(fix = null, canRetry = true, onFixClick = {}, onRetryClick = {}, onDeleteClick = {})
        }
    }
}
