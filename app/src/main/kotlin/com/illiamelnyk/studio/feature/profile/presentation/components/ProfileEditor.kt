package com.illiamelnyk.studio.feature.profile.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioTextField
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

@Composable
fun ProfileEditor(
    name: String,
    nameError: UiText?,
    canSave: Boolean,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StudioIconButton(StudioIcons.Close, stringResource(R.string.profile_close), onClose)
            StudioText(
                stringResource(R.string.profile_settings),
                StudioTheme.typography.titleLarge,
                Modifier.weight(1f),
            )
            StudioButton(
                stringResource(R.string.profile_save),
                onSave,
                style = StudioButtonStyle.Text,
                enabled = canSave,
                loading = isSaving,
            )
        }
        StudioTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = stringResource(R.string.profile_name),
            errorText = nameError?.asString(),
            enabled = canSave && !isSaving,
        )
    }
}

@PreviewLightDark
@Composable
private fun ProfileEditorPreview() {
    PreviewScreen {
        ProfileEditor(
            name = "Demo listener",
            nameError = null,
            canSave = true,
            isSaving = false,
            onNameChange = {},
            onSave = {},
            onClose = {},
        )
    }
}
