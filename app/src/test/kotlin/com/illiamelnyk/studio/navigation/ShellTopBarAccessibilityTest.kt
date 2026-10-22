package com.illiamelnyk.studio.navigation

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h850dp")
class ShellTopBarAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun ordinaryFontKeepsHeaderOnOneRow() = verify(1f, 393)

    @Test fun largeFontKeepsSearchAndLibraryVisible() = verify(2f, 393)

    @Test fun narrowHeaderKeepsSearchAndLibraryVisibleAtLargeFont() = verify(2f, 360)

    @Test fun extraNarrowHeaderKeepsSearchAndLibraryVisibleAtLargeFont() = verify(2f, 320)

    private fun verify(scale: Float, width: Int) {
        var libraryClicks = 0
        var searchClicks = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StudioTheme {
                    ShellTopBar(
                        selectedTab = TopLevelTab.Listen,
                        avatarSeed = "qa",
                        creditsLeft = 25,
                        onTabSelect = {},
                        onProfileClick = {},
                        onLibraryClick = { libraryClicks++ },
                        onSearchClick = { searchClicks++ },
                        modifier = Modifier.width(width.dp).testTag("header"),
                    )
                }
            }
        }
        val header = rule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val profile = rule.onNodeWithContentDescription("Profile").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val search = rule.onNodeWithContentDescription("Search").assertIsDisplayed()
        val searchBounds = search.fetchSemanticsNode().boundsInRoot
        assertTrue("Search stays inside header", searchBounds.left >= header.left && searchBounds.right <= header.right)
        search.performClick()
        assertEquals(1, searchClicks)
        run {
            val library = rule.onNodeWithContentDescription("Library").assertIsDisplayed()
            val bounds = library.fetchSemanticsNode().boundsInRoot
            assertTrue("Library stays inside header", bounds.left >= header.left && bounds.right <= header.right)
            assertTrue("Library stays within header height", bounds.top >= header.top && bounds.bottom <= header.bottom)
            if (scale == 1f) {
                assertEquals("Ordinary header remains one row", profile.center.y, bounds.center.y, 1f)
            }
            library.performClick()
            assertEquals(1, libraryClicks)
        }
    }
}
