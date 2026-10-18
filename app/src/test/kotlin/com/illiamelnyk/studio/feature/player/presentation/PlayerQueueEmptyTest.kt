package com.illiamelnyk.studio.feature.player.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerQueueContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerQueueEmptyTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyQueueExplainsRepeatAndAutoplayWithoutContradictoryHeading() {
        val repeat = mutableStateOf(false)
        val autoplay = mutableStateOf(false)
        rule.setContent {
            StudioTheme {
                PlayerQueueContent(
                    PlayerPreviewData.glassHarbor, persistentListOf(), false, repeat.value,
                    {}, {}, {}, {}, {}, { _, _ -> }, {}, autoplay = autoplay.value,
                )
            }
        }
        rule.onNodeWithText("Playing next").assertDoesNotExist()
        rule.onNodeWithText("No upcoming tracks").assertIsDisplayed()
        rule.onNodeWithText("Add tracks to keep listening after this one.").assertIsDisplayed()
        rule.runOnIdle { autoplay.value = true }
        rule.onNodeWithText("Autoplay is on. More music will be added when available.").assertIsDisplayed()
        rule.runOnIdle { repeat.value = true }
        rule.onNodeWithText("Repeat is on. Playback will return to the start of the queue.").assertIsDisplayed()
        rule.onNodeWithText("Autoplay is on. More music will be added when available.").assertDoesNotExist()
    }
}
