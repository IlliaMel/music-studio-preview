package com.illiamelnyk.studio.feature.profile.presentation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.presentation.UiText

/** The profile of a signed-in user, while signing out, and before the session arrives. */
class ProfilePreviewData : PreviewParameterProvider<ProfileState> {
    private val signedIn = ProfileState(
        user = ProfileUserUi(name = "Illia Melnyk", email = "illia@dev.local", avatarSeed = "dev-illia"),
        credits = UiText.Plural(R.plurals.profile_credits_left, 8, 7, 8),
        theme = ThemePreference.Dark,
        name = "Illia Melnyk",
    )

    override val values = sequenceOf(
        signedIn,
        signedIn.copy(isSigningOut = true),
        signedIn.copy(isSaving = true),
        signedIn.copy(name = "", nameError = UiText.Resource(R.string.profile_name_error)),
        signedIn.copy(confirmDelete = true),
        ProfileState(),
    )
}
