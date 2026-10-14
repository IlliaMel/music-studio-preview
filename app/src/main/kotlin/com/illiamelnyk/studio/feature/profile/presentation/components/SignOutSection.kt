package com.illiamelnyk.studio.feature.profile.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle

/** Sign out, which takes the app back to the welcome screen. */
@Composable
fun SignOutSection(isSigningOut: Boolean, onSignOutClick: () -> Unit, modifier: Modifier = Modifier) {
    StudioButton(
        text = stringResource(R.string.profile_sign_out),
        onClick = onSignOutClick,
        modifier = modifier.fillMaxWidth(),
        style = StudioButtonStyle.Secondary,
        loading = isSigningOut,
    )
}

@PreviewLightDark
@Composable
private fun SignOutSectionPreview() {
    PreviewScreen { SignOutSection(isSigningOut = false, onSignOutClick = {}) }
}
