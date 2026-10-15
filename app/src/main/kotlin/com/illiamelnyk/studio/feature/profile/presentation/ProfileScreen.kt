package com.illiamelnyk.studio.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.profile.presentation.components.DeleteAccountDialog
import com.illiamelnyk.studio.feature.profile.presentation.components.ProfileAbout
import com.illiamelnyk.studio.feature.profile.presentation.components.ProfileAccountActions
import com.illiamelnyk.studio.feature.profile.presentation.components.ProfileEditor
import com.illiamelnyk.studio.feature.profile.presentation.components.ProfileHeader
import com.illiamelnyk.studio.feature.profile.presentation.components.ThemeSection

/**
 * The profile: the account, the theme, [debugSettings] in debug builds, and Sign out.
 */
@Composable
fun ProfileScreen(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    debugSettings: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxHeight(SHEET_HEIGHT).imePadding().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.m),
    ) {
        ProfileEditor(
            name = state.name,
            nameError = state.nameError,
            canSave = state.user != null && !state.isDeleting && !state.isSigningOut,
            isSaving = state.isSaving,
            onNameChange = { onIntent(ProfileIntent.NameChanged(it)) },
            onSave = { onIntent(ProfileIntent.SaveClicked) },
            onClose = { onIntent(ProfileIntent.BackClicked) },
        )
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.layout.sectionGap),
        ) {
            state.user?.let { ProfileHeader(user = it, credits = state.credits) }
            ThemeSection(theme = state.theme, onThemeSelect = { onIntent(ProfileIntent.ThemeSelected(it)) })
            ProfileAbout()
            debugSettings?.invoke()
            ProfileAccountActions(
                busy = state.isSigningOut || state.isDeleting || state.isSaving,
                onSignOut = { onIntent(ProfileIntent.SignOutClicked) },
                onDelete = { onIntent(ProfileIntent.DeleteClicked) },
            )
        }
    }
    if (state.confirmDelete) {
        DeleteAccountDialog(
            onConfirm = { onIntent(ProfileIntent.DeleteConfirmed) },
            onDismiss = { onIntent(ProfileIntent.DeleteDismissed) },
        )
    }
}

@PreviewLightDark
@Composable
private fun ProfileScreenPreview(@PreviewParameter(ProfilePreviewData::class) state: ProfileState) {
    PreviewScreen { ProfileScreen(state = state, onIntent = {}) }
}

private const val SHEET_HEIGHT = 0.95f
