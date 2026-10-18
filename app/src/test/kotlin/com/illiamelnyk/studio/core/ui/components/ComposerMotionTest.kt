package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComposerMotionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun optionsAndInputKeepTheirSpacingThroughExpansion() {
        val expanded = mutableStateOf(false)
        var optionsBottom: Float? = null
        rule.setContent {
            StudioTheme {
                Box(Modifier.size(360.dp, 500.dp), contentAlignment = Alignment.BottomCenter) {
                    Composer(
                        "",
                        {},
                        {},
                        expanded.value,
                        "Prompt",
                        showSend = expanded.value,
                        options = {
                            StudioText(
                                "Auto",
                                StudioTheme.typography.bodyMedium,
                                modifier = Modifier.onGloballyPositioned {
                                    optionsBottom = it.positionInRoot().y + it.size.height
                                },
                            )
                        },
                    )
                }
            }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { expanded.value = true }
        val gaps = mutableListOf<Float>()
        repeat(12) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            val input = rule.onNodeWithText("Prompt", useUnmergedTree = true).fetchSemanticsNode()
            optionsBottom?.let { gaps += input.positionInRoot.y - it }
        }
        assertTrue("Must observe intermediate layout frames", gaps.size >= 8)
        gaps.forEach { assertEquals("Toolbar must track editor on every frame", gaps.first(), it, 1f) }
    }
}
