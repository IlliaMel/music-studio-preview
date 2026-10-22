package com.illiamelnyk.studio.feature.auth.presentation.welcome

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class RequestGoogleCredentialTest {
    @Test
    fun missingAccountProducesAnActionableFailureInsteadOfCancellation() = runTest {
        val intents = mutableListOf<WelcomeIntent>()
        requestGoogleCredential(7, { throw NoCredentialException("SDK details") }, intents::add)
        assertEquals(
            listOf(WelcomeIntent.GoogleCredentialFailed(7, GoogleCredentialFailure.NoAccount)),
            intents,
        )
    }

    @Test
    fun providerAndConfigurationFailuresAreVisibleWithoutExposingSdkDetails() = runTest {
        for (failure in listOf(
            GetCredentialProviderConfigurationException("private provider details"),
            IllegalStateException("private client configuration"),
            NullPointerException("old Google Play services failure"),
        )) {
            val intents = mutableListOf<WelcomeIntent>()
            requestGoogleCredential(8, { throw failure }, intents::add)
            assertEquals(
                listOf(WelcomeIntent.GoogleCredentialFailed(8, GoogleCredentialFailure.Unavailable)),
                intents,
            )
        }
    }

    @Test
    fun dismissingGoogleChooserRemainsSilent() = runTest {
        val intents = mutableListOf<WelcomeIntent>()
        requestGoogleCredential(9, { throw GetCredentialCancellationException() }, intents::add)
        assertEquals(listOf(WelcomeIntent.GoogleCredentialCancelled(9)), intents)
    }

    @Test
    fun leavingTheScreenCancelsRequestAndPropagatesCoroutineCancellation() = runTest {
        val intents = mutableListOf<WelcomeIntent>()
        val cancellation = CancellationException("screen left")
        try {
            requestGoogleCredential(10, { throw cancellation }, intents::add)
            fail("Coroutine cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(listOf(WelcomeIntent.GoogleCredentialCancelled(10)), intents)
    }
}
