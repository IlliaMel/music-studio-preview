package com.illiamelnyk.studio.core.ui.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import com.illiamelnyk.studio.core.ui.theme.StudioMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Press feedback of the design system: a pressed element shrinks to [StudioMotion.pressScale] and
 * comes back, instead of showing a ripple. StudioTheme provides it as `LocalIndication`, so every
 * `clickable` and `selectable` inside the theme uses it.
 *
 * The indication scales what is drawn after `clickable` in the modifier chain, so components put
 * `clickable` before the background and border of their container.
 */
internal data class PressScaleIndication(private val motion: StudioMotion) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressScaleNode(interactionSource, motion)
}

private class PressScaleNode(private val interactionSource: InteractionSource, private val motion: StudioMotion) :
    Modifier.Node(),
    DrawModifierNode {
    private val scale = Animatable(1f)
    private val animationSpec = tween<Float>(motion.duration.short, easing = motion.easing.standard)
    private var pressJob: Job? = null
    private var releaseJob: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            val presses = mutableSetOf<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                    else -> return@collect
                }
                if (presses.isEmpty()) release() else press()
            }
        }
    }

    private fun press() {
        releaseJob?.cancel()
        pressJob = coroutineScope.launch { scale.animateTo(motion.pressScale, animationSpec) }
    }

    private fun release() {
        // A quick tap releases before the element has shrunk: let the press finish first, so the
        // tap is still visible.
        val press = pressJob
        releaseJob = coroutineScope.launch {
            press?.join()
            scale.animateTo(1f, animationSpec)
        }
    }

    override fun ContentDrawScope.draw() {
        val factor = scale.value
        if (factor == 1f) {
            drawContent()
        } else {
            scale(factor) { this@draw.drawContent() }
        }
    }
}
