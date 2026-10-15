package com.illiamelnyk.studio.feature.profile.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioConfirmDialog

@Composable
fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    StudioConfirmDialog(
        title = stringResource(R.string.profile_delete_title),
        message = stringResource(R.string.profile_delete_message),
        confirmLabel = stringResource(R.string.profile_delete),
        dismissLabel = stringResource(R.string.profile_cancel),
        onConfirm = onConfirm,
        onDismissRequest = onDismiss,
    )
}

@PreviewLightDark
@Composable
private fun DeleteAccountDialogPreview() {
    PreviewScreen { DeleteAccountDialog(onConfirm = {}, onDismiss = {}) }
}
