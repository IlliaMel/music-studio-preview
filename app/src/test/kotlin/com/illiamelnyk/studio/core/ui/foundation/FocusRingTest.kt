package com.illiamelnyk.studio.core.ui.foundation

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.illiamelnyk.studio.core.ui.theme.StudioColors
import com.illiamelnyk.studio.core.ui.theme.StudioDimens
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.core.ui.theme.darkStudioColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Keyboard and D-pad focus draws the `outline.strong` ring just outside the element, and only while focused. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusRingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val colors: StudioColors = darkStudioColors()
    private val dimens = StudioDimens()
    private val interactions = MutableInteractionSource()

    @Test
    fun `focus draws the ring and losing it removes the ring`() {
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                Box(
                    Modifier
                        .testTag(HOST)
                        .background(StudioTheme.colors.background)
                        .padding(StudioTheme.dimens.spacing.m),
                ) {
                    Box(
                        Modifier
                            .indication(interactions, LocalIndication.current)
                            .size(StudioTheme.dimens.size.touchTarget)
                            .background(StudioTheme.colors.surface.level3, StudioTheme.shapes.full),
                    )
                }
            }
        }
        val ring = colors.outline.strong.compositeOver(colors.background)

        assertColorAtRing(colors.background)

        val focus = FocusInteraction.Focus()
        runBlocking { interactions.emit(focus) }
        composeRule.waitForIdle()
        assertColorAtRing(ring)

        runBlocking { interactions.emit(FocusInteraction.Unfocus(focus)) }
        composeRule.waitForIdle()
        assertColorAtRing(colors.background)
    }

    /** Checks the middle of the ring's left side, one ring width left of the element. */
    private fun assertColorAtRing(expected: Color) {
        val pixels = composeRule.onNodeWithTag(HOST).captureToImage().toPixelMap()
        val (x, y) = with(composeRule.density) {
            val left = dimens.spacing.m.toPx() - dimens.stroke.thick.toPx()
            val middle = (dimens.spacing.m + dimens.size.touchTarget / 2).toPx()
            left.roundToInt() to middle.roundToInt()
        }
        val actual = pixels[x, y]
        assertTrue("expected $expected at ($x, $y), was $actual", actual.isCloseTo(expected))
    }

    private fun Color.isCloseTo(other: Color): Boolean =
        abs(red - other.red) < TOLERANCE && abs(green - other.green) < TOLERANCE && abs(blue - other.blue) < TOLERANCE

    private companion object {
        const val HOST = "host"
        const val TOLERANCE = 0.04f
    }
}
