package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.room.withTransaction
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.domain.session.Onboarding
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.User
import com.illiamelnyk.studio.core.domain.session.UserId
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
class TypedStudioStoreTest {
    private lateinit var context: Context

    @Before fun clean() {
        context = RuntimeEnvironment.getApplication()
        StudioStore.clearForTests(context)
    }

    @Test fun likingOneTrackDoesNotRewriteAccountOrUnrelatedTracks() = runBlocking {
        val store = StudioStore(context)
        val account = account("owner")
        store.saveAccount(account)
        withContext(Dispatchers.IO) {
            val sql = store.database.openHelper.writableDatabase
            sql.execSQL(
                "CREATE TRIGGER forbid_account_delete BEFORE DELETE ON studio_accounts BEGIN SELECT RAISE(ABORT, 'account rewrite'); END",
            )
            sql.execSQL(
                "CREATE TRIGGER forbid_account_update BEFORE UPDATE ON studio_accounts BEGIN SELECT RAISE(ABORT, 'account rewrite'); END",
            )
            sql.execSQL(
                "CREATE TRIGGER forbid_other_track_update BEFORE UPDATE ON studio_tracks WHEN OLD.trackId != '${account.tracks.first().id.value}' BEGIN SELECT RAISE(ABORT, 'unrelated track rewrite'); END",
            )
            sql.execSQL(
                "CREATE TRIGGER forbid_track_delete BEFORE DELETE ON studio_tracks BEGIN SELECT RAISE(ABORT, 'track rewrite'); END",
            )
        }
        val target = account.tracks.first().id.value
        store.saveAccount(
            account.copy(
                likes = setOf(target),
                tracks = account.tracks.mapIndexed { index, track ->
                    if (index == 0) track.copy(isLiked = true) else track
                },
            ),
        )
        val restored = store.readAccount("owner")!!
        assertTrue(restored.tracks.first().isLiked)
        assertEquals(account.tracks.drop(1), restored.tracks.drop(1))
        assertEquals(setOf(target), restored.likes)
    }

    @Test fun committedOwnerSwitchAndMutationReachRoomFlowWithoutPartialCollections() = runBlocking {
        val store = StudioStore(context)
        store.initialize()
        val changes = Channel<ActiveAccountChange>(Channel.UNLIMITED)
        val job = launch(Dispatchers.IO) { store.observeActiveAccount().collect { changes.send(it) } }
        try {
            withTimeout(5000) { changes.receive() }
            val expected = account("flow-owner")
            val committed = store.saveAccount(expected)
            val observed = withTimeout(5000) {
                var next = changes.receive()
                while (next.revision < committed.revision) next = changes.receive()
                next
            }
            assertEquals(expected, observed.account)
            assertEquals(committed.revision, observed.revision)
        } finally {
            job.cancel()
        }
    }

    @Test fun importedOwnerPurgeRemovesPersonalDataAndCannotResurrectOnRestart() = runBlocking {
        val account = account("private-owner")
        val track = account.tracks.first()
        val root = JSONObject().put("active", "private-owner")
            .put("account-private-owner", AccountJsonCodec.encode(account))
            .put("request-" + track.id.value, GenerationRequest("private prompt").toJson())
            .put("unknown-extension", JSONObject().put("version", 99))
        File(context.filesDir, StudioStore.LEGACY_FILE).writeText(root.toString())
        val store = StudioStore(context)
        store.initialize()
        store.purgeAccount(
            AccountDeletionIntent(
                "private-owner",
                null,
                account.tracks.map { DeletionAudio(it.id.value, it.audioUri) },
                emptyList(),
                true,
            ),
        )
        // The imported file is gone, so no copy of the purged prompt is left to import again.
        assertFalse(File(context.filesDir, StudioStore.LEGACY_FILE).exists())
        assertNull(store.requestFor("private-owner", track.id.value))
        StudioStore.closeForTests(context)
        val restarted = StudioStore(context)
        restarted.initialize()
        assertNull(restarted.readAccount("private-owner"))
        assertNull(restarted.readActive().account)
    }

