package com.illiamelnyk.studio.core.ui.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import com.illiamelnyk.studio.core.ui.theme.LocalStudioDimens

/**
 * Gives an element that is drawn smaller than the touch target (`dimens.size.touchTarget`, 48 dp)
 * a 48 dp touch area, without changing how big it looks: the layout reserves at least 48 × 48 dp and
 * centers the element in it.
 *
 * Compose already lets a touch that lands within 48 dp of a small `clickable` reach it
 * (`ViewConfiguration.minimumTouchTargetSize`). Reserving the space keeps neighbors out of that area,
 * so the touch can't land on something else. Apply it before `clickable`.
 */
fun Modifier.minimumTouchTarget(): Modifier = this then MinimumTouchTargetElement

private data object MinimumTouchTargetElement : ModifierNodeElement<MinimumTouchTargetNode>() {
    override fun create() = MinimumTouchTargetNode()

    override fun update(node: MinimumTouchTargetNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "minimumTouchTarget"
    }
}

private class MinimumTouchTargetNode :
    Modifier.Node(),
    LayoutModifierNode,
    CompositionLocalConsumerModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val minSize = currentValueOf(LocalStudioDimens).size.touchTarget.roundToPx()
        val placeable = measurable.measure(constraints)
        val width = maxOf(placeable.width, minSize)
        val height = maxOf(placeable.height, minSize)
        return layout(width, height) {
            placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2)
        }
    }
}
