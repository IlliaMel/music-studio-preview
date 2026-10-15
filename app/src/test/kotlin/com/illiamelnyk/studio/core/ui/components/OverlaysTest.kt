package com.illiamelnyk.studio.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.common.OverlaySheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class OverlaysTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var visible by mutableStateOf(true)
    private var dismissals = 0

    @Test
    fun `bottom sheet closes when the scrim is tapped`() {
        showSheet()

        // The top of the screen is scrim; the sheet sits at the bottom.
        composeRule.onNodeWithContentDescription(string(R.string.ui_close_sheet))
            .performTouchInput { click(Offset(centerX, top + height / 10)) }

        assertEquals(1, dismissals)
        composeRule.onNodeWithText(SHEET_TEXT).assertDoesNotExist()
    }

    @Test
    fun `bottom sheet closes on back`() {
        showSheet()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(1, dismissals)
        composeRule.onNodeWithText(SHEET_TEXT).assertDoesNotExist()
    }

    @Test
    fun `bottom sheet dragged down closes and leaves nothing behind to block touches`() {
        showSheet()

        composeRule.onNodeWithText(SHEET_TEXT).performTouchInput {
            swipe(start = center, end = center + Offset(0f, DRAG_DISTANCE_PX), durationMillis = DRAG_MS)
        }

        assertEquals(1, dismissals)
        composeRule.onNodeWithText(SHEET_TEXT).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.ui_close_sheet)).assertDoesNotExist()
    }

    @Test
    fun `bottom sheet stays open when its content is tapped`() {
        showSheet()

        composeRule.onNodeWithText(SHEET_TEXT).performClick()

        assertEquals(0, dismissals)
        composeRule.onNodeWithText(SHEET_TEXT).assertExists()
    }

    @Test
    fun `context menu closes on a tap outside it`() {
        setThemedContent {
            Box {
                StudioContextMenu(expanded = visible, onDismissRequest = ::dismiss) {
                    ContextMenuItem(MENU_ITEM, onClick = {})
                }
            }
        }
        composeRule.onNodeWithText(MENU_ITEM).assertExists()

        // The popup's window is a little larger than the menu, for its shadow: its corner is outside.
        composeRule.onNode(isPopup()).performTouchInput { click(Offset(1f, 1f)) }

        assertEquals(1, dismissals)
        composeRule.onNodeWithText(MENU_ITEM).assertDoesNotExist()
    }

    @Test
    fun `context menu leaves closing after an item to the item's action`() {
        var clicks = 0
        setThemedContent {
            Box {
                StudioContextMenu(expanded = visible, onDismissRequest = ::dismiss) {
                    ContextMenuItem(MENU_ITEM, onClick = { clicks++ })
                    ContextMenuDivider()
                }
            }
        }

        composeRule.onNodeWithText(MENU_ITEM).performClick()

        assertEquals(1, clicks)
        assertEquals(0, dismissals)
        composeRule.onNodeWithText(MENU_ITEM).assertExists()
    }

    @Test
    fun `modal sheet keeps its content during exit then releases its window`() {
        setThemedContent {
            OverlaySheet(visible = visible, onDismissRequest = ::dismiss) { BasicText(SHEET_TEXT) }
        }
        composeRule.onNodeWithText(SHEET_TEXT).assertExists()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithContentDescription(string(R.string.ui_close_sheet)).performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.waitForIdle()

        assertEquals(1, dismissals)
        composeRule.onNodeWithText(SHEET_TEXT).assertExists()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(SHEET_TEXT).assertDoesNotExist()
    }

    @Test
    fun `optional sheet close button dismisses without requiring a drag`() {
        setThemedContent {
            Box(Modifier.fillMaxSize()) {
                StudioBottomSheet(visible, onDismissRequest = ::dismiss, showCloseButton = true) {
                    BasicText(SHEET_TEXT)
                }
            }
        }

        composeRule.onNodeWithContentDescription(string(R.string.ui_close)).performClick()

        assertEquals(1, dismissals)
        composeRule.onNodeWithText(SHEET_TEXT).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w320dp-h320dp")
    fun `long context menu remains scrollable on a small screen with large text`() {
        var clicks = 0
        setThemedContent(fontScale = 2f) {
            Box {
                StudioContextMenu(expanded = visible, onDismissRequest = ::dismiss) {
                    repeat(20) { index -> ContextMenuItem("Action $index", onClick = { clicks++ }) }
                }
            }
        }

        composeRule.onNodeWithText("Action 19").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(1, clicks)
        assertEquals(0, dismissals)
    }

    @Test
    @Config(qualifiers = "w320dp-h320dp")
    fun `confirmation remains reachable when long text fills a short screen`() {
        var confirmed = false
        setThemedContent(fontScale = 2f) {
            StudioConfirmDialog(
                title = "Delete account?",
                message = List(10) { "All tracks and recordings will be removed." }.joinToString(" "),
                confirmLabel = "Delete account",
                dismissLabel = "Keep account",
                onConfirm = { confirmed = true },
                onDismissRequest = ::dismiss,
            )
        }

        composeRule.onNodeWithText("Delete account").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(true, confirmed)
        assertEquals(0, dismissals)
    }

    @Test
    @Config(qualifiers = "w320dp-h320dp")
    fun `coming soon action stays reachable below long sheet content`() {
        var clicks = 0
        setThemedContent(fontScale = 2f) {
            Box(Modifier.fillMaxSize()) {
                StudioBottomSheet(visible, onDismissRequest = ::dismiss) {
                    ComingSoonSheetContent(
                        title = "Autoplay",
                        description = "More music for your queue.",
                        steps = List(8) { "Choose music and continue listening." },
                        actionLabel = "Close",
                        onAction = { clicks++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Close").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(1, clicks)
        assertEquals(0, dismissals)
    }

    private fun showSheet() {
        setThemedContent {
            Box(Modifier.fillMaxSize()) {
                StudioBottomSheet(visible = visible, onDismissRequest = ::dismiss) {
                    BasicText(SHEET_TEXT)
                }
            }
        }
        composeRule.onNodeWithText(SHEET_TEXT).assertExists()
    }

    private fun dismiss() {
        dismissals++
        visible = false
    }

    private fun setThemedContent(fontScale: Float = 1f, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StudioTheme(darkTheme = true, content = content)
            }
        }
    }

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private companion object {
        const val SHEET_TEXT = "Voice to Song"
        const val MENU_ITEM = "Play next"
        const val DRAG_DISTANCE_PX = 2_000f
        const val DRAG_MS = 300L
    }
}
