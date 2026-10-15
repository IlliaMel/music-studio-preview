package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.data.playback.audioCacheKey
import com.illiamelnyk.studio.core.data.playback.playbackPreferenceKey
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
class DurableStudioRepositoryTest {
    private lateinit var context: Context

    @Before
    fun cleanStore() {
        context = RuntimeEnvironment.getApplication()
        context.filesDir.listFiles()?.filter { it.name.startsWith("studio-state-v1.json") }?.forEach { it.delete() }
    }

    @Test
    fun twoVariantsSpendOneCreditAndSurviveRestart() = runTest {
        val first = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        first.signInDemo()
        val result = first.create(
            GenerationRequest("Warm cinematic piano with strings", customLyrics = "First line\nSecond line"),
            2,
        )
        assertTrue(result is Outcome.Success)
        val restarted = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        assertEquals(SessionDataMode.LocalDemo, restarted.session.first()?.dataMode)
        assertEquals(2, restarted.observeMine().first().size)
        assertEquals(24, restarted.credits.first().remaining)
        assertEquals("First line\nSecond line", restarted.observeMine().first().first().customLyrics)
        assertEquals(first.observeMine().first().map { it.id }, restarted.observeMine().first().map { it.id })
    }

    @Test
    fun signOutHidesPrivateStateAndSignInRestoresLikesAndPlaylists() = runTest {
        val repo = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        repo.signInDemo()
        repo.setLiked(FakeCatalog.glassHarbor.id, true)
        val playlist = (repo.create("Evening") as Outcome.Success).value
        repo.addTrack(playlist.id, FakeCatalog.glassHarbor)
        repo.signOut()
        assertNull(repo.session.first())
        assertTrue(repo.observeLiked().first().isEmpty())
        assertTrue(repo.observeAll().first().isEmpty())
        repo.signInDemo()
        assertEquals(1, repo.observeLiked().first().size)
        assertEquals(FakeCatalog.glassHarbor.id, repo.observeAll().first().single().tracks.single().id)
    }

    @Test
    fun deletingAccountErasesGenerationsLikesPlaylistsAndQuota() = runTest {
        val repo = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        repo.signInDemo()
        val ownedDirectory = repo.referenceDirectory()
        val reference = java.io.File(ownedDirectory, "source.wav").apply { writeBytes(ByteArray(64)) }
        val otherDirectory = RecordingFiles(context).referenceDirectory("other-test-account")
        val otherReference = java.io.File(otherDirectory, "source.wav").apply { writeBytes(ByteArray(64)) }
        repo.create(GenerationRequest("Ambient rain with soft piano"), 1)
        repo.create("Test playlist")
        repo.setLiked(FakeCatalog.glassHarbor.id, true)
        assertTrue(repo.deleteAccount() is Outcome.Success)
        assertFalse(reference.exists())
        assertFalse(ownedDirectory.exists())
        assertTrue(otherReference.exists())
        otherReference.delete()
        otherDirectory.delete()
        val restarted = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        assertNull(restarted.session.first())
        restarted.signInDemo()
        assertTrue(restarted.observeMine().first().isEmpty())
        assertTrue(restarted.observeLiked().first().isEmpty())
        assertTrue(restarted.observeAll().first().isEmpty())
        assertEquals(25, restarted.credits.first().remaining)
        assertFalse(StudioStore(context).read().keys().asSequence().any { it.startsWith("request-") })
    }

    @Test
    fun invalidRequestSpendsNoCreditAndCreatesNoRows() = runTest {
        val repo = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        repo.signInDemo()
        assertTrue(repo.create(GenerationRequest("short"), 2) is Outcome.Failure)
        assertTrue(repo.observeMine().first().isEmpty())
        assertEquals(25, repo.credits.first().remaining)
    }

    @Test
    fun recordingSurvivesRestartWithoutCreditAndDeletionRemovesPrivateAudio() = runTest {
        val repo = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        repo.signInDemo()
        val source = java.io.File(context.cacheDir, "recording-input.m4a").apply { writeBytes(ByteArray(64) { 1 }) }
        val result = repo.saveRecording(
            android.net.Uri.fromFile(source).toString(),
            "My recording",
            kotlin.time.Duration.parse("10s"),
        )
        assertTrue(result is Outcome.Success)
        val saved = (result as Outcome.Success).value
        val localFile = java.io.File(android.net.Uri.parse(saved.audioUri).path!!)
        assertTrue(localFile.exists())
        assertEquals(25, repo.credits.first().remaining)
        val restarted = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        assertEquals(saved.audioUri, restarted.observeMine().first().single().audioUri)
        val exported = java.io.File(context.cacheDir, "audio/" + audioCacheKey(saved.id) + ".mp3")
        exported.parentFile!!.mkdirs()
        exported.writeBytes(ByteArray(64))
        restarted.deleteAccount()
        assertFalse(localFile.exists())
        assertFalse(exported.exists())
        source.delete()
    }

