package com.illiamelnyk.studio.feature.profile.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

@Composable
fun ProfileAbout(modifier: Modifier = Modifier, dataMode: SessionDataMode = SessionDataMode.LocalDemo) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs)) {
        StudioText(stringResource(R.string.profile_backend_mode), StudioTheme.typography.titleSmall)
        StudioText(
            stringResource(
                if (dataMode ==
                    SessionDataMode.LocalDemo
                ) {
                    R.string.profile_backend_mock
                } else {
                    R.string.profile_backend_development_server
                },
            ),
            StudioTheme.typography.bodySmall,
            color = StudioTheme.colors.content.secondary,
        )
        StudioText(stringResource(R.string.profile_about), StudioTheme.typography.titleSmall)
        StudioText(
            stringResource(R.string.profile_about_detail),
            StudioTheme.typography.bodySmall,
            color = StudioTheme.colors.content.secondary,
        )
    }
}

@PreviewLightDark
@Composable
private fun ProfileAboutPreview() {
    PreviewScreen { ProfileAbout() }
}
