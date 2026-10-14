package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePlaybackControllerTest {

    private val a = track("a", 2.seconds)
    private val b = track("b", 2.seconds)
    private val c = track("c", 2.seconds)

    @Test
    fun `the position moves only while playing`() = runTest {
        val player = player()

        player.play(listOf(a, b), startIndex = 0)
        advance(1.seconds)
        assertEquals(1_000L, player.state.value.positionMs)

        player.togglePlayPause()
        advance(1.seconds)
        assertEquals(1_000L, player.state.value.positionMs)
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun `the next track starts when one ends, and the queue stops after the last`() = runTest {
        val player = player()

        player.play(listOf(a, b), startIndex = 0)
        advance(2.seconds)
        assertEquals(b, player.state.value.current)
        assertEquals(0L, player.state.value.positionMs)

        advance(3.seconds)
        assertEquals(b, player.state.value.current)
        assertEquals(2_000L, player.state.value.positionMs)
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun `repeat all starts the queue again and repeat one replays the track`() = runTest {
        val player = player()
        player.play(listOf(a, b), startIndex = 1)

        player.setRepeat(RepeatMode.All)
        advance(2.seconds)
        assertEquals(a, player.state.value.current)

        player.setRepeat(RepeatMode.One)
        advance(2.seconds)
        assertEquals(a, player.state.value.current)
        assertEquals(0L, player.state.value.positionMs)
    }

    @Test
    fun `previous restarts a track after 3 seconds and goes back before`() = runTest {
        val long = track("long", 10.seconds)
        val player = player()
        player.play(listOf(a, long), startIndex = 1)

        advance(3.seconds + 250.milliseconds)
        player.previous()
        assertEquals(long, player.state.value.current)
        assertEquals(0L, player.state.value.positionMs)

        advance(1.seconds)
        player.previous()
        assertEquals(a, player.state.value.current)
    }

    @Test
    fun `next does nothing on the last track unless the queue repeats`() = runTest {
        val player = player()
        player.play(listOf(a, b), startIndex = 1)

        player.next()
        assertEquals(b, player.state.value.current)

        player.setRepeat(RepeatMode.All)
        player.next()
        assertEquals(a, player.state.value.current)
    }

    @Test
    fun `shuffle keeps the current track first and turning it off restores the order`() = runTest {
        val player = player()
        player.play(listOf(a, b, c), startIndex = 1)

        player.setShuffle(true)
        val shuffled = player.state.value
        assertEquals(b, shuffled.current)
        assertEquals(0, shuffled.currentIndex)
        assertEquals(setOf(a, b, c), shuffled.queue.toSet())

        player.setShuffle(false)
        assertEquals(listOf(a, b, c), player.state.value.queue)
        assertEquals(b, player.state.value.current)
    }

    @Test
    fun `stop clears the queue and the clock`() = runTest {
        val player = player()
        player.play(listOf(a), startIndex = 0)

        player.stop()
        advance(1.seconds)

        assertEquals(null, player.state.value.current)
        assertEquals(0L, player.state.value.positionMs)
    }

    @Test
    fun `a station starts over after its last track, even when repeat is set to one track`() = runTest {
        val player = player()
        player.setRepeat(RepeatMode.One)

        player.playStation("Lo-fi", listOf(a, b))
        advance(2.seconds)
        assertEquals(b, player.state.value.current)
        advance(2.seconds)

        assertEquals(a, player.state.value.current)
        assertTrue(player.state.value.isPlaying)
        assertEquals("Lo-fi", player.state.value.stationName)
    }

    @Test
    fun `next on the last track of a station goes back to its first`() = runTest {
        val player = player()
        player.playStation("Lo-fi", listOf(a, b))

        player.next()
        player.next()

        assertEquals(a, player.state.value.current)
    }

    @Test
    fun `a queue played after a station is not a station`() = runTest {
        val player = player()
        player.playStation("Lo-fi", listOf(a, b))

        player.play(listOf(c), startIndex = 0)

        assertEquals(null, player.state.value.stationName)
    }

    @Test
    fun `clearing what plays next keeps the current track where it is`() = runTest {
        val player = player()
        player.play(listOf(a, b, c), startIndex = 1)
        advance(1.seconds)

        player.clearUpNext()

        assertEquals(listOf(b), player.state.value.queue)
        assertEquals(b, player.state.value.current)
        assertEquals(1_000L, player.state.value.positionMs)
        assertTrue(player.state.value.isPlaying)
        // Nothing comes after it now: the queue ends with it.
        advance(1.seconds)
        assertFalse(player.state.value.isPlaying)
    }

    private fun TestScope.player() = FakePlaybackController(backgroundScope, Random(seed = 7))

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    private fun track(id: String, duration: Duration) = Track(
        id = TrackId(id),
        title = id,
        artist = "Artist",
        prompt = "",
        style = null,
        duration = duration,
        model = MusicModel.V2_5,
        instrumental = true,
        coverSeed = CoverSeed(id),
        status = TrackStatus.Ready,
        createdAt = Instant.parse("2026-10-14T09:00:00Z"),
        isLiked = false,
    )
}
