package com.illiamelnyk.studio.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
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
class ComposerAccessibilityTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun sendRemainsVisibleAndClickableOnNarrowScreen() = verifySend(1f)

    @Test fun sendRemainsVisibleAndClickableWithDoubleFontSize() = verifySend(2f)

    @Test fun ordinaryFontKeepsActionsOnOneRowWithPhoneScreenPadding() =
        verifySend(1f, widthDp = 393, externalPadding = true, sameRow = true)

    private fun verifySend(
        fontScale: Float,
        widthDp: Int = 360,
        externalPadding: Boolean = false,
        sameRow: Boolean = false,
    ) {
        var sends = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StudioTheme {
                    Box(
                        Modifier.width(
                            widthDp.dp,
                        ).testTag("composerBounds").padding(horizontal = if (externalPadding) 16.dp else 0.dp),
                    ) {
                        Composer(
                            value = "Instrumental sunset",
                            onValueChange = {},
                            onSend = { sends++ },
                            expanded = true,
                            placeholder = "Describe your track",
                            leadingActions = {
                                StudioIconButton(StudioIcons.Add, "Upload audio", {}, size = StudioIconButtonSize.Small)
                                StudioSegmentedControl(
                                    listOf("Create", "Muse"),
                                    "Create",
                                    {},
                                    compact = true,
                                    isComingSoon = { it == "Muse" },
                                ) { it }
                            },
                            trailingActions = {
                                StudioIconButton(StudioIcons.AutoAwesome, "Enhance prompt", {
                                }, size = StudioIconButtonSize.Small, comingSoon = true)
                                StudioIconButton(StudioIcons.Notes, "Lyrics", {}, size = StudioIconButtonSize.Small)
                            },
                        )
                    }
                }
            }
        }
        val send = composeRule.onNodeWithContentDescription("Send").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Enhance prompt").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Lyrics").assertIsDisplayed()
        val bounds = composeRule.onNodeWithTag("composerBounds").fetchSemanticsNode().boundsInRoot
        val sendBounds = send.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Send must fit inside the composer",
            sendBounds.left >= bounds.left && sendBounds.right <= bounds.right,
        )
        if (sameRow) {
            val upload = composeRule.onNodeWithContentDescription("Upload audio").fetchSemanticsNode().boundsInRoot
            assertEquals("Ordinary font actions should share one row", upload.center.y, sendBounds.center.y, 1f)
        }
        send.performClick()
        assertEquals(1, sends)
    }
}
