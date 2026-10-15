package com.illiamelnyk.studio.core.domain.notification

import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenGenerationNotificationTest {
    @Test
    fun readyTapPlaysTheRequestedTrackRatherThanCurrentOrNewestTrack() = runTest {
        val sessions = FakeSessionRepository().apply { signIn("owner", SignInMethod.Google, "Owner") }
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        val playback = FakePlaybackController(backgroundScope)
        val ready = tracks.observeMine().first().filter { it.status == TrackStatus.Ready }
        val target = ready.last()
        playback.play(ready, 0)

        assertEquals(
            OpenGenerationNotification.Result.Player,
            OpenGenerationNotification(sessions, tracks, playback)(
                GenerationNotificationTarget("dev-owner", target.id),
            ),
        )
        assertEquals(target.id, playback.state.value.current?.id)
        assertEquals(listOf(target.id), playback.state.value.queue.map { it.id })
    }

    @Test
    fun wrongOwnerOrDeletedTrackCannotStartPlayback() = runTest {
        val sessions = FakeSessionRepository().apply { signIn("owner", SignInMethod.Google, "Owner") }
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        val playback = FakePlaybackController(backgroundScope)
        val open = OpenGenerationNotification(sessions, tracks, playback)
        val ready = tracks.observeMine().first().first { it.status == TrackStatus.Ready }
        assertEquals(
            OpenGenerationNotification.Result.Ignored,
            open(GenerationNotificationTarget("another-owner", ready.id)),
        )
        tracks.delete(ready.id)
        assertEquals(
            OpenGenerationNotification.Result.Ignored,
            open(GenerationNotificationTarget("dev-owner", ready.id)),
        )
        assertNull(playback.state.value.current)
    }

    @Test
    fun failedTapRoutesToTheSpecificCreateRowWithoutPlayingAudio() = runTest {
        val sessions = FakeSessionRepository().apply { signIn("owner", SignInMethod.Google, "Owner") }
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        val playback = FakePlaybackController(backgroundScope)
        val failed = tracks.observeMine().first().first { it.status is TrackStatus.Failed }
        assertEquals(
            OpenGenerationNotification.Result.Create,
            OpenGenerationNotification(sessions, tracks, playback)(
                GenerationNotificationTarget("dev-owner", failed.id),
            ),
        )
        assertNull(playback.state.value.current)
    }

    @Test
    fun losingTheSessionWhileResolvingTheTapCannotPlayPrivateAudio() = runTest {
        val sessions = FakeSessionRepository().apply { signIn("owner", SignInMethod.Google, "Owner") }
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        val playback = FakePlaybackController(backgroundScope)
        val ready = tracks.observeMine().first().first { it.status == TrackStatus.Ready }
        val delayed = object : TrackRepository by tracks {
            override fun observeById(id: TrackId) = flow {
                sessions.signOut()
                emit(ready)
            }
        }
        assertEquals(
            OpenGenerationNotification.Result.Ignored,
            OpenGenerationNotification(sessions, delayed, playback)(
                GenerationNotificationTarget("dev-owner", ready.id),
            ),
        )
        assertNull(playback.state.value.current)
    }
}
