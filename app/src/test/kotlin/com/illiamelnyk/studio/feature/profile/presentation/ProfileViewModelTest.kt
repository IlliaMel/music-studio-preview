package com.illiamelnyk.studio.feature.profile.presentation

import app.cash.turbine.test
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.session.DeleteAccount
import com.illiamelnyk.studio.core.domain.session.ProfileUpdate
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.SignOut
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.testing.InMemoryThemePreferenceRepository
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val sessions = FakeSessionRepository().apply {
        signIn("illia", SignInMethod.Google, "Illia", useProviderProfile = true)
    }
    private val themes = InMemoryThemePreferenceRepository()
    private lateinit var toasts: ToastController
    private val credits = FakeCreditsRepository()

    @Test
    fun `invalid names never overwrite the profile`() = runTest(main.testDispatcher) {
        val vm = viewModel()
        runCurrent()
        for (name in listOf("   ", "a".repeat(41))) {
            vm.onIntent(ProfileIntent.NameChanged(name))
            vm.onIntent(ProfileIntent.SaveClicked)
            advanceUntilIdle()
            assertEquals(UiText.Resource(R.string.profile_name_error), vm.state.value.nameError)
            assertEquals("Illia", sessions.session.first()!!.user.displayName)
        }
    }

    @Test
    fun `Save trims and persists the name then closes with feedback`() = runTest(main.testDispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.effects.test {
            vm.onIntent(ProfileIntent.NameChanged("  New name  "))
            vm.onIntent(ProfileIntent.SaveClicked)
            advanceUntilIdle()
            assertEquals("New name", sessions.session.first()!!.user.displayName)
            assertEquals(ProfileEffect.Close, awaitItem())
            assertEquals(UiText.Resource(R.string.profile_saved), toasts.message.value!!.title)
            assertFalse(vm.state.value.isSaving)
        }
    }

    @Test
    fun `failed save keeps the draft and allows retry`() = runTest(main.testDispatcher) {
        val failing = object : SessionRepository by sessions {
            override suspend fun updateProfile(update: ProfileUpdate): Outcome<Session> =
                Outcome.Failure(AppError.Network)
        }
        val vm = viewModel(failing)
        runCurrent()
        vm.effects.test {
            vm.onIntent(ProfileIntent.NameChanged("Draft"))
            vm.onIntent(ProfileIntent.SaveClicked)
            advanceUntilIdle()
            assertEquals("Draft", vm.state.value.name)
            assertFalse(vm.state.value.isSaving)
            assertTrue(toasts.message.value!!.isError)
            expectNoEvents()
        }
    }

    @Test
    fun `live theme updates do not discard unsaved name edits`() = runTest(main.testDispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.onIntent(ProfileIntent.NameChanged("Draft"))
        vm.onIntent(ProfileIntent.ThemeSelected(ThemePreference.Dark))
        advanceUntilIdle()
        assertEquals(ThemePreference.Dark, themes.preference.first())
        assertEquals("Draft", vm.state.value.name)
    }

    @Test
    fun `delete requires confirmation and cancellation preserves the account`() = runTest(main.testDispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.onIntent(ProfileIntent.DeleteConfirmed)
        vm.onIntent(ProfileIntent.DeleteClicked)
        assertTrue(vm.state.value.confirmDelete)
        vm.onIntent(ProfileIntent.DeleteDismissed)
        vm.onIntent(ProfileIntent.DeleteConfirmed)
        advanceUntilIdle()
        assertEquals("Illia", sessions.session.first()!!.user.displayName)
        assertFalse(vm.state.value.isDeleting)
    }

    @Test
    fun `confirmed deletion clears tracks and stops playback`() = runTest(main.testDispatcher) {
        toasts = ToastController(backgroundScope)
        val playback = FakePlaybackController(backgroundScope)
        val tracks = FakeTrackRepository(credits, sessions, backgroundScope, Clock.System)
        playback.play(tracks.observeMine().first())
        val vm = ProfileViewModel(
            sessions,
            credits,
            themes,
            SignOut(sessions, playback),
            DeleteAccount(sessions, playback, tracks),
            toasts,
        )
        runCurrent()
        vm.onIntent(ProfileIntent.DeleteClicked)
        vm.onIntent(ProfileIntent.DeleteConfirmed)
        advanceUntilIdle()
        assertNull(sessions.session.first())
        assertNull(playback.state.value.current)
        assertTrue(tracks.observeMine().first().isEmpty())
    }

    @Test
    fun `quota text plural follows remaining songs`() = runTest(main.testDispatcher) {
        repeat(7) { credits.trySpend() }
        val vm = viewModel()
        runCurrent()
        assertEquals(UiText.Plural(R.plurals.profile_credits_left, 1, 1, 8), vm.state.value.credits)
    }

    @Test
    fun `sign out blocks Save and closes only through the session gate`() = runTest(main.testDispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.effects.test {
            vm.onIntent(ProfileIntent.NameChanged("Unsaved"))
            vm.onIntent(ProfileIntent.SignOutClicked)
            vm.onIntent(ProfileIntent.SaveClicked)
            vm.onIntent(ProfileIntent.BackClicked)
            advanceUntilIdle()
            assertNull(sessions.session.first())
            assertFalse(vm.state.value.isSaving)
            assertNull(toasts.message.value)
            expectNoEvents()
        }
    }

    @Test
    fun `failed deletion leaves settings available for retry`() = runTest(main.testDispatcher) {
        val failing = object : SessionRepository by sessions {
            override suspend fun deleteAccount(): Outcome<Unit> = Outcome.Failure(AppError.Network)
        }
        val vm = viewModel(failing)
        runCurrent()
        vm.onIntent(ProfileIntent.DeleteClicked)
        vm.onIntent(ProfileIntent.DeleteConfirmed)
        advanceUntilIdle()
        assertFalse(vm.state.value.isDeleting)
        assertFalse(vm.state.value.confirmDelete)
        assertEquals("Illia", sessions.session.first()!!.user.displayName)
        assertTrue(toasts.message.value!!.isError)
    }

    private fun TestScope.viewModel(repository: SessionRepository = sessions): ProfileViewModel {
        toasts = ToastController(backgroundScope)
        val playback = FakePlaybackController(backgroundScope)
        val tracks = FakeTrackRepository(credits, repository, backgroundScope, Clock.System)
        return ProfileViewModel(
            repository,
            credits,
            themes,
            SignOut(repository, playback),
            DeleteAccount(repository, playback, tracks),
            toasts,
        )
    }
}
