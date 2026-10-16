package com.illiamelnyk.studio.feature.auth.data

import android.content.Context
import com.illiamelnyk.studio.core.data.local.ApiAuthMode
import com.illiamelnyk.studio.core.data.local.ApiConfiguration
import com.illiamelnyk.studio.core.data.local.ApiFailure
import com.illiamelnyk.studio.core.data.local.DurableStudioRepository
import com.illiamelnyk.studio.core.data.local.FirebaseIdentityMissing
import com.illiamelnyk.studio.core.data.local.FirebaseReauthenticationRequired
import com.illiamelnyk.studio.core.data.local.StudioApi
import com.illiamelnyk.studio.core.data.local.StudioStore
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FirebaseDeletionRecoveryTest {
    private lateinit var context: Context

    @Before
    fun clean() {
        context = RuntimeEnvironment.getApplication()
        context.filesDir.listFiles()?.filter { it.name.startsWith("studio-state-v1.json") }?.forEach { it.delete() }
    }

    @Test
    fun failedPendingDeletePreservesSdkAUntilConfirmedThenAllowsGoogleB() = runTest {
        val fixture = Fixture(this)
        fixture.repository.signInFirebase("a")
        fixture.api.failure = AppError.Network
        assertEquals(Outcome.Failure(AppError.Network), fixture.repository.deleteAccount())
        assertNull(fixture.repository.session.first())
        assertEquals("a", fixture.client.uid)
        assertTrue(fixture.store.read().has("deletion-server-a"))
        assertEquals(Outcome.Failure(AppError.Network), fixture.auth.signInWithGoogleToken("google-b"))
        assertTrue(fixture.client.signIns.isEmpty())
        assertEquals("a", fixture.client.uid)
        fixture.api.failure = null
        assertEquals(Outcome.Success(Unit), fixture.auth.signInWithGoogleToken("google-b"))
        assertEquals(listOf("a"), fixture.client.cleared)
        assertEquals("b", fixture.client.uid)
        assertEquals("server-b", fixture.repository.session.first()!!.user.id.value)
        assertFalse(fixture.store.read().has("deletion-server-a"))
        assertFalse(fixture.store.read().toString().contains("fixture.payload"))
        assertFalse(fixture.store.read().toString().contains("google-b"))
    }

    @Test
    fun cachedJwt401TriggersOneForcedProbeAndIdentityMissingRetainsUnverifiedReceiptWithoutBlockingB() = runTest {
        val fixture = Fixture(this)
        fixture.repository.signInFirebase("a")
        fixture.api.failure = AppError.Unauthorized
        fixture.client.forcedFailure = FirebaseIdentityMissing("a")
        assertEquals(Outcome.Failure(AppError.Unauthorized), fixture.repository.deleteAccount())
        val receipt = fixture.store.read().getJSONObject("deletion-server-a")
        assertTrue(receipt.getBoolean("remoteCompletionUnverified"))
        assertFalse(receipt.getBoolean("remoteConfirmed"))
        assertTrue(receipt.getBoolean("localPurged"))
        assertEquals("firebase:a", receipt.getString("token"))
        assertEquals(1, fixture.client.tokenForces.count { it })
        assertNull(fixture.client.uid)
        fixture.api.failure = null
        assertEquals(Outcome.Success(Unit), fixture.auth.signInWithGoogleToken("google-b"))
        assertTrue(fixture.store.read().getJSONObject("deletion-server-a").getBoolean("remoteCompletionUnverified"))
        assertEquals("server-b", fixture.repository.session.first()!!.user.id.value)
    }

    @Test
    fun generic401WithValidSdkIdentityIsNotTreatedAsDeletedAndDoesNotDestroyRetainedA() = runTest {
        val fixture = Fixture(this)
        fixture.repository.signInFirebase("a")
        fixture.api.failure = AppError.Unauthorized
        assertEquals(Outcome.Failure(AppError.Unauthorized), fixture.repository.deleteAccount())
        val receipt = fixture.store.read().getJSONObject("deletion-server-a")
        assertFalse(receipt.getBoolean("remoteCompletionUnverified"))
        assertFalse(receipt.getBoolean("requiresReauthentication"))
        assertEquals("a", fixture.client.uid)
        assertEquals(Outcome.Failure(AppError.Unauthorized), fixture.auth.signInWithGoogleToken("google-b"))
        assertTrue(fixture.client.signIns.isEmpty())
    }

    @Test
    fun expiredSdkIdentityRetainsReauthReceiptAllowsBAndLaterReauthenticatedARetriesDeletionBeforeSession() = runTest {
        val fixture = Fixture(this)
        fixture.repository.signInFirebase("a")
        fixture.api.failure = AppError.Unauthorized
        fixture.client.forcedFailure = FirebaseReauthenticationRequired("a")
        assertEquals(Outcome.Failure(AppError.Unauthorized), fixture.repository.deleteAccount())
        assertTrue(fixture.store.read().getJSONObject("deletion-server-a").getBoolean("requiresReauthentication"))
        fixture.api.failure = null
        assertEquals(Outcome.Success(Unit), fixture.auth.signInWithGoogleToken("google-b"))
        fixture.repository.signOut()
        fixture.client.nextUid = "a"
        assertEquals(Outcome.Failure(AppError.Unauthorized), fixture.auth.signInWithGoogleToken("reauth-google-a"))
        assertNull(fixture.repository.session.first())
        assertNull(fixture.client.uid)
        assertFalse(fixture.store.read().has("deletion-server-a"))
        assertEquals(1, fixture.api.deletions)
    }

    @Test
    fun explicitSignOutClearsMatchingFirebaseClientAndKeepsOnlyUidReferenceInAccountJson() = runTest {
        val fixture = Fixture(this)
        fixture.repository.signInFirebase("a")
        assertEquals("firebase:a", fixture.store.read().getJSONObject("account-server-a").getString("token"))
        fixture.repository.signOut()
        assertNull(fixture.client.uid)
        assertEquals(listOf("a"), fixture.client.cleared)
        assertFalse(fixture.store.read().toString().contains("fixture.payload"))
    }

    @Test
    fun unverifiedReceiptPersistedBeforeCrashStillPurgesLocalAccountOnRestartWithoutRemoteRetry() = runTest {
        val fixture = Fixture(this)
        fixture.repository.signInFirebase("a")
        backgroundScope.coroutineContext.cancelChildren()
        fixture.store.change { root ->
            root.put(
                "deletion-server-a",
                JSONObject().put("ownerId", "server-a").put("token", "firebase:a")
                    .put("remoteConfirmed", false).put("localPurged", false)
                    .put("remoteCompletionUnverified", true)
                    .put("audio", JSONArray()).put("references", JSONArray()),
            )
        }
        fixture.api.failure = AppError.Network
        val restarted = DurableStudioRepository(
            context,
            fixture.store,
            fixture.api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        assertNull(restarted.activeAccountId)
        assertNull(restarted.session.first())
        assertTrue(restarted.observeMine().first().isEmpty())
        assertTrue(fixture.store.read().has("account-server-a"))
        runCurrent()
        val receipt = fixture.store.read().getJSONObject("deletion-server-a")
        assertTrue(receipt.getBoolean("remoteCompletionUnverified"))
        assertTrue(receipt.getBoolean("localPurged"))
        assertFalse(fixture.store.read().has("account-server-a"))
        assertEquals(0, fixture.api.deletions)
        assertNull(fixture.client.uid)
    }

    @Test
    fun freshReauthenticationWhoseDeleteRetryFailsRetainsARefreshSourceAndPreventsDestructiveBSwitch() = runTest {
        val fixture = Fixture(this)
        fixture.repository.signInFirebase("a")
        fixture.api.failure = AppError.Unauthorized
        fixture.client.forcedFailure = FirebaseReauthenticationRequired("a")
        fixture.repository.deleteAccount()
        fixture.api.failure = AppError.Network
        fixture.client.nextUid = "a"
        assertEquals(Outcome.Failure(AppError.Network), fixture.auth.signInWithGoogleToken("reauth-a"))
        assertEquals("a", fixture.client.uid)
        assertNull(fixture.repository.session.first())
        assertFalse(fixture.store.read().getJSONObject("deletion-server-a").getBoolean("requiresReauthentication"))
        fixture.client.nextUid = "b"
        assertEquals(Outcome.Failure(AppError.Network), fixture.auth.signInWithGoogleToken("google-b"))
        assertEquals(listOf("reauth-a"), fixture.client.signIns)
        assertEquals("a", fixture.client.uid)
    }

    private inner class Fixture(scope: TestScope) {
        val client = TestFirebaseClient().apply { uid = "a" }
        val store = StudioStore(context)
        val api = JournalApi(client)
        val repository = DurableStudioRepository(
            context,
            store,
            api,
            scope.backgroundScope,
            StandardTestDispatcher(scope.testScheduler),
        )
        val auth = FirebaseGoogleAuthRepository(
            client,
            true,
            repository::completeFirebaseSignIn,
            repository::isFirebaseSessionActive,
            retainForDeletion = repository::needsFirebaseDeletionCredentials,
            prepareSwitch = repository::prepareFirebaseSignIn,
        )
    }

    private inner class JournalApi(client: TestFirebaseClient) :
        StudioApi(
            context,
            ApiConfiguration("https://studio.test", ApiAuthMode.Firebase, false),
            FirebaseSdkTokenProvider(client),
        ) {
        var failure: AppError? = null
        var deletions = 0
        override suspend fun request(
            method: String,
            path: String,
            token: String,
            body: JSONObject?,
            key: String?,
        ): JSONObject {
            authorization(token)
            if (path == "/v1/me") {
                failure?.let { throw ApiFailure(it) }
                if (method == "DELETE") {
                    deletions++
                    return JSONObject()
                }
                return JSONObject().put("id", "server-" + token.removePrefix("firebase:"))
                    .put("displayName", "Fixture account").put("termsVersion", "2026-10-14")
            }
            return when {
                path == "/v1/me/quota" -> JSONObject().put("remaining", 25).put("limit", 25)
                path.startsWith("/v1/tracks?") -> JSONObject().put("tracks", JSONArray())
                path == "/v1/likes" -> JSONObject().put("trackIds", JSONArray())
                path == "/v1/playlists" -> JSONObject().put("playlists", JSONArray())
                path == "/v1/recordings" -> JSONObject().put("recordings", JSONArray())
                else -> error("Unexpected fixture request")
            }
        }
    }
}
