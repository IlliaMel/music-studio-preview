package com.illiamelnyk.studio.feature.profile.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/** "Theme": System, Light or Dark. The app switches as soon as one is picked. */
@Composable
fun ThemeSection(theme: ThemePreference, onThemeSelect: (ThemePreference) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.layout.inlineGap),
    ) {
        StudioText(stringResource(R.string.profile_theme), StudioTheme.typography.titleMedium)
        StudioSegmentedControl(
            options = ThemePreference.entries,
            selected = theme,
            onSelect = onThemeSelect,
        ) { option ->
            stringResource(
                when (option) {
                    ThemePreference.System -> R.string.profile_theme_system
                    ThemePreference.Light -> R.string.profile_theme_light
                    ThemePreference.Dark -> R.string.profile_theme_dark
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ThemeSectionPreview() {
    PreviewScreen { ThemeSection(theme = ThemePreference.System, onThemeSelect = {}) }
}
