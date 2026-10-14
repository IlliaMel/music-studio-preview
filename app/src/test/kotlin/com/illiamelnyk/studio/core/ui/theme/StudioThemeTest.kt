package com.illiamelnyk.studio.core.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.illiamelnyk.studio.core.ui.foundation.LocalStudioContentColor
import com.illiamelnyk.studio.core.ui.foundation.PressScaleIndication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudioThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `dark theme provides the dark scheme`() {
        lateinit var colors: StudioColors
        var contentColor = Color.Unspecified
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                colors = StudioTheme.colors
                contentColor = LocalStudioContentColor.current
            }
        }

        assertEquals(darkStudioColors(), colors)
        // color.background and color.content.primary in tokens.json
        assertEquals(Color(0xFF111111), colors.background)
        assertEquals(Color(0xFFEBEBEB), contentColor)
    }

    @Test
    fun `light theme provides the light scheme`() {
        lateinit var colors: StudioColors
        var contentColor = Color.Unspecified
        composeRule.setContent {
            StudioTheme(darkTheme = false) {
                colors = StudioTheme.colors
                contentColor = LocalStudioContentColor.current
            }
        }

        assertEquals(lightStudioColors(), colors)
        // colorLight.background and colorLight.content.primary in tokens.json
        assertEquals(Color(0xFFFAFAFA), colors.background)
        assertEquals(Color(0xFF111111), contentColor)
    }

    @Test
    fun `switching the scheme updates what the content reads`() {
        var darkTheme by mutableStateOf(true)
        lateinit var colors: StudioColors
        composeRule.setContent {
            StudioTheme(darkTheme = darkTheme) { colors = StudioTheme.colors }
        }

        darkTheme = false
        composeRule.waitForIdle()

        assertEquals(lightStudioColors(), colors)
    }

    @Test
    fun `theme provides the scheme-independent tokens and the press-scale indication`() {
        lateinit var dimens: StudioDimens
        lateinit var motion: StudioMotion
        lateinit var effects: StudioEffects
        var indicationIsPressScale = false
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                dimens = StudioTheme.dimens
                motion = StudioTheme.motion
                effects = StudioTheme.effects
                indicationIsPressScale = LocalIndication.current is PressScaleIndication
            }
        }

        assertEquals(StudioDimens(), dimens)
        assertEquals(StudioEffects(), effects)
        assertEquals(0.96f, motion.pressScale)
        assertTrue(indicationIsPressScale)
    }

    @Test
    fun `reading the theme outside StudioTheme fails with a clear message`() {
        val error = assertThrows(IllegalStateException::class.java) {
            composeRule.setContent { StudioTheme.colors }
        }

        assertTrue(error.message.orEmpty().contains("StudioTheme is missing"))
    }
}
