package com.illiamelnyk.studio.feature.profile.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.session.DeleteAccount
import com.illiamelnyk.studio.core.domain.session.DisplayName
import com.illiamelnyk.studio.core.domain.session.ProfileUpdate
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.session.SignOut
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.domain.settings.ThemePreferenceRepository
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.toUiText
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val sessions: SessionRepository,
    credits: CreditsRepository,
    private val themes: ThemePreferenceRepository,
    private val signOut: SignOut,
    private val deleteAccount: DeleteAccount,
    private val toasts: ToastController,
) : MviViewModel<ProfileState, ProfileIntent, ProfileEffect>(ProfileState()) {

    init {
        combine(sessions.session, credits.credits, themes.preference) { session, credits, theme ->
            Triple(session, credits, theme)
        }
            .onEach { (session, credits, theme) ->
                setState {
                    copy(
                        name = if (nameEdited) name else session?.user?.displayName.orEmpty(),
                        user = session?.user?.let { user ->
                            ProfileUserUi(
                                name = user.displayName ?: user.email.orEmpty(),
                                email = user.email,
                                avatarSeed = user.avatarSeed.value,
                            )
                        },
                        credits = UiText.Plural(
                            R.plurals.profile_credits_left,
                            credits.remaining,
                            credits.remaining,
                            credits.dailyLimit,
                        ),
                        theme = theme,
                        dataMode = session?.dataMode ?: SessionDataMode.LocalDemo,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: ProfileIntent) = when (intent) {
        ProfileIntent.BackClicked -> if (!state.value.isSaving && !state.value.isDeleting &&
            !state.value.isSigningOut
        ) {
            sendEffect(ProfileEffect.Close)
        } else {
            Unit
        }

        is ProfileIntent.NameChanged -> setState { copy(name = intent.name, nameEdited = true, nameError = null) }

        ProfileIntent.SaveClicked -> save()

        ProfileIntent.DeleteClicked -> setState { copy(confirmDelete = true) }

        ProfileIntent.DeleteDismissed -> setState { copy(confirmDelete = false) }

        ProfileIntent.DeleteConfirmed -> delete()

        is ProfileIntent.ThemeSelected -> selectTheme(intent.theme)

        ProfileIntent.SignOutClicked -> leave()
    }

    private fun save() {
        if (state.value.isSaving || state.value.isDeleting || state.value.isSigningOut) return
        val parsed = DisplayName.parse(state.value.name)
        if (parsed !is DisplayName.Parsed.Valid) {
            setState { copy(nameError = UiText.Resource(R.string.profile_name_error)) }
            return
        }
        setState { copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = sessions.updateProfile(ProfileUpdate(displayName = parsed.name))) {
                is Outcome.Success -> {
                    setState { copy(isSaving = false, nameEdited = false) }
                    toasts.show(UiText.Resource(R.string.profile_saved))
                    sendEffect(ProfileEffect.Close)
                }

                is Outcome.Failure -> {
                    setState { copy(isSaving = false) }
                    toasts.show(result.error.toUiText(), isError = true)
                }
            }
        }
    }

    private fun delete() {
        if (!state.value.confirmDelete || state.value.isDeleting || state.value.isSaving ||
            state.value.isSigningOut
        ) {
            return
        }
        setState { copy(confirmDelete = false, isDeleting = true) }
        viewModelScope.launch {
            val result = deleteAccount()
            if (result is Outcome.Failure) {
                setState { copy(isDeleting = false) }
                toasts.show(result.error.toUiText(), isError = true)
            }
        }
    }

    private fun selectTheme(theme: ThemePreference) {
        viewModelScope.launch { themes.set(theme) }
    }

    /** Signing out takes the app back to the welcome screen by itself; nothing to navigate. */
    private fun leave() {
        if (state.value.isSigningOut || state.value.isDeleting || state.value.isSaving) return
        setState { copy(isSigningOut = true) }
        viewModelScope.launch { signOut() }
    }
}
