package com.illiamelnyk.studio.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

    private fun setThemedContent(content: @Composable () -> Unit) {
        composeRule.setContent { StudioTheme(darkTheme = true, content = content) }
    }

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private companion object {
        const val SHEET_TEXT = "Voice to Song"
        const val MENU_ITEM = "Play next"
        const val DRAG_DISTANCE_PX = 2_000f
        const val DRAG_MS = 300L
    }
}
