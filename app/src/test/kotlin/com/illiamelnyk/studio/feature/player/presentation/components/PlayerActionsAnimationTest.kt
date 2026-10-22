package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerActionsAnimationTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun unlikeDuringPopRestoresOriginalSize() = checkReversal(64)

    @Test fun unlikeDuringSpringRestoresOriginalSize() = checkReversal(208)

    private fun checkReversal(delay: Long) {
        val liked = mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            StudioTheme {
                PlayerActions(liked.value, {}, {}, { liked.value = !liked.value })
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val node = composeRule.onNode(hasText("Like") or hasText("Unlike"))
        val original = node.fetchSemanticsNode().boundsInRoot.height
        composeRule.runOnIdle { liked.value = true }
        composeRule.mainClock.advanceTimeBy(delay)
        composeRule.waitForIdle()
        assertTrue("Like must animate before reversal", node.fetchSemanticsNode().boundsInRoot.height > original)
        composeRule.runOnIdle { liked.value = false }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertEquals(original, node.fetchSemanticsNode().boundsInRoot.height, 0.5f)
    }
}
