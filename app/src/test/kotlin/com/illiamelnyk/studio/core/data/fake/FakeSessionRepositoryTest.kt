package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.session.DisplayName
import com.illiamelnyk.studio.core.domain.session.OnboardingStep
import com.illiamelnyk.studio.core.domain.session.ProfileUpdate
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSessionRepositoryTest {

    private val repository = FakeSessionRepository()

    @Test
    fun `the app starts signed out`() = runTest {
        assertNull(repository.session.first())
    }

    @Test
    fun `a new account has to pick a name, confirm the age and accept the terms`() = runTest {
        repository.signIn("illia", SignInMethod.Google, suggestedDisplayName = "Illia Melnyk")

        val session = repository.session.first()!!
        assertEquals(
            setOf(OnboardingStep.DisplayName, OnboardingStep.Age, OnboardingStep.Terms),
            session.onboarding.missing,
        )
        assertEquals("Illia Melnyk", session.suggestedDisplayName)
        assertEquals("illia@dev.local", session.user.email)
    }

    @Test
    fun `filling in the profile completes onboarding`() = runTest {
        repository.signIn("illia", SignInMethod.Google, suggestedDisplayName = null)

        repository.updateProfile(ProfileUpdate(displayName = name("Illia"), confirmAge = true))
        val result = repository.updateProfile(ProfileUpdate(acceptedTermsVersion = "2026-10-14"))

        val session = (result as Outcome.Success).value
        assertTrue(session.onboarding.isComplete)
        assertEquals("Illia", session.user.displayName)
        assertEquals(session, repository.session.first())
    }

    @Test
    fun `a partly filled profile still misses the rest`() = runTest {
        repository.signIn("illia", SignInMethod.Google, suggestedDisplayName = null)

        repository.updateProfile(ProfileUpdate(displayName = name("Illia")))

        assertEquals(setOf(OnboardingStep.Age, OnboardingStep.Terms), repository.session.first()!!.onboarding.missing)
    }

    @Test
    fun `outdated terms are refused and change nothing`() = runTest {
        repository.signIn("illia", SignInMethod.Google, suggestedDisplayName = null)
        val before = repository.session.first()

        val result = repository.updateProfile(ProfileUpdate(acceptedTermsVersion = "2025-01-01"))

        assertEquals(Outcome.Failure(AppError.Unknown), result)
        assertEquals(before, repository.session.first())
    }

    @Test
    fun `an account keeps its profile after signing out`() = runTest {
        repository.signIn("illia", SignInMethod.Google, suggestedDisplayName = null)
        repository.updateProfile(
            ProfileUpdate(displayName = name("Illia"), confirmAge = true, acceptedTermsVersion = "2026-10-14"),
        )

        repository.signOut()
        assertNull(repository.session.first())
        repository.signIn("illia", SignInMethod.Google, suggestedDisplayName = null)

        assertTrue(repository.session.first()!!.onboarding.isComplete)
    }

    @Test
    fun `a deleted account signs out and starts over`() = runTest {
        repository.signIn("illia", SignInMethod.Google, suggestedDisplayName = null)
        repository.updateProfile(ProfileUpdate(displayName = name("Illia")))

        repository.deleteAccount()
        assertNull(repository.session.first())
        repository.signIn("illia", SignInMethod.Google, suggestedDisplayName = null)

        assertNull(repository.session.first()!!.user.displayName)
    }

    private fun name(value: String) = (DisplayName.parse(value) as DisplayName.Parsed.Valid).name
}
