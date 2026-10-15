package com.illiamelnyk.studio.feature.auth.presentation.welcome.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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

/** The name, the promise and the sign-in button; [notice] says why the last sign-in failed. */
@Composable
fun WelcomeActions(
    isSigningIn: Boolean,
    notice: UiText?,
    onContinueWithGoogleClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTryDemoClick: () -> Unit = {},
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StudioText(stringResource(R.string.app_name), StudioTheme.typography.headlineLarge)
        StudioText(
            text = stringResource(R.string.welcome_title),
            style = StudioTheme.typography.titleMedium,
            color = StudioTheme.colors.content.secondary,
            textAlign = TextAlign.Center,
        )
        StudioButton(
            text = stringResource(R.string.welcome_continue_with_google),
            onClick = onContinueWithGoogleClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.spacing.m),
            size = StudioButtonSize.Large,
            loading = isSigningIn,
        )
        StudioText(
            stringResource(R.string.welcome_terms),
            StudioTheme.typography.bodySmall,
            color = StudioTheme.colors.content.tertiary,
            textAlign = TextAlign.Center,
        )
        // Firebase is not configured in this prototype, so demo is available in release too.
        StudioButton(
            text = stringResource(R.string.welcome_try_demo),
            onClick = onTryDemoClick,
            style = StudioButtonStyle.Text,
            enabled = !isSigningIn,
        )
        if (notice != null) {
            StudioText(notice.asString(), StudioTheme.typography.bodySmall, color = StudioTheme.colors.error.content)
        }
    }
}

@PreviewLightDark
@Composable
private fun WelcomeActionsPreview() {
    PreviewScreen {
        WelcomeActions(isSigningIn = false, notice = null, onContinueWithGoogleClick = {})
    }
}
