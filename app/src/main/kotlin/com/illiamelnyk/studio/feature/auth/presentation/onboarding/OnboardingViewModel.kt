package com.illiamelnyk.studio.feature.auth.presentation.onboarding

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.session.SignOut
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.toUiText
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class OnboardingViewModel(private val sessions: SessionRepository, private val signOut: SignOut) :
    MviViewModel<OnboardingState, OnboardingIntent, Nothing>(OnboardingState()) {

    /** The terms version the server asks for, which an accepted update has to name. */
    private var currentTermsVersion: String? = null

    init {
        sessions.session
            .filterNotNull()
            .onEach { session ->
                currentTermsVersion = session.onboarding.currentTermsVersion
                setState {
                    copy(
                        missing = session.onboarding.missing.toImmutableSet(),
                        suggestedName = session.suggestedDisplayName,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: OnboardingIntent) = when (intent) {
        OnboardingIntent.SuggestedNameToggled -> setState { copy(useSuggestedName = !useSuggestedName) }
        OnboardingIntent.AgeToggled -> setState { copy(ageConfirmed = !ageConfirmed) }
        OnboardingIntent.TermsToggled -> setState { copy(termsAccepted = !termsAccepted) }
        OnboardingIntent.MarketingToggled -> setState { copy(marketingOptIn = !marketingOptIn) }
        OnboardingIntent.ContinueClicked -> save()
        OnboardingIntent.SignOutClicked -> leave()
    }

    private fun leave() {
        viewModelScope.launch { signOut() }
    }

    private fun save() {
        val state = state.value
        val termsVersion = currentTermsVersion
        if (!state.canContinue || state.isSaving || termsVersion == null) return
        setState { copy(isSaving = true, notice = null) }
        viewModelScope.launch {
            val result = sessions.updateProfile(state.toProfileUpdate(termsVersion))
            // On success the app moves on to the tabs; until then the button keeps its spinner.
            if (result is Outcome.Failure) setState { copy(isSaving = false, notice = result.error.toUiText()) }
        }
    }
}
