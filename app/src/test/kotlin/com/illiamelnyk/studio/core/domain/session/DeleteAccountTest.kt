package com.illiamelnyk.studio.core.domain.session

import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import kotlin.time.Clock
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAccountTest {
    @Test
    fun `a rejected deletion preserves tracks and playback`() = runTest {
        val sessions = FakeSessionRepository()
        val failure = object : SessionRepository by sessions {
            override suspend fun deleteAccount(): Outcome<Unit> = Outcome.Failure(AppError.Network)
        }
        val playback = FakePlaybackController(backgroundScope)
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        val before = tracks.observeMine().first()
        playback.play(before)

        assertEquals(Outcome.Failure(AppError.Network), DeleteAccount(failure, playback, tracks)())
        assertEquals(before, tracks.observeMine().first())
        assertTrue(playback.state.value.isPlaying)
    }

    @Test
    fun `disposing the caller after deletion cannot interrupt local cleanup`() = runTest {
        val sessions = FakeSessionRepository()
        val deleting = object : SessionRepository by sessions {
            override suspend fun deleteAccount(): Outcome<Unit> {
                // The app removes the settings ViewModel as soon as its session disappears.
                currentCoroutineContext().cancel()
                return Outcome.Success(Unit)
            }
        }
        val playback = FakePlaybackController(backgroundScope)
        val tracks = FakeTrackRepository(FakeCreditsRepository(), sessions, backgroundScope, Clock.System)
        val job = launch { DeleteAccount(deleting, playback, tracks)() }
        advanceUntilIdle()
        job.join()
        assertTrue(tracks.observeMine().first().isEmpty())
    }
}
