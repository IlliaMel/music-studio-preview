package com.illiamelnyk.studio.feature.create.presentation

import app.cash.turbine.test
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.feature.create.data.fake.FakeStyleCatalog
import com.illiamelnyk.studio.feature.create.domain.GenerateTracks
import com.illiamelnyk.studio.feature.create.domain.PlayNext
import com.illiamelnyk.studio.feature.create.domain.Prompt
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature
import com.illiamelnyk.studio.feature.create.presentation.common.ToastUi
import com.illiamelnyk.studio.feature.create.presentation.common.toOptionUi
import com.illiamelnyk.studio.testing.FailingTrackRepository
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CreateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val credits = FakeCreditsRepository()
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-14T09:00:00Z")
    }
    private lateinit var tracks: FailingTrackRepository
    private lateinit var playback: FakePlaybackController

    @Test
    fun `the user's tracks load newest first, with a fix where the prompt was refused and a retry where it failed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            val state = viewModel.state.value
            assertEquals(false, state.isLoading)
            assertEquals(
                ids("seed-1", "seed-2", "seed-3", "seed-4", "seed-5", "seed-6"),
                state.tracks.map {
                    it.row.id
                },
            )
            assertEquals(
                PromptFix("Indie rock song with an original sound of its own", isSuggestion = true),
                state.track("seed-3").fix,
            )
            assertEquals(false, state.track("seed-3").canRetry)
            assertEquals(true, state.track("seed-5").canRetry)
            assertNull(state.track("seed-5").fix)
            assertEquals(true, state.track("seed-1").isLiked)
        }

    @Test
    fun `a blank prompt can't be sent and starts nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(CreateIntent.PromptChanged("   "))
        viewModel.onIntent(CreateIntent.SendClicked)
        advanceTimeBy(1.seconds)

        assertEquals(false, viewModel.state.value.composer.canSend)
        assertEquals(6, viewModel.state.value.tracks.size)
        assertEquals(8, credits.credits.first().remaining)
    }

    @Test
    fun `sending a prompt starts a composing track on top, spends a credit and empties the composer`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.effects.test {
                send(viewModel, "Lo-fi beat for studying")

                assertEquals(CreateEffect.TracksStarted, awaitItem())
                val state = viewModel.state.value
                assertEquals("Lo-fi beat for studying", state.tracks.first().row.title)
                assertEquals(TrackUi.Status.Generating, state.tracks.first().row.status)
                assertEquals(0f, state.tracks.first().progress)
                assertEquals(ComposerUi(), state.composer)
                assertEquals(7, credits.credits.first().remaining)

                // The fake composes in eight steps, one a second.
                advanceTimeBy(1.seconds)
                assertEquals(0.125f, viewModel.state.value.tracks.first().progress)
            }
        }

    @Test
    fun `a track that finishes composing is announced with a toast`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, "Lo-fi beat for studying")
            assertEquals(CreateEffect.TracksStarted, awaitItem())

            advanceTimeBy(FakeTrackRepository.GENERATION_TIME)
            runCurrent()

            val toast = ToastUi(
                title = UiText.Resource(R.string.create_toast_ready),
                message = UiText.Resource(R.string.track_subtitle, "Lo-fi beat for studying", "2:00"),
            )
            assertEquals(CreateEffect.ShowToast(toast), awaitItem())
            assertEquals(TrackUi.Status.Ready, viewModel.state.value.tracks.first().row.status)
        }
    }

    @Test
    fun `a prompt naming an artist fails with a suggestion, which Use suggestion puts in the composer`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.effects.test {
                send(viewModel, "Dreamy pop in the style of Some Band, with strings")
                assertEquals(CreateEffect.TracksStarted, awaitItem())
                advanceTimeBy(FakeTrackRepository.REJECTION_TIME)
                runCurrent()

                val suggestion = "Dreamy pop with an original sound of its own, with strings"
                val failed = viewModel.state.value.tracks.first()
                assertEquals(PromptFix(suggestion, isSuggestion = true), failed.fix)
                val toast = awaitItem() as CreateEffect.ShowToast
                assertEquals(StudioToastKind.Error, toast.toast.kind)

                viewModel.onIntent(CreateIntent.FixClicked(failed.row.id))

                assertEquals(suggestion, viewModel.state.value.composer.prompt)
                assertEquals(true, viewModel.state.value.composer.canSend)
            }
        }

    @Test
    fun `without credits the composer can't send and says why`() = runTest(mainDispatcherRule.testDispatcher) {
        repeat(FakeCreditsRepository.DAILY_LIMIT) { credits.trySpend() }
        val viewModel = viewModel()

        viewModel.onIntent(CreateIntent.PromptChanged("Lo-fi beat"))

        val composer = viewModel.state.value.composer
        assertEquals(false, composer.canSend)
        assertEquals(UiText.Resource(R.string.error_quota_exceeded), composer.notice)
    }

    @Test
    fun `two tracks per prompt need two credits`() = runTest(mainDispatcherRule.testDispatcher) {
        repeat(FakeCreditsRepository.DAILY_LIMIT - 1) { credits.trySpend() }
        val viewModel = viewModel()
        viewModel.onIntent(CreateIntent.PromptChanged("Lo-fi beat"))

        viewModel.onIntent(CreateIntent.VariantsClicked)

        assertEquals(2, viewModel.state.value.composer.variants)
        assertEquals(false, viewModel.state.value.composer.canSend)
        assertEquals(UiText.Plural(R.plurals.create_credits_short, 1, 1), viewModel.state.value.composer.notice)

        viewModel.onIntent(CreateIntent.VariantsClicked)

        assertEquals(1, viewModel.state.value.composer.variants)
        assertEquals(true, viewModel.state.value.composer.canSend)
        assertNull(viewModel.state.value.composer.notice)
    }

    @Test
    fun `two tracks per prompt start two tracks`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(CreateIntent.VariantsClicked)

        send(viewModel, "Lo-fi beat")

        assertEquals(8, viewModel.state.value.tracks.size)
        assertEquals(6, credits.credits.first().remaining)
    }

    @Test
    fun `a prompt over the limit can't be sent`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(CreateIntent.PromptChanged("a".repeat(Prompt.MAX_LENGTH + 1)))
        assertEquals(false, viewModel.state.value.composer.canSend)

        viewModel.onIntent(CreateIntent.PromptChanged("a".repeat(Prompt.MAX_LENGTH)))
        assertEquals(true, viewModel.state.value.composer.canSend)
    }

    @Test
    fun `settings change the composer only once applied`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(CreateIntent.SettingsClicked)
        viewModel.onIntent(CreateIntent.SettingsModelSelected(MusicModel.V2))
        viewModel.onIntent(CreateIntent.SettingsInstrumentalChanged(true))
        viewModel.onIntent(CreateIntent.SettingsDurationSelected(3.minutes))
        viewModel.onIntent(CreateIntent.SettingsStyleSelected("synthwave"))
        val draft =
            SettingsUi(MusicModel.V2, instrumental = true, duration = 3.minutes.toOptionUi(), styleId = "synthwave")
        assertEquals(CreateSheet.Settings(draft), viewModel.state.value.sheet)
        assertEquals("2:00", viewModel.state.value.composer.duration.label)

        // Closing the sheet drops the choices.
        viewModel.onIntent(CreateIntent.SheetDismissed)
        viewModel.onIntent(CreateIntent.SettingsClicked)
        val defaults =
            SettingsUi(MusicModel.V2_5, instrumental = false, duration = 2.minutes.toOptionUi(), styleId = null)
        assertEquals(CreateSheet.Settings(defaults), viewModel.state.value.sheet)

        viewModel.onIntent(CreateIntent.SettingsDurationSelected(3.minutes))
        viewModel.onIntent(CreateIntent.SettingsInstrumentalChanged(true))
        viewModel.onIntent(CreateIntent.SettingsStyleSelected("synthwave"))
        viewModel.onIntent(CreateIntent.SettingsApplied)

        assertNull(viewModel.state.value.sheet)
        assertEquals("3:00", viewModel.state.value.composer.duration.label)
        assertEquals("synthwave", viewModel.state.value.composer.style?.id)
        send(viewModel, "Night drive")
        val track = tracks.observeMine().first().first()
        assertEquals(3.minutes, track.duration)
        assertEquals(true, track.instrumental)
        assertEquals("synthwave", track.style?.id)
    }

    @Test
    fun `reset brings the settings back to the defaults`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(CreateIntent.SettingsClicked)
        viewModel.onIntent(CreateIntent.SettingsDurationSelected(5.minutes))
        viewModel.onIntent(CreateIntent.SettingsApplied)
        viewModel.onIntent(CreateIntent.SettingsClicked)

        viewModel.onIntent(CreateIntent.SettingsReset)

        val defaults =
            SettingsUi(MusicModel.V2_5, instrumental = false, duration = 2.minutes.toOptionUi(), styleId = null)
        assertEquals(CreateSheet.Settings(defaults), viewModel.state.value.sheet)
        assertEquals("5:00", viewModel.state.value.composer.duration.label)
    }

    @Test
    fun `quality and a style from the styles sheet go with the prompt until the style is removed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(CreateIntent.QualitySelected(Quality.Deep))
            viewModel.onIntent(CreateIntent.StylesClicked)
            assertEquals(CreateSheet.Styles, viewModel.state.value.sheet)
            viewModel.onIntent(CreateIntent.StyleSelected("lofi"))

            assertNull(viewModel.state.value.sheet)
            assertEquals(Quality.Deep, viewModel.state.value.composer.quality)
            assertEquals("Lo-fi", viewModel.state.value.composer.style?.name)

            viewModel.onIntent(CreateIntent.StyleRemoved)

            assertNull(viewModel.state.value.composer.style)
        }

    @Test
    fun `tapping a ready track plays the ready tracks from it, and a failed one doesn't play`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(CreateIntent.TrackClicked(TrackId("seed-3")))
            assertNull(playback.state.value.current)

            viewModel.onIntent(CreateIntent.TrackClicked(TrackId("seed-2")))
            runCurrent()

            assertEquals(ids("seed-1", "seed-2", "seed-4", "seed-6"), playback.state.value.queue.map { it.id })
            assertEquals(TrackId("seed-2"), playback.state.value.current?.id)
            assertEquals(TrackId("seed-2"), viewModel.state.value.playingTrackId)
        }

    @Test
    fun `play next in the menu queues the track after the one playing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(CreateIntent.TrackClicked(TrackId("seed-1")))

        viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-6")))
        viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.PlayNext))

        assertNull(viewModel.state.value.menuTrackId)
        assertEquals(ids("seed-1", "seed-6", "seed-2", "seed-4"), playback.state.value.queue.map { it.id })
        assertEquals(TrackId("seed-1"), playback.state.value.current?.id)
    }

    @Test
    fun `like in the menu likes a track, and again unlikes it`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
        viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Like))
        advanceTimeBy(1.seconds)
        assertEquals(true, viewModel.state.value.track("seed-2").isLiked)

        viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
        viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Like))
        advanceTimeBy(1.seconds)
        assertEquals(false, viewModel.state.value.track("seed-2").isLiked)
    }

    @Test
    fun `delete asks first, and only a confirmed delete removes the track`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
            viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Delete))
            assertEquals(TrackId("seed-2"), viewModel.state.value.deleting?.id)
            viewModel.onIntent(CreateIntent.DeleteDismissed)
            advanceTimeBy(1.seconds)
            assertNull(viewModel.state.value.deleting)
            assertEquals(6, viewModel.state.value.tracks.size)

            viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
            viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Delete))
            viewModel.onIntent(CreateIntent.DeleteConfirmed)
            advanceTimeBy(1.seconds)

            assertNull(viewModel.state.value.deleting)
            assertEquals(
                ids("seed-1", "seed-3", "seed-4", "seed-5", "seed-6"),
                viewModel.state.value.tracks.map {
                    it.row.id
                },
            )

            // A failed track has no menu; it is deleted from under its row, also after asking.
            viewModel.onIntent(CreateIntent.DeleteClicked(TrackId("seed-5")))
            assertEquals(TrackId("seed-5"), viewModel.state.value.deleting?.id)
            viewModel.onIntent(CreateIntent.DeleteConfirmed)
            advanceTimeBy(1.seconds)

            assertEquals(ids("seed-1", "seed-3", "seed-4", "seed-6"), viewModel.state.value.tracks.map { it.row.id })
        }

    @Test
    fun `retry composes a failed track again`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(CreateIntent.RetryClicked(TrackId("seed-5")))
        advanceTimeBy(1.seconds)

        assertEquals(TrackUi.Status.Generating, viewModel.state.value.track("seed-5").row.status)
    }

    @Test
    fun `share hands the track's title and prompt to the system`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
            viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Share))

            assertEquals(
                CreateEffect.ShareTrack("Sunrise over the Carpathians", "Cinematic folk with violin and choir"),
                awaitItem(),
            )
        }
    }

    @Test
    fun `what isn't there yet opens its coming soon sheet, and I want this thanks the user`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
            viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Download))
            assertEquals(CreateSheet.ComingSoon(ComingSoonFeature.Download), viewModel.state.value.sheet)

            viewModel.onIntent(CreateIntent.ComingSoonClicked(ComingSoonFeature.Muse))
            assertEquals(CreateSheet.ComingSoon(ComingSoonFeature.Muse), viewModel.state.value.sheet)

            viewModel.effects.test {
                viewModel.onIntent(CreateIntent.WantClicked)

                assertNull(viewModel.state.value.sheet)
                val toast = ToastUi(
                    title = UiText.Resource(R.string.create_toast_noted),
                    message = UiText.Resource(
                        R.string.create_toast_noted_text,
                        UiText.Resource(R.string.create_soon_muse),
                    ),
                )
                assertEquals(CreateEffect.ShowToast(toast), awaitItem())
            }
        }

    @Test
    fun `a failed send keeps the prompt and says why`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(CreateIntent.PromptChanged("Lo-fi beat"))
        tracks.failWith = AppError.Network

        viewModel.effects.test {
            viewModel.onIntent(CreateIntent.SendClicked)
            advanceTimeBy(1.seconds)

            val toast = ToastUi(
                title = UiText.Resource(R.string.create_toast_error),
                message = UiText.Resource(R.string.error_network),
                kind = StudioToastKind.Error,
            )
            assertEquals(CreateEffect.ShowToast(toast), awaitItem())
        }
        assertEquals("Lo-fi beat", viewModel.state.value.composer.prompt)
        assertEquals(false, viewModel.state.value.composer.isSending)
    }

    private fun TestScope.viewModel(): CreateViewModel {
        tracks = FailingTrackRepository(FakeTrackRepository(credits, FakeSessionRepository(), backgroundScope, clock))
        playback = FakePlaybackController(backgroundScope)
        val viewModel = CreateViewModel(
            tracks = tracks,
            credits = credits,
            styleCatalog = FakeStyleCatalog(latency = Duration.ZERO),
            playback = playback,
            generateTracks = GenerateTracks(tracks, credits),
            playNext = PlayNext(playback),
        )
        runCurrent()
        return viewModel
    }

    /** Types [prompt] and sends it, then waits for the request. */
    private fun TestScope.send(viewModel: CreateViewModel, prompt: String) {
        viewModel.onIntent(CreateIntent.PromptChanged(prompt))
        viewModel.onIntent(CreateIntent.SendClicked)
        advanceTimeBy(NETWORK)
        runCurrent()
    }

    private fun CreateState.track(id: String) = tracks.first { it.row.id == TrackId(id) }

    private fun ids(vararg values: String) = values.map(::TrackId)

    private companion object {
        /** Longer than the fakes take to answer the two requests of a 2× prompt. */
        val NETWORK = 1.seconds
    }
}
