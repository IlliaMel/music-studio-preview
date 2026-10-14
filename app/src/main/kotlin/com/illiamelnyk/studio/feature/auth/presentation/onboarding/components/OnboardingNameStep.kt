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
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * "What's your name?": the name the sign-in provider suggests, picked with a chip until the kit
 * has a text field, and whether the user wants updates by email.
 */
@Composable
fun OnboardingNameStep(
    suggestedName: String?,
    useSuggestedName: Boolean,
    marketingOptIn: Boolean,
    onSuggestedNameClick: () -> Unit,
    onMarketingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spacing.s)) {
        StudioText(stringResource(R.string.onboarding_name_title), StudioTheme.typography.headlineLarge)
        StudioText(
            text = stringResource(R.string.onboarding_name_hint),
            style = StudioTheme.typography.bodyMedium,
            color = StudioTheme.colors.content.secondary,
        )
        if (suggestedName != null) {
            StudioFilterChip(text = suggestedName, selected = useSuggestedName, onClick = onSuggestedNameClick)
        }
        StudioFilterChip(
            text = stringResource(R.string.onboarding_marketing),
            selected = marketingOptIn,
            onClick = onMarketingClick,
        )
    }
}

@PreviewLightDark
@Composable
private fun OnboardingNameStepPreview() {
    PreviewScreen {
        OnboardingNameStep(
            suggestedName = "Illia Melnyk",
            useSuggestedName = true,
            marketingOptIn = false,
            onSuggestedNameClick = {},
            onMarketingClick = {},
        )
    }
}
