package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.core.net.toUri
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecordingDraftsTest {
    private lateinit var context: Context
    private lateinit var source: File

    @Before
    fun clean() {
        context = RuntimeEnvironment.getApplication()
        RecordingFiles(context).deleteRecordingStaging("owner")
        RecordingFiles(context).deleteRecordingStaging("second-owner")
        context.filesDir.listFiles()?.filter {
            it.name.startsWith("studio-state-v1.json") ||
                it.name.startsWith("recording-")
        }
            ?.forEach { it.delete() }
        source = File(context.cacheDir, "recording-test.wav").apply {
            writeBytes("RIFF1234WAVEdata-recording".toByteArray())
        }
    }

    @Test
    fun savedDraftRestoresOnAnotherInstallWithoutSpendingCreditsAndSupportsRenameDelete() = runTest {
        val api = MemoryRecordings(context)
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        val credits = repository.credits.first()
        val track = (
            repository.saveRecording(
                source.toUri().toString(),
                "My idea",
                12.seconds,
            ) as Outcome.Success
            ).value
        assertTrue(track.audioUri!!.contains("/v1/recordings/"))
        assertEquals(credits, repository.credits.first())
        assertEquals(1, api.rows.size)
        assertEquals(0, api.generationPosts)
        assertEquals(Outcome.Success(Unit), repository.rename(track.id, "Renamed idea"))
        repository.signOut()
        backgroundScope.coroutineContext.cancelChildren()
        context.filesDir.listFiles()?.filter { it.name.startsWith("studio-state-v1.json") }?.forEach { it.delete() }
        val second = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        second.signInServer()
        val restored = second.observeMine().first().single()
        assertEquals(track.id, restored.id)
        assertEquals("Renamed idea", restored.title)
        assertEquals(mapOf("Authorization" to "Bearer dev:illia"), second.playbackHeaders(restored))
        assertEquals(Outcome.Success(Unit), second.delete(restored.id))
        assertTrue(second.observeMine().first().isEmpty())
        assertTrue(api.rows.isEmpty())
    }

    @Test
    fun uncertainPutRetainsPrivateCopyAndRestartRetriesTheSameUuidExactlyOnce() = runTest {
        val api = MemoryRecordings(context).apply { loseFirstResponse = true }
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        assertEquals(
            Outcome.Failure(AppError.Timeout),
            repository.saveRecording(source.toUri().toString(), "Retry idea", 12.seconds),
        )
        assertEquals(1, api.rows.size)
        val originalId = api.putIds.single()
        assertTrue(context.filesDir.walkTopDown().any { it.isFile && it.name.startsWith("recording-" + originalId) })
        assertTrue(repository.observeMine().first().isEmpty())
        repository.signOut()
        backgroundScope.coroutineContext.cancelChildren()
        val restarted = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        restarted.signInServer()
        assertEquals(listOf(originalId, originalId), api.putIds)
        assertEquals(1, restarted.observeMine().first().size)
        assertEquals(1, api.rows.size)
        assertFalse(context.filesDir.walkTopDown().any { it.isFile && it.name.startsWith("recording-" + originalId) })
        val repeated = restarted.saveRecording(source.toUri().toString(), "Retry idea", 12.seconds)
        assertTrue(repeated is Outcome.Success)
        assertEquals(2, api.putIds.size)
        assertEquals(0, api.generationPosts)
    }

    @Test
    fun pendingRecordingNeverUploadsUnderAnotherOwnerAndDeletionPurgesPendingCopy() = runTest {
        val api = MemoryRecordings(context).apply { failPut = true }
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        repository.saveRecording(source.toUri().toString(), "Private idea", 12.seconds)
        val id = api.putIds.single()
        repository.signOut()
        api.owner = "second-owner"
        api.failPut = false
        repository.signInServer()
        assertTrue(repository.observeMine().first().isEmpty())
        assertEquals(1, api.putIds.size)
        repository.signOut()
        api.owner = "owner"
        api.failPut = true
        repository.signInServer()
        assertEquals(2, api.putIds.size)
        assertTrue(repository.deleteAccount() is Outcome.Success)
        assertFalse(context.filesDir.walkTopDown().any { it.isFile && it.name.startsWith("recording-" + id) })
        assertFalse(StudioStore(context).read().keys().asSequence().any { it.startsWith("recording-pending-") })
    }

    @Test
    fun deletedRemoteRecordingDisappearsAfterSignInRefreshWithoutResurrectingIt() = runTest {
        val api = MemoryRecordings(context)
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        repository.saveRecording(source.toUri().toString(), "Gone idea", 12.seconds)
        repository.signOut()
        api.rows.clear()
        repository.signInServer()
        assertTrue(repository.observeMine().first().isEmpty())
        assertEquals(1, api.putIds.size)
        assertEquals(
            Outcome.Failure(AppError.InvalidReference),
            repository.saveRecording(source.toUri().toString(), "Gone idea", 12.seconds),
        )
    }

    @Test
    fun foreignOriginDraftMetadataCannotEnterPrivatePlaybackState() = runTest {
        val api = MemoryRecordings(context).apply { badAudioOrigin = true }
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        assertEquals(
            Outcome.Failure(AppError.Server),
            repository.saveRecording(source.toUri().toString(), "Bad origin", 12.seconds),
        )
        assertTrue(repository.observeMine().first().isEmpty())
    }

    @Test
    fun acknowledgementStoreFailureKeepsTheRetryablePrivateAudio() = runTest {
        val api = MemoryRecordings(context)
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        val account = AccountJsonCodec.decode(store.read().getJSONObject("account-owner"))
        val drafts = RecordingDrafts(store, RecordingFiles(context), api)
        val saved = drafts.save(account, source.toUri().toString(), "Crash safe", 12.seconds)
        val pending = store.read().getJSONObject(saved.key)
        val privateFile = File(java.net.URI(pending.getString("uri")))
        File(context.filesDir, "studio-state-v1.json.new").mkdir()
        try {
            drafts.acknowledge(listOf(saved))
            org.junit.Assert.fail("Journal write must fail")
        } catch (_: java.io.IOException) {
            Unit
        }
        assertTrue(privateFile.exists())
        assertFalse(store.read().getJSONObject(saved.key).optBoolean("confirmed"))
        assertEquals(saved.track.id, drafts.recover(account).single().track.id)
        assertEquals(
            listOf(saved.track.id.value.removePrefix("recording-"), saved.track.id.value.removePrefix("recording-")),
            api.putIds,
        )
        drafts.acknowledge(listOf(saved))
        assertFalse(privateFile.exists())
    }

    @Test
    fun cleanupFailureRetainsConfirmedIntentAndNeverResubmitsTheRecording() = runTest {
        val api = MemoryRecordings(context)
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        val account = AccountJsonCodec.decode(store.read().getJSONObject("account-owner"))
        val drafts = RecordingDrafts(store, RecordingFiles(context), api)
        val saved = drafts.save(account, source.toUri().toString(), "Cleanup safe", 12.seconds)
        val pending = store.read().getJSONObject(saved.key)
        val privateFile = File(java.net.URI(pending.getString("uri")))
        privateFile.delete()
        privateFile.mkdir()
        val blocker = File(privateFile, "cleanup-blocker").apply { writeText("keep") }
        try {
            drafts.acknowledge(listOf(saved))
            org.junit.Assert.fail("Nonempty private directory cannot be deleted")
        } catch (_: java.io.IOException) {
            Unit
        }
        assertTrue(store.read().getJSONObject(saved.key).getBoolean("confirmed"))
        assertTrue(drafts.recover(account.copy(tracks = listOf(saved.track))).isEmpty())
        assertEquals(1, api.putIds.size)
        blocker.delete()
        privateFile.delete()
        drafts.recover(account.copy(tracks = listOf(saved.track)))
        assertFalse(store.read().has(saved.key))
        assertEquals(1, api.putIds.size)
    }

    @Test
    fun trustedDeletedTombstoneClearsPendingPrivateAudioAndDoesNotRetry() = runTest {
        val api = MemoryRecordings(context).apply { failPut = true }
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        repository.saveRecording(source.toUri().toString(), "Deleted pending", 12.seconds)
        val pendingKey = store.read().keys().asSequence().single { it.startsWith("recording-pending-") }
        val privateFile = File(java.net.URI(store.read().getJSONObject(pendingKey).getString("uri")))
        val account = AccountJsonCodec.decode(store.read().getJSONObject("account-owner"))
        val drafts = RecordingDrafts(store, RecordingFiles(context), api)
        api.failPut = false
        api.failCode = "RECORDING_DELETED"
        assertTrue(drafts.recover(account).isEmpty())
        assertFalse(privateFile.exists())
        assertFalse(store.read().has(pendingKey))
        drafts.recover(account)
        assertEquals(2, api.putIds.size)
    }

    @Test
    fun remoteRenameAndDeletionUpdateLocalRecordingPlaylistMembers() = runTest {
        val api = MemoryRecordings(context).apply { playlistCreated = true }
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        val track = (
            repository.saveRecording(
                source.toUri().toString(),
                "Playlist idea",
                12.seconds,
            ) as Outcome.Success
            ).value
        assertTrue(repository.addTrack("playlist", track) is Outcome.Success)
        repository.signOut()
        api.rows.values.single().put("title", "Other install rename")
        repository.signInServer()
        assertEquals("Other install rename", repository.observeAll().first().single().tracks.single().title)
        repository.signOut()
        api.rows.clear()
        repository.signInServer()
        assertTrue(repository.observeAll().first().single().tracks.isEmpty())
    }

    @Test
    fun restartReclaimsUnjournaledStagingWhilePreservingPendingAudioAndAnotherOwner() = runTest {
        val api = MemoryRecordings(context).apply { failPut = true }
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        repository.saveRecording(source.toUri().toString(), "Pending survives", 12.seconds)
        backgroundScope.coroutineContext.cancelChildren()
        val pendingKey = store.read().keys().asSequence().single { it.startsWith("recording-pending-") }
        val pendingAudio = File(java.net.URI(store.read().getJSONObject(pendingKey).getString("uri")))
        val files = RecordingFiles(context)
        val orphan = File(java.net.URI(files.copyToStaging(source.toUri().toString(), "recording-orphan", "owner")))
        val foreign =
            File(java.net.URI(files.copyToStaging(source.toUri().toString(), "recording-foreign", "second-owner")))
        val restartedStore = StudioStore(context)
        val account = AccountJsonCodec.decode(restartedStore.read().getJSONObject("account-owner"))

        val recovered = RecordingDrafts(restartedStore, files, api).recover(account)

        assertTrue(recovered.isEmpty())
        assertFalse(orphan.exists())
        assertTrue(foreign.exists())
        assertTrue(pendingAudio.exists())
        assertTrue(restartedStore.read().has(pendingKey))
        assertEquals(2, api.putIds.size)
    }

    @Test
    fun accountDeletionClearsUnjournaledOwnerStagingAndPreservesAnotherOwner() = runTest {
        val api = MemoryRecordings(context)
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        val files = RecordingFiles(context)
        val orphan = File(java.net.URI(files.copyToStaging(source.toUri().toString(), "recording-orphan", "owner")))
        val foreign =
            File(java.net.URI(files.copyToStaging(source.toUri().toString(), "recording-foreign", "second-owner")))
        assertTrue(repository.observeMine().first().isEmpty())
        assertFalse(StudioStore(context).read().keys().asSequence().any { it.startsWith("recording-pending-") })

        assertEquals(Outcome.Success(Unit), repository.deleteAccount())

        assertFalse(orphan.exists())
        assertTrue(foreign.exists())
        assertFalse(StudioStore(context).read().has("account-owner"))
    }

    @Test
    fun failedOrphanCleanupKeepsAccountDeletionIntentForCheckedRetry() = runTest {
        val api = MemoryRecordings(context)
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        val files = RecordingFiles(context)
        val orphan = File(java.net.URI(files.copyToStaging(source.toUri().toString(), "recording-orphan", "owner")))
        assertTrue(orphan.delete())
        assertTrue(orphan.mkdir())
        val blocker = File(orphan, "cleanup-blocker").apply { writeText("keep") }
        assertTrue(repository.deleteAccount() is Outcome.Failure)
        assertTrue(StudioStore(context).read().has("deletion-owner"))
        assertTrue(StudioStore(context).read().has("account-owner"))
        assertTrue(blocker.delete())
        assertTrue(orphan.delete())

        assertEquals(Outcome.Success(Unit), repository.deleteAccount())

        assertFalse(StudioStore(context).read().has("deletion-owner"))
        assertFalse(StudioStore(context).read().has("account-owner"))
    }

    @Test
    fun savedDraftReferenceChecksRemoteExistenceWithoutGenerationOrCreditMutation() = runTest {
        val api = MemoryRecordings(context)
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        val track = (
            repository.saveRecording(source.toUri().toString(), "Reference idea", 12.seconds)
                as Outcome.Success
            ).value
        val credits = repository.credits.first()
        val reference = (repository.prepareRecordingReference(track.id) as Outcome.Success).value
        assertEquals(track.audioUri, reference.uri)
        assertEquals("Reference idea", reference.title)
        assertTrue(repository.isRecordingReferenceCurrent(reference))
        assertEquals(credits, repository.credits.first())
        assertEquals(0, api.generationPosts)
        api.rows.clear()
        assertEquals(Outcome.Failure(AppError.InvalidReference), repository.prepareRecordingReference(track.id))
        repository.signOut()
        assertFalse(repository.isRecordingReferenceCurrent(reference))
        assertEquals(0, api.generationPosts)
    }

    @Test
    fun savedDraftReferenceRejectsNoncanonicalServerAudioBeforeAnyGeneration() = runTest {
        val api = MemoryRecordings(context)
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        val track = (
            repository.saveRecording(source.toUri().toString(), "Owned idea", 12.seconds)
                as Outcome.Success
            ).value
        val row = api.rows.values.single()
        listOf("https://evil.test/audio", track.audioUri + "?token=secret", track.audioUri + "#fragment")
            .forEach { uri ->
                row.put("audioUrl", uri)
                assertEquals(Outcome.Failure(AppError.InvalidReference), repository.prepareRecordingReference(track.id))
            }
        assertEquals(0, api.generationPosts)
        assertEquals(25, repository.credits.first().remaining)
    }

    private class MemoryRecordings(context: Context) : StudioApi(context) {
        var owner = "owner"
        var failPut = false
        var loseFirstResponse = false
        var badAudioOrigin = false
        var generationPosts = 0
        var failCode: String? = null
        var playlistCreated = false
        val putIds = mutableListOf<String>()
        val rows = mutableMapOf<String, JSONObject>()
        private val owners = mutableMapOf<String, String>()
        override suspend fun putRecording(
            id: String,
            uri: String,
            title: String,
            durationMs: Long,
            token: String,
        ): JSONObject {
            putIds += id
            if (failPut) throw ApiFailure(AppError.Network)
            failCode?.let { throw ApiFailure(AppError.InvalidReference, it) }
            if (!File(java.net.URI(uri)).exists()) throw java.io.FileNotFoundException(uri)
            val row = rows.getOrPut(id) {
                owners[id] = owner
                JSONObject().put("id", id).put("title", title).put("durationMs", durationMs)
                    .put("createdAt", "2026-10-16T09:00:00Z").put("contentType", "audio/wav").put("sizeBytes", 24)
                    .put(
                        "audioUrl",
                        (if (badAudioOrigin) "https://evil.test" else baseUrl) + "/v1/recordings/" + id + "/audio",
                    )
            }
            if (loseFirstResponse) {
                loseFirstResponse = false
                throw ApiFailure(AppError.Timeout)
            }
            return JSONObject(row.toString())
        }
        override suspend fun request(
            method: String,
            path: String,
            token: String,
            body: JSONObject?,
            key: String?,
        ): JSONObject = when {
            path == "/v1/me" -> JSONObject().put("id", owner).put("displayName", "Recorder")

            path == "/v1/me/quota" -> JSONObject().put("limit", 25).put("remaining", 25)

            path == "/v1/likes" -> JSONObject().put("trackIds", JSONArray())

            path.startsWith("/v1/tracks?") -> JSONObject().put("tracks", JSONArray())

            path == "/v1/playlists" -> JSONObject().put(
                "playlists",
                JSONArray().apply {
                    if (playlistCreated) {
                        put(
                            JSONObject().put("id", "playlist").put("name", "Ideas").put("trackIds", JSONArray()),
                        )
                    }
                },
            )

            path == "/v1/recordings" -> JSONObject().put(
                "recordings",
                JSONArray(
                    rows.filterKeys {
                        owners[it] == owner
                    }.values.toList(),
                ),
            )

            path.startsWith(
                "/v1/recordings/",
            ) && method == "PATCH" -> rows.getValue(
                path.substringAfterLast('/'),
            ).put("title", body!!.getString("title"))

            path.startsWith("/v1/recordings/") && method == "DELETE" -> {
                rows.remove(path.substringAfterLast('/'))
                JSONObject()
            }

            method == "POST" -> {
                generationPosts++
                error("Recording drafts must never submit a generation")
            }

            else -> error("Unexpected request " + method + " " + path)
        }
    }
}
