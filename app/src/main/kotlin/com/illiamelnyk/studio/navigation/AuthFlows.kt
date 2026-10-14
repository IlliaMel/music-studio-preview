package com.illiamelnyk.studio.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.auth.presentation.onboarding.OnboardingRoute
import com.illiamelnyk.studio.feature.auth.presentation.welcome.WelcomeRoute

/** Nobody is signed in: the welcome screen, the start of the sign-in flow. */
@Composable
fun SignedOutFlow(modifier: Modifier = Modifier) {
    AuthFlow(start = Destination.Welcome, modifier = modifier) { contentPadding ->
        entryProvider {
            entry<Destination.Welcome> {
                WelcomeRoute(
                    modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

/** Signed in, but the profile misses steps: onboarding, until the server has them all. */
@Composable
fun OnboardingFlow(modifier: Modifier = Modifier) {
    AuthFlow(start = Destination.Onboarding, modifier = modifier) { contentPadding ->
        entryProvider {
            entry<Destination.Onboarding> {
                OnboardingRoute(
                    modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

/**
 * A flow before the tabs, with its own back stack. Its ViewModels live as long as the flow: once
 * the app moves on, they are cleared, and coming back starts the flow afresh.
 */
@Composable
private fun AuthFlow(
    start: Destination,
    modifier: Modifier = Modifier,
    entries: (contentPadding: PaddingValues) -> (NavKey) -> NavEntry<NavKey>,
) {
    val backStack = rememberNavBackStack(start)
    val contentPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val transitions = rememberShellTransitions()
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        // Back on the first screen of a flow leaves the app.
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = transitions.push,
        popTransitionSpec = transitions.pop,
        entryProvider = entries(contentPadding),
    )
}
