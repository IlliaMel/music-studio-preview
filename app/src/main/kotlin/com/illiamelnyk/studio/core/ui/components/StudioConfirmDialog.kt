package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.illiamelnyk.studio.core.ui.foundation.FeedbackWindow
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * Asks before something that can't be undone, such as deleting a track: a dialog with the [title],
 * the [message], a secondary button that dismisses and a [confirmLabel] button in the error color.
 * The window behind dims by the theme's scrim.
 */
@Composable
fun StudioConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        FeedbackWindow {
            val scrim = StudioTheme.colors.overlay.scrim
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { window?.setDimAmount(scrim.alpha) }
            val dimens = StudioTheme.dimens
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(StudioTheme.shapes.xxl)
                    .background(StudioTheme.colors.surface.level1)
                    .verticalScroll(rememberScrollState())
                    .padding(dimens.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            ) {
                StudioText(title, StudioTheme.typography.titleLarge)
                StudioText(message, StudioTheme.typography.bodyMedium, color = StudioTheme.colors.content.secondary)
                FlowRow(
                    modifier = Modifier
                        .padding(top = dimens.spacing.m)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
                ) {
                    StudioButton(
                        text = dismissLabel,
                        onClick = onDismissRequest,
                        style = StudioButtonStyle.Secondary,
                    )
                    StudioButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        style = StudioButtonStyle.Danger,
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun StudioConfirmDialogPreview() {
    PreviewSurface {
        StudioConfirmDialog(
            title = "Delete this track?",
            message = "It used credits and can't be restored.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = {},
            onDismissRequest = {},
        )
    }
}
