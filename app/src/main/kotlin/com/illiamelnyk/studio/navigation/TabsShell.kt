package com.illiamelnyk.studio.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.mini.MiniPlayerHost
import kotlinx.collections.immutable.toImmutableList

/**
 * The signed-in app: the screens of the selected tab with the shell's bars around them, laid out
 * as [variant] says. Each tab keeps its own back stack, saved state and ViewModels while another
 * tab is shown. Leaving the shell (signing out, switching the variant) drops all of them.
 */
@Composable
fun TabsShell(
    variant: ShellVariant,
    avatarSeed: String,
    creditsLeft: Int,
    hasPlayback: Boolean,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberTabNavigator(variant.tabs)
    val atTabRoot = navigator.isAtTabRoot
    val showsMiniPlayer = hasPlayback && navigator.current != Destination.Player
    val padding = rememberUpdatedState(shellPadding(variant, showsMiniPlayer))
    val transitions = rememberShellTransitions()
    val entryProvider = remember(navigator, transitions) { signedInEntries(navigator, padding, transitions) }
    // Every tab's entries stay decorated, shown or not, so a hidden tab keeps its state.
    val entriesByTab = navigator.tabs.associateWith { tab ->
        key(tab) {
            rememberDecoratedNavEntries(
                backStack = navigator.backStack(tab),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
            )
        }
    }
    val motion = StudioTheme.motion
    Box(modifier.fillMaxSize()) {
        NavDisplay(
            entries = navigator.tabsInUse.flatMap { entriesByTab.getValue(it) },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = transitions.push,
            popTransitionSpec = transitions.pop,
            onBack = { navigator.goBack() },
        )
        AnimatedVisibility(
            visible = atTabRoot,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(motion.duration.medium)),
            exit = fadeOut(tween(motion.duration.short)),
        ) {
            ShellTopBar(
                variant = variant,
                selectedTab = navigator.selectedTab,
                avatarSeed = avatarSeed,
                creditsLeft = creditsLeft,
                onTabSelect = navigator::select,
                onProfileClick = { navigator.navigate(Destination.Profile) },
                onLibraryClick = { navigator.navigate(Destination.Library) },
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            MiniPlayerHost(
                visible = showsMiniPlayer,
                onOpenPlayer = { navigator.navigate(Destination.Player) },
                modifier = Modifier.padding(
                    horizontal = StudioTheme.dimens.layout.screenPadding,
                    vertical = StudioTheme.dimens.spacing.xs,
                ),
            )
            val showsBottomBar = variant == ShellVariant.BottomBar && atTabRoot
            AnimatedVisibility(
                visible = showsBottomBar,
                enter = expandVertically(tween(motion.duration.medium, easing = motion.easing.standard)),
                exit = shrinkVertically(tween(motion.duration.medium, easing = motion.easing.standard)),
            ) {
                ShellNavigationBar(
                    tabs = remember(variant) { variant.tabs.toImmutableList() },
                    selectedTab = navigator.selectedTab,
                    onTabSelect = navigator::select,
                )
            }
            // Behind the system navigation bar: the bar's background while it shows, the screen otherwise.
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .then(if (showsBottomBar) Modifier.background(StudioTheme.colors.background) else Modifier)
                    .windowInsetsBottomHeight(WindowInsets.navigationBars),
            )
        }
    }
}
