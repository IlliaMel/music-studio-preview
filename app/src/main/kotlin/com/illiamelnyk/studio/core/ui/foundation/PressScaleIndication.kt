package com.illiamelnyk.studio.core.ui.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import com.illiamelnyk.studio.core.ui.theme.StudioMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Press and focus feedback of the design system, instead of a Material ripple:
 * - a pressed element shrinks to [StudioMotion.pressScale] and comes back;
 * - an element focused with a keyboard or a D-pad gets a [ringWidth] ring in [ringColor] just
 *   outside its bounds, in [focusShape].
 *
 * StudioTheme provides it as `LocalIndication`, so every `clickable`, `selectable` and `toggleable`
 * inside the theme uses it. The scale applies to what is drawn after `clickable` in the modifier
 * chain, so components put `clickable` before the background and border of their container.
 */
internal data class PressScaleIndication(
    private val motion: StudioMotion,
    private val ringColor: Color,
    private val ringWidth: Dp,
    private val focusShape: Shape,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressScaleNode(interactionSource, motion, ringColor, ringWidth, focusShape)

    fun withFocusShape(shape: Shape): PressScaleIndication = copy(focusShape = shape)
}

/**
 * The theme's press feedback with the focus ring in [shape], for clickable rows, cards and tiles;
 * the default ring is a pill, which fits buttons, chips and segments.
 *
 * Pass it as the `indication` of `clickable`, with `interactionSource = null`.
 */
@Composable
@ReadOnlyComposable
fun studioIndication(shape: Shape): Indication {
    val indication = LocalIndication.current
    return if (indication is PressScaleIndication) indication.withFocusShape(shape) else indication
}

private class PressScaleNode(
    private val interactionSource: InteractionSource,
    private val motion: StudioMotion,
    private val ringColor: Color,
    private val ringWidth: Dp,
    private val focusShape: Shape,
) : Modifier.Node(),
    DrawModifierNode {
    private val scale = Animatable(1f)
    private val animationSpec = tween<Float>(motion.duration.short, easing = motion.easing.standard)
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var focused by mutableStateOf(false)

    override fun onAttach() {
        coroutineScope.launch {
            val presses = mutableSetOf<PressInteraction.Press>()
            val focuses = mutableSetOf<FocusInteraction.Focus>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                    is FocusInteraction.Focus -> focuses += interaction
                    is FocusInteraction.Unfocus -> focuses -= interaction.focus
                    else -> return@collect
                }
                focused = focuses.isNotEmpty()
                when {
                    interaction !is PressInteraction -> Unit
                    presses.isEmpty() -> release()
                    else -> press()
                }
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
        if (focused) drawFocusRing()
        val factor = scale.value
        if (factor == 1f) {
            drawContent()
        } else {
            scale(factor) { this@draw.drawContent() }
        }
    }

    /** The ring lies outside the element, half a ring width away from its edge. */
    private fun ContentDrawScope.drawFocusRing() {
        val width = ringWidth.toPx()
        val ringSize = Size(size.width + width * 2, size.height + width * 2)
        val outline = focusShape.createOutline(ringSize, layoutDirection, this)
        translate(-width, -width) { drawOutline(outline, ringColor, style = Stroke(width)) }
    }
}
