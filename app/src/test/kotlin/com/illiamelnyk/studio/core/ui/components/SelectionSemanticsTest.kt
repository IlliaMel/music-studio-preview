package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Components with a selection expose it to accessibility services, and only those. */
@RunWith(RobolectricTestRunner::class)
class SelectionSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `segmented control marks the selected option and moves the selection on click`() {
        val picked = mutableListOf<String>()
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                var selected by remember { mutableStateOf("Create") }
                StudioSegmentedControl(
                    options = listOf("Create", "Listen"),
                    selected = selected,
                    onSelect = {
                        picked += it
                        selected = it
                    },
                ) { it }
            }
        }

        composeRule.onNodeWithText("Create").assertIsSelected()
        composeRule.onNodeWithText("Listen").assertIsNotSelected()
        composeRule.onNodeWithText("Create").onParent()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))

        composeRule.onNodeWithText("Listen").performClick()

        assertEquals(listOf("Listen"), picked)
        composeRule.onNodeWithText("Listen").assertIsSelected()
        composeRule.onNodeWithText("Create").assertIsNotSelected()
    }

    @Test
    fun `filter chip exposes its selection and an action chip has none`() {
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                Column {
                    StudioFilterChip("All", selected = true, onClick = {})
                    StudioFilterChip("Liked", selected = false, onClick = {})
                    StudioChip("Styles", onClick = {}, leadingIcon = StudioIcons.AutoAwesome)
                }
            }
        }

        composeRule.onNodeWithText("All").assertIsSelected()
        composeRule.onNodeWithText("Liked").assertIsNotSelected()
        composeRule.onNodeWithText("Styles").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun `navigation bar marks the selected destination`() {
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                StudioNavigationBar(
                    items = listOf(
                        StudioNavigationItem("Create", StudioIcons.AutoAwesome, StudioIcons.AutoAwesomeFilled),
                        StudioNavigationItem("Listen", StudioIcons.Headphones, StudioIcons.HeadphonesFilled),
                    ),
                    selectedIndex = 1,
                    onSelect = {},
                )
            }
        }

        composeRule.onNodeWithText("Listen").assertIsSelected()
        composeRule.onNodeWithText("Create").assertIsNotSelected()
    }
}
