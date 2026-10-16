package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.data.fake.FakeCatalog
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
import kotlinx.coroutines.cancelChildren
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
        assertEquals(
            listOf(FakeCatalog.glassHarbor.id.value),
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
            assertNull(work.owner)
            assertEquals(Outcome.Success(false), repo.syncPending("server-user"))
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
            audioHeaders = emptyMap(),
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
        repo.signInServer()
        notifier.failRecord = true
        repo.create(GenerationRequest("Warm cinematic piano"), 1)
        assertEquals(Outcome.Success(true), repo.syncPending("server-user"))
        assertTrue(store.read().getBoolean("notification-dirty-server-user"))
        assertEquals("server-user", work.owner)
        backgroundScope.coroutineContext.cancelChildren()
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
        runCurrent()
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
        val sources = FakeCatalog.tracks.take(2).map { it.id.value }
        val request = GenerationRequest("Blend two original catalogue moods", remixTrackIds = sources)
        val result = repository.create(request, 1) as Outcome.Success
        assertEquals(sources, api.lastGeneration!!.getJSONArray("remixTrackIds").strings())
        assertFalse(api.lastGeneration!!.has("remixTrackId"))
        assertEquals(sources, result.value.single().remixSourceIds)
        val restored = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        assertEquals(sources, restored.observeById(result.value.single().id).first()!!.remixSourceIds)
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
    fun legacyPendingScalarSignatureReusesItsIdempotencyKeyForTheEquivalentArrayRequest() = runTest {
        val api = MemoryApi(context)
        val store = StudioStore(context)
        val repository = DurableStudioRepository(context, store, api, backgroundScope)
        repository.signInServer()
        val source = FakeCatalog.tracks.first().id.value
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
        assertEquals(mapOf("Authorization" to "Bearer dev:illia"), playback.await())
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

    private class MemoryApi(
        context: Context,
        configuration: ApiConfiguration = ApiConfiguration("http://10.0.2.2:8787", ApiAuthMode.Development, true),
        bearerTokens: BearerTokenProvider = UnavailableBearerTokenProvider,
    ) : StudioApi(context, configuration, bearerTokens) {
        override val isAvailable: Boolean = true
        private val likes = mutableSetOf<String>()
        private val members = mutableSetOf<String>()
        private var playlistCreated = false
        var detailRequests = 0
        var libraryRequests = 0
        var generationRequests = 0
        var returnedGenerationId: String? = null
        var failLibrary = false
        var renamedTitle: String? = null
        var failures: List<org.json.JSONObject> = emptyList()
        var lastGeneration: org.json.JSONObject? = null
        var lastGenerationKey: String? = null
        var maxRemixSources = 4
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
                .put("audioUrl", baseUrl + "/v1/tracks/server-track/audio")
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
            path == "/v1/capabilities" -> org.json.JSONObject()
                .put("provider", "mock")
                .put("qualityModes", org.json.JSONArray(listOf("auto", "deep", "fast")))
                .put("customLyrics", true).put("variants", org.json.JSONArray(listOf(1, 2)))
                .put("audioReferences", "mock").put("liveCatalogRemix", false).put("maxRemixSources", maxRemixSources)

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

            path == "/v1/recordings" -> org.json.JSONObject().put("recordings", org.json.JSONArray())

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