    @Test
    fun repeatedRetryTapsShareOneReplacementAndOneCredit() = runTest {
        val store = StudioStore(context)
        val original = DurableStudioRepository(context, store, StudioApi(context), backgroundScope)
        original.signInDemo()
        original.create(GenerationRequest("Ambient music with warm piano"), 1)
        store.change { root ->
            val account = root.getJSONObject("account-local-demo")
            account.getJSONArray("tracks").getJSONObject(0).put("status", "failed")
        }
        val restarted = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        val id = restarted.observeMine().first().single().id
        val results = listOf(async { restarted.retry(id) }, async { restarted.retry(id) }).awaitAll()
        assertTrue(results.all { it is Outcome.Success })
        assertEquals((results[0] as Outcome.Success).value.id, (results[1] as Outcome.Success).value.id)
        assertEquals(23, restarted.credits.first().remaining)
        assertEquals(2, restarted.observeMine().first().size)
    }

    @Test
    fun serverLikesPlaylistsAndWordTimingsSynchronizeAcrossRepositoryRestart() = runTest {
        val api = MemoryApi(context)
        val repo = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        assertTrue(repo.signInServer() is Outcome.Success)
        assertEquals(SessionDataMode.DevelopmentServer, repo.session.first()?.dataMode)
        val track = repo.observeMine().first().single()
        assertEquals(4000L, track.words.single().startMs)
        assertEquals(4600L, track.words.single().endMs)
        assertTrue(repo.rename(track.id, "Renamed on server") is Outcome.Success)
        assertEquals("Renamed on server", api.renamedTitle)
        assertTrue(repo.setLiked(track.id, true) is Outcome.Success)
        val playlist = (repo.create("Server playlist") as Outcome.Success).value
        assertTrue(repo.addTrack(playlist.id, track) is Outcome.Success)
        val restarted = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        assertEquals(SessionDataMode.DevelopmentServer, restarted.session.first()?.dataMode)
        assertTrue(restarted.signInServer() is Outcome.Success)
        assertEquals(track.id, restarted.observeLiked().first().single().id)
        assertEquals(track.id, restarted.observeAll().first().single().tracks.single().id)
        assertEquals(4000L, restarted.observeMine().first().single().words.single().startMs)
        assertEquals(1, api.detailRequests)
    }

