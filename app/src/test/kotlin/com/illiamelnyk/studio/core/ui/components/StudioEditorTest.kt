package com.illiamelnyk.studio.core.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudioEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun delayedStateEchoKeepsBurstInputAndAcceptsPresetAndClear() {
        val external = mutableStateOf("")
        var latest = ""
        compose.setContent {
            StudioTheme { StudioTextArea(external.value, { latest = it }, placeholder = "Lyrics") }
        }
        compose.onNodeWithText("Lyrics").performTextInput("Morning")
        compose.onNodeWithText("Morning").performTextInput(" light")
        compose.waitUntil { latest == "Morning light" }
        assertEquals("Morning light", latest)
        compose.runOnIdle { external.value = "Morning" }
        compose.onNodeWithText("Morning light").assertExists()
        compose.runOnIdle { external.value = "Morning light" }
        compose.runOnIdle { external.value = "Use this prompt" }
        compose.onNodeWithText("Use this prompt").assertExists()
        compose.runOnIdle { external.value = "" }
        compose.onNodeWithText("Lyrics").assertExists()
    }
}
