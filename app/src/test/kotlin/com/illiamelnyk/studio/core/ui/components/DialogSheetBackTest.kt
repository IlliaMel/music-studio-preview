package com.illiamelnyk.studio.core.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DialogSheetBackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun resizingTheRecorderContentKeepsTheSheetOpen() {
        val tall = mutableStateOf(false)
        var dismissals = 0
        rule.setContent {
            StudioTheme {
                StudioBottomSheet(true, { dismissals++ }) {
                    Box(Modifier.height(if (tall.value) 320.dp else 100.dp)) { BasicText("Recorder") }
                }
            }
        }
        rule.onNodeWithText("Recorder").assertIsDisplayed()
        repeat(3) {
            rule.runOnIdle { tall.value = !tall.value }
            rule.onNodeWithText("Recorder").assertIsDisplayed()
        }
        assertEquals(0, dismissals)
    }

    @Test fun dialogSheetOwnsPredictiveGestureAndCancelDoesNotDismiss() {
        val visible = mutableStateOf(true)
        var dismissals = 0
        var fallback = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        rule.setContent {
            StudioTheme {
                Dialog(
                    onDismissRequest = { fallback++ },
                    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) {
                    dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    StudioBottomSheet(visible.value, {
                        dismissals++
                        visible.value = false
                    }) { BasicText("Profile sheet") }
                }
            }
        }
        rule.onNodeWithText("Profile sheet").assertIsDisplayed()
        assertNotSame(rule.activity.onBackPressedDispatcher, dispatcher)
        rule.mainClock.autoAdvance = false
        fun start() {
            rule.runOnUiThread {
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.6f, BackEventCompat.EDGE_LEFT))
            }
            repeat(6) {
                rule.mainClock.advanceTimeByFrame()
                rule.waitForIdle()
            }
        }
        start()
        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(0, dismissals)
        assertEquals(0, fallback)
        rule.onNodeWithText("Profile sheet").assertIsDisplayed()
        start()
        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(1, dismissals)
        assertEquals(0, fallback)
    }
}
