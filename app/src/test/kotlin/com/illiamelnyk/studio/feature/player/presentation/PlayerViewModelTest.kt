package com.illiamelnyk.studio.feature.player.presentation

import app.cash.turbine.test
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.ui.components.LyricLine
import com.illiamelnyk.studio.core.ui.components.LyricWord
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.feature.player.fake.FAKE_LATENCY
import com.illiamelnyk.studio.feature.player.fake.InMemoryLyricsRepository
import com.illiamelnyk.studio.feature.player.fake.InMemoryTrackRepository
import com.illiamelnyk.studio.feature.player.fake.lyricLine
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sung = track("sung", 10.seconds)
    private val noLyrics = track("no-lyrics", 20.seconds)
    private val instrumental = track("instrumental", 30.seconds, instrumental = true, prompt = "Lo-fi rain")
    private val tracks = InMemoryTrackRepository(mine = listOf(sung, noLyrics))
    private val lyrics = InMemoryLyricsRepository(
        mapOf(sung.id to listOf(lyricLine(1_000, "Lights are low"), lyricLine(4_000, "Boats asleep"))),
    )

    @Test
    fun `opens on the track that is playing, before anything else runs`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(sung, noLyrics), startIndex = 1)

        val content = PlayerViewModel(playback, lyrics, tracks).state.value.content

        assertEquals(noLyrics.toNowPlaying(), content.track)
        assertTrue(content.isPlaying)
        assertTrue(content.upNext.isEmpty())
    }

    @Test
    fun `the clock moves while the content stays the same object`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(sung), startIndex = 0)
        val viewModel = PlayerViewModel(playback, lyrics, tracks)
        advance(FAKE_LATENCY)
        val content = viewModel.state.value.content

        advance(1.seconds)

        // The clock ticks every 250 ms: the last tick by 1.3 s was at 1.25 s.
        assertEquals(PlaybackClock(positionMs = 1_250, durationMs = 10_000), viewModel.state.value.clock)
        assertSame(content, viewModel.state.value.content)
    }

    @Test
    fun `lyrics follow the track, synced, missing, or the prompt of an instrumental`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val playback = playback()
            playback.play(listOf(sung, noLyrics, instrumental), startIndex = 0)
            val viewModel = PlayerViewModel(playback, lyrics, tracks)
            runCurrent()
            assertEquals(LyricsUi.Loading, viewModel.lyrics())

            advance(FAKE_LATENCY)
            assertEquals(
                LyricsUi.Synced(
                    persistentListOf(
                        LyricLine(
                            1_000,
                            listOf(LyricWord("Lights", 1_000), LyricWord("are", 1_500), LyricWord("low", 2_000)),
                        ),
                        LyricLine(4_000, listOf(LyricWord("Boats", 4_000), LyricWord("asleep", 4_500))),
                    ),
                ),
                viewModel.lyrics(),
            )

            playback.next()
            advance(FAKE_LATENCY)
            assertEquals(LyricsUi.None, viewModel.lyrics())
            playback.next()
            runCurrent()
            assertEquals(LyricsUi.None, viewModel.lyrics())
            // An instrumental has nothing to ask for.
            assertEquals(listOf(sung.id, noLyrics.id), lyrics.requested)
        }

    @Test
    fun `lyrics still loading for the last track don't land on the next one`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val playback = playback()
            playback.play(listOf(sung, noLyrics), startIndex = 0)
            val viewModel = PlayerViewModel(playback, lyrics, tracks)
            advance(FAKE_LATENCY / 2)

            playback.next()
            advance(FAKE_LATENCY / 2)

            assertEquals(LyricsUi.Loading, viewModel.lyrics())
        }

    @Test
    fun `server word timestamps arriving after playback update lyrics without changing tracks`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val song = sung.copy(audioUri = "https://example.test/audio")
            val repository = InMemoryTrackRepository(mine = listOf(song))
            val playback = playback()
            playback.play(listOf(song))
            val lyrics = com.illiamelnyk.studio.core.data.playback.LocalLyricsRepository(repository)
            val viewModel = PlayerViewModel(playback, lyrics, repository)
            runCurrent()
            assertEquals(LyricsUi.None, viewModel.state.value.content.lyrics)
            repository.replace(
                song.copy(
                    words = listOf(
                        com.illiamelnyk.studio.core.domain.playback.LyricWord("Hello", 1000, 1500),
                    ),
                ),
            )
            runCurrent()
            val synced = viewModel.state.value.content.lyrics as LyricsUi.Synced
            assertEquals("Hello", synced.lines.single().words.single().text)
            assertEquals(1000L, synced.lines.single().startMs)
        }

    @Test
    fun `a like shows at once and stays once the repository keeps it`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(sung), startIndex = 0)
        val viewModel = PlayerViewModel(playback, lyrics, tracks)
        runCurrent()

        viewModel.onIntent(PlayerIntent.LikeClicked)
        runCurrent()
        assertTrue(viewModel.state.value.content.isLiked)

        advance(FAKE_LATENCY)
        assertTrue(viewModel.state.value.content.isLiked)
        tracks.observeById(sung.id).test { assertEquals(true, awaitItem()?.isLiked) }
    }

    @Test
    fun `a like that fails to save goes back and says why`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(sung), startIndex = 0)
        val viewModel = PlayerViewModel(playback, lyrics, tracks)
        runCurrent()
        tracks.nextLikeFailure = AppError.Network

        viewModel.effects.test {
            viewModel.onIntent(PlayerIntent.LikeClicked)
            advance(FAKE_LATENCY)

            assertFalse(viewModel.state.value.content.isLiked)
            assertEquals(
                PlayerEffect.ShowToast(
                    PlayerToastUi(
                        title = UiText.Resource(R.string.player_like_failed),
                        message = UiText.Resource(R.string.error_network),
                        kind = StudioToastKind.Error,
                    ),
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `a like changed elsewhere shows up, also after a like here`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(sung), startIndex = 0)
        val viewModel = PlayerViewModel(playback, lyrics, tracks)
        viewModel.onIntent(PlayerIntent.LikeClicked)
        advance(FAKE_LATENCY)

        // Unliked in Library, say.
        tracks.setLiked(sung.id, liked = false)
        advance(FAKE_LATENCY)

        assertFalse(viewModel.state.value.content.isLiked)
    }

    @Test
    fun `a like of a catalog track, which the repository doesn't keep, stays`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val catalog = track("catalog", 10.seconds)
            val playback = playback()
            playback.play(listOf(catalog), startIndex = 0)
            val viewModel = PlayerViewModel(playback, lyrics, tracks)

            viewModel.onIntent(PlayerIntent.LikeClicked)
            advance(FAKE_LATENCY)

            assertTrue(viewModel.state.value.content.isLiked)
        }

    @Test
    fun `seeking goes to that part of the track`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(noLyrics), startIndex = 0)
        val viewModel = PlayerViewModel(playback, lyrics, tracks)

        viewModel.onIntent(PlayerIntent.SeekRequested(0.25f))
        assertEquals(5_000L, playback.state.value.positionMs)

        viewModel.onIntent(PlayerIntent.LyricLineClicked(startMs = 12_000))
        assertEquals(12_000L, playback.state.value.positionMs)
    }

    @Test
    fun `a station can't be sought`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.playStation("Station", listOf(noLyrics))
        val viewModel = PlayerViewModel(playback, lyrics, tracks)

        viewModel.onIntent(PlayerIntent.SeekRequested(0.5f))

        assertEquals(0L, playback.state.value.positionMs)
    }

    @Test
    fun `the repeat switch repeats the whole queue or nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(sung), startIndex = 0)
        val viewModel = PlayerViewModel(playback, lyrics, tracks)

        viewModel.onIntent(PlayerIntent.RepeatChanged(enabled = true))
        assertEquals(RepeatMode.All, playback.state.value.repeat)

        viewModel.onIntent(PlayerIntent.RepeatChanged(enabled = false))
        assertEquals(RepeatMode.Off, playback.state.value.repeat)
    }

    @Test
    fun `up next selects a future track and clear retains current track`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val playback = playback()
            playback.play(listOf(sung, noLyrics, instrumental), startIndex = 0)
            val viewModel = PlayerViewModel(playback, lyrics, tracks)
            viewModel.onIntent(PlayerIntent.UpNextTrackClicked(position = 0))
            assertEquals(noLyrics, playback.state.value.current)
            viewModel.onIntent(PlayerIntent.ClearUpNextClicked)
            assertEquals(listOf(noLyrics), playback.state.value.queue)
            runCurrent()
            assertTrue(viewModel.state.value.content.upNext.isEmpty())
        }

    @Test
    fun `queue move positions refer to upcoming tracks and keep playback position`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val playback = playback()
            playback.play(listOf(sung, noLyrics, instrumental))
            playback.seekTo(2500)
            val viewModel = PlayerViewModel(playback, lyrics, tracks)
            viewModel.onIntent(PlayerIntent.QueueItemMoved(1, 0))
            assertEquals(listOf(sung, instrumental, noLyrics), playback.state.value.queue)
            assertEquals(sung, playback.state.value.current)
            assertEquals(2500L, playback.state.value.positionMs)
        }

    @Test
    fun `invalid up next positions leave playback untouched`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(sung, noLyrics), startIndex = 0)
        playback.seekTo(2_000)
        val viewModel = PlayerViewModel(playback, lyrics, tracks)
        val before = playback.state.value
        viewModel.onIntent(PlayerIntent.UpNextTrackClicked(-1))
        assertEquals(before, playback.state.value)
        viewModel.onIntent(PlayerIntent.UpNextTrackClicked(2))
        assertEquals(before, playback.state.value)
    }

    @Test
    fun `stop ends a station and closes any sheet`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.playStation("Station", listOf(sung, noLyrics))
        val viewModel = PlayerViewModel(playback, lyrics, tracks)
        viewModel.onIntent(PlayerIntent.QueueClicked)

        viewModel.onIntent(PlayerIntent.StopClicked)
        runCurrent()

        assertNull(viewModel.state.value.content.track)
        assertNull(viewModel.state.value.content.sheet)
    }

    @Test
    fun `add to passes the playing track to the shared playlist picker`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(sung), startIndex = 0)
        val viewModel = PlayerViewModel(playback, lyrics, tracks)
        viewModel.effects.test {
            viewModel.onIntent(PlayerIntent.AddToClicked)
            runCurrent()
            assertEquals(PlayerEffect.OpenPlaylistPicker(sung), awaitItem())
        }
    }

    @Test
    fun `sharing exports the playing audio track`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(sung), startIndex = 0)
        val viewModel = PlayerViewModel(playback, lyrics, tracks)

        viewModel.effects.test {
            viewModel.onIntent(PlayerIntent.ShareClicked)
            runCurrent()

            assertEquals(
                PlayerEffect.ShareAudio(sung),
                awaitItem(),
            )
        }
    }

    @Test
    fun `remix sends the playing track to composer and collapses player`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val playback = playback()
            playback.play(listOf(sung), startIndex = 0)
            val drafts = com.illiamelnyk.studio.core.domain.track.ComposerDraftStore()
            val viewModel = PlayerViewModel(playback, lyrics, tracks, drafts)
            viewModel.effects.test {
                viewModel.onIntent(PlayerIntent.ComingSoonClicked(SoonFeature.Remix))
                runCurrent()
                assertEquals(sung, drafts.pending.value)
                assertEquals(PlayerEffect.Collapse, awaitItem())
            }
        }

    @Test
    fun `a remix cannot create another remix draft`() = runTest(mainDispatcherRule.testDispatcher) {
        val remix = sung.copy(remixTrackId = "original")
        val playback = playback()
        playback.play(listOf(remix))
        val drafts = com.illiamelnyk.studio.core.domain.track.ComposerDraftStore()
        val viewModel = PlayerViewModel(playback, lyrics, tracks, drafts)
        assertFalse(viewModel.state.value.content.canRemix)
        viewModel.effects.test {
            viewModel.onIntent(PlayerIntent.ComingSoonClicked(SoonFeature.Remix))
            runCurrent()
            assertNull(drafts.pending.value)
            expectNoEvents()
        }
    }

    @Test
    fun `download exports the playing track and unavailable autoplay preserves repeat`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val playback = playback()
            playback.play(listOf(sung), startIndex = 0)
            val viewModel = PlayerViewModel(playback, lyrics, tracks)
            viewModel.effects.test {
                viewModel.onIntent(PlayerIntent.ComingSoonClicked(SoonFeature.Download))
                runCurrent()
                assertEquals(PlayerEffect.Download(sung), awaitItem())
            }
            val before = playback.state.value.repeat
            viewModel.onIntent(PlayerIntent.ComingSoonClicked(SoonFeature.Autoplay))
            assertEquals(before, playback.state.value.repeat)
            assertEquals(PlayerSheet.ComingSoon(SoonFeature.Autoplay), viewModel.state.value.content.sheet)
        }

    @Test
    fun `the lyrics and the queue open and close`() = runTest(mainDispatcherRule.testDispatcher) {
        val playback = playback()
        playback.play(listOf(sung), startIndex = 0)
        val viewModel = PlayerViewModel(playback, lyrics, tracks)

        viewModel.onIntent(PlayerIntent.LyricsClicked)
        viewModel.onIntent(PlayerIntent.QueueClicked)
        assertTrue(viewModel.state.value.content.showLyrics)
        assertEquals(PlayerSheet.Queue, viewModel.state.value.content.sheet)

        viewModel.onIntent(PlayerIntent.SheetDismissed)
        viewModel.onIntent(PlayerIntent.LyricsClicked)
        assertFalse(viewModel.state.value.content.showLyrics)
        assertNull(viewModel.state.value.content.sheet)
    }

    @Test
    fun `queue toggles inline and lyrics switches directly from queue to lyrics`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val playback = playback()
            playback.play(listOf(sung, noLyrics))
            val viewModel = PlayerViewModel(playback, lyrics, tracks)
            viewModel.onIntent(PlayerIntent.QueueClicked)
            assertEquals(PlayerSheet.Queue, viewModel.state.value.content.sheet)
            viewModel.onIntent(PlayerIntent.QueueClicked)
            assertNull(viewModel.state.value.content.sheet)
            viewModel.onIntent(PlayerIntent.QueueClicked)
            viewModel.onIntent(PlayerIntent.LyricsClicked)
            assertNull(viewModel.state.value.content.sheet)
            assertTrue(viewModel.state.value.content.showLyrics)
        }

    @Test
    fun `collapse asks the route to close the player`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = PlayerViewModel(playback(), lyrics, tracks)

        viewModel.effects.test {
            viewModel.onIntent(PlayerIntent.CollapseClicked)
            runCurrent()

            assertEquals(PlayerEffect.Collapse, awaitItem())
        }
    }

    private fun PlayerViewModel.lyrics(): LyricsUi = state.value.content.lyrics

    private fun TestScope.playback() = FakePlaybackController(backgroundScope, Random(seed = 7))

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }
}
