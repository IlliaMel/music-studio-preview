package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.core.net.toUri
import com.illiamelnyk.studio.core.data.local.database.RecordingDraftEntity
import com.illiamelnyk.studio.core.data.network.RecordingDto
import com.illiamelnyk.studio.core.domain.session.Onboarding
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.User
import com.illiamelnyk.studio.core.domain.session.UserId
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecordingDraftHandoffTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val owner = "recording-handoff-owner"
    private val account = AccountSnapshot(
        Session(
            User(UserId(owner), null, false, SignInMethod.Email, "Recorder", false, null, false),
            Onboarding(emptySet(), "2026-10-14"),
        ),
        "dev:fixture",
    )

    @Before fun clean() {
        StudioStore.clearForTests(context)
    }

    @After fun close() {
        StudioStore.closeForTests(context)
    }

    @Test
    fun cancellationAfterCommitPreservesTheStagedAudioAndRecoveryFinishesTheSameDraft() = runTest {
        val store = StudioStore(context)
        val api = RecordingApi()
        val files = RecordingFiles(context)
        val source = source()
        val drafts = RecordingDrafts(store, files, api) { value ->
            store.saveRecordingDraft(value)
            currentCoroutineContext().cancel()
            yield()
        }
        val save = async { drafts.save(account, source.toUri().toString(), "Idea", 12.seconds) }
        try {
            save.await()
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) { }
        val persisted = store.recordingDrafts(owner).single()
        assertTrue(File(java.net.URI(persisted.uri)).exists())
        val restarted = RecordingDrafts(store, files, api)
        val recovered = restarted.recover(account)
        assertEquals("recording-" + persisted.localId, recovered.single().track.id.value)
        assertEquals(listOf(persisted.localId), api.ids)
        restarted.acknowledge(recovered)
        assertTrue(store.recordingDrafts(owner).isEmpty())
        assertFalse(File(java.net.URI(persisted.uri)).exists())
        assertEquals(recovered.single().track.id.value, store.recordingReceipt(persisted.key))
        assertTrue(source.exists())
    }

    @Test
    fun failureBeforeCommitRemovesOnlyTheUnreferencedCopy() = runTest {
        val store = StudioStore(context)
        var attempted: RecordingDraftEntity? = null
        val source = source()
        val drafts = RecordingDrafts(store, RecordingFiles(context), RecordingApi()) { value ->
            attempted = value
            throw java.io.IOException("Simulated rollback")
        }
        assertTrue(runCatching { drafts.save(account, source.toUri().toString(), "Idea", 12.seconds) }.isFailure)
        assertTrue(store.recordingDrafts(owner).isEmpty())
        assertFalse(File(java.net.URI(checkNotNull(attempted).uri)).exists())
        assertTrue(source.exists())
    }

    @Test
    fun ambiguousReadbackPreservesAudioUntilTheDatabaseCanReopen() = runTest {
        val store = StudioStore(context)
        var attempted: RecordingDraftEntity? = null
        val drafts = RecordingDrafts(store, RecordingFiles(context), RecordingApi()) { value ->
            store.saveRecordingDraft(value)
            attempted = value
            store.database.close()
            currentCoroutineContext().cancel()
            yield()
        }
        val save = async { drafts.save(account, source().toUri().toString(), "Idea", 12.seconds) }
        try {
            save.await()
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) { }
        val pending = checkNotNull(attempted)
        assertTrue(File(java.net.URI(pending.uri)).exists())
        StudioStore.closeForTests(context)
        val reopened = StudioStore(context)
        assertEquals(pending, reopened.recordingDraft(pending.key))
        assertTrue(File(java.net.URI(pending.uri)).exists())
    }

    private fun source(): File = File(context.filesDir, "handoff-source.wav").apply {
        writeBytes("RIFF1234WAVE".toByteArray() + ByteArray(48))
    }

    private inner class RecordingApi :
        StudioApi(context, ApiConfiguration("https://studio.test", ApiAuthMode.Development, true)) {
        val ids = mutableListOf<String>()
        internal override suspend fun putRecording(
            id: String,
            uri: String,
            title: String,
            durationMs: Long,
            token: String,
        ): RecordingDto {
            assertTrue(File(java.net.URI(uri)).exists())
            ids += id
            return RecordingDto(
                id,
                title,
                "audio/wav",
                60,
                durationMs,
                "2026-10-21T00:00:00Z",
                "2026-10-21T00:00:00Z",
                baseUrl + "/v1/recordings/" + id + "/audio",
            )
        }
    }
}
