package com.illiamelnyk.studio.navigation

import app.cash.turbine.test
import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.SignOut
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.domain.settings.ThemePreferenceRepository
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupGateTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun knownOwnerDoesNotWaitForOptionalSettingsAndKeepsIdentityWhenTheyArrive() = runTest(main.testDispatcher) {
        val sessions = FakeSessionRepository()
        sessions.signIn("startup", SignInMethod.Google, "Startup", useProviderProfile = true)
        val themes = MutableSharedFlow<ThemePreference>()
        val model = model(sessions, themes)
        var settled = AppState()

        model.state.test {
            assertEquals(SessionGate.Loading, awaitItem().gate)
            val ready = awaitItem()
            assertEquals(SessionGate.SignedIn, ready.gate)
            assertEquals("dev-startup", ready.ownerId)
            assertEquals(8, ready.creditsLeft)
            assertNull(ready.theme)
            assertFalse(ready.readyForFirstFrame)
            runCurrent()

            themes.emit(ThemePreference.Light)
            val themed = awaitItem()
            assertTrue(themed.readyForFirstFrame)
            assertEquals(ready.copy(theme = ThemePreference.Light), themed)
            settled = themed
        }
        advanceTimeBy(6_000)
        runCurrent()
        model.state.test {
            assertEquals(settled, awaitItem())
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun settingsNeverOpenEitherAccountFlowBeforeSessionIsKnown() = runTest(main.testDispatcher) {
        val pending = MutableSharedFlow<Session?>()
        val sessions = object : SessionRepository by FakeSessionRepository() {
            override val session = pending
        }
        val themes = MutableSharedFlow<ThemePreference>()
        val model = model(sessions, themes)

        model.state.test {
            assertEquals(SessionGate.Loading, awaitItem().gate)
            runCurrent()
            themes.emit(ThemePreference.Dark)
            expectNoEvents()
            pending.emit(null)
            val signedOut = awaitItem()
            assertEquals(SessionGate.SignedOut, signedOut.gate)
            assertNull(signedOut.ownerId)
            assertTrue(signedOut.readyForFirstFrame)
        }
    }

    private fun TestScope.model(
        sessions: SessionRepository,
        themes: MutableSharedFlow<ThemePreference>,
        playback: FakePlaybackController = FakePlaybackController(backgroundScope),
    ) = AppViewModel(
        sessions,
        FakeCreditsRepository(),
        object : ThemePreferenceRepository {
            override val preference = themes
            override suspend fun set(preference: ThemePreference) = themes.emit(preference)
        },
        playback,
        ToastController(backgroundScope),
        SignOut(sessions, playback),
    )
}
