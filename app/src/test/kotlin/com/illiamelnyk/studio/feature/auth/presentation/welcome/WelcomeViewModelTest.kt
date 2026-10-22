package com.illiamelnyk.studio.feature.auth.presentation.welcome

import app.cash.turbine.test
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.presentation.UiText
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WelcomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun demoActionUsesOnlyTheExplicitDemoContract() = runTest(main.testDispatcher) {
        var demoCalls = 0
        val vm = WelcomeViewModel(object : AuthRepository {
            override val requiresGoogleCredential = true
            override suspend fun signInWithGoogle(): Outcome<Unit> = error("Demo must not sign into Google")
            override suspend fun signInWithGoogleToken(idToken: String): Outcome<Unit> = error("No credential needed")
            override suspend fun signInDemo(): Outcome<Unit> {
                demoCalls++
                return Outcome.Failure(AppError.UnsupportedMode)
            }
        })
        vm.effects.test {
            vm.onIntent(WelcomeIntent.TryDemoClicked)
            advanceUntilIdle()
            assertEquals(1, demoCalls)
            assertFalse(vm.state.value.isSigningIn)
            expectNoEvents()
        }
    }

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
            override val requiresGoogleCredential = false
            override suspend fun signInWithGoogleToken(idToken: String): Outcome<Unit> =
                Outcome.Failure(AppError.UnsupportedMode)
            override suspend fun signInDemo(): Outcome<Unit> = Outcome.Failure(AppError.UnsupportedMode)
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
            override val requiresGoogleCredential = false
            override suspend fun signInWithGoogleToken(idToken: String): Outcome<Unit> =
                Outcome.Failure(AppError.UnsupportedMode)
            override suspend fun signInDemo(): Outcome<Unit> = Outcome.Failure(AppError.UnsupportedMode)
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

    @Test
    fun credentialChooserCancellationIsSilentAndStaleResponsesCannotStartTheNextAttempt() =
        runTest(main.testDispatcher) {
            var tokens = 0
            val vm = WelcomeViewModel(object : AuthRepository {
                override val requiresGoogleCredential = true
                override suspend fun signInDemo(): Outcome<Unit> = Outcome.Failure(AppError.UnsupportedMode)
                override suspend fun signInWithGoogle() = error("UI-hosted credentials required")
                override suspend fun signInWithGoogleToken(idToken: String): Outcome<Unit> {
                    tokens++
                    return Outcome.Failure(AppError.Network)
                }
            })
            vm.effects.test {
                vm.onIntent(WelcomeIntent.ContinueWithGoogleClicked)
                val first = (awaitItem() as WelcomeEffect.RequestGoogleCredential).requestId
                vm.onIntent(WelcomeIntent.ContinueWithGoogleClicked)
                expectNoEvents()
                vm.onIntent(WelcomeIntent.GoogleCredentialCancelled(first))
                assertFalse(vm.state.value.isSigningIn)
                assertNull(vm.state.value.notice)
                vm.onIntent(WelcomeIntent.ContinueWithGoogleClicked)
                val second = (awaitItem() as WelcomeEffect.RequestGoogleCredential).requestId
                assertTrue(second > first)
                vm.onIntent(WelcomeIntent.GoogleCredentialReceived(first, "old-token"))
                assertEquals(0, tokens)
                vm.onIntent(WelcomeIntent.GoogleCredentialReceived(second, "new-token"))
                advanceUntilIdle()
                assertEquals(1, tokens)
                assertFalse(vm.state.value.isSigningIn)
                assertNotNull(vm.state.value.notice)
            }
        }

    @Test
    fun credentialFailuresExplainRecoveryAndAllowRetryWithoutAcceptingStaleFailures() = runTest(main.testDispatcher) {
        val vm = WelcomeViewModel(object : AuthRepository {
            override val requiresGoogleCredential = true
            override suspend fun signInDemo(): Outcome<Unit> = error("Not used")
            override suspend fun signInWithGoogle(): Outcome<Unit> = error("Credentials required")
            override suspend fun signInWithGoogleToken(idToken: String): Outcome<Unit> = error("Not used")
        })
        vm.effects.test {
            vm.onIntent(WelcomeIntent.ContinueWithGoogleClicked)
            val first = (awaitItem() as WelcomeEffect.RequestGoogleCredential).requestId
            vm.onIntent(WelcomeIntent.GoogleCredentialFailed(first, GoogleCredentialFailure.NoAccount))
            assertFalse(vm.state.value.isSigningIn)
            assertEquals(UiText.Resource(R.string.auth_google_no_account), vm.state.value.notice)

            vm.onIntent(WelcomeIntent.ContinueWithGoogleClicked)
            val second = (awaitItem() as WelcomeEffect.RequestGoogleCredential).requestId
            assertNull(vm.state.value.notice)
            vm.onIntent(WelcomeIntent.GoogleCredentialFailed(first, GoogleCredentialFailure.NoAccount))
            assertTrue(vm.state.value.isSigningIn)
            assertNull(vm.state.value.notice)
            vm.onIntent(WelcomeIntent.GoogleCredentialFailed(second, GoogleCredentialFailure.Unavailable))
            assertFalse(vm.state.value.isSigningIn)
            assertEquals(UiText.Resource(R.string.auth_google_unavailable), vm.state.value.notice)
        }
    }

    @Test
    fun cancelledSdkExchangeUnlocksWelcomeWithoutAnErrorNoticeOrTokenInState() = runTest(main.testDispatcher) {
        val vm = WelcomeViewModel(object : AuthRepository {
            override val requiresGoogleCredential = true
            override suspend fun signInDemo(): Outcome<Unit> = Outcome.Failure(AppError.UnsupportedMode)
            override suspend fun signInWithGoogle() = error("UI-hosted credentials required")
            override suspend fun signInWithGoogleToken(idToken: String): Outcome<Unit> =
                throw kotlinx.coroutines.CancellationException()
        })
        vm.effects.test {
            vm.onIntent(WelcomeIntent.ContinueWithGoogleClicked)
            val request = (awaitItem() as WelcomeEffect.RequestGoogleCredential).requestId
            val response = WelcomeIntent.GoogleCredentialReceived(request, "sensitive-google-id-token")
            assertFalse(response.toString().contains("sensitive-google-id-token"))
            vm.onIntent(response)
            advanceUntilIdle()
            assertFalse(vm.state.value.isSigningIn)
            assertNull(vm.state.value.notice)
            assertFalse(vm.state.value.toString().contains("sensitive-google-id-token"))
        }
    }
}
