package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.ui.foundation.skeleton
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveMotionScaleTest {
    private val scale = object : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(1f)
    }

    @get:Rule val composeRule = createComposeRule(effectContext = scale)

    @Test fun changingMotionScaleStopsAndRestartsExistingOrbWithoutRecomposition() {
        lateinit var clock: OrbClock
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { clock = rememberOrbClock(true, null) }
        composeRule.mainClock.advanceTimeBy(500)
        assertTrue(clock.running)
        assertTrue(clock.flow > 0f)
        composeRule.runOnIdle { scale.scaleFactor = 0f }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val paused = clock.flow
        composeRule.mainClock.advanceTimeBy(1000)
        assertFalse(clock.running)
        assertEquals(paused, clock.flow, 0f)
        composeRule.runOnIdle { scale.scaleFactor = 1f }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        assertTrue(clock.running)
        assertTrue(clock.flow > paused)
    }

    @Test fun changingMotionScaleStopsAndRestartsAttachedSkeletonClock() {
        lateinit var node: Modifier.Node

        @Suppress("UNCHECKED_CAST")
        val original = Modifier.skeleton(true).foldIn<ModifierNodeElement<*>?>(null) { acc, next ->
            next as? ModifierNodeElement<*> ?: acc
        } as ModifierNodeElement<Modifier.Node>
        val retained = object : ModifierNodeElement<Modifier.Node>() {
            override fun create() = original.create().also { node = it }
            override fun update(node: Modifier.Node) = original.update(node)
            override fun equals(other: Any?) = this === other
            override fun hashCode() = System.identityHashCode(this)
        }
        fun time(): Long = node.javaClass.getDeclaredMethod("getFrameTimeMillis")
            .apply { isAccessible = true }.invoke(node) as Long
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { StudioTheme { Box(Modifier.size(80.dp).then(retained)) } }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        val initial = time()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        assertTrue("Attached skeleton clock must advance", time() > initial)
        composeRule.runOnIdle { scale.scaleFactor = 0f }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val paused = time()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        assertEquals("Disabled clock must stop", paused, time())
        composeRule.runOnIdle { scale.scaleFactor = 1f }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        assertTrue("Existing clock must resume", time() > paused)
        assertTrue(node.isAttached)
    }
}