    @Test
    fun localRemixCannotUseAnotherRemixAndDoesNotSpendCredit() = runTest {
        val store = StudioStore(context)
        val repo = DurableStudioRepository(context, store, StudioApi(context), backgroundScope)
        repo.signInDemo()
        val request = GenerationRequest(
            "Cinematic remix with warm strings",
            remixTrackId = FakeCatalog.glassHarbor.id.value,
        )
        assertTrue(repo.create(request, 1) is Outcome.Success)
        val remix = repo.observeMine().first().single()
        assertEquals(FakeCatalog.glassHarbor.id.value, remix.remixTrackId)
        store.change { root ->
            root.getJSONObject("account-local-demo").getJSONArray("tracks").getJSONObject(0).put("status", "done")
        }
        val restarted = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        val result = restarted.create(request.copy(remixTrackId = remix.id.value), 1)
        assertTrue(result is Outcome.Failure)
        assertEquals(24, restarted.credits.first().remaining)
        assertEquals(1, restarted.observeMine().first().size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun retryCannotReadAnOldAccountWhileAnotherOperationSwitchesTheSession() = runTest {
        val api = MemoryApi(context)
        api.failures = listOf(api.failure("failed-reference", "deep", reference = "upload-original"))
        val repo = DurableStudioRepository(
            context,
            StudioStore(context),
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        repo.signInServer()
        api.holdProfile = true
        val editing = async {
            repo.updateProfile(com.illiamelnyk.studio.core.domain.session.ProfileUpdate(marketingOptIn = true))
        }
        runCurrent()
        api.profileStarted.await()
        val switching = async { repo.signInDemo() }
        runCurrent()
        val retrying = async { repo.retry(com.illiamelnyk.studio.core.domain.track.TrackId("failed-reference")) }
        runCurrent()
        api.profileRelease.complete(Unit)
        editing.await()
        assertTrue(switching.await() is Outcome.Success)
        assertTrue(retrying.await() is Outcome.Failure)
        assertTrue(repo.observeMine().first().isEmpty())
        assertEquals("local-demo", repo.session.first()?.user?.id?.value)
        assertEquals(25, repo.credits.first().remaining)
    }

    @Test
    fun serverLoadedRetriesKeepReferenceRemixAndQualityWithoutALocalRequestCache() = runTest {
        val api = MemoryApi(context)
        api.failures = listOf(
            api.failure("failed-reference", "deep", reference = "upload-original"),
            api.failure("failed-remix", "fast", remix = FakeCatalog.glassHarbor.id.value),
        )
        val repo = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repo.signInServer()
        val reference = com.illiamelnyk.studio.core.domain.track.TrackId("failed-reference")
        assertTrue(repo.retry(reference) is Outcome.Success)
        assertEquals("upload-original", api.lastGeneration?.getString("referenceUploadId"))
        assertEquals("deep", api.lastGeneration?.getString("quality"))
        val remix = com.illiamelnyk.studio.core.domain.track.TrackId("failed-remix")
        assertTrue(repo.retry(remix) is Outcome.Success)
        assertEquals(FakeCatalog.glassHarbor.id.value, api.lastGeneration?.getString("remixTrackId"))
        assertEquals("fast", api.lastGeneration?.getString("quality"))
    }

    @Test
    fun confirmedServerRenameDoesNotMaskLaterRemoteChanges() = runTest {
        val api = MemoryApi(context)
        val repo = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repo.signInServer()
        val track = repo.observeMine().first().single()
        repo.rename(track.id, "First client title")
        api.renamedTitle = "Other client title"
        repo.signInServer()
        assertEquals("Other client title", repo.observeMine().first().single().title)
    }

    @Test
    fun confirmedRemoteDeletionRetriesFailedLocalCleanupWithoutAuthenticatingAgain() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repo = DurableStudioRepository(context, store, api, backgroundScope)
        repo.signInServer()
        val track = repo.observeMine().first().single()
        val blocker = java.io.File(context.cacheDir, "audio/" + audioCacheKey(track.id) + ".mp3")
        blocker.mkdirs()
        val child = java.io.File(blocker, "busy").apply { writeText("fixture") }

        assertTrue(repo.deleteAccount() is Outcome.Failure)
        assertTrue(api.deleted)
        assertEquals(1, api.deleteRequests)
        assertTrue(store.read().getJSONObject("deletion-server-user").getBoolean("remoteConfirmed"))
        child.delete()
        blocker.delete()

        assertTrue(repo.deleteAccount() is Outcome.Success)
        assertEquals(1, api.deleteRequests)
        assertNull(repo.session.first())
        assertFalse(store.read().has("deletion-server-user"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun restartFinishesConfirmedLocalCleanupWithoutTheDeletedPrincipal() = runTest {
        val api = MemoryApi(context)
        val repo = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repo.signInServer()
        val track = repo.observeMine().first().single()
        val blocker = java.io.File(context.cacheDir, "audio/" + audioCacheKey(track.id) + ".mp3")
        blocker.mkdirs()
        val child = java.io.File(blocker, "busy").apply { writeText("fixture") }
        assertTrue(repo.deleteAccount() is Outcome.Failure)
        child.delete()
        blocker.delete()

        val restartedStore = StudioStore(context)
        val restarted = DurableStudioRepository(
            context,
            restartedStore,
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        assertNull(restarted.session.first())
        assertNull(restarted.activeAccountId)
        runCurrent()
        assertFalse(restartedStore.read().has("deletion-server-user"))
        assertEquals(1, api.deleteRequests)
    }

    @Test
    fun remoteSuccessBeforePhaseWriteFailurePurgesLocallyButDoesNotCall401Confirmation() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repo = DurableStudioRepository(context, store, api, backgroundScope)
        repo.signInServer()
        api.afterDelete = { java.io.File(context.filesDir, "studio-state-v1.json.new").mkdir() }

        assertTrue(repo.deleteAccount() is Outcome.Failure)
        assertFalse(store.read().getJSONObject("deletion-server-user").getBoolean("remoteConfirmed"))
        assertTrue(repo.deleteAccount() is Outcome.Failure)

        assertNull(repo.session.first())
        assertEquals(1, api.deleteRequests)
        val pending = store.read().getJSONObject("deletion-server-user")
        assertTrue(pending.getBoolean("localPurged"))
        assertFalse(pending.getBoolean("remoteConfirmed"))
        assertFalse(store.read().has("account-server-user"))
    }

    @Test
    fun failedRemoteDeletionPurgesAuthorizedLocalDataAndKeepsHonestPendingStatus() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repo = DurableStudioRepository(context, store, api, backgroundScope)
        repo.signInServer()
        val reference = java.io.File(repo.referenceDirectory(), "private.wav").apply { writeBytes(ByteArray(64)) }
        api.failDelete = true

        assertTrue(repo.deleteAccount() is Outcome.Failure)

        assertNull(repo.session.first())
        assertFalse(reference.exists())
        assertFalse(store.read().has("account-server-user"))
        val pending = store.read().getJSONObject("deletion-server-user")
        assertTrue(pending.getBoolean("localPurged"))
        assertFalse(pending.getBoolean("remoteConfirmed"))
        assertEquals(0, pending.getJSONArray("audio").length())
    }

    @Test
    fun deletionRecoveryNeverDeletesANewAccountResolvedFromTheSameDevToken() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repo = DurableStudioRepository(context, store, api, backgroundScope)
        repo.signInServer()
        api.failDelete = true
        assertTrue(repo.deleteAccount() is Outcome.Failure)
        api.failDelete = false
        api.profileOwnerId = "new-server-user"

        assertTrue(repo.deleteAccount() is Outcome.Failure)

        assertEquals(1, api.deleteRequests)
        assertFalse(api.deleted)
        assertFalse(store.read().getJSONObject("deletion-server-user").getBoolean("remoteConfirmed"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pollingCannotRehydrateAnAccountAwaitingPrivateFileCleanup() = runTest {
        val api = MemoryApi(context)
        api.failures = listOf(api.failure("pending-track", "auto").put("status", "queued"))
        val store = StudioStore(context)
        val repo = DurableStudioRepository(
            context,
            store,
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        repo.signInServer()
        val track = repo.observeMine().first().first()
        val blocker = java.io.File(context.cacheDir, "audio/" + audioCacheKey(track.id) + ".mp3")
        blocker.mkdirs()
        val child = java.io.File(blocker, "busy").apply { writeText("fixture") }
        try {
            assertTrue(repo.deleteAccount() is Outcome.Failure)
            val requestsBeforePolling = api.libraryRequests
            advanceTimeBy(2_001)
            runCurrent()
            assertEquals(requestsBeforePolling, api.libraryRequests)
            assertTrue(store.read().has("deletion-server-user"))
            assertNull(repo.activeAccountId)
        } finally {
            child.delete()
            blocker.delete()
        }
    }

    @Test
    fun deletingAndRecreatingSameDemoOwnerCannotRestorePrivatePlayback() = runTest {
        val repo = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        repo.signInDemo()
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        val ownerKey = playbackPreferenceKey("local-demo")
        assertTrue(preferences.edit().putString(ownerKey, "private paused queue").putString("state", "legacy").commit())

        assertTrue(repo.deleteAccount() is Outcome.Success)
        assertTrue(repo.signInDemo() is Outcome.Success)

        assertEquals("local-demo", repo.activeAccountId)
        assertFalse(preferences.contains(ownerKey))
        assertFalse(preferences.contains("state"))
        val restarted = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        assertEquals("local-demo", restarted.activeAccountId)
        assertFalse(context.getSharedPreferences("playback", Context.MODE_PRIVATE).contains(ownerKey))
    }

    @Test
    fun malformedCachedJsonNeverAppearsInDiagnosticLogs() {
        val marker = "PRIVATE_CACHE_TEST_MARKER"
        java.io.File(context.filesDir, "studio-state-v1.json").writeText("{\"token\":\"" + marker + "\",\"active\":")
        org.robolectric.shadows.ShadowLog.clear()

        StudioStore(context)

        val logs = org.robolectric.shadows.ShadowLog.getLogsForTag("StudioData")
        assertTrue(logs.isNotEmpty())
        assertFalse(logs.any { it.msg.contains(marker) || it.throwable?.toString()?.contains(marker) == true })
    }

    private class MemoryApi(context: Context) : StudioApi(context) {
        override val isAvailable: Boolean = true
        private val likes = mutableSetOf<String>()
        private val members = mutableSetOf<String>()
        private var playlistCreated = false
        var detailRequests = 0
        var libraryRequests = 0
        var renamedTitle: String? = null
        var failures: List<org.json.JSONObject> = emptyList()
        var lastGeneration: org.json.JSONObject? = null
        var holdProfile = false
        var deleted = false
        var deleteRequests = 0
        var failDelete = false
        var afterDelete: (() -> Unit)? = null
        var profileOwnerId = "server-user"
        val profileStarted = CompletableDeferred<Unit>()
        val profileRelease = CompletableDeferred<Unit>()

        fun failure(
            id: String,
            quality: String,
            reference: String? = null,
            remix: String? = null,
        ): org.json.JSONObject = track.put("id", id).put("status", "failed").put("audioUrl", org.json.JSONObject.NULL)
            .put("quality", quality).put("referenceUploadId", reference).put("remixTrackId", remix)
        private val track: org.json.JSONObject
            get() = org.json.JSONObject()
                .put("id", "server-track").put("prompt", "Server generated piano")
                .put("title", renamedTitle ?: "Server piano").put("status", "done").put("durationMs", 120000)
                .put("modelId", "music_v2_5").put("createdAt", "2026-10-15T09:00:00Z")
                .put("audioUrl", "http://10.0.2.2:8787/v1/tracks/server-track/audio")
        private fun playlist(): org.json.JSONObject = org.json.JSONObject()
            .put("id", "server-playlist").put("name", "Server playlist")
            .put("trackIds", org.json.JSONArray(members.toList()))

        override suspend fun request(
            method: String,
            path: String,
            token: String,
            body: org.json.JSONObject?,
            key: String?,
        ): org.json.JSONObject = when {
            path == "/v1/me" && method == "DELETE" -> {
                deleteRequests++
                if (failDelete) throw ApiFailure(com.illiamelnyk.studio.core.domain.AppError.Network)
                deleted = true
                afterDelete?.invoke()
                org.json.JSONObject()
            }

            path == "/v1/me" && deleted -> throw ApiFailure(com.illiamelnyk.studio.core.domain.AppError.Unauthorized)

            path == "/v1/me" && method == "PATCH" && holdProfile -> {
                profileStarted.complete(Unit)
                profileRelease.await()
                org.json.JSONObject().put("id", profileOwnerId).put("displayName", "Server listener")
            }

            path == "/v1/me" -> org.json.JSONObject()
                .put("id", profileOwnerId).put("displayName", "Server listener")
                .put("termsVersion", "2026-10-14")

            path == "/v1/me/quota" -> org.json.JSONObject().put("limit", 25).put("remaining", 25)

            path.startsWith("/v1/tracks?") -> {
                libraryRequests++
                org.json.JSONObject().put(
                    "tracks",
                    org.json.JSONArray().put(track).apply { failures.forEach { put(it) } },
                )
            }

            path == "/v1/tracks" && method == "POST" -> {
                lastGeneration = body?.let { org.json.JSONObject(it.toString()) }
                track.put("id", "replacement-" + key.hashCode())
                    .put("status", "queued").put("audioUrl", org.json.JSONObject.NULL)
            }

            path == "/v1/tracks/server-track" && method == "PATCH" -> {
                renamedTitle = body?.getString("title")
                track
            }

            path == "/v1/tracks/server-track" -> {
                detailRequests++
                track.put(
                    "words",
                    org.json.JSONArray().put(
                        org.json.JSONObject().put("word", "Hello").put("startMs", 4000).put("endMs", 4600),
                    ),
                )
            }

            path == "/v1/likes" -> org.json.JSONObject().put("trackIds", org.json.JSONArray(likes.toList()))

            path.startsWith("/v1/likes/") -> {
                val id = path.substringAfterLast('/')
                if (method == "PUT") likes.add(id) else likes.remove(id)
                org.json.JSONObject()
            }

            path == "/v1/playlists" && method == "POST" -> {
                playlistCreated = true
                playlist()
            }

            path == "/v1/playlists" -> org.json.JSONObject().put(
                "playlists",
                org.json.JSONArray().apply { if (playlistCreated) put(playlist()) },
            )

            path.startsWith("/v1/playlists/server-playlist/tracks/") -> {
                val id = path.substringAfterLast('/')
                if (method == "PUT") members.add(id) else members.remove(id)
                org.json.JSONObject()
            }

            else -> error("Unexpected request: " + method + " " + path)
        }
    }
}