    @Test fun generationAndDetailJournalsNeverReadThroughAnotherOwnerWithSameTrackId() = runBlocking {
        val store = StudioStore(context)
        val account = account("owner-a")
        val id = account.tracks.first().id.value
        val request = GenerationRequest("private request prompt")
        store.saveAccount(account, generationRequests = mapOf(id to request), retryLinks = mapOf(id to "replacement"))
        store.saveDetail("owner-a", account.tracks.first().copy(customLyrics = "private lyrics"))
        assertEquals(request, store.requestFor("owner-a", id))
        assertEquals("replacement", store.retryFor("owner-a", id))
        assertEquals("private lyrics", store.detailFor("owner-a", id)?.customLyrics)
        assertNull(store.requestFor("owner-b", id))
        assertNull(store.retryFor("owner-b", id))
        assertNull(store.detailFor("owner-b", id))
        val second = account.copy(
            session = account.session.copy(user = account.session.user.copy(id = UserId("owner-b"))),
        )
        val secondRequest = GenerationRequest("second owner private prompt")
        store.saveAccount(
            second,
            generationRequests = mapOf(id to secondRequest),
            retryLinks = mapOf(id to "second-replacement"),
        )
        store.saveDetail("owner-b", second.tracks.first().copy(customLyrics = "second private lyrics"))
        assertEquals(request, store.requestFor("owner-a", id))
        assertEquals(secondRequest, store.requestFor("owner-b", id))
        assertEquals("replacement", store.retryFor("owner-a", id))
        assertEquals("second-replacement", store.retryFor("owner-b", id))
        assertEquals("private lyrics", store.detailFor("owner-a", id)?.customLyrics)
        assertEquals("second private lyrics", store.detailFor("owner-b", id)?.customLyrics)
    }

    @Test fun uploadIdentityDoesNotAliasDifferentUrisWithTheSameJavaHashCode() = runBlocking {
        val store = StudioStore(context)
        val first = "file:///private/Aa.wav"
        val second = "file:///private/BB.wav"
        assertEquals(first.hashCode(), second.hashCode())
        store.saveUpload("owner", first, "upload-first")
        assertNull(store.uploadFor("owner", second))
        store.saveUpload("owner", second, "upload-second")
        assertEquals("upload-first", store.uploadFor("owner", first))
        assertEquals("upload-second", store.uploadFor("owner", second))
    }

    @Test fun malformedJournalObjectsCannotBecomeTypedStrings() = runBlocking {
        val account = account("owner")
        val invalid = JSONObject().put("future", "preserve")
        val root = JSONObject().put("account-owner", AccountJsonCodec.encode(account))
            .put("upload-owner-42", invalid)
            .put("retry-" + account.tracks.first().id.value, invalid)
            .put("recording-receipt-" + StudioStore.ownerHash("owner") + "-fixture", invalid)
            .put("pending-owner", JSONObject().put("signature", invalid).put("key", invalid))
        File(context.filesDir, StudioStore.LEGACY_FILE).writeText(root.toString())
        val store = StudioStore(context)
        store.initialize()
        assertTrue(store.database.typedDao().allReferenceUpload().isEmpty())
        assertTrue(store.database.typedDao().allRetryLink().isEmpty())
        assertTrue(store.database.typedDao().allRecordingReceipt().isEmpty())
        assertTrue(store.generationAttempt("owner")!!.blocked)
    }

    @Test fun delayedPrePurgeSnapshotCannotReactivateOwnerOrRecreateDeletedReferences() = runBlocking {
        val store = StudioStore(context)
        val runtime = StudioAccountRuntime(
            context,
            store,
            StudioApi(context),
            Dispatchers.IO,
            com.illiamelnyk.studio.core.domain.track.GenerationWorkScheduler.None,
            com.illiamelnyk.studio.core.domain.notification.GenerationNotifications.None,
        )
        val sessions = DurableSessionRepository(runtime)
        assertTrue(sessions.signInDemo() is com.illiamelnyk.studio.core.domain.Outcome.Success)
        val references = runtime.referenceDirectory()
        val delayedBeforePurge = store.readActive()
        assertTrue(sessions.deleteAccount() is com.illiamelnyk.studio.core.domain.Outcome.Success)
        assertFalse(references.exists())
        runtime.mutex.withLock { runtime.publish(delayedBeforePurge) }
        assertNull(runtime.activeAccountId)
        assertNull(runtime.state.value)
        assertFalse(references.exists())
        assertTrue(runCatching { runtime.referenceDirectory() }.isFailure)
        assertNull(store.readActive().account)
    }

