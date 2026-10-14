package com.illiamelnyk.studio.feature.auth.presentation.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/** Continue, and a way out to another account; [notice] says why the last save failed. */
@Composable
fun OnboardingActions(
    canContinue: Boolean,
    isSaving: Boolean,
    notice: UiText?,
    onContinueClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimens.layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (notice != null) {
            StudioText(notice.asString(), StudioTheme.typography.bodySmall, color = StudioTheme.colors.error.content)
        }
        StudioButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onContinueClick,
            modifier = Modifier.fillMaxWidth(),
            size = StudioButtonSize.Large,
            enabled = canContinue,
            loading = isSaving,
        )
        StudioButton(
            text = stringResource(R.string.onboarding_sign_out),
            onClick = onSignOutClick,
            style = StudioButtonStyle.Text,
            enabled = !isSaving,
        )
    }
}

@PreviewLightDark
@Composable
private fun OnboardingActionsPreview() {
    PreviewScreen {
        OnboardingActions(
            canContinue = false,
            isSaving = false,
            notice = null,
            onContinueClick = {},
            onSignOutClick = {},
        )
    }
}
