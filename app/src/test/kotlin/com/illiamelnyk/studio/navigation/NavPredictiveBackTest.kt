package com.illiamelnyk.studio.navigation

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.illiamelnyk.studio.core.ui.theme.StudioMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavPredictiveBackTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val collection = Destination.Collection("predictive-test")
    private val stack = mutableStateListOf<NavKey>(Destination.Listen, collection)
    private var backs = 0

    @Test fun cancellingBackRestoresCollectionWithoutPopping() {
        showCollection()
        val original = bounds()
        preview()
        assertTrue("Gesture must move the collection", bounds().left > original.left + 1f)
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        settle()
        assertEquals(original.left, bounds().left, 1f)
        assertEquals(original.width, bounds().width, 1f)
        assertEquals(0, backs)
        assertEquals(listOf(Destination.Listen, collection), stack.toList())
        composeRule.onNodeWithTag(COLLECTION).assertIsDisplayed()
    }

    @Test fun completingBackPopsExactlyOnceAndShowsListen() {
        showCollection()
        val original = bounds()
        preview()
        assertTrue("Gesture must move before commit", bounds().left > original.left + 1f)
        assertEquals(0, backs)
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertEquals(1, backs)
        assertEquals(listOf(Destination.Listen), stack.toList())
        composeRule.onNodeWithTag(COLLECTION).assertDoesNotExist()
        composeRule.onNodeWithTag(LISTEN).assertIsDisplayed()
        pumpFrames(6)
        assertEquals("Settling must not pop twice", 1, backs)
    }

    private fun showCollection() {
        val transitions = ShellTransitions(StudioMotion(), LayoutDirection.Ltr)
        composeRule.setContent {
            NavDisplay(
                entries = stack.map { destination ->
                    NavEntry<NavKey>(
                        key = destination,
                        metadata = if (destination == Destination.Listen) transitions.tabRoot else emptyMap(),
                    ) {
                        Box(Modifier.fillMaxSize().testTag(if (destination == collection) COLLECTION else LISTEN))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                transitionSpec = transitions.push,
                popTransitionSpec = transitions.pop,
                predictivePopTransitionSpec = { _ -> transitions.pop(this) },
                onBack = {
                    backs++
                    if (stack.size > 1) stack.removeAt(stack.lastIndex)
                },
            )
        }
        composeRule.onNodeWithTag(COLLECTION).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
    }

    private fun preview() {
        composeRule.runOnUiThread {
            val dispatcher = composeRule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 0f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        pumpFrames(6)
    }

    private fun bounds(): Rect = composeRule.onNodeWithTag(COLLECTION).fetchSemanticsNode().boundsInRoot

    private fun pumpFrames(count: Int) {
        repeat(count) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    private fun settle() {
        pumpFrames(6)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        pumpFrames(6)
    }

    private companion object {
        const val COLLECTION = "predictiveCollection"
        const val LISTEN = "predictiveListen"
    }
}
