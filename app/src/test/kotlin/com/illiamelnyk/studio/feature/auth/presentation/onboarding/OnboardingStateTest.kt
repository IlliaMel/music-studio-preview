package com.illiamelnyk.studio.feature.auth.presentation.onboarding

import com.illiamelnyk.studio.core.domain.session.DisplayName
import com.illiamelnyk.studio.core.domain.session.OnboardingStep
import com.illiamelnyk.studio.core.domain.session.ProfileUpdate
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {

    private val newAccount = OnboardingState(
        missing = persistentSetOf(OnboardingStep.DisplayName, OnboardingStep.Age, OnboardingStep.Terms),
        suggestedName = "Illia Melnyk",
    )

    @Test
    fun `a new account can continue only with every step answered`() {
        val answered = newAccount.copy(useSuggestedName = true, ageConfirmed = true, termsAccepted = true)

        assertTrue(answered.canContinue)
        assertFalse(answered.copy(useSuggestedName = false).canContinue)
        assertFalse(answered.copy(ageConfirmed = false).canContinue)
        assertFalse(answered.copy(termsAccepted = false).canContinue)
    }

    @Test
    fun `a suggested name the server would refuse can't be picked`() {
        val tooLong = newAccount.copy(
            suggestedName = "a".repeat(41),
            useSuggestedName = true,
            ageConfirmed = true,
            termsAccepted = true,
        )

        assertFalse(tooLong.canContinue)
    }

    @Test
    fun `nothing missing means nothing to send`() {
        assertFalse(OnboardingState(termsAccepted = true, ageConfirmed = true).canContinue)
    }

    @Test
    fun `the update carries every answer of a new account`() {
        val answered = newAccount.copy(
            useSuggestedName = true,
            ageConfirmed = true,
            termsAccepted = true,
            marketingOptIn = true,
        )

        assertEquals(
            ProfileUpdate(
                displayName = (DisplayName.parse("Illia Melnyk") as DisplayName.Parsed.Valid).name,
                confirmAge = true,
                acceptedTermsVersion = "2026-10-14",
                marketingOptIn = true,
            ),
            answered.toProfileUpdate(currentTermsVersion = "2026-10-14"),
        )
    }

    @Test
    fun `new terms alone send only the terms`() {
        // Age and marketing were answered long ago; they aren't asked again, so they aren't sent.
        val newTerms = OnboardingState(missing = persistentSetOf(OnboardingStep.Terms), termsAccepted = true)

        assertEquals(
            ProfileUpdate(acceptedTermsVersion = "2027-01-01"),
            newTerms.toProfileUpdate(currentTermsVersion = "2027-01-01"),
        )
    }
}
