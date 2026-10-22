package com.illiamelnyk.studio.core.data.local

import android.content.Context
import android.database.sqlite.SQLiteException
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.data.local.database.RecordingDraftEntity
import com.illiamelnyk.studio.core.domain.playlist.Playlist
import com.illiamelnyk.studio.core.domain.session.Onboarding
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.User
import com.illiamelnyk.studio.core.domain.session.UserId
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
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
class TypedStudioStoreRollbackTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before fun clean() {
        StudioStore.clearForTests(context)
    }

    @After fun close() {
        StudioStore.closeForTests(context)
    }

    @Test
    fun failedTypedSaveRollsBackAccountOwnerAndAllGenerationJournals() = runBlocking {
        val store = StudioStore(context)
        val original = account("owner-a")
        val id = original.tracks.single().id.value
        val request = GenerationRequest("Original paid request")
        store.saveAccount(
            original,
            generationRequests = mapOf(id to request),
            retryLinks = mapOf(id to "original-replacement"),
        )
        val attempt = PendingGeneration("stable-paid-key", request, 1)
        store.putGenerationAttempt("owner-a", attempt)
        val before = store.readActive()
        val outboxes = store.outboxes()
        withContext(Dispatchers.IO) {
            store.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_typed_commit BEFORE INSERT ON studio_notification_outbox BEGIN SELECT RAISE(ABORT, 'rollback typed save'); END",
            )
        }
        try {
            val failure = runCatching {
                store.saveAccount(
                    account("owner-b"),
                    generationRequests = mapOf(id to GenerationRequest("Different request")),
                    completeAttempt = true,
                    retryLinks = mapOf(id to "changed-replacement"),
                    removedTracks = setOf(id),
                )
            }.exceptionOrNull()
            assertTrue("SQLite failure must abort the transaction", failure is SQLiteException)
            assertEquals(before, store.readActive())
            assertEquals(original, store.readAccount("owner-a"))
            assertNull(store.readAccount("owner-b"))
            assertEquals(request, store.requestFor("owner-a", id))
            assertEquals("original-replacement", store.retryFor("owner-a", id))
            assertEquals(attempt, store.generationAttempt("owner-a"))
            assertEquals(outboxes, store.outboxes())
        } finally {
            withContext(Dispatchers.IO) {
                store.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_typed_commit")
            }
        }
        StudioStore.closeForTests(context)
        val reopened = StudioStore(context)
        assertEquals(original, reopened.readActive().account)
        assertEquals(attempt, reopened.generationAttempt("owner-a"))
    }

    @Test
    fun typedPurgeRetainsPendingDeletionAndOtherOwnersWithTheSameTrackIdAcrossRestart() = runBlocking {
        val store = StudioStore(context)
        val first = account("owner-a")
        val second = account("owner-b")
        val id = first.tracks.single().id.value
        val requestA = GenerationRequest("Owner A private request")
        val requestB = GenerationRequest("Owner B private request")
        store.saveAccount(first, generationRequests = mapOf(id to requestA), retryLinks = mapOf(id to "retry-a"))
        store.saveAccount(second, generationRequests = mapOf(id to requestB), retryLinks = mapOf(id to "retry-b"))
        store.putGenerationAttempt("owner-a", PendingGeneration("paid-a", requestA, 1))
        store.putGenerationAttempt("owner-b", PendingGeneration("paid-b", requestB, 1))
        store.saveDetail("owner-a", first.tracks.single().copy(customLyrics = "private A"))
        store.saveDetail("owner-b", second.tracks.single().copy(customLyrics = "private B"))
        store.saveUpload("owner-a", "file:///private/a.wav", "upload-a")
        store.saveUpload("owner-b", "file:///private/b.wav", "upload-b")
        val hash = StudioStore.ownerHash("owner-a")
        val draft =
            RecordingDraftEntity(
                "recording-pending-$hash-test",
                "owner-a",
                hash,
                "draft-a",
                "file:///private/staged.wav",
                "Idea",
                12000,
                false,
            )
        store.saveRecordingDraft(draft)
        store.confirmRecording(draft, "recording-draft-a")
        val intent =
            AccountDeletionIntent(
                "owner-a",
                "firebase:a",
                first.tracks.map {
                    DeletionAudio(it.id.value, it.audioUri)
                },
                listOf("file:///private/a.wav"),
                false,
            )
        store.saveDeletion(intent)
        val result = store.purgeAccount(intent)
        assertEquals(second, result.account)
        assertNull(store.readAccount("owner-a"))
        assertEquals(second, store.readAccount("owner-b"))
        assertEquals(second, store.readActive().account)
        assertNull(store.requestFor("owner-a", id))
        assertNull(store.retryFor("owner-a", id))
        assertNull(store.detailFor("owner-a", id))
        assertNull(store.generationAttempt("owner-a"))
        assertNull(store.uploadFor("owner-a", "file:///private/a.wav"))
        assertTrue(store.recordingDrafts("owner-a").isEmpty())
        assertNull(store.recordingReceipt(draft.key))
        assertEquals(requestB, store.requestFor("owner-b", id))
        assertEquals("retry-b", store.retryFor("owner-b", id))
        assertEquals("private B", store.detailFor("owner-b", id)?.customLyrics)
        assertEquals("paid-b", store.generationAttempt("owner-b")?.key)
        assertEquals("upload-b", store.uploadFor("owner-b", "file:///private/b.wav"))
        val pending = store.pendingDeletions().single()
        assertEquals("owner-a", pending.ownerId)
        assertTrue(pending.localPurged)
        assertFalse(pending.remoteConfirmed)
        assertTrue(pending.audio.isEmpty() && pending.references.isEmpty())
        assertEquals(listOf("owner-b"), store.outboxes().map { it.ownerId })
        StudioStore.closeForTests(context)
        val reopened = StudioStore(context)
        assertNull(reopened.readAccount("owner-a"))
        assertEquals(second, reopened.readActive().account)
        assertEquals(pending, reopened.pendingDeletions().single())
    }

    private fun account(owner: String): AccountSnapshot {
        val track = DemoCatalog.tracks.first()
        val session =
            Session(
                User(UserId(owner), null, false, SignInMethod.Email, owner, false, null, false),
                Onboarding(emptySet(), "2026-10-14"),
            )
        return AccountSnapshot(
            session,
            null,
            tracks = listOf(track),
            likes = setOf(track.id.value),
            playlists = listOf(Playlist("saved", "Saved", listOf(track))),
        )
    }
}
