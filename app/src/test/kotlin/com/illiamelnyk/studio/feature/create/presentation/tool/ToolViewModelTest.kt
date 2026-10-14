package com.illiamelnyk.studio.feature.create.presentation.tool

import app.cash.turbine.test
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.ui.components.PromptPart
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.feature.create.domain.GenerateTracks
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature
import com.illiamelnyk.studio.feature.create.presentation.common.ToastUi
import com.illiamelnyk.studio.feature.create.presentation.common.toOptionUi
import com.illiamelnyk.studio.testing.FailingTrackRepository
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ToolViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val credits = FakeCreditsRepository()
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-14T09:00:00Z")
    }
    private lateinit var tracks: FailingTrackRepository

    @Test
    fun `a tool can't be used until every slot is filled`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel(Tool.Genreshift)
        assertEquals(false, viewModel.state.value.canUse)
        assertEquals(UiText.Resource(R.string.tool_fill_slots), viewModel.state.value.hint)

        viewModel.onIntent(ToolIntent.SlotClicked("to"))
        assertEquals(
            SlotSheetUi(
                slotId = "to",
                label = "New genre",
                options = persistentListOf(
                    "Drum and bass",
                    "Synthwave",
                    "Bossa nova",
                    "Orchestral",
                    "Afrobeats",
                    "Punk",
                ),
                selected = null,
            ),
            viewModel.state.value.slotSheet,
        )
        viewModel.onIntent(ToolIntent.SlotValueSelected("Punk"))

        assertNull(viewModel.state.value.slotSheet)
        assertEquals(PromptPart.Slot("to", "New genre", "Punk"), viewModel.state.value.template[3])
        assertEquals(false, viewModel.state.value.canUse)

        fill(viewModel, "from", "Jazz")

        assertEquals(true, viewModel.state.value.canUse)
        assertNull(viewModel.state.value.hint)
    }

    @Test
    fun `a slot opens with the value picked before`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel(Tool.Genreshift)
        fill(viewModel, "from", "Folk")

        viewModel.onIntent(ToolIntent.SlotClicked("from"))

        assertEquals("Folk", viewModel.state.value.slotSheet?.selected)
    }

    @Test
    fun `using the tool generates its filled prompt at the chosen length and leaves`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(Tool.Genreshift)
            fill(viewModel, "from", "Jazz")
            fill(viewModel, "to", "Punk")
            assertEquals(2.minutes.toOptionUi(), viewModel.state.value.duration)
            viewModel.onIntent(ToolIntent.DurationSelected(3.minutes.toOptionUi()))

            viewModel.effects.test {
                viewModel.onIntent(ToolIntent.UseToolClicked)
                assertEquals(true, viewModel.state.value.isSubmitting)
                advanceTimeBy(1.seconds)

                assertEquals(ToolEffect.Close, awaitItem())
            }
            val track = tracks.observeMine().first().first()
            assertEquals(
                "Take an existing Jazz song and completely transform it into Punk. Keep the melody, the mood " +
                    "and the emotional arc, but rebuild the rhythm, the instruments and the production so it " +
                    "sounds native to the new genre, with a strong hook in the chorus.",
                track.prompt,
            )
            assertEquals(3.minutes, track.duration)
            assertEquals(7, credits.credits.first().remaining)
        }

    @Test
    fun `a tool that needs a recording can't be used, and its row explains what is coming`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(Tool.VoiceToSong)
            fill(viewModel, "style", "Pop")

            assertEquals(false, viewModel.state.value.canUse)
            assertEquals(UiText.Resource(R.string.tool_needs_recording), viewModel.state.value.hint)
            assertEquals(1.minutes.toOptionUi(), viewModel.state.value.duration)

            viewModel.onIntent(ToolIntent.AddRecordingClicked)
            assertEquals(ComingSoonFeature.Voice, viewModel.state.value.comingSoon)

            viewModel.effects.test {
                viewModel.onIntent(ToolIntent.WantClicked)

                assertNull(viewModel.state.value.comingSoon)
                val toast = ToastUi(
                    title = UiText.Resource(R.string.create_toast_noted),
                    message = UiText.Resource(
                        R.string.create_toast_noted_text,
                        UiText.Resource(R.string.create_soon_voice),
                    ),
                )
                assertEquals(ToolEffect.ShowToast(toast), awaitItem())
            }
            viewModel.onIntent(ToolIntent.UseToolClicked)
            advanceTimeBy(1.seconds)
            assertEquals(6, tracks.observeMine().first().size)
        }

    @Test
    fun `without credits the tool can't be used and says why`() = runTest(mainDispatcherRule.testDispatcher) {
        repeat(FakeCreditsRepository.DAILY_LIMIT) { credits.trySpend() }
        val viewModel = viewModel(Tool.Unplugged)

        fill(viewModel, "genre", "Folk")
        fill(viewModel, "instrument", "Piano")

        assertEquals(false, viewModel.state.value.canUse)
        assertEquals(UiText.Resource(R.string.error_quota_exceeded), viewModel.state.value.hint)
    }

    @Test
    fun `a failed generation stays on the tool and says why`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel(Tool.Unplugged)
        fill(viewModel, "genre", "Folk")
        fill(viewModel, "instrument", "Piano")
        tracks.failWith = AppError.Network

        viewModel.effects.test {
            viewModel.onIntent(ToolIntent.UseToolClicked)
            advanceTimeBy(1.seconds)

            val toast = ToastUi(
                title = UiText.Resource(R.string.create_toast_error),
                message = UiText.Resource(R.string.error_network),
                kind = StudioToastKind.Error,
            )
            assertEquals(ToolEffect.ShowToast(toast), awaitItem())
        }
        assertEquals(false, viewModel.state.value.isSubmitting)
        assertEquals(true, viewModel.state.value.canUse)
    }

    private fun TestScope.viewModel(tool: Tool): ToolViewModel {
        tracks = FailingTrackRepository(FakeTrackRepository(credits, FakeSessionRepository(), backgroundScope, clock))
        val viewModel = ToolViewModel(tool, GenerateTracks(tracks, credits), credits)
        runCurrent()
        return viewModel
    }

    private fun fill(viewModel: ToolViewModel, slotId: String, value: String) {
        viewModel.onIntent(ToolIntent.SlotClicked(slotId))
        viewModel.onIntent(ToolIntent.SlotValueSelected(value))
    }
}
