package com.illiamelnyk.studio.feature.profile.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.session.SignOut
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.domain.settings.ThemePreferenceRepository
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.UiText
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ProfileViewModel(
    sessions: SessionRepository,
    credits: CreditsRepository,
    private val themes: ThemePreferenceRepository,
    private val signOut: SignOut,
) : MviViewModel<ProfileState, ProfileIntent, ProfileEffect>(ProfileState()) {

    init {
        combine(sessions.session, credits.credits, themes.preference) { session, credits, theme ->
            Triple(session, credits, theme)
        }
            .onEach { (session, credits, theme) ->
                setState {
                    copy(
                        user = session?.user?.let { user ->
                            ProfileUserUi(
                                name = user.displayName ?: user.email.orEmpty(),
                                email = user.email,
                                avatarSeed = user.avatarSeed.value,
                            )
                        },
                        credits = UiText.Plural(
                            R.plurals.profile_credits_left,
                            credits.dailyLimit,
                            credits.remaining,
                            credits.dailyLimit,
                        ),
                        theme = theme,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: ProfileIntent) = when (intent) {
        ProfileIntent.BackClicked -> sendEffect(ProfileEffect.Close)
        is ProfileIntent.ThemeSelected -> selectTheme(intent.theme)
        ProfileIntent.SignOutClicked -> leave()
    }

    private fun selectTheme(theme: ThemePreference) {
        viewModelScope.launch { themes.set(theme) }
    }

    /** Signing out takes the app back to the welcome screen by itself; nothing to navigate. */
    private fun leave() {
        if (state.value.isSigningOut) return
        setState { copy(isSigningOut = true) }
        viewModelScope.launch { signOut() }
    }
}
