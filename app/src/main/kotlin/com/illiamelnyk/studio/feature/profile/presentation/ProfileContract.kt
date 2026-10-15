package com.illiamelnyk.studio.feature.profile.presentation

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.presentation.UiText

/**
 * The profile: who is signed in, how many tracks are left today, the theme, and signing out.
 *
 * @param user `null` until the session arrives, and after signing out.
 */
@Immutable
data class ProfileState(
    val user: ProfileUserUi? = null,
    val credits: UiText? = null,
    val theme: ThemePreference = ThemePreference.System,
    val isSigningOut: Boolean = false,
    val name: String = "",
    val nameEdited: Boolean = false,
    val nameError: UiText? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val confirmDelete: Boolean = false,
)

/** @param email `null` when the sign-in provider hides it. */
@Immutable
data class ProfileUserUi(val name: String, val email: String?, val avatarSeed: String)

sealed interface ProfileIntent {
    data class NameChanged(val name: String) : ProfileIntent
    data object SaveClicked : ProfileIntent
    data object DeleteClicked : ProfileIntent
    data object DeleteConfirmed : ProfileIntent
    data object DeleteDismissed : ProfileIntent
    data object BackClicked : ProfileIntent

    data class ThemeSelected(val theme: ThemePreference) : ProfileIntent

    data object SignOutClicked : ProfileIntent
}

sealed interface ProfileEffect {
    data object Close : ProfileEffect
}