    @Test fun unreadableStoredAccountClosesAccessInsteadOfCrashing() = runBlocking {
        val store = StudioStore(context)
        val runtime = runtime(store)
        runtime.initialize()
        runtime.mutex.withLock { runtime.save(account("owner")) }
        assertEquals("owner", runtime.activeAccountId)
        val crashes = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO +
                kotlinx.coroutines.CoroutineExceptionHandler { _, error -> crashes += error },
        )
        try {
            runtime.observeStorage(scope)
            // No version of the app writes this value, so mapping the row back to a snapshot fails.
            store.database.withTransaction {
                store.database.openHelper.writableDatabase.execSQL(
                    "UPDATE studio_accounts SET signInMethod = 'unknown-method'",
                )
            }
            withTimeout(5_000) { while (runtime.state.value != null) kotlinx.coroutines.delay(10) }
            assertTrue(crashes.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test fun cancelledPostCommitSavePublishesNewOwnerAndRejectsDelayedOldOwner() = runBlocking {
        val store = StudioStore(context)
        val runtime = runtime(store)
        runtime.initialize()
        runtime.mutex.withLock { runtime.save(account("before")) }
        val delayed = store.readActive()
        val replacement = account("after")
        val task = async {
            runtime.mutex.withLock {
                runtime.commitStateChange {
                    val committed = store.saveAccount(replacement)
                    currentCoroutineContext().cancel()
                    currentCoroutineContext().ensureActive()
                    committed
                }
            }
        }
        try {
            task.await()
        } catch (_: CancellationException) { }
        assertTrue(task.isCancelled)
        assertEquals("after", runtime.activeAccountId)
        runtime.mutex.withLock { runtime.publish(delayed) }
        assertEquals("after", runtime.activeAccountId)
        assertEquals(replacement, runtime.state.value)
    }

    @Test fun cancelledPostCommitSignOutFencesOldSnapshotsAndClearsTheFirebaseSdk() = runBlocking {
        val store = StudioStore(context)
        val cleared = mutableListOf<String>()
        val provider = object : BearerTokenProvider {
            override suspend fun getToken(firebaseUid: String): FirebaseBearerToken? = null
            override suspend fun clearSession(firebaseUid: String) {
                cleared += firebaseUid
            }
        }
        val runtime = runtime(store, StudioApi(context, bearerTokens = provider))
        runtime.initialize()
        runtime.mutex.withLock { runtime.save(account("before")) }
        val delayed = store.readActive()
        val task = async {
            runtime.mutex.withLock {
                runtime.signOutActive("firebase:before") {
                    val committed = store.clearActive()
                    currentCoroutineContext().cancel()
                    currentCoroutineContext().ensureActive()
                    committed
                }
            }
        }
        try {
            task.await()
        } catch (_: CancellationException) { }
        assertTrue(task.isCancelled)
        assertEquals(listOf("before"), cleared)
        assertTrue(store.isSignedOut())
        runtime.mutex.withLock { runtime.publish(delayed) }
        assertNull(runtime.activeAccountId)
        assertNull(runtime.state.value)
        assertTrue(runCatching { runtime.referenceDirectory() }.isFailure)
    }

    private fun runtime(store: StudioStore, api: StudioApi = StudioApi(context)) = StudioAccountRuntime(
        context,
        store,
        api,
        Dispatchers.IO,
        com.illiamelnyk.studio.core.domain.track.GenerationWorkScheduler.None,
        com.illiamelnyk.studio.core.domain.notification.GenerationNotifications.None,
    )

    private fun account(owner: String) = AccountSnapshot(
        Session(
            User(UserId(owner), null, false, SignInMethod.Email, "Listener", false, null, false),
            Onboarding(emptySet(), "2026-10-14"),
        ),
        token = null,
        tracks = DemoCatalog.tracks.take(3),
    )
}
