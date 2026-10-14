package com.illiamelnyk.studio.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.domain.settings.ThemePreferenceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Which part of the app the session allows. */
enum class SessionGate {
    /** The session isn't known yet: nothing is drawn, so no flow flashes by. */
    Loading,

    /** Nobody is signed in: the welcome screen. */
    SignedOut,

    /** Signed in, with steps of the profile still missing: onboarding. */
    Onboarding,

    /** Signed in and onboarded: the tabs. */
    SignedIn,
}

/** The gate a [Session] opens; signing in is mandatory, and so is a complete profile. */
fun Session?.toGate(): SessionGate = when {
    this == null -> SessionGate.SignedOut
    !onboarding.isComplete -> SessionGate.Onboarding
    else -> SessionGate.SignedIn
}

/**
 * What the app around the screens needs: the gate, the theme, the shell variant and the shell's
 * own bars (avatar, credits, whether the mini player shows).
 *
 * @param theme `null` until the stored choice is read.
 */
@Immutable
data class AppState(
    val gate: SessionGate = SessionGate.Loading,
    val theme: ThemePreference? = null,
    val shellVariant: ShellVariant = ShellVariant.Default,
    val avatarSeed: String = "",
    val creditsLeft: Int = 0,
    val hasPlayback: Boolean = false,
)

/**
 * The state of the app around the screens. The user does nothing here directly, so unlike screen
 * ViewModels it has no intents: everything follows from the repositories.
 */
class AppViewModel(
    sessions: SessionRepository,
    credits: CreditsRepository,
    themes: ThemePreferenceRepository,
    shellVariants: ShellVariantSource,
    playback: PlaybackController,
) : ViewModel() {

    val state: StateFlow<AppState> = combine(
        sessions.session,
        themes.preference,
        shellVariants.variant,
        credits.credits,
        playback.state.map { it.current != null }.distinctUntilChanged(),
    ) { session, theme, variant, credits, hasPlayback ->
        AppState(
            gate = session.toGate(),
            theme = theme,
            shellVariant = variant,
            avatarSeed = session?.user?.avatarSeed?.value.orEmpty(),
            creditsLeft = credits.remaining,
            hasPlayback = hasPlayback,
        )
    }
        // Kept through a configuration change, so the app doesn't pass through Loading again.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
