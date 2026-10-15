package com.illiamelnyk.studio.feature.create.presentation.tool

import app.cash.turbine.test
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.ui.components.PromptPart
import com.illiamelnyk.studio.feature.create.domain.GenerateTracks
import com.illiamelnyk.studio.feature.create.domain.RecordedAudio
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ToolViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `all six tools wait for recording even when every slot is filled`() =
        runTest(mainDispatcherRule.testDispatcher) {
            assertEquals(6, Tool.entries.size)
            Tool.entries.forEach { tool ->
                val viewModel = ToolViewModel(tool)
                tool.template.slots.forEach { slot ->
                    viewModel.onIntent(ToolIntent.SlotClicked(slot.id))
                    viewModel.onIntent(ToolIntent.SlotValueSelected(slot.options.first()))
                }
                assertEquals(false, viewModel.state.value.canUse)
                assertEquals(UiText.Resource(R.string.tool_needs_recording), viewModel.state.value.hint)
                viewModel.effects.test {
                    viewModel.onIntent(ToolIntent.UseToolClicked)
                    expectNoEvents()
                }
            }
        }

    @Test
    fun `a slot retains its selected value and closes the sheet`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = ToolViewModel(Tool.Genreshift)
        viewModel.onIntent(ToolIntent.SlotClicked("to"))
        viewModel.onIntent(ToolIntent.SlotValueSelected("Punk"))
        assertNull(viewModel.state.value.slotSheet)
        assertTrue(viewModel.state.value.template.contains(PromptPart.Slot("to", "New genre", "Punk")))
        viewModel.onIntent(ToolIntent.SlotClicked("to"))
        assertEquals("Punk", viewModel.state.value.slotSheet?.selected)
    }

    @Test
    fun `every tool exposes audio controls and only lengths up to three minutes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            Tool.entries.forEach { tool ->
                val viewModel = ToolViewModel(tool)
                assertTrue(viewModel.state.value.durations.all { it.duration <= 3.minutes })
                viewModel.onIntent(ToolIntent.AddRecordingClicked)
                assertTrue(viewModel.state.value.recordingOpen)
                val values = tool.template.slots.associate { it.id to it.options.first() }
                val prompt = tool.template.fill(values).orEmpty()
                assertTrue(prompt.length in 10..2000)
            }
        }

    @Test
    fun `a filled tool submits its reference once and closes after acceptance`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val credits = FakeCreditsRepository()
            val fake = FakeTrackRepository(credits, FakeSessionRepository(), backgroundScope, Clock.System)
            var submitted: GenerationRequest? = null
            var calls = 0
            val tracks = object : TrackRepository by fake {
                override suspend fun create(request: GenerationRequest, variants: Int): Outcome<List<Track>> {
                    calls++
                    submitted = request
                    return fake.create(request, variants)
                }
            }
            val viewModel = ToolViewModel(Tool.VoiceToSong, GenerateTracks(tracks, credits), credits)
            runCurrent()
            viewModel.onIntent(ToolIntent.SlotClicked("style"))
            viewModel.onIntent(ToolIntent.SlotValueSelected("Acoustic"))
            viewModel.onIntent(
                ToolIntent.RecordingSelected(RecordedAudio("file:///reference.wav", "Reference", 30_000)),
            )
            assertTrue(viewModel.state.value.canUse)
            viewModel.effects.test {
                viewModel.onIntent(ToolIntent.UseToolClicked)
                viewModel.onIntent(ToolIntent.UseToolClicked)
                assertEquals(ToolEffect.Close, awaitItem())
            }
            assertEquals(1, calls)
            assertEquals("file:///reference.wav", submitted?.referenceAudioUri)
            assertEquals(90.seconds, submitted?.duration)
            assertTrue(submitted?.prompt?.contains("Acoustic") == true)
        }
}
