package com.illiamelnyk.studio.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.BackTopBar
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.presentation.withoutTop
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.profile.presentation.components.ProfileHeader
import com.illiamelnyk.studio.feature.profile.presentation.components.SignOutSection
import com.illiamelnyk.studio.feature.profile.presentation.components.ThemeSection

/**
 * The profile: the account, the theme, [debugSettings] in debug builds, and Sign out.
 */
@Composable
fun ProfileScreen(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    debugSettings: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        BackTopBar(onBack = { onIntent(ProfileIntent.BackClicked) }, title = stringResource(R.string.nav_profile))
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding.withoutTop())
                .padding(StudioTheme.dimens.layout.screenPadding),
            verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.layout.sectionGap),
        ) {
            val user = state.user
            if (user != null) ProfileHeader(user = user, credits = state.credits)
            ThemeSection(theme = state.theme, onThemeSelect = { onIntent(ProfileIntent.ThemeSelected(it)) })
            debugSettings?.invoke()
            SignOutSection(isSigningOut = state.isSigningOut, onSignOutClick = {
                onIntent(ProfileIntent.SignOutClicked)
            })
        }
    }
}

@PreviewLightDark
@Composable
private fun ProfileScreenPreview(@PreviewParameter(ProfilePreviewData::class) state: ProfileState) {
    PreviewScreen { ProfileScreen(state = state, onIntent = {}) }
}
