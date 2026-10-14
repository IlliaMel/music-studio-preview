package com.illiamelnyk.studio.feature.auth.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.core.domain.session.OnboardingStep
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.auth.presentation.onboarding.components.OnboardingActions
import com.illiamelnyk.studio.feature.auth.presentation.onboarding.components.OnboardingConsents
import com.illiamelnyk.studio.feature.auth.presentation.onboarding.components.OnboardingNameStep

/** The missing onboarding steps, then Continue. */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(StudioTheme.dimens.layout.screenPadding),
            verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.layout.sectionGap),
        ) {
            if (OnboardingStep.DisplayName in state.missing) {
                OnboardingNameStep(
                    suggestedName = state.suggestedName,
                    useSuggestedName = state.useSuggestedName,
                    marketingOptIn = state.marketingOptIn,
                    onSuggestedNameClick = { onIntent(OnboardingIntent.SuggestedNameToggled) },
                    onMarketingClick = { onIntent(OnboardingIntent.MarketingToggled) },
                )
            }
            OnboardingConsents(
                askAge = OnboardingStep.Age in state.missing,
                askTerms = OnboardingStep.Terms in state.missing,
                ageConfirmed = state.ageConfirmed,
                termsAccepted = state.termsAccepted,
                onAgeClick = { onIntent(OnboardingIntent.AgeToggled) },
                onTermsClick = { onIntent(OnboardingIntent.TermsToggled) },
            )
        }
        OnboardingActions(
            canContinue = state.canContinue,
            isSaving = state.isSaving,
            notice = state.notice,
            onContinueClick = { onIntent(OnboardingIntent.ContinueClicked) },
            onSignOutClick = { onIntent(OnboardingIntent.SignOutClicked) },
        )
    }
}

@PreviewLightDark
@Composable
private fun OnboardingScreenPreview(@PreviewParameter(OnboardingPreviewData::class) state: OnboardingState) {
    PreviewScreen { OnboardingScreen(state = state, onIntent = {}) }
}
