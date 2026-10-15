package com.illiamelnyk.studio.navigation

import app.cash.turbine.test
import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.domain.session.DisplayName
import com.illiamelnyk.studio.core.domain.session.ProfileUpdate
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.testing.InMemoryThemePreferenceRepository
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            assertEquals(
                AppState(
                    gate = SessionGate.SignedIn,
                    theme = ThemePreference.Light,
                    shellVariant = ShellVariant.TopSwitch,
                    avatarSeed = "dev-illia",
                    creditsLeft = 8,
                    hasPlayback = false,
                ),
                awaitItem(),
            )
        }
    }

    private fun TestScope.viewModel() = AppViewModel(
        sessions = sessions,
        credits = FakeCreditsRepository(),
        themes = themes,
        shellVariants = object : ShellVariantSource {
            override val variant = flowOf(ShellVariant.TopSwitch)
        },
        playback = FakePlaybackController(backgroundScope),
    )

    private fun name(value: String) = (DisplayName.parse(value) as DisplayName.Parsed.Valid).name
}
