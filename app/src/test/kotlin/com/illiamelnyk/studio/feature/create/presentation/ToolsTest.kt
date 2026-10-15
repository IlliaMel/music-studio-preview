package com.illiamelnyk.studio.feature.create.presentation

import app.cash.turbine.test
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.tools.ToolsEffect
import com.illiamelnyk.studio.feature.create.presentation.tools.ToolsFilter
import com.illiamelnyk.studio.feature.create.presentation.tools.ToolsIntent
import com.illiamelnyk.studio.feature.create.presentation.tools.ToolsViewModel
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ToolsTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test
    fun `catalog filters expose six studio tools and four featured tools`() = runTest(dispatcher.testDispatcher) {
        val viewModel = ToolsViewModel()
        assertEquals(6, viewModel.state.value.tools.size)
        viewModel.onIntent(ToolsIntent.FilterSelected(ToolsFilter.Featured))
        assertEquals(
            listOf(Tool.VoiceToSong, Tool.Genreshift, Tool.SampleToSong, Tool.Unplugged),
            viewModel.state.value.tools.map {
                it.tool
            },
        )
        viewModel.onIntent(ToolsIntent.FilterSelected(ToolsFilter.Studio))
        assertEquals(6, viewModel.state.value.tools.size)
        viewModel.effects.test {
            viewModel.onIntent(ToolsIntent.ToolClicked(Tool.HumToSong))
            assertEquals(ToolsEffect.OpenTool(Tool.HumToSong), awaitItem())
        }
    }
}
