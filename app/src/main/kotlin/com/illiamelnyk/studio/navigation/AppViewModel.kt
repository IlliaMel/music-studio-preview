package com.illiamelnyk.studio.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.domain.settings.ThemePreferenceRepository
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.toUiText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Which part of the app the session allows. */
enum class SessionGate {
    /** The session isn't known yet: nothing is drawn, so no flow flashes by. */
    Loading,

    /** Nobody is signed in: the welcome screen. */
    SignedOut,

    /** Signed in and onboarded: the tabs. */
    SignedIn,
}

/** The gate a [Session] opens; signing in opens Create; server onboarding metadata never adds a UI gate. */
fun Session?.toGate(): SessionGate = when {
    this == null -> SessionGate.SignedOut
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
    val ownerId: String? = null,
) {
    val readyForFirstFrame: Boolean
        get() = gate != SessionGate.Loading && theme != null
}

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
    feedback: ToastController,
) : ViewModel() {

    init {
        playback.state.map { state -> state.failure?.let { it to state.isStation } }
            .distinctUntilChanged().onEach { failed ->
                if (failed != null) {
                    feedback.show(
                        UiText.Resource(
                            if (failed.second) {
                                R.string.player_station_playback_failed
                            } else {
                                R.string.player_playback_failed
                            },
                        ),
                        failed.first.error.toUiText(),
                        isError = true,
                    )
                }
            }.launchIn(viewModelScope)
    }

    private val themePreference = themes.preference.map<ThemePreference, ThemePreference?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val shellVariant = shellVariants.variant
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShellVariant.Default)

    val state: StateFlow<AppState> = combine(
        sessions.session,
        themePreference,
        shellVariant,
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
            ownerId = session?.user?.id?.value,
        )
    }
        // Kept through a configuration change, so the app doesn't pass through Loading again.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
