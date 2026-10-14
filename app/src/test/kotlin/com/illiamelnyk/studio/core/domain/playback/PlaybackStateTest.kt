package com.illiamelnyk.studio.core.domain.playback

import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTest {

    private val a = track("a")
    private val b = track("b")

    @Test
    fun `there is a next track before the last one`() {
        assertTrue(PlaybackState(queue = listOf(a, b), currentIndex = 0).hasNext)
    }

    @Test
    fun `the last track has a next one only when the queue starts over`() {
        val last = PlaybackState(queue = listOf(a, b), currentIndex = 1)

        assertFalse(last.hasNext)
        assertFalse(last.copy(repeat = RepeatMode.One).hasNext)
        assertTrue(last.copy(repeat = RepeatMode.All).hasNext)
        assertTrue(last.copy(stationName = "Lo-fi").hasNext)
    }

    @Test
    fun `nothing loaded has no next track, even on repeat`() {
        assertFalse(PlaybackState(repeat = RepeatMode.All).hasNext)
    }

    private fun track(id: String) = Track(
        id = TrackId(id),
        title = id,
        artist = "Artist",
        prompt = "",
        style = null,
        duration = 2.seconds,
        model = MusicModel.V2_5,
        instrumental = true,
        coverSeed = CoverSeed(id),
        status = TrackStatus.Ready,
        createdAt = Instant.parse("2026-10-14T09:00:00Z"),
        isLiked = false,
    )
}
