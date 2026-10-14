package com.illiamelnyk.studio.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A checkbox with its [label]: a 20 dp square that fills with the inverse color and a check when
 * [checked]. The whole row toggles, and TalkBack reads it as a checkbox.
 */
@Composable
fun StudioCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val motion = StudioTheme.motion
    val fill by animateColorAsState(
        targetValue = if (checked) colors.inverse.surface else Color.Transparent,
        animationSpec = tween(motion.duration.short, easing = motion.easing.standard),
        label = "checkboxFill",
    )
    val boxColor = when {
        !enabled -> colors.content.disabled
        checked -> colors.inverse.surface
        else -> colors.content.secondary
    }
    val checkColor = colors.inverse.content
    val corner = StudioTheme.dimens.spacing.xxs
    Row(
        modifier = modifier
            .toggleable(
                value = checked,
                interactionSource = null,
                indication = studioIndication(StudioTheme.shapes.s),
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = dimens.size.touchTarget),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(dimens.size.icon.m)) {
            val stroke = dimens.stroke.thick.toPx()
            val radius = CornerRadius(corner.toPx())
            drawRoundRect(fill, cornerRadius = radius)
            drawRoundRect(
                color = boxColor,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = radius,
                style = Stroke(stroke),
            )
            if (checked) {
                val check = Path().apply {
                    moveTo(size.width * CHECK_START.x, size.height * CHECK_START.y)
                    lineTo(size.width * CHECK_CORNER.x, size.height * CHECK_CORNER.y)
                    lineTo(size.width * CHECK_END.x, size.height * CHECK_END.y)
                }
                drawPath(check, checkColor, style = Stroke(stroke))
            }
        }
        StudioText(
            text = label,
            style = StudioTheme.typography.bodyMedium,
            color = if (enabled) colors.content.secondary else colors.content.disabled,
        )
    }
}

// The kit draws the check as an L of 8 × 4 px with 2 px strokes, rotated by -45° in the 20 px box.
// These are the ends and the corner of that L as fractions of the box.
private val CHECK_START = Offset(0.22f, 0.5f)
private val CHECK_CORNER = Offset(0.39f, 0.68f)
private val CHECK_END = Offset(0.71f, 0.36f)

@PreviewLightDark
@Composable
private fun StudioCheckboxPreview() {
    PreviewSurface {
        StudioCheckbox(checked = true, onCheckedChange = {}, label = "I confirm that I am 18 years of age or older.")
        StudioCheckbox(checked = false, onCheckedChange = {}, label = "I want to receive updates.")
    }
}
