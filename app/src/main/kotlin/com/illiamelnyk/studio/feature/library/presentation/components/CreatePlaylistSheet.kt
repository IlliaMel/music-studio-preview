package com.illiamelnyk.studio.feature.library.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.StudioBottomSheet
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioTextField
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

@Composable
fun CreatePlaylistSheet(
    visible: Boolean,
    name: String,
    nameInvalid: Boolean,
    failed: Boolean,
    busy: Boolean,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StudioBottomSheet(visible, onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.imePadding()) {
            StudioText(stringResource(R.string.library_create_playlist), StudioTheme.typography.titleMedium)
            StudioTextField(
                name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.library_playlist_name),
                enabled = !busy,
                errorText = if (nameInvalid) stringResource(R.string.library_name_error) else null,
            )
            if (failed) StudioText(stringResource(R.string.library_playlist_error), StudioTheme.typography.bodyMedium)
            StudioButton(stringResource(R.string.library_create), onClick = onCreate, enabled = !busy)
        }
    }
}
