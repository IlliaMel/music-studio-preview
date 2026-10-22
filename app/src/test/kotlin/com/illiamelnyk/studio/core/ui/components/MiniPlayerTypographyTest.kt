package com.illiamelnyk.studio.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiniPlayerTypographyTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun titleAndArtistFitInsideThePill() = verifyText(1f)

    @Test fun titleAndArtistFitInsideThePillWithDoubleFontSize() = verifyText(2f)

    private fun verifyText(fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StudioTheme {
                    Box(Modifier.width(360.dp)) {
                        MiniPlayer(
                            "Test track",
                            "Test artist",
                            "test",
                            false,
                            {},
                            {},
                            modifier = Modifier.testTag("pill"),
                        )
                    }
                }
            }
        }
        val pill = composeRule.onNodeWithTag("pill").fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithText(
            "Test track",
            useUnmergedTree = true,
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val artist = composeRule.onNodeWithText(
            "Test artist",
            useUnmergedTree = true,
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Title must remain inside the pill", title.top >= pill.top)
        assertTrue(
            "Artist $artist must remain above the pill's bottom edge $pill; title=$title",
            artist.bottom < pill.bottom,
        )
        assertTrue("Text lines must not overlap", title.bottom <= artist.top)
    }
}
