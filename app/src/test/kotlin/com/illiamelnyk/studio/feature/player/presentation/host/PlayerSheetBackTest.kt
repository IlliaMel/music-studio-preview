package com.illiamelnyk.studio.feature.player.presentation.host
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.illiamelnyk.studio.core.ui.theme.StudioMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class PlayerSheetBackTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var sheet: PlayerSheetState
    private lateinit var scope: CoroutineScope

    @Test fun backPreviewKeepsItsHandlerAndCancellationRestoresThePlayer() {
        showPlayer()
        val (events, job) = previewBack()
        composeRule.runOnIdle { job.cancel() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle {
            assertTrue(sheet.isExpanded)
            assertTrue(sheet.transitionState.currentState)
            assertTrue(sheet.handlesBack)
            assertEquals(1f, sheet.expandedFraction, 0.001f)
        }
        events.close()
    }

    @Test fun completingBackCollapsesThePlayerAndReleasesItsHandler() {
        showPlayer()
        val (events, _) = previewBack()
        composeRule.runOnIdle { events.close() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle {
            assertFalse(sheet.isExpanded)
            assertFalse(sheet.transitionState.currentState)
            assertFalse(sheet.handlesBack)
            assertEquals(0f, sheet.expandedFraction, 0.001f)
        }
    }

    @Test fun backBeginningDuringOpeningKeepsPositionAndCancellationResumesOpening() {
        verifyInterruptedBack(opening = true, cancel = true)
    }

    @Test fun backBeginningDuringClosingKeepsPositionAndCancellationResumesClosing() {
        verifyInterruptedBack(opening = false, cancel = true)
    }

    @Test fun committedBackDuringOpeningEndsClosed() {
        verifyInterruptedBack(opening = true, cancel = false)
    }

    private fun verifyInterruptedBack(opening: Boolean, cancel: Boolean) {
        showPlayer()
        if (opening) {
            composeRule.runOnIdle { sheet.collapse() }
            composeRule.mainClock.advanceTimeBy(1000)
        }
        composeRule.runOnIdle { if (opening) sheet.expand() else sheet.collapse() }
        composeRule.mainClock.advanceTimeBy(96)
        val start = composeRule.runOnIdle { sheet.expandedFraction }
        assertTrue("Test must interrupt an active transition: $start", start > 0f && start < 1f)
        val events = Channel<BackEventCompat>(Channel.UNLIMITED)
        lateinit var job: Job
        composeRule.runOnIdle {
            job = scope.launch { sheet.collapseWithBack(events.receiveAsFlow()) }
            events.trySend(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.runOnIdle { assertEquals("Zero progress must not jump", start, sheet.expandedFraction, 0.015f) }
        composeRule.runOnIdle { events.trySend(BackEventCompat(0f, 0f, 0.8f, BackEventCompat.EDGE_LEFT)) }
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.runOnIdle { assertEquals(start * 0.76f, sheet.expandedFraction, 0.015f) }
        composeRule.runOnIdle { if (cancel) job.cancel() else events.close() }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.runOnIdle { assertEquals(if (cancel && opening) 1f else 0f, sheet.expandedFraction, 0.001f) }
        events.close()
    }

    private fun showPlayer() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            scope = rememberCoroutineScope()
            sheet = remember { PlayerSheetState(true, scope, StudioMotion()) }
            val transition = rememberTransition(sheet.transitionState, label = "backTest")
            val expansion by transition.animateFloat(label = "expansion") { if (it) 1f else 0f }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = expansion })
        }
        composeRule.mainClock.advanceTimeByFrame()
    }
    private fun previewBack(): Pair<Channel<BackEventCompat>, Job> {
        val events = Channel<BackEventCompat>(Channel.UNLIMITED)
        lateinit var job: Job
        composeRule.runOnIdle {
            job = scope.launch { sheet.collapseWithBack(events.receiveAsFlow()) }
            events.trySend(BackEventCompat(0f, 0f, 0.8f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle {
            assertTrue("Preview must move the player", sheet.expandedFraction < 1f)
            assertFalse(sheet.isExpanded)
            assertTrue("Preview must keep its back callback", sheet.handlesBack)
        }
        return events to job
    }
}
