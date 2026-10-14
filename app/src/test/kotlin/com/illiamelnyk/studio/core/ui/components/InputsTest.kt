package com.illiamelnyk.studio.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InputsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `checkbox toggles on every tap`() {
        setThemedContent {
            var checked by remember { mutableStateOf(false) }
            StudioCheckbox(checked, onCheckedChange = { checked = it }, label = "Send me updates")
        }
        val checkbox = composeRule.onNodeWithText("Send me updates")

        checkbox.assertIsOff()
        checkbox.performClick()
        checkbox.assertIsOn()
        checkbox.performClick()
        checkbox.assertIsOff()
    }

    @Test
    fun `text field takes typed text and announces its error`() {
        setThemedContent {
            var name by remember { mutableStateOf("") }
            StudioTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Track name",
                errorText = if (name.length > MAX_NAME) "Up to $MAX_NAME characters" else null,
            )
        }
        val field = composeRule.onNode(hasSetTextAction())
        field.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))

        field.performTextInput("Night Drive Through Kyiv")

        field.assertTextEquals("Night Drive Through Kyiv", includeEditableText = true)
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Up to $MAX_NAME characters"))
        composeRule.onNodeWithText("Up to $MAX_NAME characters").assertExists()
    }

    @Test
    fun `composer sends only when there is text within the limit`() {
        var sent = 0
        setThemedContent {
            var prompt by remember { mutableStateOf("") }
            Composer(
                value = prompt,
                onValueChange = { prompt = it },
                onSend = { sent++ },
                expanded = false,
                placeholder = "Describe a song",
                characterLimit = PROMPT_LIMIT,
            )
        }
        val send = composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.ui_send))
        send.assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextInput("A slow beat")
        send.assertIsEnabled()
        send.performClick()
        assertEquals(1, sent)

        composeRule.onNode(hasSetTextAction()).performTextInput(" for a rainy night drive")
        send.assertIsNotEnabled()
    }

    private fun setThemedContent(content: @Composable () -> Unit) {
        composeRule.setContent { StudioTheme(darkTheme = true, content = content) }
    }

    private companion object {
        const val MAX_NAME = 12
        const val PROMPT_LIMIT = 20
    }
}
