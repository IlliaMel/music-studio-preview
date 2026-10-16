package com.illiamelnyk.studio.core.domain.session

import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.feature.player.fake.track
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAccountTest {
    @Test
    fun `a rejected deletion preserves tracks and playback`() = runTest {
        val sessions = FakeSessionRepository()
        sessions.signIn("demo", SignInMethod.Google, "Demo listener", useProviderProfile = true)
        val activeSession = sessions.session.first()
        assertNotNull(activeSession)
        val failure = object : SessionRepository by sessions {
            override suspend fun deleteAccount(): Outcome<Unit> = Outcome.Failure(AppError.Network)
        }
        val playback = FakePlaybackController(backgroundScope)
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        val before = tracks.observeMine().first()
        playback.play(before)

        val drafts = ComposerDraftStore { "test-owner" }.apply { remix(track("private-remix-source", 20.seconds)) }
        val source = drafts.pending.value
        assertNotNull(source)
        assertEquals(Outcome.Failure(AppError.Network), DeleteAccount(failure, playback, tracks, drafts)())
        assertEquals(source, drafts.pending.value)
        assertEquals(listOf(source), drafts.sources.value)
        assertEquals(before, tracks.observeMine().first())
        assertEquals(activeSession, sessions.session.first())
        assertTrue(playback.state.value.isPlaying)
    }

    @Test
    fun `local purge stops playback even when remote deletion is unconfirmed`() = runTest {
        val sessions = FakeSessionRepository()
        sessions.signIn("demo", SignInMethod.Google, "Demo listener", useProviderProfile = true)
        assertNotNull(sessions.session.first())
        val uncertain = object : SessionRepository by sessions {
            override suspend fun deleteAccount(): Outcome<Unit> {
                sessions.deleteAccount()
                return Outcome.Failure(AppError.Network)
            }
        }
        val playback = FakePlaybackController(backgroundScope)
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        playback.play(tracks.observeMine().first())
        assertFalse(playback.state.value.queue.isEmpty())
        assertTrue(playback.state.value.isPlaying)

        val drafts = ComposerDraftStore { "test-owner" }.apply { remix(track("purged-remix-source", 20.seconds)) }
        assertEquals(Outcome.Failure(AppError.Network), DeleteAccount(uncertain, playback, tracks, drafts)())
        assertNull(drafts.pending.value)
        assertTrue(drafts.sources.value.isEmpty())

        assertNull(sessions.session.first())
        assertTrue(playback.state.value.queue.isEmpty())
        assertFalse(playback.state.value.isPlaying)
    }

    @Test
    fun successfulDeletionClearsRetainedRemixSources() = runTest {
        val sessions = FakeSessionRepository()
        sessions.signIn("demo", SignInMethod.Google, "Demo listener", useProviderProfile = true)
        val playback = FakePlaybackController(backgroundScope)
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        val drafts = ComposerDraftStore { "test-owner" }.apply {
            remix(track("private-one", 20.seconds))
            remix(track("private-two", 20.seconds))
        }
        assertEquals(2, drafts.sources.value.size)
        assertEquals(Outcome.Success(Unit), DeleteAccount(sessions, playback, tracks, drafts)())
        assertNull(drafts.pending.value)
        assertTrue(drafts.sources.value.isEmpty())
        assertNull(drafts.problem.value)
    }

    @Test
    fun `disposing the caller after deletion cannot interrupt local cleanup`() = runTest {
        val sessions = FakeSessionRepository()
        sessions.signIn("demo", SignInMethod.Google, "Demo listener", useProviderProfile = true)
        val sessionRemoved = CompletableDeferred<Unit>()
        val returnFromIo = CompletableDeferred<Unit>()
        val deleting = object : SessionRepository by sessions {
            override suspend fun deleteAccount(): Outcome<Unit> {
                val result = sessions.deleteAccount()
                sessionRemoved.complete(Unit)
                returnFromIo.await()
                return result
            }
        }
        val playback = FakePlaybackController(backgroundScope)
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        playback.play(tracks.observeMine().first())
        val job = launch { DeleteAccount(deleting, playback, tracks)() }
        sessionRemoved.await()
        assertNull(sessions.session.first())
        job.cancel()
        returnFromIo.complete(Unit)
        job.join()
        assertTrue(tracks.observeMine().first().isEmpty())
        assertTrue(playback.state.value.queue.isEmpty())
        assertFalse(playback.state.value.isPlaying)
    }

    @Test
    fun replacementAccountSurvivesCompletionOfAnOlderAccountDeletion() = runTest {
        val sessions = FakeSessionRepository()
        sessions.signIn("first", SignInMethod.Google, "First", useProviderProfile = true)
        var draftOwner = sessions.session.first()!!.user.id.value
        val deleted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val deleting = object : SessionRepository by sessions {
            override suspend fun deleteAccount(): Outcome<Unit> {
                val result = sessions.deleteAccount()
                deleted.complete(Unit)
                release.await()
                return result
            }
        }
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        val playback = FakePlaybackController(backgroundScope)
        val drafts = ComposerDraftStore { draftOwner }.apply { remix(track("deleted-owner-source", 20.seconds)) }
        val result = async { DeleteAccount(deleting, playback, tracks, drafts)() }
        deleted.await()
        sessions.signIn("replacement", SignInMethod.Google, "Replacement", useProviderProfile = true)
        val replacementOwner = sessions.session.first()!!.user.id
        draftOwner = replacementOwner.value
        val replacementSource = track("replacement-owner-source", 20.seconds)
        drafts.remix(replacementSource)
        val replacementTracks = tracks.observeMine().first()
        playback.play(replacementTracks)
        release.complete(Unit)
        assertEquals(Outcome.Success(Unit), result.await())
        assertEquals(replacementOwner, sessions.session.first()!!.user.id)
        assertEquals(listOf(replacementSource), drafts.sources.value)
        assertEquals(replacementTracks, tracks.observeMine().first())
        assertEquals(replacementTracks, playback.state.value.queue)
        assertTrue(playback.state.value.isPlaying)
    }

    @Test
    fun replacementAccountTracksSurviveAnOlderDeletionDurabilityWait() = runTest {
        val sessions = FakeSessionRepository()
        sessions.signIn("first", SignInMethod.Google, "First", useProviderProfile = true)
        var draftOwner = sessions.session.first()!!.user.id.value
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        val native = FakePlaybackController(backgroundScope)
        val stopped = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val playback = object : com.illiamelnyk.studio.core.domain.playback.PlaybackController by native {
            override suspend fun stopDurably() {
                native.stop()
                stopped.complete(Unit)
                release.await()
            }
        }
        val drafts = ComposerDraftStore { draftOwner }.apply { remix(track("old-deleted-source", 20.seconds)) }
        val result = async { DeleteAccount(sessions, playback, tracks, drafts)() }
        stopped.await()
        sessions.signIn("replacement", SignInMethod.Google, "Replacement", useProviderProfile = true)
        val replacementOwner = sessions.session.first()!!.user.id
        draftOwner = replacementOwner.value
        val source = track("replacement-after-stop", 20.seconds)
        drafts.remix(source)
        val replacementTracks = tracks.observeMine().first()
        native.play(replacementTracks)
        release.complete(Unit)
        assertEquals(Outcome.Success(Unit), result.await())
        assertEquals(replacementOwner, sessions.session.first()!!.user.id)
        assertEquals(listOf(source), drafts.sources.value)
        assertEquals(replacementTracks, tracks.observeMine().first())
        assertEquals(replacementTracks, native.state.value.queue)
        assertTrue(native.state.value.isPlaying)
    }
}
