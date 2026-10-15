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
import com.illiamelnyk.studio.feature.create.domain.GenerateTracks
import com.illiamelnyk.studio.feature.create.domain.LyricsMode
import com.illiamelnyk.studio.feature.create.domain.NotificationChoice
import com.illiamelnyk.studio.feature.create.domain.PlayNext
import com.illiamelnyk.studio.feature.create.domain.Prompt
import com.illiamelnyk.studio.feature.create.domain.TrackRenames
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
    private var answered = false
    private val notificationChoice = object : NotificationChoice {
        override suspend fun hasAnswered() = answered
        override suspend fun answer() {
            answered = true
        }
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
    fun `two tracks per prompt need only one credit`() = runTest(mainDispatcherRule.testDispatcher) {
        repeat(FakeCreditsRepository.DAILY_LIMIT - 1) { credits.trySpend() }
        val viewModel = viewModel()
        viewModel.onIntent(CreateIntent.PromptChanged("Lo-fi beat"))

        viewModel.onIntent(CreateIntent.VariantsClicked)

        assertEquals(2, viewModel.state.value.composer.variants)
        assertEquals(true, viewModel.state.value.composer.canSend)
        assertNull(viewModel.state.value.composer.notice)

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
        assertEquals(7, credits.credits.first().remaining)
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
        val draft =
            SettingsUi(MusicModel.V2, instrumental = true, duration = 3.minutes.toOptionUi())
        assertEquals(CreateSheet.Settings(draft), viewModel.state.value.sheet)
        assertNull(viewModel.state.value.composer.duration)

        // Closing the sheet drops the choices.
        viewModel.onIntent(CreateIntent.SheetDismissed)
        viewModel.onIntent(CreateIntent.SettingsClicked)
        val defaults =
            SettingsUi(MusicModel.V2_5, instrumental = false, duration = null)
        assertEquals(CreateSheet.Settings(defaults), viewModel.state.value.sheet)

        viewModel.onIntent(CreateIntent.SettingsDurationSelected(3.minutes))
        viewModel.onIntent(CreateIntent.SettingsInstrumentalChanged(true))
        viewModel.onIntent(CreateIntent.SettingsApplied)

        assertNull(viewModel.state.value.sheet)
        assertEquals("3:00", viewModel.state.value.composer.duration?.label)
        send(viewModel, "Night drive")
        val track = tracks.observeMine().first().first()
        assertEquals(3.minutes, track.duration)
        assertEquals(true, track.instrumental)
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
            SettingsUi(MusicModel.V2_5, instrumental = false, duration = null)
        assertEquals(CreateSheet.Settings(defaults), viewModel.state.value.sheet)
        assertEquals("5:00", viewModel.state.value.composer.duration?.label)
    }

    @Test
    fun `lyrics mode is removable and styles are coming soon`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(CreateIntent.LyricsSelected(true))
        assertEquals(LyricsMode.Instrumental, viewModel.state.value.composer.lyricsMode)
        viewModel.onIntent(CreateIntent.InstrumentalRemoved)
        assertEquals(LyricsMode.Auto, viewModel.state.value.composer.lyricsMode)
        viewModel.onIntent(CreateIntent.StylesClicked)
        assertEquals(CreateSheet.ComingSoon(ComingSoonFeature.Styles), viewModel.state.value.sheet)
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
    fun `only a ready track asks to expand the player`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onIntent(CreateIntent.TrackClicked(TrackId("seed-3")))
            expectNoEvents()
            viewModel.onIntent(CreateIntent.TrackClicked(TrackId("seed-2")))
            assertEquals(CreateEffect.OpenPlayer, awaitItem())
        }
    }

    @Test
    fun `last credit opens quota and tapping its notice reopens it`() = runTest(mainDispatcherRule.testDispatcher) {
        repeat(7) { credits.trySpend() }
        val viewModel = viewModel()
        viewModel.onIntent(CreateIntent.VariantsClicked)
        send(viewModel, "Final variations")
        assertEquals(0, credits.credits.first().remaining)
        assertEquals(CreateSheet.Quota(8), viewModel.state.value.sheet)
        viewModel.onIntent(CreateIntent.SheetDismissed)
        viewModel.onIntent(CreateIntent.QuotaClicked)
        assertEquals(CreateSheet.Quota(8), viewModel.state.value.sheet)
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
    fun `rename changes the row and Use prompt fills the composer`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
        viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Rename))
        viewModel.onIntent(CreateIntent.RenameChanged("  New name  "))
        viewModel.onIntent(CreateIntent.RenameConfirmed)
        assertEquals("New name", viewModel.state.value.track("seed-2").row.title)
        viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
        viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.UsePrompt))
        assertEquals("Cinematic folk with violin and choir", viewModel.state.value.composer.prompt)
    }

    @Test
    fun `the notification answer is remembered and permission is requested only for Notify me`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            send(viewModel, "First track")
            assertEquals(CreateSheet.Notify, viewModel.state.value.sheet)
            viewModel.effects.test {
                assertEquals(CreateEffect.TracksStarted, awaitItem())
                viewModel.onIntent(CreateIntent.NotificationAnswered(true))
                runCurrent()
                assertEquals(CreateEffect.RequestNotifications, awaitItem())
            }
            assertEquals(true, answered)
            send(viewModel, "Second track")
            assertNull(viewModel.state.value.sheet)
        }

    @Test
    fun `declining notifications is remembered without requesting permission`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            viewModel.effects.test {
                send(viewModel, "First idea")
                assertEquals(CreateEffect.TracksStarted, awaitItem())
                viewModel.onIntent(CreateIntent.NotificationAnswered(false))
                runCurrent()
                assertNull(viewModel.state.value.sheet)
                assertEquals(true, answered)
                expectNoEvents()
                send(viewModel, "Second idea")
                assertEquals(CreateEffect.TracksStarted, awaitItem())
                assertNull(viewModel.state.value.sheet)
                expectNoEvents()
            }
        }

    @Test
    fun `repeated send while starting creates one track and keeps a newly typed draft`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            viewModel.onIntent(CreateIntent.PromptChanged("Original idea"))
            viewModel.onIntent(CreateIntent.SendClicked)
            viewModel.onIntent(CreateIntent.SendClicked)
            viewModel.onIntent(CreateIntent.PromptChanged("Next idea"))
            advanceTimeBy(NETWORK)
            runCurrent()
            assertEquals(7, viewModel.state.value.tracks.size)
            assertEquals("Original idea", viewModel.state.value.tracks.first().row.title)
            assertEquals("Next idea", viewModel.state.value.composer.prompt)
            assertEquals(true, viewModel.state.value.composer.canSend)
            assertEquals(7, credits.credits.first().remaining)
        }

    @Test
    fun `quota failure opens quota sheet and preserves prompt`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        tracks.failWith = AppError.QuotaExceeded
        send(viewModel, "Keep this idea")
        assertEquals(CreateSheet.Quota(8), viewModel.state.value.sheet)
        assertEquals("Keep this idea", viewModel.state.value.composer.prompt)
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
            clock = clock,
            notificationChoice = notificationChoice,
            renames = TrackRenames(),
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
