package com.illiamelnyk.studio.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.debug.debugSettings
import com.illiamelnyk.studio.feature.player.presentation.mini.MiniPlayerHost
import com.illiamelnyk.studio.feature.profile.presentation.ProfileOverlay
import kotlinx.collections.immutable.toImmutableList

/**
 * The signed-in app: the screens of the selected tab with the shell's bars around them, laid out
 * as [variant] says. Each tab keeps its own back stack, saved state and ViewModels while another
 * tab is shown. Switching the variant keeps the screens; signing out drops all of them.
 *
 * Like a scaffold, the shell measures its bars along the bottom first and gives the screens that
 * height as their bottom padding, so lists scroll behind the bars and the mini player sits right
 * above the bottom bar.
 */
@Composable
fun TabsShell(
    variant: ShellVariant,
    avatarSeed: String,
    creditsLeft: Int,
    hasPlayback: Boolean,
    modifier: Modifier = Modifier,
) {
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    val navigator = rememberTabNavigator(variant.tabs)
    val atTabRoot = navigator.isAtTabRoot
    val bottomChrome = remember { BottomChrome() }
    val padding = rememberShellPadding(bottomChrome)
    val paddingState = rememberUpdatedState(padding)
    val transitions = rememberShellTransitions()
    val entryProvider = remember(navigator, transitions) { signedInEntries(navigator, paddingState, transitions) }
    // Every tab's entries stay decorated, shown or not, so a hidden tab keeps its state.
    val entriesByTab = TopLevelTab.entries.associateWith { tab ->
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
    Layout(
        contents = listOf(
            {
                NavDisplay(
                    entries = navigator.tabsInUse.flatMap { entriesByTab.getValue(it) },
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = transitions.push,
                    popTransitionSpec = transitions.pop,
                    onBack = { navigator.goBack() },
                )
            },
            {
                AnimatedVisibility(
                    visible = atTabRoot,
                    enter = fadeIn(tween(motion.duration.medium)),
                    exit = fadeOut(tween(motion.duration.short)),
                ) {
                    ShellTopBar(
                        variant = variant,
                        selectedTab = navigator.selectedTab,
                        avatarSeed = avatarSeed,
                        creditsLeft = creditsLeft,
                        onTabSelect = navigator::select,
                        onProfileClick = { profileOpen = true },
                        onLibraryClick = { navigator.navigate(Destination.Library) },
                    )
                }
            },
            {
                // The bottom bar stays on every screen but the player, so a tab can be left mid-way
                // and found as it was; the top bar belongs to a tab's first screen only.
                val fullScreen = navigator.current == Destination.Player
                ShellBottomChrome(
                    navigator = navigator,
                    showsBottomBar = variant == ShellVariant.BottomBar && !fullScreen,
                    showsMiniPlayer = hasPlayback && !fullScreen,
                )
            },
        ),
        modifier = modifier.fillMaxSize(),
    ) { (screens, topBar, bottomBars), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // The bars along the bottom first: their height is the screens' bottom padding, read by the
        // screens when they are measured below, in this same pass.
        val bottom = bottomBars.map { it.measure(loose) }
        val bottomHeight = bottom.maxOfOrNull { it.height } ?: 0
        bottomChrome.height = bottomHeight.toDp()
        val top = topBar.map { it.measure(loose) }
        val content = screens.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            content.forEach { it.place(0, 0) }
            top.forEach { it.place(0, 0) }
            bottom.forEach { it.place(0, constraints.maxHeight - bottomHeight) }
        }
    }
    if (profileOpen) {
        ProfileOverlay(onClose = { profileOpen = false }, debugSettings = debugSettings)
    }
}

/**
 * Along the bottom edge: the mini player, then the bottom bar of [ShellVariant.BottomBar], which
 * draws behind the system navigation bar. Without the bar, the space of the system navigation bar.
 * The bar and that space swap with the same animation, so the mini player glides instead of
 * jumping.
 */
@Composable
private fun ShellBottomChrome(
    navigator: TabNavigator,
    showsBottomBar: Boolean,
    showsMiniPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = StudioTheme.motion
    val dimens = StudioTheme.dimens
    val expand = expandVertically(tween(motion.duration.medium, easing = motion.easing.standard))
    val shrink = shrinkVertically(tween(motion.duration.medium, easing = motion.easing.standard))
    Column(modifier.fillMaxWidth()) {
        MiniPlayerHost(
            visible = showsMiniPlayer,
            onOpenPlayer = { navigator.navigate(Destination.Player) },
            modifier = Modifier.padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.xs),
        )
        AnimatedVisibility(visible = showsBottomBar, enter = expand, exit = shrink) {
            ShellNavigationBar(
                tabs = remember(navigator.tabs) { navigator.tabs.toImmutableList() },
                selectedTab = navigator.selectedTab,
                onTabSelect = navigator::select,
            )
        }
        AnimatedVisibility(visible = !showsBottomBar, enter = expand, exit = shrink) {
            Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
