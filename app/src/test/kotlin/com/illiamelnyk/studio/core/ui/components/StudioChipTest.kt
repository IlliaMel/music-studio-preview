package com.illiamelnyk.studio.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h640dp")
class StudioChipTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longTitleKeepsRemoveTouchableAtFont13() = assertLongTitleRemoval(1.3f)

    @Test
    fun longTitleKeepsRemoveTouchableAtFont2() = assertLongTitleRemoval(2f)

    @Test
    fun shortRemovableChipKeepsItsNaturalWidth() {
        composeRule.setContent {
            StudioTheme {
                Box(Modifier.width(280.dp).testTag("parent")) {
                    StudioChip("Muse", onClick = {}, onRemove = {}, modifier = Modifier.testTag("chip"))
                }
            }
        }
        val parent = composeRule.onNodeWithTag("parent").fetchSemanticsNode().boundsInRoot
        val chip = composeRule.onNodeWithTag("chip").fetchSemanticsNode().boundsInRoot
        assertTrue(chip.width < parent.width / 2)
    }

    private fun assertLongTitleRemoval(fontScale: Float) {
        val title = "A".repeat(80)
        var removed = 0
        var opened = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StudioTheme {
                    Box(Modifier.width(280.dp).testTag("parent")) {
                        StudioChip(title, onClick = { opened++ }, onRemove = { removed++ })
                    }
                }
            }
        }
        val remove = composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.ui_remove, title))
        remove.assertIsDisplayed()
        val parent = composeRule.onNodeWithTag("parent").fetchSemanticsNode().boundsInRoot
        val bounds = remove.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width > 0 && bounds.right <= parent.right)
        remove.performTouchInput { click() }
        assertEquals(1, removed)
        assertEquals(0, opened)
    }
}
