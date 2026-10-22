package com.illiamelnyk.studio.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.AdaptiveTopBar
import com.illiamelnyk.studio.core.ui.components.ComingSoonSheetContent
import com.illiamelnyk.studio.core.ui.components.CreditsCounter
import com.illiamelnyk.studio.core.ui.components.StudioBottomSheet
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.components.TopBarAvatar
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The top bar over a tab's first screen, under the status bar: the avatar that opens the profile,
 * the Create | Listen switch and the selected tab's actions: credits on Create, Library on Listen.
 */
@Composable
fun ShellTopBar(
    selectedTab: TopLevelTab,
    avatarSeed: String,
    creditsLeft: Int,
    onTabSelect: (TopLevelTab) -> Unit,
    onProfileClick: () -> Unit,
    onLibraryClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
) {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    AdaptiveTopBar(
        modifier = modifier
            .background(StudioTheme.colors.background)
            .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        navigation = {
            TopBarAvatar(
                seed = avatarSeed,
                contentDescription = stringResource(R.string.nav_profile),
                onClick = onProfileClick,
            )
        },
        actions = {
            when (selectedTab) {
                TopLevelTab.Create -> CreditsCounter(left = creditsLeft)

                TopLevelTab.Listen -> {
                    StudioIconButton(
                        StudioIcons.Search,
                        stringResource(R.string.listen_search),
                        onSearchClick,
                        comingSoon = true,
                    )
                    StudioIconButton(StudioIcons.Bookmark, stringResource(R.string.nav_library), onLibraryClick)
                }

                TopLevelTab.Library -> Unit
            }
        },
    ) {
        StudioSegmentedControl(
            options = TopLevelTab.primaryTabs,
            selected = selectedTab,
            onSelect = onTabSelect,
        ) { tab -> stringResource(tab.label) }
    }
}

@PreviewLightDark
@Composable
private fun ShellBarsPreview() {
    PreviewScreen {
        Column {
            ShellTopBar(
                selectedTab = TopLevelTab.Listen,
                avatarSeed = "dev-illia",
                creditsLeft = 7,
                onTabSelect = {},
                onProfileClick = {},
                onLibraryClick = {},
            )
            ShellTopBar(
                selectedTab = TopLevelTab.Create,
                avatarSeed = "dev-illia",
                creditsLeft = 7,
                onTabSelect = {},
                onProfileClick = {},
                onLibraryClick = {},
            )
        }
    }
}

@Composable
internal fun ShellSearchSheet(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    StudioBottomSheet(visible = visible, onDismissRequest = onDismiss, modifier = modifier) {
        ComingSoonSheetContent(
            title = stringResource(R.string.listen_search),
            description = stringResource(R.string.listen_search_soon),
            steps = emptyList(),
            actionLabel = stringResource(R.string.listen_close),
            onAction = onDismiss,
        )
    }
}
