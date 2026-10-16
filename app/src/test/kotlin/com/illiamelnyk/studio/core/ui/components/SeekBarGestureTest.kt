package com.illiamelnyk.studio.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeekBarGestureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun verticalSwipeStartingOnSeekBarScrollsParentWithoutSeeking() {
        lateinit var scroll: ScrollState
        val seeks = mutableListOf<Float>()
        composeRule.setContent {
            StudioTheme {
                scroll = rememberScrollState()
                Column(Modifier.width(280.dp).height(240.dp).verticalScroll(scroll)) {
                    Spacer(Modifier.height(80.dp))
                    SeekBar(progress = { 0.25f }, onSeek = { seeks += it }, elapsed = "0:05", remaining = "-0:15")
                    Spacer(Modifier.height(600.dp))
                }
            }
        }
        val bar = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        bar.performTouchInput {
            swipe(center, Offset(center.x, center.y - 70f), durationMillis = 400)
        }
        composeRule.runOnIdle {
            assertTrue("A vertical drag on the seek area must scroll its parent", scroll.value > 0)
            assertTrue("A vertical drag must never submit a seek", seeks.isEmpty())
        }
    }

    @Test
    fun horizontalDragAndTapEachSeekOnceInLtr() = assertHorizontalAndTap(LayoutDirection.Ltr)

    @Test
    fun horizontalDragAndTapEachSeekOnceInRtl() = assertHorizontalAndTap(LayoutDirection.Rtl)

    private fun assertHorizontalAndTap(direction: LayoutDirection) {
        val seeks = mutableListOf<Float>()
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                StudioTheme {
                    SeekBar(
                        progress = { 0.25f },
                        onSeek = { seeks += it },
                        elapsed = "0:05",
                        remaining = "-0:15",
                        modifier = Modifier.width(280.dp),
                    )
                }
            }
        }
        val bar = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        bar.performTouchInput {
            swipe(Offset(width * 0.2f, center.y), Offset(width * 0.8f, center.y), durationMillis = 400)
        }
        composeRule.runOnIdle {
            assertEquals(1, seeks.size)
            assertEquals(if (direction == LayoutDirection.Ltr) 0.8f else 0.2f, seeks.single(), 0.05f)
        }
        bar.performTouchInput { click(Offset(width * 0.6f, center.y)) }
        composeRule.runOnIdle {
            assertEquals(2, seeks.size)
            assertEquals(if (direction == LayoutDirection.Ltr) 0.6f else 0.4f, seeks.last(), 0.05f)
        }
    }
}
