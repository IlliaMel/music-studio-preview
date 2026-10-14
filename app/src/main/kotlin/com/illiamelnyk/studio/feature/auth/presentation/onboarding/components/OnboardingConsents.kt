package com.illiamelnyk.studio.feature.auth.presentation.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioFilterChip
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The required confirmations: being 18 or older and the current terms, each only when the server
 * still misses it. Chips stand in for check boxes until the kit has them; TalkBack reads both as
 * check boxes.
 */
@Composable
fun OnboardingConsents(
    askAge: Boolean,
    askTerms: Boolean,
    ageConfirmed: Boolean,
    termsAccepted: Boolean,
    onAgeClick: () -> Unit,
    onTermsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s)) {
        if (askAge) {
            StudioFilterChip(
                text = stringResource(R.string.onboarding_age),
                selected = ageConfirmed,
                onClick = onAgeClick,
            )
        }
        if (askTerms) {
            StudioFilterChip(
                text = stringResource(R.string.onboarding_terms),
                selected = termsAccepted,
                onClick = onTermsClick,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun OnboardingConsentsPreview() {
    PreviewScreen {
        OnboardingConsents(
            askAge = true,
            askTerms = true,
            ageConfirmed = true,
            termsAccepted = false,
            onAgeClick = {},
            onTermsClick = {},
        )
    }
}
