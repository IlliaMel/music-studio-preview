package com.illiamelnyk.studio.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.illiamelnyk.studio.core.domain.notification.GenerationNotificationTargets
import com.illiamelnyk.studio.core.domain.notification.OpenGenerationNotification
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.core.ui.foundation.ProvideGlassBackdrop
import com.illiamelnyk.studio.core.ui.foundation.glassBackdrop
import com.illiamelnyk.studio.core.ui.foundation.rememberGlassBackdrop
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.host.PlayerHost
import com.illiamelnyk.studio.feature.player.presentation.host.PlayerHostScope
import com.illiamelnyk.studio.feature.player.presentation.mini.MiniPlayerHost
import com.illiamelnyk.studio.feature.profile.presentation.ProfileOverlay
import org.koin.compose.koinInject

/**
 * The signed-in app: Create and Listen with a fixed top switch. Each tab keeps its own back stack,
 * saved state and ViewModels while another tab is shown. Signing out drops all of them.
 *
 * Like a scaffold, the shell measures its bars along the bottom first and gives the screens that
 * height as their bottom padding, so lists scroll behind the mini player and system navigation
 * inset. The screens are the backdrop the glass mini player blurs.
 *
 * The full player opens over all of it, from the mini player: see [PlayerHost].
 */
@Composable
fun TabsShell(avatarSeed: String, creditsLeft: Int, hasPlayback: Boolean, modifier: Modifier = Modifier) {
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val navigator = rememberTabNavigator()
    val notificationTargets: GenerationNotificationTargets = koinInject()
    val openNotification: OpenGenerationNotification = koinInject()
    val notificationTarget by notificationTargets.pending.collectAsStateWithLifecycle()
    DisposableEffect(notificationTargets) { onDispose { notificationTargets.clear() } }
    val drafts: ComposerDraftStore = koinInject()
    val draftRevision by drafts.revision.collectAsStateWithLifecycle()
    ObserveRemixNavigation(draftRevision, navigator)
    val atTabRoot = navigator.isAtTabRoot
    val bottomChrome = remember { BottomChrome() }
    val topChrome = remember { TopChrome() }
    val padding = rememberShellPadding(bottomChrome, topChrome)
    val paddingState = rememberUpdatedState(padding)
    val transitions = rememberShellTransitions()
    val motion = StudioTheme.motion
    val backdrop = rememberGlassBackdrop()
    PlayerHost(modifier.fillMaxSize()) { player ->
        LaunchedEffect(notificationTarget) {
            val target = notificationTarget ?: return@LaunchedEffect
            when (openNotification(target)) {
                OpenGenerationNotification.Result.Player -> player.expand()

                OpenGenerationNotification.Result.Create -> {
                    navigator.navigate(Destination.Create)
                    navigator.select(TopLevelTab.Create)
                    notificationTargets.focus(target.trackId)
                }

                OpenGenerationNotification.Result.Ignored -> Unit
            }
            notificationTargets.consume(target)
        }
        val entryProvider =
            remember(navigator, transitions, player) {
                signedInEntries(navigator, paddingState, transitions, onOpenPlayer = player::expand)
            }
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
        Layout(
            contents = listOf(
                {
                    NavDisplay(
                        entries = navigator.tabsInUse.flatMap { entriesByTab.getValue(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .glassBackdrop(backdrop),
                        transitionSpec = transitions.push,
                        popTransitionSpec = transitions.pop,
                        predictivePopTransitionSpec = { _ -> transitions.pop(this) },
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
                            selectedTab = navigator.selectedTab,
                            avatarSeed = avatarSeed,
                            creditsLeft = creditsLeft,
                            onTabSelect = navigator::select,
                            onProfileClick = { profileOpen = true },
                            onSearchClick = { searchOpen = true },
                            onLibraryClick = { navigator.navigate(Destination.Library) },
                        )
                    }
                },
                {
                    // The mini player stays above the system navigation inset on every screen.
                    ProvideGlassBackdrop(backdrop) {
                        ShellBottomChrome(
                            player = player,
                            showsMiniPlayer = hasPlayback,
                        )
                    }
                },
            ),
            modifier = Modifier.fillMaxSize(),
        ) { (screens, topBar, bottomBars), constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            // The bars along the bottom first: their height is the screens' bottom padding, read by
            // the screens when they are measured below, in this same pass.
            val bottom = bottomBars.map { it.measure(loose) }
            val bottomHeight = bottom.maxOfOrNull { it.height } ?: 0
            bottomChrome.height = bottomHeight.toDp()
            val top = topBar.map { it.measure(loose) }
            if (atTabRoot) topChrome.height = top.maxOfOrNull { it.height }?.toDp()
            val content = screens.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                content.forEach { it.place(0, 0) }
                top.forEach { it.place(0, 0) }
                bottom.forEach { it.place(0, constraints.maxHeight - bottomHeight) }
            }
        }
    }
    ShellSearchSheet(visible = searchOpen, onDismiss = { searchOpen = false })
    if (profileOpen) ProfileOverlay(onClose = { profileOpen = false })
}

/** A draft revision is a navigation request once, including after saved-state recreation. */
@Composable
internal fun ObserveRemixNavigation(revision: Int, navigator: TabNavigator) {
    var handledRevision by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(revision) {
        if (revision > 0 && revision != handledRevision) navigator.select(TopLevelTab.Create)
        // Clearing the draft resets the counter; a later first remix must be handled again.
        handledRevision = revision
    }
}

/** The mini player and the persistent system-navigation inset, shared by every destination. */
@Composable
private fun ShellBottomChrome(player: PlayerHostScope, showsMiniPlayer: Boolean, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Column(modifier.fillMaxWidth()) {
        MiniPlayerHost(
            host = player,
            visible = showsMiniPlayer,
            modifier = Modifier.padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.xs),
        )
        Spacer(
            Modifier.fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars),
        )
    }
}
