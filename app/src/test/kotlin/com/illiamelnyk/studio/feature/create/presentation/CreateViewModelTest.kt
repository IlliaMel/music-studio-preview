package com.illiamelnyk.studio.feature.create.presentation

import app.cash.turbine.test
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
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
import kotlinx.coroutines.CompletableDeferred
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
    private var notificationsEnabled = false
    private var failNotificationRead = false
    private var failNotificationWrite = false
    private val notificationChoice = object : NotificationChoice {
        override suspend fun hasAnswered(): Boolean {
            if (failNotificationRead) throw java.io.IOException("simulated preference read failure")
            return answered
        }
        override suspend fun isEnabled() = notificationsEnabled
        override suspend fun answer(enabled: Boolean) {
            if (failNotificationWrite) throw java.io.IOException("simulated preference write failure")
            answered = true
            notificationsEnabled = enabled
        }
    }
    private lateinit var tracks: FailingTrackRepository
    private lateinit var playback: FakePlaybackController
    private var submittedRequest: GenerationRequest? = null
    private var renameGate: CompletableDeferred<Outcome<Unit>>? = null
    private var renameCalls = 0

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
            SettingsUi(MusicModel.V2, lyricsMode = LyricsMode.Instrumental, duration = 3.minutes.toOptionUi())
        assertEquals(CreateSheet.Settings(draft), viewModel.state.value.sheet)
        assertNull(viewModel.state.value.composer.duration)

        // Closing the sheet drops the choices.
        viewModel.onIntent(CreateIntent.SheetDismissed)
        viewModel.onIntent(CreateIntent.SettingsClicked)
        val defaults =
            SettingsUi(MusicModel.V2_5, lyricsMode = LyricsMode.Auto, duration = null)
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
    fun `custom lyrics settings apply only on confirmation and require lyrics`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            viewModel.onIntent(CreateIntent.SettingsClicked)
            viewModel.onIntent(CreateIntent.SettingsCustomLyricsSelected)
            assertEquals(LyricsMode.Auto, viewModel.state.value.composer.lyricsMode)
            assertEquals(LyricsMode.Custom, (viewModel.state.value.sheet as CreateSheet.Settings).draft.lyricsMode)
            viewModel.onIntent(CreateIntent.SettingsApplied)
            viewModel.onIntent(CreateIntent.PromptChanged("A warm acoustic song"))
            assertEquals(LyricsMode.Custom, viewModel.state.value.composer.lyricsMode)
            assertEquals(false, viewModel.state.value.composer.canSend)
            viewModel.onIntent(CreateIntent.CustomLyricsChanged("Morning light"))
            assertEquals(true, viewModel.state.value.composer.canSend)
        }

    @Test
    fun `duration settings preserve custom lyrics`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(CreateIntent.CustomLyricsClicked)
        viewModel.onIntent(CreateIntent.CustomLyricsChanged("Morning light"))
        viewModel.onIntent(CreateIntent.SettingsClicked)
        viewModel.onIntent(CreateIntent.SettingsDurationSelected(3.minutes))
        viewModel.onIntent(CreateIntent.SettingsApplied)

        assertEquals(LyricsMode.Custom, viewModel.state.value.composer.lyricsMode)
        assertEquals("Morning light", viewModel.state.value.composer.customLyrics)
        assertEquals("3:00", viewModel.state.value.composer.duration?.label)
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
            SettingsUi(MusicModel.V2_5, lyricsMode = LyricsMode.Auto, duration = null)
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
    fun delayedRenameDoesNotCloseReplacementSheetAndIgnoresDoubleSave() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        renameGate = CompletableDeferred()
        vm.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
        vm.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Rename))
        vm.onIntent(CreateIntent.RenameChanged("Saved title"))
        vm.onIntent(CreateIntent.RenameConfirmed)
        vm.onIntent(CreateIntent.RenameConfirmed)
        runCurrent()
        assertEquals(1, renameCalls)
        vm.onIntent(CreateIntent.SheetDismissed)
        vm.onIntent(CreateIntent.SettingsClicked)
        val replacement = vm.state.value.sheet
        renameGate!!.complete(Outcome.Success(Unit))
        runCurrent()
        assertEquals(replacement, vm.state.value.sheet)
        assertEquals("Saved title", vm.state.value.track("seed-2").row.title)
    }

    @Test
    fun successfulOldRenameDoesNotDismissAnIdenticalReopenedRename() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        renameGate = CompletableDeferred()
        vm.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
        vm.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Rename))
        vm.onIntent(CreateIntent.RenameConfirmed)
        runCurrent()
        val original = vm.state.value.sheet
        vm.onIntent(CreateIntent.SheetDismissed)
        vm.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
        vm.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Rename))
        assertEquals(original, vm.state.value.sheet)
        renameGate!!.complete(Outcome.Success(Unit))
        runCurrent()
        assertEquals(original, vm.state.value.sheet)
        vm.onIntent(CreateIntent.RenameConfirmed)
        runCurrent()
        assertNull(vm.state.value.sheet)
        assertEquals(2, renameCalls)
    }

    @Test
    fun delayedRenamePreservesEditedOrReopenedRenameAndFailureCanRetry() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        renameGate = CompletableDeferred()
        vm.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
        vm.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Rename))
        vm.onIntent(CreateIntent.RenameChanged("First title"))
        vm.onIntent(CreateIntent.RenameConfirmed)
        runCurrent()
        vm.onIntent(CreateIntent.RenameChanged("Newer edit"))
        renameGate!!.complete(Outcome.Success(Unit))
        runCurrent()
        assertEquals("Newer edit", (vm.state.value.sheet as CreateSheet.Rename).title)
        renameGate = CompletableDeferred()
        vm.onIntent(CreateIntent.RenameConfirmed)
        runCurrent()
        vm.onIntent(CreateIntent.SheetDismissed)
        vm.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
        vm.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Rename))
        val reopened = vm.state.value.sheet
        renameGate!!.complete(Outcome.Failure(AppError.Network))
        runCurrent()
        assertEquals(reopened, vm.state.value.sheet)
        assertEquals(StudioToastKind.Error, (vm.effects.first() as CreateEffect.ShowToast).toast.kind)
        renameGate = null
        vm.onIntent(CreateIntent.RenameChanged("Retry title"))
        vm.onIntent(CreateIntent.RenameConfirmed)
        runCurrent()
        assertEquals(3, renameCalls)
        assertNull(vm.state.value.sheet)
        assertEquals("Retry title", vm.state.value.track("seed-2").row.title)
    }

    @Test
    fun `rename changes the row and Use prompt fills the composer`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
        viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Rename))
        viewModel.onIntent(CreateIntent.RenameChanged("  New name  "))
        viewModel.onIntent(CreateIntent.RenameConfirmed)
        runCurrent()
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
            assertEquals(true, notificationsEnabled)
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
                assertEquals(false, notificationsEnabled)
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
            viewModel.onIntent(CreateIntent.PromptChanged("Next track idea"))
            advanceTimeBy(NETWORK)
            runCurrent()
            assertEquals(7, viewModel.state.value.tracks.size)
            assertEquals("Original idea", viewModel.state.value.tracks.first().row.title)
            assertEquals("Next track idea", viewModel.state.value.composer.prompt)
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
            viewModel.onIntent(CreateIntent.TrackClicked(TrackId("seed-2")))
            assertEquals(TrackId("seed-2"), playback.state.value.current?.id)

            viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
            viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Delete))
            assertEquals(TrackId("seed-2"), viewModel.state.value.deleting?.id)
            viewModel.onIntent(CreateIntent.DeleteDismissed)
            advanceTimeBy(1.seconds)
            assertNull(viewModel.state.value.deleting)
            assertEquals(6, viewModel.state.value.tracks.size)
            assertEquals(TrackId("seed-2"), playback.state.value.current?.id)

            viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
            viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Delete))
            viewModel.onIntent(CreateIntent.DeleteConfirmed)
            advanceTimeBy(1.seconds)

            assertNull(viewModel.state.value.deleting)
            assertEquals(TrackId("seed-4"), playback.state.value.current?.id)
            assertEquals(ids("seed-1", "seed-4", "seed-6"), playback.state.value.queue.map { it.id })
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
                "seed-2",
                (awaitItem() as CreateEffect.ShareTrack).track?.id?.value,
            )
        }
    }

    @Test
    fun `what isn't there yet opens its coming soon sheet, and I want this thanks the user`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(CreateIntent.MoreClicked(TrackId("seed-2")))
            viewModel.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.Download))
            viewModel.effects.test {
                assertEquals("seed-2", (awaitItem() as CreateEffect.DownloadAudio).track.id.value)
            }

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

    @Test
    fun notificationPreferenceReadFailurePreservesSuccessfulGenerationAndPermitsAnotherSend() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            failNotificationRead = true
            viewModel.effects.test {
                send(viewModel, "Warm cinematic piano")
                assertEquals(CreateEffect.TracksStarted, awaitItem())
                val error = awaitItem() as CreateEffect.ShowToast
                assertEquals(UiText.Resource(R.string.error_preferences), error.toast.message)
                assertEquals(StudioToastKind.Error, error.toast.kind)
                assertEquals(7, credits.credits.first().remaining)
                assertEquals(TracksFilter.Drafts, viewModel.state.value.filter)
                assertEquals("", viewModel.state.value.composer.prompt)
                assertEquals(false, viewModel.state.value.composer.isSending)
                assertEquals(TrackUi.Status.Generating, viewModel.state.value.tracks.first().row.status)
                failNotificationRead = false
                send(viewModel, "Second warm piano idea")
                assertEquals(CreateEffect.TracksStarted, awaitItem())
                assertEquals(CreateSheet.Notify, viewModel.state.value.sheet)
                assertEquals(6, credits.credits.first().remaining)
            }
        }

    @Test
    fun notificationPreferenceWriteFailureKeepsPromptRetryableWithoutRequestingPermission() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            viewModel.effects.test {
                send(viewModel, "Warm cinematic piano")
                assertEquals(CreateEffect.TracksStarted, awaitItem())
                failNotificationWrite = true
                viewModel.onIntent(CreateIntent.NotificationAnswered(true))
                runCurrent()
                val error = awaitItem() as CreateEffect.ShowToast
                assertEquals(UiText.Resource(R.string.error_preferences), error.toast.message)
                assertEquals(CreateSheet.Notify, viewModel.state.value.sheet)
                assertEquals(false, answered)
                expectNoEvents()
                failNotificationWrite = false
                viewModel.onIntent(CreateIntent.NotificationAnswered(true))
                runCurrent()
                assertEquals(CreateEffect.RequestNotifications, awaitItem())
                assertEquals(true, answered)
                assertNull(viewModel.state.value.sheet)
                assertEquals(7, credits.credits.first().remaining)
            }
        }

    @Test
    fun replacingRemixWithRecordingGeneratesUsingOnlyTheNewReference() = runTest(mainDispatcherRule.testDispatcher) {
        val drafts = ComposerDraftStore { "test-owner" }
        val viewModel = viewModel(drafts)
        val original = tracks.observeMine().first().first()
        drafts.remix(original)
        runCurrent()
        assertEquals(original.title, viewModel.state.value.composer.referenceLabel)

        val uri = "content://recordings/new-reference"
        viewModel.onIntent(CreateIntent.ReferenceSelected(uri, "New recording"))
        assertEquals("New recording", viewModel.state.value.composer.referenceLabel)
        viewModel.effects.test {
            send(viewModel, "An original mellow arrangement")
            assertEquals(CreateEffect.TracksStarted, awaitItem())
        }

        val request = requireNotNull(submittedRequest)
        assertEquals(uri, request.referenceAudioUri)
        assertNull(request.remixTrackId)
        assertEquals(7, credits.credits.first().remaining)
    }

    @Test
    fun replacingRecordingWithRemixGeneratesUsingOnlyTheNewOriginal() = runTest(mainDispatcherRule.testDispatcher) {
        val drafts = ComposerDraftStore { "test-owner" }
        val viewModel = viewModel(drafts)
        viewModel.onIntent(CreateIntent.ReferenceSelected("content://recordings/old-reference", "Old recording"))
        val original = tracks.observeMine().first().first()
        drafts.remix(original)
        runCurrent()
        assertEquals(original.title, viewModel.state.value.composer.referenceLabel)

        viewModel.effects.test {
            send(viewModel, "An original mellow arrangement")
            assertEquals(CreateEffect.TracksStarted, awaitItem())
        }

        val request = requireNotNull(submittedRequest)
        assertEquals(original.id.value, request.remixTrackId)
        assertNull(request.referenceAudioUri)
        assertEquals(7, credits.credits.first().remaining)
    }

    @Test
    fun multipleSourcesSurviveNavigationRemoveIndividuallyAndSubmitInOrder() =
        runTest(mainDispatcherRule.testDispatcher) {
            val drafts = ComposerDraftStore { "test-owner" }
            val firstScreen = viewModel(drafts)
            val originals = tracks.observeMine().first().filter {
                it.status ==
                    com.illiamelnyk.studio.core.domain.track.TrackStatus.Ready
            }.take(3)
            drafts.remix(originals[0])
            drafts.remix(originals[1])
            runCurrent()
            firstScreen.onIntent(CreateIntent.PromptChanged("Keep this custom remix prompt"))
            drafts.remix(originals[2])
            drafts.remix(originals[0])
            runCurrent()
            assertEquals("Keep this custom remix prompt", firstScreen.state.value.composer.prompt)
            assertEquals(originals.map { it.id }, firstScreen.state.value.composer.remixSources.map { it.id })
            val restoredScreen = viewModel(drafts)
            assertEquals(originals.map { it.id }, restoredScreen.state.value.composer.remixSources.map { it.id })
            restoredScreen.onIntent(CreateIntent.RemixSourceRemoved(originals[1].id))
            runCurrent()
            restoredScreen.effects.test {
                send(restoredScreen, "Blend these original moods together")
                assertEquals(CreateEffect.TracksStarted, awaitItem())
            }
            assertEquals(listOf(originals[0].id.value, originals[2].id.value), submittedRequest!!.remixTrackIds)
            assertEquals(emptyList<Track>(), drafts.sources.value)
            assertEquals(7, credits.credits.first().remaining)
        }

    @Test
    fun failedSubmissionKeepsEveryNamedSourceForRetry() = runTest(mainDispatcherRule.testDispatcher) {
        val drafts = ComposerDraftStore { "test-owner" }
        val viewModel = viewModel(drafts)
        val originals = tracks.observeMine().first().filter {
            it.status ==
                com.illiamelnyk.studio.core.domain.track.TrackStatus.Ready
        }.take(2)
        originals.forEach(drafts::remix)
        runCurrent()
        tracks.failWith = AppError.Network
        viewModel.effects.test {
            send(viewModel, "Retry these two original ideas")
            assertEquals(StudioToastKind.Error, (awaitItem() as CreateEffect.ShowToast).toast.kind)
        }
        assertEquals(originals, drafts.sources.value)
        assertEquals(originals.map { it.id }, viewModel.state.value.composer.remixSources.map { it.id })
        assertEquals(8, credits.credits.first().remaining)
    }

    @Test
    fun recordingReferenceClearsAllRemixSourcesAndSourcesReplaceTheRecording() =
        runTest(mainDispatcherRule.testDispatcher) {
            val drafts = ComposerDraftStore { "test-owner" }
            val viewModel = viewModel(drafts)
            val originals = tracks.observeMine().first().filter {
                it.status ==
                    com.illiamelnyk.studio.core.domain.track.TrackStatus.Ready
            }.take(2)
            originals.forEach(drafts::remix)
            runCurrent()
            viewModel.onIntent(CreateIntent.ReferenceSelected("content://reference/new", "Fresh recording"))
            runCurrent()
            assertEquals(emptyList<Track>(), drafts.sources.value)
            assertEquals("Fresh recording", viewModel.state.value.composer.referenceLabel)
            assertEquals(0, viewModel.state.value.composer.remixSources.size)
            drafts.remix(originals[1])
            runCurrent()
            viewModel.effects.test {
                send(viewModel, "Remix just the newly selected source")
                assertEquals(CreateEffect.TracksStarted, awaitItem())
            }
            assertEquals(listOf(originals[1].id.value), submittedRequest!!.remixTrackIds)
            assertNull(submittedRequest!!.referenceAudioUri)
        }

    @Test
    fun backgroundDeletionThenNewOwnerCreatesAnEmptyComposerWithoutThePreviousPrivateSource() =
        runTest(mainDispatcherRule.testDispatcher) {
            var owner: String? = "owner-a"
            val drafts = ComposerDraftStore { owner }
            val first = viewModel(drafts)
            val privateSource = tracks.observeMine().first().first().copy(
                title = "Owner A private idea",
                prompt = "Owner A private musical prompt",
            )
            drafts.remix(privateSource)
            runCurrent()
            assertEquals(privateSource.title, first.state.value.composer.referenceLabel)
            owner = null
            owner = "owner-b"
            val second = viewModel(drafts)
            assertEquals(0, second.state.value.composer.remixSources.size)
            assertNull(second.state.value.composer.referenceLabel)
            assertEquals("", second.state.value.composer.prompt)
            assertEquals(emptyList<Track>(), drafts.sources.value)
        }

    private fun TestScope.viewModel(drafts: ComposerDraftStore = ComposerDraftStore { "test-owner" }): CreateViewModel {
        val fake = FakeTrackRepository(credits, FakeSessionRepository(), backgroundScope, clock)
        val capturing = object : TrackRepository by fake {
            override suspend fun rename(id: TrackId, title: String): Outcome<Unit> {
                renameCalls++
                val result = renameGate?.await() ?: Outcome.Success(Unit)
                return if (result is Outcome.Success) fake.rename(id, title) else result
            }

            override suspend fun create(request: GenerationRequest, variants: Int): Outcome<List<Track>> {
                submittedRequest = request
                if (request.referenceAudioUri != null && request.remixTrackId != null) {
                    return Outcome.Failure(AppError.InvalidReference)
                }
                return fake.create(request, variants)
            }
        }
        tracks = FailingTrackRepository(capturing)
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
            drafts = drafts,
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
