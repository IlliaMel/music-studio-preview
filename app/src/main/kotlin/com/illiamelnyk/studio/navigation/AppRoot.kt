package com.illiamelnyk.studio.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The whole app: the flow the session allows, cross-fading when it changes. Signing in leads to
 * onboarding or straight to the tabs, signing out back to the welcome screen; switching the shell
 * variant rebuilds the tabs.
 */
@Composable
fun AppRoot(state: AppState, modifier: Modifier = Modifier) {
    val content = when (state.gate) {
        SessionGate.Loading -> RootContent.Loading
        SessionGate.SignedOut -> RootContent.SignedOut
        SessionGate.Onboarding -> RootContent.Onboarding
        SessionGate.SignedIn -> RootContent.Tabs(state.shellVariant)
    }
    val duration = StudioTheme.motion.duration.long
    AnimatedContent(
        targetState = content,
        modifier = modifier
            .fillMaxSize()
            .background(StudioTheme.colors.background),
        transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
        label = "appRoot",
    ) { target ->
        when (target) {
            // Nothing until the session is known, so neither flow flashes by.
            RootContent.Loading -> Box(Modifier.fillMaxSize())

            RootContent.SignedOut -> SignedOutFlow()

            RootContent.Onboarding -> OnboardingFlow()

            is RootContent.Tabs -> TabsShell(
                variant = target.variant,
                avatarSeed = state.avatarSeed,
                creditsLeft = state.creditsLeft,
                hasPlayback = state.hasPlayback,
            )
        }
    }
}

private sealed interface RootContent {
    data object Loading : RootContent

    data object SignedOut : RootContent

    data object Onboarding : RootContent

    data class Tabs(val variant: ShellVariant) : RootContent
}
