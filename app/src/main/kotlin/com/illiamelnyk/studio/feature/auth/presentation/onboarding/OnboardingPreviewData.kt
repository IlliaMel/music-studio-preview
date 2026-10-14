package com.illiamelnyk.studio.feature.auth.presentation.onboarding

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.session.OnboardingStep
import com.illiamelnyk.studio.core.presentation.UiText
import kotlinx.collections.immutable.persistentSetOf

/** Onboarding: a new account, one with everything answered, only new terms, and a failed save. */
class OnboardingPreviewData : PreviewParameterProvider<OnboardingState> {
    private val newAccount = OnboardingState(
        missing = persistentSetOf(OnboardingStep.DisplayName, OnboardingStep.Age, OnboardingStep.Terms),
        suggestedName = "Illia Melnyk",
    )

    override val values = sequenceOf(
        newAccount,
        newAccount.copy(useSuggestedName = true, ageConfirmed = true, termsAccepted = true, isSaving = true),
        OnboardingState(missing = persistentSetOf(OnboardingStep.Terms)),
        newAccount.copy(notice = UiText.Resource(R.string.error_network)),
    )
}
