package com.illiamelnyk.studio.feature.player.presentation.mini

import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.feature.player.presentation.toNowPlaying
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MiniPlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val a = track("a", 4.seconds)
    private val b = track("b", 4.seconds)

    @Test
    fun `shows the playing track and how far into it`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(a), startIndex = 0)
        val viewModel = MiniPlayerViewModel(playback)

        advanceTimeBy(1.seconds)
        runCurrent()

        assertEquals(
            MiniPlayerState(track = a.toNowPlaying(), isPlaying = true, progress = 0.25f),
            viewModel.state.value,
        )
    }

    @Test
    fun `the button pauses a queue but stops a station`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(a, b), startIndex = 0)
        val viewModel = MiniPlayerViewModel(playback)

        viewModel.onIntent(MiniPlayerIntent.PlayPauseClicked)
        assertFalse(playback.state.value.isPlaying)
        assertEquals(a, playback.state.value.current)

        playback.playStation("Station", listOf(a, b))
        runCurrent()
        viewModel.onIntent(MiniPlayerIntent.PlayPauseClicked)
        assertNull(playback.state.value.current)
    }

    @Test
    fun `next skips to the next track`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(a, b), startIndex = 0)
        val viewModel = MiniPlayerViewModel(playback)

        viewModel.onIntent(MiniPlayerIntent.NextClicked)

        assertEquals(b, playback.state.value.current)
    }

    @Test
    fun `next availability follows the queue end and repeat`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(a, b), startIndex = 0)
        val viewModel = MiniPlayerViewModel(playback)
        runCurrent()
        assertEquals(true, viewModel.state.value.hasNext)
        playback.next()
        runCurrent()
        assertFalse(viewModel.state.value.hasNext)
        playback.setRepeat(com.illiamelnyk.studio.core.domain.playback.RepeatMode.All)
        runCurrent()
        assertEquals(true, viewModel.state.value.hasNext)
    }

    private fun TestScope.playback() = FakePlaybackController(backgroundScope, Random(seed = 7))
}
