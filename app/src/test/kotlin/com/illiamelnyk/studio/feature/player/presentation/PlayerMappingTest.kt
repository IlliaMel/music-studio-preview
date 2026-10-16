package com.illiamelnyk.studio.feature.player.presentation

import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.domain.track.Style
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.player.fake.track
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMappingTest {

    private val a = track("a", 10.seconds)
    private val b = track("b", 20.seconds)

    @Test
    fun `next is disabled on the last track and reenabled when repeat all is selected`() {
        val ended = PlayerContent().withPlayback(PlaybackState(queue = listOf(a, b), currentIndex = 1))
        org.junit.Assert.assertFalse(ended.hasNext)
        val looping = ended.withPlayback(PlaybackState(queue = listOf(a, b), currentIndex = 1, repeat = RepeatMode.All))
        assertTrue(looping.hasNext)
    }

    @Test
    fun `repeating one track shows the repeat switch on`() {
        val content = PlayerContent().withPlayback(PlaybackState(queue = listOf(a), repeat = RepeatMode.One))

        assertTrue(content.repeat)
    }

    @Test
    fun `playback replaces the track and the queue and keeps the lyrics and the panels`() {
        val before = PlayerContent(
            lyrics = LyricsUi.Loading,
            showLyrics = true,
            isLiked = true,
            sheet = PlayerSheet.Queue,
        )

        val after = before.withPlayback(PlaybackState(queue = listOf(a, b), currentIndex = 0))

        assertEquals(a.toNowPlaying(), after.track)
        assertEquals(listOf("Title b"), after.upNext.map { it.title })
        assertEquals(before.copy(track = after.track, hasNext = true, upNext = after.upNext), after)
    }

    @Test
    fun `station title is its name and subtitle is the current track`() {
        val content = PlayerContent(sheet = PlayerSheet.Queue)
            .withPlayback(PlaybackState(queue = listOf(a), stationName = "Lo-fi radio"))
        assertNull(content.sheet)
        assertEquals("Lo-fi radio", content.track?.title)
        assertEquals("Title a", content.track?.subtitle)
        assertTrue(content.isStation)
    }

    @Test
    fun `nothing loaded closes the open sheet`() {
        val content = PlayerContent(sheet = PlayerSheet.ComingSoon(SoonFeature.Remix)).withPlayback(PlaybackState())

        assertNull(content.sheet)
        assertNull(content.track)
    }

    @Test
    fun `empty and failed lyrics hide the toggle`() {
        assertEquals(LyricsUi.None, Outcome.Failure(AppError.Network).toLyricsUi())
        assertEquals(
            LyricsUi.None,
            Outcome.Success(emptyList<com.illiamelnyk.studio.core.domain.playback.LyricLine>()).toLyricsUi(),
        )
    }

    @Test
    fun remixEligibilityExcludesRawRecordingsPendingAndCanonicalRemixes() {
        val recording = a.copy(id = com.illiamelnyk.studio.core.domain.track.TrackId("recording-raw-fixture"))
        val remix = a.copy(remixTrackIds = listOf("one", "two"))
        val pending = a.copy(status = com.illiamelnyk.studio.core.domain.track.TrackStatus.Queued)
        assertTrue(PlayerContent().withPlayback(PlaybackState(queue = listOf(a))).canRemix)
        listOf(recording, remix, pending).forEach {
            org.junit.Assert.assertFalse(PlayerContent().withPlayback(PlaybackState(queue = listOf(it))).canRemix)
        }
        val station = PlaybackState(queue = listOf(a), stationName = "LIVE")
        org.junit.Assert.assertFalse(PlayerContent().withPlayback(station).canRemix)
    }

    @Test
    fun `the clock is at no fraction of a track without length`() {
        assertEquals(0f, PlaybackClock(positionMs = 500, durationMs = 0).fraction)
        assertEquals(0.25f, PlaybackClock(positionMs = 500, durationMs = 2_000).fraction)
    }
}
