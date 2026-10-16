package com.illiamelnyk.studio.feature.auth.data

import com.illiamelnyk.studio.core.data.local.FirebaseBearerToken
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseAuthAdapterTest {
    @Test
    fun everyPublicFirebaseConfigurationValueIsRequired() {
        val valid = FirebaseConfiguration("application", "api-key", "project", "web-client")
        assertTrue(valid.isConfigured)
        listOf(
            valid.copy(appId = ""),
            valid.copy(apiKey = " "),
            valid.copy(projectId = ""),
            valid.copy(webClientId = ""),
        ).forEach { assertFalse(it.isConfigured) }
    }

    @Test
    fun missingConfigurationFailsClosedWithoutTouchingSdkOrBackend() = runTest {
        val client = TestFirebaseClient()
        val repository = FirebaseGoogleAuthRepository(
            client,
            false,
            { error("Backend must not be called") },
            { false },
        ) {
            error("Pending deletion must not be consulted without configuration")
        }
        assertFalse(repository.requiresGoogleCredential)
        assertEquals(Outcome.Failure(AppError.UnsupportedMode), repository.signInWithGoogleToken("google-id-token"))
        assertTrue(client.signIns.isEmpty())
    }

    @Test
    fun googleTokenIsExchangedBySdkAndOnlyFirebaseUidEntersServerSessionContract() = runTest {
        val client = TestFirebaseClient()
        var serverUid: String? = null
        val repository = FirebaseGoogleAuthRepository(client, true, { uid ->
            serverUid = uid
            Outcome.Success(Unit)
        }, { false }) { Outcome.Success(Unit) }
        assertTrue(repository.requiresGoogleCredential)
        assertEquals(Outcome.Success(Unit), repository.signInWithGoogleToken("google-id-token"))
        assertEquals(listOf("google-id-token"), client.signIns)
        assertEquals("b", serverUid)
        assertTrue(client.cleared.isEmpty())
        assertEquals(Outcome.Failure(AppError.UnsupportedMode), repository.signInWithGoogle())
    }

    @Test
    fun pendingDeletionNetworkFailurePreservesOldSdkOwnerAndPreventsCredentialExchange() = runTest {
        val client = TestFirebaseClient().apply { uid = "a" }
        var consulted: String? = null
        val repository = FirebaseGoogleAuthRepository(client, true, { error("No backend session") }, { false }) { uid ->
            consulted = uid
            Outcome.Failure(AppError.Network)
        }
        assertEquals(Outcome.Failure(AppError.Network), repository.signInWithGoogleToken("account-b-token"))
        assertEquals("a", consulted)
        assertEquals("a", client.uid)
        assertTrue(client.signIns.isEmpty())
        assertTrue(client.cleared.isEmpty())
    }

    @Test
    fun failedBackendHandshakeAndUncommittedCancellationClearOnlyTheMatchingSdkSession() = runTest {
        val client = TestFirebaseClient()
        val failed = FirebaseGoogleAuthRepository(client, true, { Outcome.Failure(AppError.Network) }, { false }) {
            Outcome.Success(Unit)
        }
        assertEquals(Outcome.Failure(AppError.Network), failed.signInWithGoogleToken("google-token"))
        assertEquals(listOf("b"), client.cleared)
        assertNull(client.uid)
        val cancelled = FirebaseGoogleAuthRepository(client, true, { throw CancellationException() }, { false }) {
            Outcome.Success(Unit)
        }
        val cancellation = runCatching { cancelled.signInWithGoogleToken("google-token") }.exceptionOrNull()
        assertTrue(cancellation is CancellationException)
        assertEquals(listOf("b", "b"), client.cleared)
    }

    @Test
    fun cancellationAfterCommittedAppSessionDoesNotDestroyItsSdkRefreshSource() = runTest {
        val client = TestFirebaseClient()
        val repository = FirebaseGoogleAuthRepository(client, true, { throw CancellationException() }, { true }) {
            Outcome.Success(Unit)
        }
        val cancellation = runCatching { repository.signInWithGoogleToken("google-token") }.exceptionOrNull()
        assertTrue(cancellation is CancellationException)
        assertEquals("b", client.uid)
        assertTrue(client.cleared.isEmpty())
    }

    @Test
    fun providerChecksUidBeforeAndAfterResolutionAndForcesOnlyExplicitIdentityProbe() = runTest {
        val client = TestFirebaseClient().apply { uid = "a" }
        val provider = FirebaseSdkTokenProvider(client)
        assertNull(provider.getToken("b"))
        assertTrue(client.tokenForces.isEmpty())
        assertEquals("a", provider.getToken("a")!!.firebaseUid)
        assertEquals(listOf(false), client.tokenForces)
        provider.confirmIdentity("a")
        assertEquals(listOf(false, true), client.tokenForces)
        client.afterToken = { client.uid = "b" }
        assertNull(provider.getToken("a"))
        provider.clearSession("a")
        assertEquals("b", client.uid)
    }
}

internal class TestFirebaseClient : FirebaseClient {
    var uid: String? = null
    var nextUid = "b"
    var forcedFailure: Exception? = null
    var afterToken: (() -> Unit)? = null
    val signIns = mutableListOf<String>()
    val cleared = mutableListOf<String>()
    val tokenForces = mutableListOf<Boolean>()
    override val currentUid: String? get() = uid

    override suspend fun signIn(googleIdToken: String): String {
        signIns += googleIdToken
        uid = nextUid
        forcedFailure = null
        return nextUid
    }

    override suspend fun token(uid: String, forceRefresh: Boolean): FirebaseBearerToken? {
        tokenForces += forceRefresh
        if (forceRefresh) forcedFailure?.let { throw it }
        val token = FirebaseBearerToken(uid, "fixture.payload.signature-$uid", System.currentTimeMillis() + 3_600_000)
        afterToken?.invoke()
        return token
    }

    override suspend fun clear(uid: String) {
        if (this.uid == uid) {
            cleared += uid
            this.uid = null
        }
    }
}
