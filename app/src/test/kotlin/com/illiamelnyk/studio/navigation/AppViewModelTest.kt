package com.illiamelnyk.studio.navigation

import app.cash.turbine.test
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.PlaybackFailure
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.session.DisplayName
import com.illiamelnyk.studio.core.domain.session.ProfileUpdate
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.SignOut
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.testing.InMemoryThemePreferenceRepository
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessions = FakeSessionRepository()
    private val themes = InMemoryThemePreferenceRepository()

    @Test
    fun `the session decides between welcome, onboarding and the tabs`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.state.map { it.gate }.distinctUntilChanged().test {
            assertEquals(SessionGate.Loading, awaitItem())
            assertEquals(SessionGate.SignedOut, awaitItem())

            sessions.signIn("illia", SignInMethod.Google, suggestedDisplayName = "Illia Melnyk")
            assertEquals(SessionGate.SignedIn, awaitItem())

            // Legacy missing steps must not block entry.
            sessions.updateProfile(ProfileUpdate(displayName = name("Illia"), confirmAge = true))
            sessions.updateProfile(ProfileUpdate(acceptedTermsVersion = "2026-10-14"))
            expectNoEvents()

            sessions.signOut()
            assertEquals(SessionGate.SignedOut, awaitItem())
        }
    }

    @Test
    fun `an onboarded account signing in again goes straight to the tabs`() =
        runTest(mainDispatcherRule.testDispatcher) {
            sessions.signIn("illia", SignInMethod.Google, suggestedDisplayName = null)
            sessions.updateProfile(
                ProfileUpdate(displayName = name("Illia"), confirmAge = true, acceptedTermsVersion = "2026-10-14"),
            )
            sessions.signOut()
            val viewModel = viewModel()

            viewModel.state.map { it.gate }.distinctUntilChanged().test {
                assertEquals(SessionGate.Loading, awaitItem())
                assertEquals(SessionGate.SignedOut, awaitItem())

                sessions.signIn("illia", SignInMethod.Google, suggestedDisplayName = null)
                assertEquals(SessionGate.SignedIn, awaitItem())
            }
        }

    @Test
    fun `the stored theme and the account reach the app state`() = runTest(mainDispatcherRule.testDispatcher) {
        themes.set(ThemePreference.Light)
        sessions.signIn("illia", SignInMethod.Google, suggestedDisplayName = null)
        val viewModel = viewModel()

        viewModel.state.test {
            assertEquals(AppState(), awaitItem())
            var ready = awaitItem()
            if (ready.theme == null) {
                assertEquals(SessionGate.SignedIn, ready.gate)
                assertEquals("dev-illia", ready.ownerId)
                ready = awaitItem()
            }
            assertEquals(
                AppState(
                    gate = SessionGate.SignedIn,
                    theme = ThemePreference.Light,
                    avatarSeed = "dev-illia",
                    creditsLeft = 8,
                    hasPlayback = false,
                    ownerId = "dev-illia",
                ),
                ready,
            )
        }
    }

    @Test
    fun playbackFailureShowsOnceWhileClockAndOrdinaryPauseStaySilent() = runTest(mainDispatcherRule.testDispatcher) {
        val song = track("feedback-audio", 20.seconds)
        val values = MutableStateFlow(PlaybackState(queue = listOf(song), isPlaying = true))
        val playback = object : PlaybackController by FakePlaybackController(backgroundScope) {
            override val state = values.asStateFlow()
        }
        val feedback = ToastController(backgroundScope)
        viewModel(playback, feedback)
        runCurrent()
        values.value = values.value.copy(isPlaying = false)
        runCurrent()
        assertNull(feedback.message.value)
        val failure = PlaybackFailure(1, song.id, AppError.Network)
        values.value = values.value.copy(failure = failure)
        runCurrent()
        val first = feedback.message.value!!
        assertTrue(first.isError)
        values.value = values.value.copy(positionMs = 1_000)
        runCurrent()
        assertEquals(first.id, feedback.message.value!!.id)
        values.value = values.value.copy(failure = null, isPlaying = true)
        runCurrent()
        values.value = values.value.copy(isPlaying = false, failure = failure.copy(id = 2))
        runCurrent()
        assertTrue(feedback.message.value!!.id > first.id)
        values.value = values.value.copy(stationName = "LIVE", failure = failure.copy(id = 3))
        runCurrent()
        assertEquals(
            com.illiamelnyk.studio.core.presentation.UiText.Resource(
                com.illiamelnyk.studio.R.string.player_station_playback_failed,
            ),
            feedback.message.value!!.title,
        )
    }

    @Test
    fun directSignedInOwnerChangePublishesIdentityWithoutSignedOutGap() = runTest(mainDispatcherRule.testDispatcher) {
        sessions.signIn("first", SignInMethod.Google, "First", useProviderProfile = true)
        val viewModel = viewModel()
        viewModel.state.map { it.ownerId }.distinctUntilChanged().test {
            assertNull(awaitItem())
            assertEquals("dev-first", awaitItem())
            sessions.signIn("second", SignInMethod.Google, "Second", useProviderProfile = true)
            assertEquals("dev-second", awaitItem())
            assertEquals(SessionGate.SignedIn, viewModel.state.value.gate)
            expectNoEvents()
        }
    }

    @Test
    fun `an account outside the invite list is told so and signed out`() = runTest(mainDispatcherRule.testDispatcher) {
        val feedback = ToastController(backgroundScope)
        viewModel(feedback = feedback)
        sessions.signIn("guest", SignInMethod.Google, "Guest", useProviderProfile = true, canGenerate = false)
        assertEquals(SessionGate.SignedOut, sessions.session.first()?.toGate())
        runCurrent()

        assertEquals(UiText.Resource(R.string.auth_not_invited_title), feedback.message.value?.title)
        assertNull(sessions.session.first())
    }

    private fun TestScope.viewModel(
        playback: PlaybackController = FakePlaybackController(backgroundScope),
        feedback: ToastController = ToastController(backgroundScope),
    ) = AppViewModel(
        sessions = sessions,
        credits = FakeCreditsRepository(),
        themes = themes,
        playback = playback,
        feedback = feedback,
        signOut = SignOut(sessions, playback),
    )

    private fun name(value: String) = (DisplayName.parse(value) as DisplayName.Parsed.Valid).name
}
