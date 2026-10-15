package com.illiamelnyk.studio.core.data.playback

import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.domain.playback.AutoplayCatalog
import com.illiamelnyk.studio.core.domain.playback.AutoplayPolicy
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.feature.player.fake.track
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAutoplayTest {
    private val a = track("autoplay-a", 2.seconds)
    private val b = track("autoplay-b", 2.seconds)
    private val c = track("autoplay-c", 2.seconds)

    @Test
    fun policySkipsQueueHistoryAndDuplicateEntriesInDeterministicOrder() {
        val state = PlaybackState(queue = listOf(a), autoplay = true, autoplayHistory = setOf(b.id))
        assertEquals(c, AutoplayPolicy.next(listOf(a, b, b, c), state))
        assertNull(AutoplayPolicy.next(listOf(a, b), state))
        assertNull(AutoplayPolicy.next(listOf(c), state.copy(stationName = "LIVE")))
        assertNull(AutoplayPolicy.next(listOf(c), state.copy(repeat = RepeatMode.All)))
    }

    @Test
    fun enabledAutoplayAppendsWithoutChangingCurrentClockThenNextAdvances() = runTest {
        val player = FakePlaybackController(backgroundScope)
        PlaybackAutoplayCoordinator(player, AutoplayCatalog { listOf(a, b, c) }, backgroundScope) { "owner" }
        player.play(listOf(a))
        advanceTimeBy(500)
        runCurrent()
        player.setAutoplay(true)
        runCurrent()
        assertEquals(listOf(a, b), player.state.value.queue)
        assertEquals(500L, player.state.value.positionMs)
        player.next()
        runCurrent()
        assertEquals(b, player.state.value.current)
        assertEquals(listOf(a, b, c), player.state.value.queue)
        player.next()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(c, player.state.value.current)
        assertFalse(player.state.value.isPlaying)
        assertEquals(3, player.state.value.queue.size)
    }

    @Test
    fun pendingFetchCannotAppendAfterDisableOwnerSwitchOrQueueReplacement() = runTest {
        var owner = "first"
        val pending = CompletableDeferred<List<com.illiamelnyk.studio.core.domain.track.Track>>()
        val player = FakePlaybackController(backgroundScope)
        PlaybackAutoplayCoordinator(player, AutoplayCatalog { pending.await() }, backgroundScope) { owner }
        player.play(listOf(a))
        player.setAutoplay(true)
        runCurrent()
        owner = "second"
        pending.complete(listOf(b))
        runCurrent()
        assertEquals(listOf(a), player.state.value.queue)
        val expected = player.state.value
        player.setAutoplay(false)
        player.appendAutoplay(expected, b)
        assertEquals(listOf(a), player.state.value.queue)
        player.setAutoplay(true)
        player.play(listOf(c))
        player.appendAutoplay(expected, b)
        assertEquals(listOf(c), player.state.value.queue)
    }

    @Test
    fun slowCatalogResumesOnlyCompletionWaitAndNotUserPausedPlayback() = runTest {
        val pending = CompletableDeferred<List<com.illiamelnyk.studio.core.domain.track.Track>>()
        val player = FakePlaybackController(backgroundScope)
        PlaybackAutoplayCoordinator(player, AutoplayCatalog { pending.await() }, backgroundScope) { "owner" }
        player.play(listOf(a))
        player.setAutoplay(true)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(player.state.value.autoplayWaiting)
        pending.complete(listOf(b))
        runCurrent()
        assertEquals(b, player.state.value.current)
        assertTrue(player.state.value.isPlaying)
        player.togglePlayPause()
        player.appendAutoplay(player.state.value, c)
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun failedCatalogLeavesCurrentQueueUsableAndRepeatAndLiveRemainIndependent() = runTest {
        var calls = 0
        val player = FakePlaybackController(backgroundScope)
        PlaybackAutoplayCoordinator(
            player,
            AutoplayCatalog {
                calls++
                error("offline")
            },
            backgroundScope,
        ) { "owner" }
        player.play(listOf(a))
        player.setAutoplay(true)
        runCurrent()
        assertEquals(listOf(a), player.state.value.queue)
        assertTrue(player.state.value.isPlaying)
        val failedCalls = calls
        player.setRepeat(RepeatMode.All)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(a, player.state.value.current)
        assertTrue(player.state.value.isPlaying)
        assertEquals(failedCalls, calls)
        player.playStation("station", listOf(a))
        runCurrent()
        assertEquals(failedCalls, calls)
        player.setAutoplay(false)
        assertTrue(player.state.value.autoplay)
    }

    @Test
    fun historyPreventsClearOrNewQueueFromReintroducingAlreadyOfferedTrack() = runTest {
        val player = FakePlaybackController(backgroundScope)
        PlaybackAutoplayCoordinator(player, AutoplayCatalog { listOf(a, b, c) }, backgroundScope) { "owner" }
        player.play(listOf(a))
        player.setAutoplay(true)
        runCurrent()
        player.clearUpNext()
        runCurrent()
        assertEquals(listOf(a, c), player.state.value.queue)
        assertTrue(b.id in player.state.value.autoplayHistory)
    }
}
