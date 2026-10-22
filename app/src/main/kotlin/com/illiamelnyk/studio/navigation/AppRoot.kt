package com.illiamelnyk.studio.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The whole app: the flow the session allows, cross-fading when it changes. Signing in leads to
 * straight to the tabs, signing out back to the welcome screen.
 */
@Composable
fun AppRoot(state: AppState, modifier: Modifier = Modifier) {
    val content = when (state.gate) {
        SessionGate.Loading -> RootContent.Loading
        SessionGate.SignedOut -> RootContent.SignedOut
        SessionGate.SignedIn -> RootContent.Tabs
    }
    val transitions = rememberShellTransitions()
    AnimatedContent(
        targetState = content,
        modifier = modifier
            .fillMaxSize()
            .background(StudioTheme.colors.background),
        transitionSpec = {
            if (initialState == RootContent.Loading) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                transitions.fadeThrough(this)
            }
        },
        label = "appRoot",
    ) { target ->
        when (target) {
            // Nothing until the session is known, so neither flow flashes by.
            RootContent.Loading -> Box(Modifier.fillMaxSize())

            RootContent.SignedOut -> SignedOutFlow()

            RootContent.Tabs -> OwnerScopedTabs(state.ownerId) {
                TabsShell(
                    avatarSeed = state.avatarSeed,
                    creditsLeft = state.creditsLeft,
                    hasPlayback = state.hasPlayback,
                )
            }
        }
    }
}

@Composable
internal fun OwnerScopedTabs(ownerId: String?, content: @Composable () -> Unit) {
    key(ownerId) {
        // Unlike a composition key alone, this also isolates non-navigation ViewModels (the
        // full/mini player and their sheets). Lifecycle retains the store through rotation and
        // clears it when this owner's shell permanently leaves the composition.
        val owner = rememberViewModelStoreOwner()
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
    }
}

private sealed interface RootContent {
    data object Loading : RootContent

    data object SignedOut : RootContent

    data object Tabs : RootContent
}
