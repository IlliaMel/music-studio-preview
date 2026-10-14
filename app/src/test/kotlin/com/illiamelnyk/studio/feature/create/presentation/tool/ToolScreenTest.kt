package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.Tool
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ToolScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val intents = mutableListOf<ToolIntent>()

    @Test
    fun `an empty slot opens its options, and Use tool waits until it is filled`() {
        show(ToolState(Tool.Genreshift).copy(hint = UiText.Resource(R.string.tool_fill_slots)))

        composeRule.onNodeWithText(string(R.string.tool_fill_slots)).assertExists()
        composeRule.onNodeWithText(string(R.string.tool_use)).assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("New genre").performClick()

        assertEquals(ToolIntent.SlotClicked("to"), intents.last())
    }

    @Test
    fun `a ready tool is used with the button`() {
        val filled = Tool.Unplugged.template.toParts(mapOf("genre" to "Folk", "instrument" to "Piano"))
        show(ToolState(Tool.Unplugged).copy(template = filled, canUse = true))

        composeRule.onNodeWithText(string(R.string.tool_use)).assertIsEnabled().performClick()

        assertEquals(ToolIntent.UseToolClicked, intents.last())
    }

    @Test
    fun `a tool that needs a recording explains it from its row`() {
        show(ToolState(Tool.VoiceToSong).copy(hint = UiText.Resource(R.string.tool_needs_recording)))

        composeRule.onNodeWithText(string(R.string.tool_add_voice)).performClick()

        assertEquals(ToolIntent.AddRecordingClicked, intents.last())
        composeRule.onNodeWithText(string(R.string.tool_use)).assertIsNotEnabled()
    }

    private fun show(state: ToolState) {
        composeRule.setContent {
            StudioTheme(darkTheme = true) { ToolScreen(state = state, onIntent = { intents += it }) }
        }
    }

    private fun string(id: Int): String = composeRule.activity.getString(id)
}
