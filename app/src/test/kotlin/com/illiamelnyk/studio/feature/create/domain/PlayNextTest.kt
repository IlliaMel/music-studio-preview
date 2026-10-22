package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.domain.track.TrackId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayNextTest {

    private val glass = DemoCatalog.tracks[0]
    private val nightDrive = DemoCatalog.tracks[1]
    private val paperMoons = DemoCatalog.tracks[2]
    private val coldBrew = DemoCatalog.tracks[3]

    @Test
    fun `with nothing loaded, the track plays`() = runTest {
        val (playback, playNext) = subject()

        playNext(paperMoons)

        assertEquals(listOf(paperMoons.id), playback.state.value.queue.map { it.id })
        assertEquals(true, playback.state.value.isPlaying)
    }

    @Test
    fun `the track goes right after the one playing, which keeps playing where it was`() = runTest {
        val (playback, playNext) = subject()
        playback.play(listOf(glass, nightDrive, paperMoons), startIndex = 1)
        advanceTimeBy(10.seconds)
        runCurrent()

        playNext(coldBrew)

        val state = playback.state.value
        assertEquals(ids("glass-harbor", "night-drive", "cold-brew", "paper-moons"), state.queue.map { it.id })
        assertEquals(nightDrive.id, state.current?.id)
        assertEquals(10_000L, state.positionMs)
        assertEquals(true, state.isPlaying)
    }

    @Test
    fun `a paused track stays paused`() = runTest {
        val (playback, playNext) = subject()
        playback.play(listOf(glass, nightDrive))
        advanceTimeBy(5.seconds)
        runCurrent()
        playback.togglePlayPause()

        playNext(paperMoons)

        val state = playback.state.value
        assertEquals(ids("glass-harbor", "paper-moons", "night-drive"), state.queue.map { it.id })
        assertEquals(5_000L, state.positionMs)
        assertEquals(false, state.isPlaying)
    }

    @Test
    fun `a track already in the queue moves instead of playing twice`() = runTest {
        val (playback, playNext) = subject()
        playback.play(listOf(glass, nightDrive, paperMoons, coldBrew), startIndex = 2)

        playNext(glass)

        val state = playback.state.value
        assertEquals(ids("night-drive", "paper-moons", "glass-harbor", "cold-brew"), state.queue.map { it.id })
        assertEquals(paperMoons.id, state.current?.id)
    }

    @Test
    fun `the track that is playing stays as it is`() = runTest {
        val (playback, playNext) = subject()
        playback.play(listOf(glass, nightDrive))
        advanceTimeBy(3.seconds)
        runCurrent()
        val before = playback.state.value

        playNext(glass)

        assertEquals(before, playback.state.value)
    }

    private fun TestScope.subject(): Pair<FakePlaybackController, PlayNext> {
        val playback = FakePlaybackController(backgroundScope)
        return playback to PlayNext(playback)
    }

    private fun ids(vararg values: String) = values.map(::TrackId)
}
