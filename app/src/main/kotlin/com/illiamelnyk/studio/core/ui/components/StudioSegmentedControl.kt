package com.illiamelnyk.studio.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A pill with two or three mutually exclusive options. The selected one sits on the inverse pill,
 * which slides to the option the user picks.
 *
 * TalkBack reads it as a group of tabs, with the selected one marked as selected.
 *
 * @param compact the 32 dp variant with 24 dp segments, for the composer.
 * @param label the text of an option.
 */
@Composable
fun <T> StudioSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    isComingSoon: (T) -> Boolean = { false },
    label: @Composable (T) -> String,
) {
    require(options.isNotEmpty()) { "A segmented control needs at least one option" }
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val motion = StudioTheme.motion
    val gap = dimens.spacing.xxs
    val selectedIndex = options.indexOf(selected)
    // A fractional index: between two segments the pill takes a mix of their positions and widths.
    val pillPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(motion.duration.medium, easing = motion.easing.standard),
        label = "pillPosition",
    )
    Layout(
        content = {
            Box(Modifier.layoutId(PILL_ID).background(colors.inverse.surface, StudioTheme.shapes.full))
            options.forEach { option ->
                Segment(
                    text = label(option),
                    selected = option == selected,
                    compact = compact,
                    comingSoon = isComingSoon(option),
                    onClick = { onSelect(option) },
                )
            }
        },
        modifier = modifier
            .minimumTouchTarget()
            .selectableGroup()
            .heightIn(min = if (compact) dimens.size.control.s else dimens.size.component.segmentedControl)
            .background(colors.surface.level3, StudioTheme.shapes.full)
            .padding(gap),
    ) { measurables, constraints ->
        val segmentConstraints = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val segments = measurables.filter { it.layoutId != PILL_ID }.map { it.measure(segmentConstraints) }
        val gapPx = gap.roundToPx()
        val xs = segments.runningFold(0) { x, segment -> x + segment.width + gapPx }
        val width = xs.last() - gapPx
        val height = segments.maxOf { it.height }

        val from = floor(pillPosition).toInt().coerceIn(segments.indices)
        val to = ceil(pillPosition).toInt().coerceIn(segments.indices)
        val fraction = pillPosition - from
        val pillWidth = lerp(segments[from].width, segments[to].width, fraction)
        val pill = measurables.first { it.layoutId == PILL_ID }.measure(Constraints.fixed(pillWidth, height))

        layout(width, height) {
            if (selectedIndex >= 0) pill.place(lerp(xs[from], xs[to], fraction), 0)
            segments.forEachIndexed { index, segment -> segment.place(xs[index], (height - segment.height) / 2) }
        }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, compact: Boolean, comingSoon: Boolean, onClick: () -> Unit) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val motion = StudioTheme.motion
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.inverse.content else colors.content.secondary,
        animationSpec = tween(motion.duration.medium, easing = motion.easing.standard),
        label = "segmentText",
    )
    Row(
        modifier = Modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            // tokens.json has no size for a compact segment; the kit uses the 24 dp avatar.xs.
            .heightIn(min = if (compact) dimens.size.avatar.xs else dimens.size.component.segment)
            .padding(horizontal = if (compact) dimens.spacing.xs else dimens.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (compact && comingSoon) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StudioText(text, StudioTheme.typography.labelMedium, color = textColor, maxLines = 1)
                StudioSoonBadge()
            }
        } else {
            StudioText(
                text = text,
                style = if (compact) StudioTheme.typography.labelMedium else StudioTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
            )
            if (comingSoon) StudioSoonBadge()
        }
    }
}

private const val PILL_ID = "pill"

@PreviewLightDark
@Composable
private fun StudioSegmentedControlPreview() {
    PreviewSurface {
        var mode by remember { mutableStateOf("Create") }
        StudioSegmentedControl(listOf("Create", "Listen"), selected = mode, onSelect = { mode = it }) { it }
        StudioSegmentedControl(listOf("Vocals", "Instrumental"), selected = "Instrumental", onSelect = {}) { it }
        StudioSegmentedControl(listOf("Auto", "Deep", "Fast"), selected = "Auto", onSelect = {}, compact = true) { it }
    }
}

/** Interpolates between two pixel values; the pill's position and width between two segments. */
private fun lerp(start: Int, stop: Int, fraction: Float): Int = (start + (stop - start) * fraction).roundToInt()
