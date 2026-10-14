package com.illiamelnyk.studio.core.ui.foundation

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.illiamelnyk.studio.core.ui.theme.LocalStudioColors
import com.illiamelnyk.studio.core.ui.theme.LocalStudioMotion
import com.illiamelnyk.studio.core.ui.theme.LocalStudioShapes
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * While [visible], draws the shape of content that is still loading instead of the content: a
 * `surface.level2` block in [shape] with a `surface.level4` band sweeping across it. Prefer it to a
 * spinner when the layout of the content is known. The content stays in the semantics tree.
 *
 * @param shape the block's shape; by default the `xs` radius, as for a line of text.
 */
fun Modifier.skeleton(visible: Boolean, shape: Shape? = null): Modifier =
    if (visible) this then SkeletonElement(shape) else this

private data class SkeletonElement(private val shape: Shape?) : ModifierNodeElement<SkeletonNode>() {
    override fun create() = SkeletonNode(shape)

    override fun update(node: SkeletonNode) {
        node.shape = shape
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "skeleton"
        properties["shape"] = shape
    }
}

private class SkeletonNode(var shape: Shape?) :
    Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    // The frame clock drives the sweep, and draw turns it into a position within the period: theme
    // values are read in draw, where the node sees their current value.
    private var frameTimeMillis by mutableLongStateOf(0L)

    override fun onAttach() {
        coroutineScope.launch {
            while (isActive) {
                withInfiniteAnimationFrameMillis { frameTimeMillis = it }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val colors = currentValueOf(LocalStudioColors)
        val period = currentValueOf(LocalStudioMotion).loop.skeleton
        val progress = (frameTimeMillis % period).toFloat() / period
        val shape = shape ?: currentValueOf(LocalStudioShapes).xs
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, colors.surface.level2)
        // The kit's CSS: a gradient twice as wide as the block, repeated, sliding from 200 % to -200 %.
        val width = size.width * 2
        val start = -width + 2 * width * progress
        val band = Brush.horizontalGradient(
            0.25f to Color.Transparent,
            0.5f to colors.surface.level4,
            0.75f to Color.Transparent,
            startX = start,
            endX = start + width,
            tileMode = TileMode.Repeated,
        )
        drawOutline(outline, band)
    }
}
