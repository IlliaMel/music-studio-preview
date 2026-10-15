package com.illiamelnyk.studio.feature.profile.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

@Composable
fun ProfileAccountActions(busy: Boolean, onSignOut: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s)) {
        StudioButton(
            stringResource(R.string.profile_sign_out),
            onSignOut,
            Modifier.fillMaxWidth(),
            enabled = !busy,
            style = StudioButtonStyle.Secondary,
            size = StudioButtonSize.Large,
        )
        StudioButton(
            stringResource(R.string.profile_delete),
            onDelete,
            Modifier.fillMaxWidth(),
            enabled = !busy,
            style = StudioButtonStyle.Text,
        )
    }
}

@PreviewLightDark
@Composable
private fun ProfileAccountActionsPreview() {
    PreviewScreen { ProfileAccountActions(busy = false, onSignOut = {}, onDelete = {}) }
}
