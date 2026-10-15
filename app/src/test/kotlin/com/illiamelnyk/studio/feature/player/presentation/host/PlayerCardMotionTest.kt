package com.illiamelnyk.studio.feature.player.presentation.host

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.illiamelnyk.studio.core.ui.theme.StudioMotion
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class PlayerCardMotionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rootDistancePastHalfDismissesMovingCardWithoutEndingFling() = dragPastHalf(32, true)

    @Test
    fun fastRootDistancePastHalfDismissesWithBatchedEventsWithoutEndingFling() = dragPastHalf(10, false)

    private fun dragPastHalf(eventDelay: Long, renderEachMove: Boolean) {
        lateinit var sheet: PlayerSheetState
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val scope = rememberCoroutineScope()
            sheet = remember { PlayerSheetState(true, scope, StudioMotion()) }
            val transition = rememberTransition(sheet.transitionState, label = "testPlayer")
            val host = remember { PlayerHostScope(sheet, transition) }
            StudioTheme(darkTheme = true) {
                Box(
                    Modifier.fillMaxSize().testTag("motionRoot").onGloballyPositioned {
                        val bounds = it.boundsInRoot()
                        host.hostBounds = bounds
                        host.miniBounds = Rect(16f, bounds.height - 100f, bounds.width - 16f, bounds.height)
                        sheet.dragDistance = bounds.height
                    },
                ) {
                    PlayerCardMotion(host) { modifier ->
                        Box(modifier.background(Color.Blue))
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        val root = composeRule.onNodeWithTag("motionRoot")
        val size = root.fetchSemanticsNode().size
        val x = size.width * 0.5f
        val start = size.height * 0.19f
        val distance = size.height * 0.74f
        if (renderEachMove) {
            root.performTouchInput { down(Offset(x, start)) }
            repeat(24) { step ->
                root.performTouchInput {
                    moveTo(Offset(x, start + distance * (step + 1) / 24), delayMillis = eventDelay)
                }
                composeRule.mainClock.advanceTimeBy(eventDelay)
                composeRule.waitForIdle()
            }
            composeRule.runOnIdle {
                assertTrue("Drag must exceed the dismissal threshold", sheet.expandedFraction < 0.5f)
            }
            root.performTouchInput {
                moveTo(Offset(x, start + distance), delayMillis = 250)
                up()
            }
        } else {
            root.performTouchInput {
                down(Offset(x, start))
                repeat(24) { step ->
                    moveTo(Offset(x, start + distance * (step + 1) / 24), delayMillis = eventDelay)
                }
                moveTo(Offset(x, start + distance), delayMillis = 250)
                up()
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle {
            assertFalse("A completed drag must settle collapsed without relying on fling velocity", sheet.isExpanded)
            assertFalse(sheet.transitionState.currentState)
        }
    }
}
