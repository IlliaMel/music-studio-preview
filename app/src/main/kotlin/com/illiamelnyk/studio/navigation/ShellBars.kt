package com.illiamelnyk.studio.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.CreditsCounter
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioNavigationBar
import com.illiamelnyk.studio.core.ui.components.StudioNavigationItem
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.components.TopBar
import com.illiamelnyk.studio.core.ui.components.TopBarAvatar
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The top bar over a tab's first screen, under the status bar: the avatar that opens the profile,
 * then, by [variant], the Create | Listen switch or the tab's title, and the tab's actions:
 * credits on Create, Library on Listen when Library isn't a tab of its own.
 */
@Composable
fun ShellTopBar(
    variant: ShellVariant,
    selectedTab: TopLevelTab,
    avatarSeed: String,
    creditsLeft: Int,
    onTabSelect: (TopLevelTab) -> Unit,
    onProfileClick: () -> Unit,
    onLibraryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    TopBar(
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

                TopLevelTab.Listen -> if (TopLevelTab.Library !in variant.tabs) {
                    StudioIconButton(StudioIcons.LibraryMusic, stringResource(R.string.nav_library), onLibraryClick)
                }

                TopLevelTab.Library -> Unit
            }
        },
    ) {
        when (variant) {
            ShellVariant.TopSwitch -> StudioSegmentedControl(
                options = variant.tabs,
                selected = selectedTab,
                onSelect = onTabSelect,
            ) { tab -> stringResource(tab.label) }

            ShellVariant.BottomBar -> Crossfade(
                targetState = selectedTab,
                animationSpec = tween(StudioTheme.motion.duration.medium),
                label = "tabTitle",
            ) { tab -> StudioText(stringResource(tab.label), StudioTheme.typography.headlineLarge, maxLines = 1) }
        }
    }
}

/**
 * The bottom bar of [ShellVariant.BottomBar] on the screen background. The space for the system
 * navigation bar below it is the shell's.
 */
@Composable
fun ShellNavigationBar(
    tabs: ImmutableList<TopLevelTab>,
    selectedTab: TopLevelTab,
    onTabSelect: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    StudioNavigationBar(
        items = tabs.map { tab -> StudioNavigationItem(stringResource(tab.label), tab.icon, tab.selectedIcon) },
        selectedIndex = tabs.indexOf(selectedTab),
        onSelect = { index -> onTabSelect(tabs[index]) },
        modifier = modifier
            .background(StudioTheme.colors.background)
            .padding(
                start = dimens.layout.screenPadding,
                end = dimens.layout.screenPadding,
                bottom = dimens.spacing.xs,
            ),
    )
}

@PreviewLightDark
@Composable
private fun ShellBarsPreview() {
    PreviewScreen {
        Column {
            ShellTopBar(
                variant = ShellVariant.TopSwitch,
                selectedTab = TopLevelTab.Listen,
                avatarSeed = "dev-illia",
                creditsLeft = 7,
                onTabSelect = {},
                onProfileClick = {},
                onLibraryClick = {},
            )
            ShellTopBar(
                variant = ShellVariant.BottomBar,
                selectedTab = TopLevelTab.Create,
                avatarSeed = "dev-illia",
                creditsLeft = 7,
                onTabSelect = {},
                onProfileClick = {},
                onLibraryClick = {},
            )
            ShellNavigationBar(
                tabs = ShellVariant.BottomBar.tabs.toImmutableList(),
                selectedTab = TopLevelTab.Listen,
                onTabSelect = {},
            )
        }
    }
}
