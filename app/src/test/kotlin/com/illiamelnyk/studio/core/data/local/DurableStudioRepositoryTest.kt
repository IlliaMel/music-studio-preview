package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.data.playback.audioCacheKey
import com.illiamelnyk.studio.core.data.playback.playbackPreferenceKey
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.notification.GenerationNotifications
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.GenerationWorkScheduler
import com.illiamelnyk.studio.core.domain.track.Track
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
        context.getSharedPreferences("generation-notifications", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        com.illiamelnyk.studio.core.data.local.StudioStore.clearForTests(context)
    }

    @Test
    fun firstSignedInSnapshotUsesServerQuotaAndRestoresSongsEvenIfRecordingsFail() = runTest {
        val api = MemoryApi(context).apply {
            quotaLimit = 5
            quotaRemaining = 4
            failRecordings = true
        }
        val runtime = StudioAccountRuntime(
            context,
            StudioStore(context),
            api,
            StandardTestDispatcher(testScheduler),
            GenerationWorkScheduler.None,
            GenerationNotifications.None,
        )
        api.onQuota = {
            runtime.state.value?.let { assertEquals(5, it.credits.dailyLimit) }
        }
        assertTrue(DurableSessionRepository(runtime).signInServer() is Outcome.Success)
        assertEquals(4, runtime.state.value!!.credits.remaining)
        assertEquals(5, runtime.state.value!!.credits.dailyLimit)
        assertTrue(runtime.state.value!!.tracks.any { it.id.value == "server-track" })
    }

    @Test
    fun foregroundImmediatelyRestoresRemoteLibraryWithoutWaitingForIdlePoll() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val runtime = StudioAccountRuntime(
            context,
            store,
            api,
            StandardTestDispatcher(testScheduler),
            GenerationWorkScheduler.None,
            GenerationNotifications.None,
        )
        DurableSessionRepository(runtime).signInServer()
        val account = runtime.state.value!!
        runtime.operation { runtime.save(account.copy(tracks = emptyList())) }
        val readsBefore = api.libraryRequests
        val startedAt = testScheduler.currentTime
        var firstReadAt: Long? = null
        api.onLibrary = { if (firstReadAt == null) firstReadAt = testScheduler.currentTime }
        runtime.sync.requestInitialRefresh(account.session.user.id.value)
        runtime.sync.start(backgroundScope)
        runCurrent()
        awaitDatabaseWork { api.libraryRequests > readsBefore }
        runtime.operation { Unit }
        assertTrue(runtime.state.value!!.tracks.any { it.id.value == "server-track" })
        assertTrue(requireNotNull(firstReadAt) - startedAt < 2_000)
    }

    @Test
    fun cancellationAfterRecordingCommitPreservesThePersistedAudioFile() = runTest {
        val store = StudioStore(context)
        var savingJob: kotlinx.coroutines.Job? = null
        val notifications = object : GenerationNotifications {
            override fun record(ownerId: String?, tracks: List<Track>) {
                if (tracks.any { it.id.isRecording }) savingJob?.cancel()
            }
            override suspend fun deliver(ownerId: String?) = Unit
            override fun forget(ownerId: String) = Unit
        }
        val repository = DurableStudioRepository(
            context,
            store,
            StudioApi(context),
            backgroundScope,
            notifications = notifications,
        )
        repository.signInDemo()
        val source = java.io.File(context.filesDir, "postcommit-cancellation.wav").apply { writeBytes(ByteArray(64)) }
        try {
            val task = async {
                savingJob = coroutineContext[kotlinx.coroutines.Job]
                repository.saveRecording(
                    source.toURI().toString(),
                    "Preserved recording",
                    kotlin.time.Duration.parse("2s"),
                )
            }
            try {
                task.await()
            } catch (_: kotlinx.coroutines.CancellationException) { }
            assertTrue(task.isCancelled)
            val committed = store.readAccount("local-demo")!!.tracks.single { it.id.isRecording }
            assertTrue(java.io.File(java.net.URI(committed.audioUri!!)).isFile)
            assertEquals("Preserved recording", committed.title)
        } finally {
            source.delete()
        }
    }

    @Test
    fun focusedRepositoriesShareOneOwnerAndAtomicLibraryState() = runTest {
        val repo = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        assertTrue(repo.signInDemo() is Outcome.Success)
        val created = repo.tracks.create(GenerationRequest("Warm piano with spacious strings"), 1) as Outcome.Success
        assertEquals(24, repo.creditBalance.credits.first().remaining)
        val collection = repo.playlists.create("Saved") as Outcome.Success
        assertTrue(repo.playlists.addTrack(collection.value.id, DemoCatalog.glassHarbor) is Outcome.Success)
        assertEquals(1, repo.playlists.observeAll().first().single().tracks.size)
        val owner = repo.sessions.session.first()!!.user.id
        assertTrue(repo.sessions.signOutIfCurrent(owner))
        assertTrue(repo.tracks.observeMine().first().isEmpty())
        assertTrue(repo.playlists.observeAll().first().isEmpty())
        assertEquals(null, repo.synchronization.activeAccountId)
        repo.signInDemo()
        assertEquals(created.value.single().id, repo.tracks.observeMine().first().single().id)
        assertEquals(1, repo.playlists.observeAll().first().single().tracks.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun foregroundPollingStopsWhileUiIsHiddenAndResumesPendingDemoWork() = runTest {
        val repo = DurableStudioRepository(
            context,
            StudioStore(context),
            StudioApi(context),
            backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = object : kotlin.time.Clock {
                override fun now() = kotlin.time.Instant.fromEpochMilliseconds(testScheduler.currentTime)
            },
        )
        repo.synchronization.setForeground(false)
        repo.signInDemo()
        repo.tracks.create(GenerationRequest("Warm piano with spacious strings"), 1)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(
            com.illiamelnyk.studio.core.domain.track.TrackStatus.Queued,
            repo.tracks.observeMine().first().single().status,
        )
        repo.synchronization.setForeground(true)
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        awaitDatabaseWork {
            repo.tracks.observeMine().first().single().status ==
                com.illiamelnyk.studio.core.domain.track.TrackStatus.Ready
        }
        assertEquals(
            com.illiamelnyk.studio.core.domain.track.TrackStatus.Ready,
            repo.tracks.observeMine().first().single().status,
        )
    }

    @Test
    fun referenceDirectoryDoesNotWaitForDatabaseAndRejectsSignedOutOwner() = runTest {
        val store = StudioStore(context)
        val repo = DurableStudioRepository(context, store, StudioApi(context), backgroundScope)
        repo.signInDemo()
        val expected = repo.referenceDirectory()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val heldDatabase = executor.submit {
            store.withDatabase {
                entered.countDown()
                check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val callback = java.util.concurrent.CompletableFuture.supplyAsync { repo.referenceDirectory() }
            assertEquals(expected, callback.get(2, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            release.countDown()
            heldDatabase.get(5, java.util.concurrent.TimeUnit.SECONDS)
            executor.shutdownNow()
        }
        repo.sessions.signOut()
        assertTrue(runCatching { repo.referenceDirectory() }.isFailure)
        repo.signInDemo()
        assertEquals(expected, repo.referenceDirectory())
        assertTrue(repo.referenceDirectory().isDirectory)
    }

    @Test
    fun accountDeletionClosesReferenceAccessBeforeRemoteDeletionCompletes() = runTest {
        val api = MemoryApi(context)
        val repo = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repo.signInServer()
        val original = repo.referenceDirectory()
        api.holdDelete = true
        val deleting = async { repo.sessions.deleteAccount() }
        api.deleteStarted.await()
        assertTrue(runCatching { repo.referenceDirectory() }.isFailure)
        api.deleteRelease.complete(Unit)
        assertTrue(deleting.await() is Outcome.Success)
        assertFalse(original.exists())
        assertTrue(runCatching { repo.referenceDirectory() }.isFailure)
        repo.signInDemo()
        assertTrue(repo.referenceDirectory().isDirectory)
        assertFalse(repo.referenceDirectory() == original)
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
    fun guardedSignOutRejectsAnotherOwnerAndPersistsMatchingLogout() = runTest {
        val repository = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        repository.signInDemo()
        val owner = repository.session.first()!!.user.id
        assertFalse(repository.signOutIfCurrent(com.illiamelnyk.studio.core.domain.session.UserId("stale-owner")))
        assertEquals(owner, repository.session.first()!!.user.id)
        assertEquals(owner.value, StudioStore(context).read().getString("active"))
        assertTrue(repository.signOutIfCurrent(owner))
        assertNull(repository.session.first())
        assertFalse(StudioStore(context).read().has("active"))
    }

    @Test
    fun signOutHidesPrivateStateAndSignInRestoresLikesAndPlaylists() = runTest {
        val repo = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        repo.signInDemo()
        repo.setLiked(DemoCatalog.glassHarbor.id, true)
        val playlist = (repo.create("Evening") as Outcome.Success).value
        repo.addTrack(playlist.id, DemoCatalog.glassHarbor)
        repo.signOut()
        assertNull(repo.session.first())
        assertTrue(repo.observeLiked().first().isEmpty())
        assertTrue(repo.observeAll().first().isEmpty())
        repo.signInDemo()
        assertEquals(1, repo.observeLiked().first().size)
        assertEquals(DemoCatalog.glassHarbor.id, repo.observeAll().first().single().tracks.single().id)
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
        repo.setLiked(DemoCatalog.glassHarbor.id, true)
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
            remixTrackIds = listOf(DemoCatalog.glassHarbor.id.value),
        )
        assertTrue(repo.create(request, 1) is Outcome.Success)
        val remix = repo.observeMine().first().single()
        assertEquals(DemoCatalog.glassHarbor.id.value, remix.remixTrackIds.firstOrNull())
        store.change { root ->
            root.getJSONObject("account-local-demo").getJSONArray("tracks").getJSONObject(0).put("status", "done")
        }
        val restarted = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        val result = restarted.create(request.copy(remixTrackIds = listOf(remix.id.value)), 1)
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
            api.failure("failed-remix", "fast", remix = DemoCatalog.glassHarbor.id.value),
        )
        val repo = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repo.signInServer()
        val reference = com.illiamelnyk.studio.core.domain.track.TrackId("failed-reference")
        assertTrue(repo.retry(reference) is Outcome.Success)
        assertEquals("upload-original", api.lastGeneration?.getString("referenceUploadId"))
        assertEquals("deep", api.lastGeneration?.getString("quality"))
        val remix = com.illiamelnyk.studio.core.domain.track.TrackId("failed-remix")
        assertTrue(repo.retry(remix) is Outcome.Success)
        assertEquals(
            listOf(DemoCatalog.glassHarbor.id.value),
            api.lastGeneration?.getJSONArray("remixTrackIds")?.strings(),
        )
        assertFalse(requireNotNull(api.lastGeneration).has("remixTrackId"))
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
        awaitDatabaseWork { !restartedStore.read().has("deletion-server-user") }
        assertFalse(restartedStore.read().has("deletion-server-user"))
        assertEquals(1, api.deleteRequests)
    }

    @Test
    fun remoteSuccessBeforePhaseWriteFailurePurgesLocallyButDoesNotCall401Confirmation() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repo = DurableStudioRepository(context, store, api, backgroundScope)
        repo.signInServer()
        api.afterDelete = { store.failConfirmedDeletionWrites() }

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
    fun backgroundRecoveryRetriesDeletionAfterLocalOwnerHasBeenPurged() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val work = RecordingGenerationWork()
        val repo = DurableStudioRepository(
            context,
            store,
            api,
            backgroundScope,
            generationWork = work,
        )
        repo.signInServer()
        api.failDelete = true
        assertTrue(repo.sessions.deleteAccount() is Outcome.Failure)
        assertNull(repo.synchronization.activeAccountId)
        assertEquals("account-deletion", work.owner)
        assertEquals(Outcome.Success(true), repo.synchronization.recoverPendingDeletions())
        api.failDelete = false
        assertEquals(Outcome.Success(false), repo.synchronization.recoverPendingDeletions())
        assertFalse(store.read().has("deletion-server-user"))
        assertNull(work.owner)
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
        val work = RecordingGenerationWork()
        val repo = DurableStudioRepository(
            context,
            store,
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            generationWork = work,
        )
        repo.signInServer()
        assertEquals("server-user", work.owner)
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
            assertEquals("account-deletion", work.owner)
            assertEquals(Outcome.Success(false), repo.syncPending("server-user"))
        } finally {
            child.delete()
            blocker.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pollingRoundThatSeesAGenerationFinishCompletesItsFollowUp() = runTest {
        val api = MemoryApi(context)
        api.failures = listOf(api.failure("pending-track", "auto").put("status", "queued"))
        val quotaTimes = java.util.concurrent.CopyOnWriteArrayList<Long>()
        api.onQuota = { quotaTimes += testScheduler.currentTime }
        val repo = DurableStudioRepository(
            context,
            StudioStore(context),
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        repo.signInServer()
        runCurrent()
        // The server finishes the track before the next 2-second poll.
        api.failures = listOf(api.failure("pending-track", "auto"))
        quotaTimes.clear()
        val roundStart = testScheduler.currentTime
        advanceTimeBy(2_001)
        runCurrent()
        awaitDatabaseWork { quotaTimes.isNotEmpty() }
        // Cancelled half-way, the quota would only be refreshed by the next idle poll, 30 s later.
        assertTrue(quotaTimes.first() - roundStart < 10_000)
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
        val original = "{\"token\":\"" + marker + "\",\"active\":"
        val source = java.io.File(context.filesDir, "studio-state-v1.json").apply { writeText(original) }
        org.robolectric.shadows.ShadowLog.clear()

        try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { StudioStore(context).initialize() }
            org.junit.Assert.fail("Malformed journal must fail closed and retain its source")
        } catch (failure: LegacyStudioStateCorruptedException) {
            assertFalse(failure.message.orEmpty().contains(marker))
        }
        assertEquals(original, source.readText())

        val logs = org.robolectric.shadows.ShadowLog.getLogsForTag("StudioData")
        assertTrue(logs.isEmpty())
        assertFalse(
            logs.any {
                it.msg.contains(marker) || it.throwable?.stackTraceToString()?.contains(marker) == true
            },
        )
    }

    @Test
    fun acceptedGenerationSchedulesOwnerWorkAndCompletionNeverResubmitsThePaidRequest() = runTest {
        val api = MemoryApi(context)
        val work = RecordingGenerationWork()
        val repo = DurableStudioRepository(context, StudioStore(context), api, backgroundScope, generationWork = work)
        repo.signInServer()
        assertTrue(repo.create(GenerationRequest("Warm cinematic piano"), 1) is Outcome.Success)
        assertEquals("server-user", work.owner)
        assertEquals(1, api.generationRequests)

        assertEquals(Outcome.Success(false), repo.syncPending("server-user"))

        assertNull(work.owner)
        assertEquals(1, api.generationRequests)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun unauthorizedWorkerBlocksOnlyItsOwnerAcrossRestartAndSuccessfulAuthenticationResumesWork() = runTest {
        val api = MemoryApi(context)
        val work = RecordingGenerationWork()
        val repo = DurableStudioRepository(
            context,
            StudioStore(context),
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            generationWork = work,
        )
        repo.signInServer()
        repo.tracks.create(GenerationRequest("Warm cinematic piano"), 1)
        api.unauthorizedLibrary = true
        fun worker(sync: StudioSynchronization) =
            com.illiamelnyk.studio.core.data.generation.generationWorker(context, sync)
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker(repo.synchronization).doWork())
        assertTrue(
            StudioStore(context).read().getJSONObject("account-server-user").getBoolean("requiresAuthentication"),
        )
        assertNull(work.owner)
        val blockedReads = api.libraryRequests
        repeat(3) {
            assertEquals(androidx.work.ListenableWorker.Result.success(), worker(repo.synchronization).doWork())
        }
        assertEquals(blockedReads, api.libraryRequests)
        val previousRuntimeJobs = backgroundScope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList()
        previousRuntimeJobs.forEach { it.cancel() }
        previousRuntimeJobs.forEach { it.join() }
        val restarted = DurableStudioRepository(
            context,
            StudioStore(context),
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            generationWork = work,
        )
        runCurrent()
        advanceTimeBy(60_001)
        runCurrent()
        assertEquals(blockedReads, api.libraryRequests)
        assertNull(work.owner)
        assertEquals("server-user", restarted.synchronization.activeAccountId)
        restarted.signInDemo()
        assertTrue(restarted.tracks.create(GenerationRequest("Local piano remains available"), 1) is Outcome.Success)
        api.unauthorizedLibrary = false
        assertTrue(restarted.signInServer() is Outcome.Success)
        assertFalse(
            StudioStore(context).read().getJSONObject("account-server-user").getBoolean("requiresAuthentication"),
        )
        assertEquals(1, api.generationRequests)
        assertTrue(restarted.tracks.create(GenerationRequest("New authenticated piano request"), 1) is Outcome.Success)
        assertEquals("server-user", work.owner)
        api.unauthorizedLibrary = true
        worker(restarted.synchronization).doWork()
        assertNull(work.owner)
        api.failDelete = true
        assertTrue(restarted.sessions.deleteAccount() is Outcome.Failure)
        assertEquals("account-deletion", work.owner)
        assertEquals(Outcome.Success(true), restarted.synchronization.recoverPendingDeletions())
        api.failDelete = false
        assertEquals(Outcome.Success(false), restarted.synchronization.recoverPendingDeletions())
    }

    @Test
    fun backgroundSyncRejectsStaleOwnersAndRetainsPendingWorkAfterNetworkFailure() = runTest {
        val api = MemoryApi(context)
        val work = RecordingGenerationWork()
        val repo = DurableStudioRepository(context, StudioStore(context), api, backgroundScope, generationWork = work)
        repo.signInServer()
        repo.create(GenerationRequest("Warm cinematic piano"), 1)
        val reads = api.libraryRequests
        assertEquals(Outcome.Success(false), repo.syncPending("other-owner"))
        assertEquals(reads, api.libraryRequests)
        assertEquals("server-user", work.owner)
        api.failLibrary = true

        assertEquals(
            Outcome.Failure(com.illiamelnyk.studio.core.domain.AppError.Network),
            repo.syncPending("server-user"),
        )
        assertEquals("server-user", work.owner)
        assertEquals(1, api.generationRequests)
        api.failLibrary = false
        assertEquals(Outcome.Success(false), repo.syncPending("server-user"))
        assertNull(work.owner)
    }

    @Test
    fun acceptedGenerationRemainsSuccessfulWhenTheOsSchedulerRejectsWork() = runTest {
        val api = MemoryApi(context)
        val work = object : GenerationWorkScheduler {
            override fun reconcile(ownerId: String?) {
                if (ownerId != null) throw java.io.IOException("simulated scheduler rejection")
            }
        }
        val repo = DurableStudioRepository(context, StudioStore(context), api, backgroundScope, generationWork = work)
        repo.signInServer()

        assertTrue(repo.create(GenerationRequest("Warm cinematic piano"), 1) is Outcome.Success)
        assertEquals(1, api.generationRequests)
        val queued = repo.observeMine().first().any {
            it.status == com.illiamelnyk.studio.core.domain.track.TrackStatus.Queued
        }
        assertTrue(queued)
    }

    @Test
    fun signOutCancelsPendingWorkAndAStaleJobCannotReviveTheSession() = runTest {
        val api = MemoryApi(context)
        val work = RecordingGenerationWork()
        val repo = DurableStudioRepository(context, StudioStore(context), api, backgroundScope, generationWork = work)
        repo.signInServer()
        repo.create(GenerationRequest("Warm cinematic piano"), 1)
        repo.signOut()
        val reads = api.libraryRequests

        assertNull(work.owner)
        assertEquals(Outcome.Success(false), repo.syncPending("server-user"))
        assertEquals(reads, api.libraryRequests)
        assertNull(repo.session.first())
    }

    @Test
    fun localDemoGenerationDoesNotScheduleServerWork() = runTest {
        val work = RecordingGenerationWork()
        val repo = DurableStudioRepository(
            context,
            StudioStore(context),
            StudioApi(context),
            backgroundScope,
            generationWork = work,
        )
        repo.signInDemo()
        repo.create(GenerationRequest("Warm cinematic piano"), 1)
        assertNull(work.owner)
        assertEquals(Outcome.Success(false), repo.syncPending("local-demo"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun restartingWithPersistedPendingGenerationSchedulesCatchUpWithoutCreatingAnotherTrack() = runTest {
        val api = MemoryApi(context)
        val repo = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repo.signInServer()
        repo.create(GenerationRequest("Warm cinematic piano"), 1)
        val work = RecordingGenerationWork()
        val restarted = DurableStudioRepository(
            context,
            StudioStore(context),
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            work,
        )
        runCurrent()
        awaitDatabaseWork { work.owner == "server-user" }

        assertEquals("server-user", work.owner)
        assertEquals("server-user", restarted.activeAccountId)
        assertEquals(1, api.generationRequests)
    }

    @Test
    fun notificationIoFailuresCannotTurnAnAcceptedGenerationIntoAFailedSend() = runTest {
        val api = MemoryApi(context)
        val notifier = object : GenerationNotifications {
            override fun record(ownerId: String?, tracks: List<Track>): Unit =
                throw java.io.IOException("simulated notification ledger failure")
            override suspend fun deliver(ownerId: String?): Unit =
                throw java.io.IOException("simulated preference read failure")
            override fun forget(ownerId: String) = Unit
        }
        val repo = DurableStudioRepository(
            context,
            StudioStore(context),
            api,
            backgroundScope,
            notifications = notifier,
        )
        repo.signInServer()
        assertTrue(repo.create(GenerationRequest("Warm cinematic piano"), 1) is Outcome.Success)
        assertEquals(1, api.generationRequests)
        val queued = repo.observeMine().first().any {
            it.status == com.illiamelnyk.studio.core.domain.track.TrackStatus.Queued
        }
        assertTrue(queued)
    }

    @Test
    fun accountDeletionRetriesNotificationLedgerCleanupBeforeDiscardingOwnerMetadata() = runTest {
        var fail = true
        var forgotten: String? = null
        val notifier = object : GenerationNotifications {
            override fun record(ownerId: String?, tracks: List<Track>) = Unit
            override suspend fun deliver(ownerId: String?) = Unit
            override fun forget(ownerId: String) {
                if (fail) throw java.io.IOException("simulated notification cleanup failure")
                forgotten = ownerId
            }
        }
        val store = StudioStore(context)
        val repo = DurableStudioRepository(
            context,
            store,
            StudioApi(context),
            backgroundScope,
            notifications = notifier,
        )
        repo.signInDemo()
        assertTrue(repo.deleteAccount() is Outcome.Failure)
        assertTrue(store.read().has("deletion-local-demo"))
        assertTrue(store.read().has("account-local-demo"))
        fail = false
        assertTrue(repo.deleteAccount() is Outcome.Success)
        assertEquals("local-demo", forgotten)
        assertNull(repo.session.first())
        assertFalse(store.read().has("deletion-local-demo"))
        assertFalse(store.read().has("notification-observed-local-demo"))
        assertFalse(store.read().has("notification-dirty-local-demo"))
    }

    @Test
    fun signingOutReconcilesNotificationOwnershipBeforeTheSessionCanBeReused() = runTest {
        var owner: String? = null
        val notifier = object : GenerationNotifications {
            override fun record(ownerId: String?, tracks: List<Track>) {
                owner = ownerId
            }
            override suspend fun deliver(ownerId: String?) = Unit
            override fun forget(ownerId: String) = Unit
        }
        val repo = DurableStudioRepository(
            context,
            StudioStore(context),
            StudioApi(context),
            backgroundScope,
            notifications = notifier,
        )
        repo.signInDemo()
        assertEquals("local-demo", owner)
        repo.signOut()
        assertNull(owner)
        assertNull(repo.session.first())
    }

    @Test
    fun restoredFirebasePlaybackRefreshesBearerWithoutPersistingItOrSendingItToAnotherOrigin() = runTest {
        var refreshes = 0
        val configuration = ApiConfiguration("https://api.studio.test", ApiAuthMode.Firebase, false)
        val api = MemoryApi(
            context,
            configuration,
            BearerTokenProvider { uid ->
                refreshes++
                val value = "fixture.payload.signature-" + refreshes
                FirebaseBearerToken(uid, value, System.currentTimeMillis() + 3_600_000)
            },
        )
        val store = StudioStore(context)
        val repo = DurableStudioRepository(context, store, api, backgroundScope)
        assertTrue(repo.signInFirebase("firebase-user") is Outcome.Success)
        val track = repo.observeMine().first().first().copy(
            audioUri = "https://api.studio.test/v1/tracks/server-track/audio",
        )
        assertEquals(mapOf("Authorization" to "Bearer fixture.payload.signature-1"), repo.playbackHeaders(track))
        assertEquals(mapOf("Authorization" to "Bearer fixture.payload.signature-2"), repo.playbackHeaders(track))
        assertEquals(mapOf("Authorization" to "Bearer fixture.payload.signature-3"), repo.exportHeaders(track))
        assertFalse(store.read().toString().contains("fixture.payload.signature"))
        val foreign = track.copy(audioUri = "https://other.test/audio")
        assertEquals(emptyMap<String, String>(), repo.playbackHeaders(foreign))
        assertEquals(3, refreshes)
        repo.signOut()
        try {
            repo.playbackHeaders(track)
            org.junit.Assert.fail("Signed-out audio must not obtain credentials")
        } catch (failure: ApiFailure) {
            assertEquals(com.illiamelnyk.studio.core.domain.AppError.Unauthorized, failure.error)
        }
        assertEquals(3, refreshes)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun failedFirstLedgerRecordRecoversTerminalNotificationFromAtomicJournalAfterRestart() = runTest {
        val api = MemoryApi(context).apply { returnedGenerationId = "server-track" }
        val store = StudioStore(context)
        val notifier = RetryingNotifications(context)
        val work = RecordingGenerationWork()
        val repo = DurableStudioRepository(
            context,
            store,
            api,
            backgroundScope,
            generationWork = work,
            notifications = notifier,
        )
        // This scenario drives sync explicitly; foreground polling has its own lifecycle coverage.
        repo.synchronization.setForeground(false)
        repo.signInServer()
        notifier.failRecord = true
        repo.create(GenerationRequest("Warm cinematic piano"), 1)
        assertEquals(Outcome.Success(true), repo.syncPending("server-user"))
        assertTrue(store.read().getBoolean("notification-dirty-server-user"))
        assertEquals("server-user", work.owner)
        val previousTasks = backgroundScope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList()
        previousTasks.forEach { it.cancel() }
        previousTasks.forEach { it.join() }
        val recovered = RetryingNotifications(context)
        val restartedStore = StudioStore(context)
        val restarted = DurableStudioRepository(
            context,
            restartedStore,
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            work,
            recovered,
        )
        restarted.synchronization.setForeground(false)
        runCurrent()
        awaitDatabaseWork {
            !restartedStore.read().has("notification-dirty-server-user") &&
                !recovered.hasPending("server-user") && work.owner == null &&
                context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.size == 1
        }
        assertEquals("server-user", restarted.activeAccountId)
        assertFalse(restartedStore.read().has("notification-dirty-server-user"))
        assertFalse(recovered.hasPending("server-user"))
        assertNull(work.owner)
        assertEquals(1, api.generationRequests)
        assertEquals(1, context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.size)
    }

    @Test
    fun notificationOnlyIoRetryKeepsJobUntilDeliveryWithoutRefreshingOrResubmittingGeneration() = runTest {
        val api = MemoryApi(context).apply { returnedGenerationId = "server-track" }
        val notifier = RetryingNotifications(context)
        val work = RecordingGenerationWork()
        val repo = DurableStudioRepository(
            context,
            StudioStore(context),
            api,
            backgroundScope,
            generationWork = work,
            notifications = notifier,
        )
        repo.signInServer()
        repo.create(GenerationRequest("Warm cinematic piano"), 1)
        notifier.failDelivery = true
        assertEquals(Outcome.Success(true), repo.syncPending("server-user"))
        assertEquals("server-user", work.owner)
        val reads = api.libraryRequests
        notifier.failDelivery = false
        assertEquals(Outcome.Success(false), repo.syncPending("server-user"))
        assertEquals(reads, api.libraryRequests)
        assertEquals(1, api.generationRequests)
        assertNull(work.owner)
        assertFalse(notifier.hasPending("server-user"))
    }

    @Test
    fun suppressedCompletionDoesNotKeepOsWorkAliveAndSignOutBlocksDirtyOwnerRecovery() = runTest {
        val api = MemoryApi(context).apply { returnedGenerationId = "server-track" }
        val notifier = RetryingNotifications(context).apply { enabled = false }
        val work = RecordingGenerationWork()
        val repo = DurableStudioRepository(
            context,
            StudioStore(context),
            api,
            backgroundScope,
            generationWork = work,
            notifications = notifier,
        )
        repo.signInServer()
        repo.create(GenerationRequest("Warm cinematic piano"), 1)
        assertEquals(Outcome.Success(false), repo.syncPending("server-user"))
        assertNull(work.owner)
        assertFalse(notifier.hasPending("server-user"))
        notifier.failRecord = true
        repo.create(GenerationRequest("Another cinematic piano"), 1)
        repo.syncPending("server-user")
        repo.signOut()
        val reads = api.libraryRequests
        notifier.failRecord = false
        assertEquals(Outcome.Success(false), repo.syncPending("server-user"))
        assertEquals(reads, api.libraryRequests)
        assertNull(work.owner)
        assertNull(repo.session.first())
        assertTrue(context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.isEmpty())
    }

    @Test
    fun multipleRemixSourcesPersistInOrderAndProviderLimitRejectsBeforeMutation() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        val sources = DemoCatalog.tracks.take(2).map { it.id.value }
        val request = GenerationRequest("Blend two original catalogue moods", remixTrackIds = sources)
        val result = repository.create(request, 1) as Outcome.Success
        assertEquals(sources, api.lastGeneration!!.getJSONArray("remixTrackIds").strings())
        assertFalse(api.lastGeneration!!.has("remixTrackId"))
        assertEquals(sources, result.value.single().remixTrackIds)
        val restored = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        assertEquals(sources, restored.observeById(result.value.single().id).first()!!.remixTrackIds)
        val credits = repository.credits.first()
        api.maxRemixSources = 1
        assertEquals(
            Outcome.Failure(com.illiamelnyk.studio.core.domain.AppError.UnsupportedMode),
            repository.create(request, 1),
        )
        assertEquals(1, api.generationRequests)
        assertEquals(credits, repository.credits.first())
    }

    @Test
    fun expiredReferenceIsRefreshedWithTheSameGenerationKey() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        val request =
            GenerationRequest("Refresh the expired reference safely", referenceAudioUri = "file:///source.wav")
        store.saveUpload("server-user", request.referenceAudioUri!!, "expired-reference")
        api.expiredReferences.add("expired-reference")
        assertTrue(repository.create(request, 1) is Outcome.Success)
        assertEquals(1, api.referenceUploads)
        assertEquals(2, api.generationKeys.size)
        assertEquals(api.generationKeys.first(), api.generationKeys.last())
        assertEquals("fresh-reference-1", api.lastGeneration!!.getString("referenceUploadId"))
        assertFalse(api.lastGeneration!!.has("referenceAudioUri"))
        assertEquals("fresh-reference-1", store.uploadFor("server-user", request.referenceAudioUri!!))
    }

    @Test
    fun refreshedReferenceSurvivesLostResponseWithoutAnotherUploadOrKey() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        val request = GenerationRequest("Keep the refreshed request durable", referenceAudioUri = "file:///source.wav")
        store.saveUpload("server-user", request.referenceAudioUri!!, "expired-reference")
        api.expiredReferences.add("expired-reference")
        api.failGeneration = true
        assertEquals(
            Outcome.Failure(com.illiamelnyk.studio.core.domain.AppError.Network),
            repository.create(request, 1),
        )
        val pending = store.generationAttempt("server-user")!!
        assertEquals("fresh-reference-1", pending.request.referenceUploadId)
        api.failGeneration = false
        assertTrue(repository.create(request, 1) is Outcome.Success)
        assertEquals(1, api.referenceUploads)
        assertEquals(setOf(pending.key), api.generationKeys.toSet())
    }

    @Test
    fun networkFailureNeverRefreshesAReferenceAndASecondMissingUploadDoesNotLoop() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        val request =
            GenerationRequest("Do not replay uncertain reference requests", referenceAudioUri = "file:///source.wav")
        store.saveUpload("server-user", request.referenceAudioUri!!, "cached-reference")
        api.failGeneration = true
        assertEquals(
            Outcome.Failure(com.illiamelnyk.studio.core.domain.AppError.Network),
            repository.create(request, 1),
        )
        assertEquals(0, api.referenceUploads)
        assertEquals(1, api.generationKeys.size)
        api.failGeneration = false
        api.expiredReferences.addAll(listOf("cached-reference", "fresh-reference-1"))
        assertTrue(repository.create(request, 1) is Outcome.Failure)
        assertEquals(1, api.referenceUploads)
        assertEquals(3, api.generationKeys.size)
        assertEquals(1, api.generationKeys.toSet().size)
    }

    @Test
    fun typedPendingReferencePreservesItsAcceptedKeyBeforeUploadingAgain() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        val request =
            GenerationRequest(
                "Keep the accepted audio reference identity",
                referenceAudioUri = "file:///private/source.wav",
            )
        store.putGenerationAttempt(
            "server-user",
            PendingGeneration(
                "already-accepted-reference",
                request.copy(referenceUploadId = "uploaded-source"),
                1,
            ),
        )
        assertTrue(repository.create(request, 1) is Outcome.Success)
        assertEquals("already-accepted-reference", api.lastGenerationKey)
        assertEquals("uploaded-source", api.lastGeneration!!.getString("referenceUploadId"))
        assertFalse(api.lastGeneration!!.has("referenceAudioUri"))
    }

    @Test
    fun ambiguousLegacyHashOnlyReferenceDoesNotSpendANewGenerationKey() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        val request =
            GenerationRequest(
                "Keep the accepted legacy audio reference",
                referenceAudioUri = "file:///private/source.wav",
            )
        val original = org.json.JSONObject().put(
            "signature",
            request.copy(
                referenceAudioUri = null,
                referenceUploadId = "legacy-upload",
            ).toJson().put("variants", 1).toString(),
        ).put("key", "legacy-paid-key")
        store.change { it.put("pending-server-user", original) }
        assertEquals(
            Outcome.Failure(com.illiamelnyk.studio.core.domain.AppError.ProviderOutcomeUnknown),
            repository.create(request, 1),
        )
        assertEquals(0, api.generationRequests)
        assertEquals("legacy-paid-key", store.generationAttempt("server-user")!!.key)
    }

    @Test
    fun malformedLegacyPendingAttemptBlocksPosting() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        val original = org.json.JSONObject().put("signature", "{unreadable").put("key", "possibly-paid")
        store.change { it.put("pending-server-user", original) }
        assertEquals(
            Outcome.Failure(com.illiamelnyk.studio.core.domain.AppError.ProviderOutcomeUnknown),
            repository.create(GenerationRequest("Preserve a possibly paid generation attempt"), 1),
        )
        assertEquals(0, api.generationRequests)
        assertTrue(store.generationAttempt("server-user")!!.blocked)
    }

    @Test
    fun legacyPendingScalarSignatureReusesItsIdempotencyKeyForTheEquivalentArrayRequest() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        val source = DemoCatalog.tracks.first().id.value
        val request = GenerationRequest("Keep the original accepted remix identity", remixTrackIds = listOf(source))
        val legacyBody = request.toJson().apply {
            remove("remixTrackIds")
            remove("referenceAudioUri")
            put("remixTrackId", source)
            put("variants", 1)
        }
        store.change { root ->
            root.put(
                "pending-server-user",
                org.json.JSONObject()
                    .put("signature", legacyBody.toString()).put("key", "existing-accepted-key"),
            )
        }
        assertTrue(repository.create(request, 1) is Outcome.Success)
        assertEquals("existing-accepted-key", api.lastGenerationKey)
        assertEquals(listOf(source), api.lastGeneration!!.getJSONArray("remixTrackIds").strings())
    }

    @Test
    fun deletingAnOriginalAfterSelectionRejectsItsStaleRemixReferenceBeforePostingOrSpendingCredit() = runTest {
        val api = MemoryApi(context)
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        val original = repository.observeMine().first().first()
        val credits = repository.credits.first()
        assertTrue(repository.delete(original.id) is Outcome.Success)
        assertEquals(
            Outcome.Failure(com.illiamelnyk.studio.core.domain.AppError.InvalidReference),
            repository.create(
                GenerationRequest("Try a stale original reference", remixTrackIds = listOf(original.id.value)),
                1,
            ),
        )
        assertEquals(0, api.generationRequests)
        assertEquals(credits, repository.credits.first())
    }

    @Test
    fun rawOwnedRecordingCannotBypassRemixEligibilityOrSpendCreditWhileAnOriginalCanPost() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val first = DurableStudioRepository(context, store, api, backgroundScope)
        first.signInServer()
        val original = first.observeMine().first().first()
        val recordingId = "recording-8ced07a1-2964-4a3f-aa80-75177020463b"
        val recording = original.copy(
            id = com.illiamelnyk.studio.core.domain.track.TrackId(recordingId),
            audioUri = api.baseUrl + "/v1/recordings/8ced07a1-2964-4a3f-aa80-75177020463b/audio",
        )
        store.change { root ->
            root.getJSONObject("account-server-user").getJSONArray("tracks").put(recording.toJson())
        }
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        val credits = repository.credits.first()
        assertEquals(
            Outcome.Failure(com.illiamelnyk.studio.core.domain.AppError.InvalidReference),
            repository.create(
                GenerationRequest("Use a raw recording as a remix source", remixTrackIds = listOf(recordingId)),
                1,
            ),
        )
        assertEquals(0, api.generationRequests)
        assertEquals(credits, repository.credits.first())
        assertTrue(
            repository.create(
                GenerationRequest("Remix the actual completed original", remixTrackIds = listOf(original.id.value)),
                1,
            )
                is Outcome.Success,
        )
        assertEquals(1, api.generationRequests)
        assertEquals(listOf(original.id.value), api.lastGeneration!!.getJSONArray("remixTrackIds").strings())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun playbackAndExportAuthorizationDoNotWaitForAnUnrelatedSuspendedServerOperation() = runTest {
        val api = MemoryApi(context)
        val repository = DurableStudioRepository(
            context,
            StudioStore(context),
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        repository.signInServer()
        val track = repository.observeMine().first().first()
        api.holdProfile = true
        val editing = async {
            repository.updateProfile(com.illiamelnyk.studio.core.domain.session.ProfileUpdate(marketingOptIn = true))
        }
        runCurrent()
        api.profileStarted.await()
        val playback = async { repository.playbackHeaders(track) }
        val export = async { repository.exportHeaders(track) }
        runCurrent()
        assertTrue("Playback authorization waited for the repository network mutex", playback.isCompleted)
        assertTrue("Export authorization waited for the repository network mutex", export.isCompleted)
        assertEquals(mapOf("Authorization" to "Bearer dev:local-developer"), playback.await())
        assertEquals(playback.await(), export.await())
        assertFalse(editing.isCompleted)
        api.profileRelease.complete(Unit)
        assertTrue(editing.await() is Outcome.Success)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun signOutWhileBearerRefreshIsSuspendedRejectsTheStalePlaybackAuthorization() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = MemoryApi(
            context,
            ApiConfiguration("https://api.studio.test", ApiAuthMode.Firebase, false),
            BearerTokenProvider { uid ->
                started.complete(Unit)
                release.await()
                FirebaseBearerToken(uid, "fixture.payload.signature", System.currentTimeMillis() + 3_600_000)
            },
        )
        val repository = DurableStudioRepository(
            context,
            StudioStore(context),
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        repository.signInFirebase("firebase-user")
        val track = repository.observeMine().first().first()
        val pending = async { runCatching { repository.playbackHeaders(track) } }
        runCurrent()
        started.await()
        repository.signOut()
        release.complete(Unit)
        assertEquals(
            com.illiamelnyk.studio.core.domain.AppError.Unauthorized,
            (pending.await().exceptionOrNull() as ApiFailure).error,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deletionWhileBearerRefreshIsSuspendedRejectsTheStaleExportAuthorization() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = MemoryApi(
            context,
            ApiConfiguration("https://api.studio.test", ApiAuthMode.Firebase, false),
            BearerTokenProvider { uid ->
                started.complete(Unit)
                release.await()
                FirebaseBearerToken(uid, "fixture.payload.signature", System.currentTimeMillis() + 3_600_000)
            },
        )
        val repository = DurableStudioRepository(
            context,
            StudioStore(context),
            api,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        repository.signInFirebase("firebase-user")
        val track = repository.observeMine().first().first()
        val pending = async { runCatching { repository.exportHeaders(track) } }
        runCurrent()
        started.await()
        assertTrue(repository.delete(track.id) is Outcome.Success)
        release.complete(Unit)
        assertEquals(
            com.illiamelnyk.studio.core.domain.AppError.Unauthorized,
            (pending.await().exceptionOrNull() as ApiFailure).error,
        )
    }

    @Test
    fun authorizedLocalFixtureWithoutAudioUriCanExportButSignOutRejectsItsCachedAudio() = runTest {
        val repository = DurableStudioRepository(context, StudioStore(context), MemoryApi(context), backgroundScope)
        repository.signInDemo()
        val track = (repository.create(GenerationRequest("Local fixture reference audio"), 1) as Outcome.Success)
            .value.single()
        assertNull(track.audioUri)
        val files = com.illiamelnyk.studio.core.data.playback.AudioFiles(
            context,
            ownerId = { repository.activeAccountId },
            authorization = repository::exportHeaders,
        )
        assertEquals(emptyMap<String, String>(), repository.exportHeaders(track))
        val exported = files.export(track)
        assertTrue(exported.length() > 44)
        assertEquals("RIFF", exported.inputStream().use { String(it.readNBytes(4)) })
        repository.signOut()
        assertTrue(runCatching { files.export(track) }.isFailure)
    }

    @Test
    fun ownedServerTrackWithoutAudioUriCannotFallBackToFixtureExport() = runTest {
        val api = MemoryApi(context).apply { failures = listOf(failure("no-server-audio", "auto")) }
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        val track = repository.observeMine().first().first { it.id.value == "no-server-audio" }
        assertNull(track.audioUri)
        val result = runCatching { repository.exportHeaders(track) }
        assertEquals(
            com.illiamelnyk.studio.core.domain.AppError.Unauthorized,
            (result.exceptionOrNull() as ApiFailure).error,
        )
    }

    private class RetryingNotifications(context: Context) : GenerationNotifications {
        var enabled = true
        var failRecord = false
        var failDelivery = false
        private val actual = com.illiamelnyk.studio.core.data.notification.AndroidGenerationNotifications(
            context,
            object : com.illiamelnyk.studio.core.domain.notification.GenerationNotificationPreferences {
                override suspend fun hasAnswered() = true
                override suspend fun answer(enabled: Boolean) {
                    this@RetryingNotifications.enabled = enabled
                }
                override suspend fun isEnabled() = enabled
            },
        )
        init {
            org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        override fun record(ownerId: String?, tracks: List<Track>) = recover(ownerId, tracks, emptySet())
        override fun recover(ownerId: String?, tracks: List<Track>, observedIds: Set<String>) {
            if (failRecord && ownerId != null) throw java.io.IOException("simulated ledger IO failure")
            actual.recover(ownerId, tracks, observedIds)
        }
        override fun hasPending(ownerId: String) = actual.hasPending(ownerId)
        override suspend fun deliver(ownerId: String?) {
            if (failDelivery) throw java.io.IOException("simulated delivery IO failure")
            actual.deliver(ownerId)
        }
        override fun forget(ownerId: String) = actual.forget(ownerId)
    }

    private class RecordingGenerationWork : GenerationWorkScheduler {
        var owner: String? = null
        override fun reconcile(ownerId: String?) {
            owner = ownerId
        }
    }

    /** runCurrent drains virtual tasks, while suspend Room transactions finish on their own executor. */
    private suspend fun awaitDatabaseWork(condition: suspend () -> Boolean) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withTimeout(5_000) {
                while (!condition()) kotlinx.coroutines.delay(10)
            }
        }

    private class MemoryApi(
        context: Context,
        configuration: ApiConfiguration = ApiConfiguration("http://10.0.2.2:8787", ApiAuthMode.Development, true),
        bearerTokens: BearerTokenProvider = UnavailableBearerTokenProvider,
    ) : JsonFixtureStudioApi(context, configuration, bearerTokens) {
        override val isAvailable: Boolean = true
        private val likes = mutableSetOf<String>()
        private val members = mutableSetOf<String>()
        private var playlistCreated = false
        var detailRequests = 0
        var libraryRequests = 0
        var onLibrary: () -> Unit = {}
        var generationRequests = 0
        var returnedGenerationId: String? = null
        var failLibrary = false
        var unauthorizedLibrary = false
        var renamedTitle: String? = null
        var failures: List<org.json.JSONObject> = emptyList()
        var lastGeneration: org.json.JSONObject? = null
        var lastGenerationKey: String? = null
        val expiredReferences = mutableSetOf<String>()
        val generationKeys = mutableListOf<String?>()
        var referenceUploads = 0
        var failGeneration = false

        override suspend fun upload(uri: String, token: String): String = "fresh-reference-${++referenceUploads}"
        var maxRemixSources = 4
        var holdProfile = false
        var onQuota: () -> Unit = {}
        var quotaLimit = 25
        var quotaRemaining = 25
        var failRecordings = false
        var deleted = false
        var deleteRequests = 0
        var holdDelete = false
        val deleteStarted = CompletableDeferred<Unit>()
        val deleteRelease = CompletableDeferred<Unit>()
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
                .put("audioUrl", baseUrl + "/v1/tracks/server-track/audio")
        private fun playlist(): org.json.JSONObject = org.json.JSONObject()
            .put("id", "server-playlist").put("name", "Server playlist")
            .put("trackIds", org.json.JSONArray(members.toList()))

        override suspend fun requestFixture(
            method: String,
            path: String,
            token: String,
            body: org.json.JSONObject?,
            key: String?,
        ): org.json.JSONObject = when {
            path == "/v1/capabilities" -> org.json.JSONObject()
                .put("provider", "mock")
                .put("qualityModes", org.json.JSONArray(listOf("auto", "deep", "fast")))
                .put("customLyrics", true).put("variants", org.json.JSONArray(listOf(1, 2)))
                .put("audioReferences", "mock").put("liveCatalogRemix", false).put("maxRemixSources", maxRemixSources)

            path == "/v1/me" && method == "DELETE" -> {
                deleteRequests++
                if (holdDelete) {
                    deleteStarted.complete(Unit)
                    deleteRelease.await()
                }
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

            path == "/v1/me/quota" -> {
                onQuota()
                org.json.JSONObject().put("limit", quotaLimit).put("remaining", quotaRemaining)
            }

            path.startsWith("/v1/tracks?") -> {
                libraryRequests++
                onLibrary()
                if (unauthorizedLibrary) throw ApiFailure(com.illiamelnyk.studio.core.domain.AppError.Unauthorized)
                if (failLibrary) throw ApiFailure(com.illiamelnyk.studio.core.domain.AppError.Network)
                org.json.JSONObject().put(
                    "tracks",
                    org.json.JSONArray().put(track).apply { failures.forEach { put(it) } },
                )
            }

            path == "/v1/tracks" && method == "POST" -> {
                generationRequests++
                lastGeneration = body?.let { org.json.JSONObject(it.toString()) }
                lastGenerationKey = key
                generationKeys.add(key)
                if (body?.optString("referenceUploadId") in expiredReferences) {
                    throw ApiFailure(com.illiamelnyk.studio.core.domain.AppError.InvalidReference, "UPLOAD_NOT_FOUND")
                }
                if (failGeneration) throw ApiFailure(com.illiamelnyk.studio.core.domain.AppError.Network)
                track.put("id", returnedGenerationId ?: "replacement-" + key.hashCode())
                    .put("status", "queued").put("audioUrl", org.json.JSONObject.NULL)
                    .put("remixTrackIds", body?.optJSONArray("remixTrackIds") ?: org.json.JSONArray())
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

            path == "/v1/recordings" -> {
                if (failRecordings) throw ApiFailure(com.illiamelnyk.studio.core.domain.AppError.Network)
                org.json.JSONObject().put("recordings", org.json.JSONArray())
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
