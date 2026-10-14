package com.illiamelnyk.studio.core.ui.components

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudioButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `enabled button reports clicks`() {
        var clicks = 0
        composeRule.setContent {
            StudioTheme(darkTheme = true) { StudioButton("Generate", onClick = { clicks++ }) }
        }

        composeRule.onNodeWithText("Generate").assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `disabled button is not clickable`() {
        var clicks = 0
        composeRule.setContent {
            StudioTheme(darkTheme = true) { StudioButton("Use tool", onClick = { clicks++ }, enabled = false) }
        }

        composeRule.onNodeWithText("Use tool").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun `loading button is not clickable and reports progress`() {
        var clicks = 0
        composeRule.setContent {
            StudioTheme(darkTheme = true) { StudioButton("Creating", onClick = { clicks++ }, loading = true) }
        }

        composeRule.onNodeWithText("Creating")
            .assertIsNotEnabled()
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .performClick()

        assertEquals(0, clicks)
    }
}
