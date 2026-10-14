package com.illiamelnyk.studio.core.ui.components

import android.animation.ValueAnimator
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The orb moves on the infinite animation clock, which runs in tests only while the main clock is
 * advanced by hand.
 */
@RunWith(RobolectricTestRunner::class)
class OrbTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun pauseClock() {
        composeRule.mainClock.autoAdvance = false
    }

    @After
    fun restoreAnimations() {
        setAnimatorDurationScale(1f)
    }

    @Test
    fun `an active orb keeps time`() {
        lateinit var clock: OrbClock
        composeRule.setContent { clock = rememberOrbClock(active = true, level = null) }

        composeRule.mainClock.advanceTimeBy(ONE_SECOND)

        assertTrue(clock.running)
        assertEquals(1f, clock.elapsed, FRAME_TOLERANCE)
        assertEquals(1f, clock.flow, FRAME_TOLERANCE)
    }

    @Test
    fun `an inactive orb stands still`() {
        lateinit var clock: OrbClock
        composeRule.setContent { clock = rememberOrbClock(active = false, level = null) }

        composeRule.mainClock.advanceTimeBy(ONE_SECOND)

        assertFalse(clock.running)
        assertEquals(0f, clock.elapsed)
        assertEquals(0f, clock.flow)
    }

    @Test
    fun `with animations removed in the system settings the orb stands still`() {
        setAnimatorDurationScale(0f)
        lateinit var clock: OrbClock
        composeRule.setContent { clock = rememberOrbClock(active = true, level = null) }

        composeRule.mainClock.advanceTimeBy(ONE_SECOND)

        assertFalse(clock.running)
        assertEquals(0f, clock.elapsed)
        assertEquals(0f, clock.flow)
    }

    @Test
    fun `a full level makes the color flow three times as fast`() {
        lateinit var clock: OrbClock
        composeRule.setContent { clock = rememberOrbClock(active = true, level = { 1f }) }

        composeRule.mainClock.advanceTimeBy(ONE_SECOND)

        assertEquals(1f, clock.elapsed, FRAME_TOLERANCE)
        assertEquals(3f, clock.flow, FRAME_TOLERANCE * 3)
    }

    @Test
    fun `a moving orb doesn't recompose`() {
        var compositions = 0
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                SideEffect { compositions++ }
                Orb(level = { 0.5f })
            }
        }
        // Let the frame loop start before counting.
        composeRule.mainClock.advanceTimeBy(FRAME)
        val changesBefore = recomposerChanges()

        composeRule.mainClock.advanceTimeBy(ONE_SECOND)

        assertEquals(1, compositions)
        assertEquals(changesBefore, recomposerChanges())
    }

    /** How many times the recomposers applied changes: it stays put while only drawing changes. */
    private fun recomposerChanges(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

    /** The system's "Animator duration scale"; 0 is what "Remove animations" sets. */
    private fun setAnimatorDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    private companion object {
        const val FRAME = 16L
        const val ONE_SECOND = 1_000L
        const val FRAME_TOLERANCE = 0.05f
    }
}
