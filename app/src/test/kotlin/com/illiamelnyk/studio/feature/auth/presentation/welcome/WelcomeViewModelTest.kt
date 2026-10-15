package com.illiamelnyk.studio.feature.auth.presentation.welcome

import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.feature.auth.data.fake.FakeAuthRepository
import com.illiamelnyk.studio.feature.auth.domain.AuthRepository
import com.illiamelnyk.studio.navigation.SessionGate
import com.illiamelnyk.studio.navigation.toGate
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class WelcomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `Google uses provider name and enters Create without onboarding`() = runTest(main.testDispatcher) {
        val sessions = FakeSessionRepository()
        val vm = WelcomeViewModel(FakeAuthRepository(sessions))
        vm.onIntent(WelcomeIntent.ContinueWithGoogleClicked)
        advanceUntilIdle()
        assertEquals("Illia Melnyk", sessions.session.first()!!.user.displayName)
        assertEquals(SessionGate.SignedIn, sessions.session.first().toGate())
    }

    @Test
    fun `demo enters Create without Google credentials`() = runTest(main.testDispatcher) {
        val sessions = FakeSessionRepository()
        val vm = WelcomeViewModel(FakeAuthRepository(sessions))
        vm.onIntent(WelcomeIntent.TryDemoClicked)
        advanceUntilIdle()
        assertEquals("Demo listener", sessions.session.first()!!.user.displayName)
        assertEquals(SessionGate.SignedIn, sessions.session.first().toGate())
    }

    @Test
    fun `failed sign in unlocks the actions for another attempt`() = runTest(main.testDispatcher) {
        val vm = WelcomeViewModel(object : AuthRepository {
            override suspend fun signInWithGoogle(): Outcome<Unit> = Outcome.Failure(AppError.Network)
        })
        vm.onIntent(WelcomeIntent.ContinueWithGoogleClicked)
        advanceUntilIdle()
        assertFalse(vm.state.value.isSigningIn)
        assertNotNull(vm.state.value.notice)
    }

    @Test
    fun `repeated sign in taps start only one request`() = runTest(main.testDispatcher) {
        var calls = 0
        val vm = WelcomeViewModel(object : AuthRepository {
            override suspend fun signInWithGoogle(): Outcome<Unit> {
                calls++
                kotlinx.coroutines.delay(1_000)
                return Outcome.Failure(AppError.Network)
            }
        })
        vm.onIntent(WelcomeIntent.ContinueWithGoogleClicked)
        vm.onIntent(WelcomeIntent.TryDemoClicked)
        vm.onIntent(WelcomeIntent.ContinueWithGoogleClicked)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertFalse(vm.state.value.isSigningIn)
    }
}
