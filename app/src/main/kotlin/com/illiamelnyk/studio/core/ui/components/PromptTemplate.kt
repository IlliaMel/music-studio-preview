package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioColors
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.math.roundToInt

/** A piece of a [PromptTemplate]: fixed text, or a slot the user fills in. */
@Immutable
sealed interface PromptPart {
    data class Text(val text: String) : PromptPart

    /** Shows [value] once filled in and [placeholder] until then. */
    data class Slot(val id: String, val placeholder: String, val value: String? = null) : PromptPart
}

/**
 * The prompt of a tool, with the places to fill in: a card with the tool's picture, [kind] and
 * [name], and the text in which each slot is underlined with dashes and opens [onSlotClick].
 *
 * Each slot is a button of its own for TalkBack, read as its value or its placeholder.
 */
@Composable
fun PromptTemplate(
    kind: String,
    name: String,
    imageSeed: String,
    parts: List<PromptPart>,
    onSlotClick: (slotId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(StudioTheme.shapes.l)
            .background(StudioTheme.colors.surface.level3)
            .padding(dimens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(imageSeed, dimens.size.avatar.xs, shape = StudioTheme.shapes.xs)
            StudioText(kind, StudioTheme.typography.bodySmall, color = StudioTheme.colors.content.tertiary)
            StudioText(name, StudioTheme.typography.titleSmall, maxLines = 1)
        }
        TemplateText(parts, onSlotClick)
    }
}

/** The text with its slots: the slots are styled spans, with a tappable box laid over each line of a slot. */
@Composable
private fun TemplateText(parts: List<PromptPart>, onSlotClick: (slotId: String) -> Unit) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val density = LocalDensity.current
    val template = remember(parts, colors) { buildTemplate(parts, colors) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box {
        BasicText(
            text = template.text,
            modifier = Modifier.drawBehind {
                val current = layout ?: return@drawBehind
                val stroke = dimens.stroke.hairline.toPx()
                val dashes = PathEffect.dashPathEffect(
                    floatArrayOf(dimens.spacing.xxs.toPx(), dimens.spacing.hairline.toPx()),
                )
                template.slots.forEach { slot ->
                    current.fragmentsOf(slot.range).forEach { rect ->
                        val y = rect.bottom - stroke
                        drawLine(slot.color, Offset(rect.left, y), Offset(rect.right, y), stroke, pathEffect = dashes)
                    }
                }
            },
            style = StudioTheme.typography.bodyMedium.copy(color = colors.content.primary),
            onTextLayout = { layout = it },
        )
        val current = layout
        if (current != null) {
            template.slots.forEach { slot ->
                current.fragmentsOf(slot.range).forEachIndexed { index, rect ->
                    val description = slot.part.value ?: slot.part.placeholder
                    // One node per slot for TalkBack; the other lines of a wrapped slot only take touches.
                    val semantics = if (index == 0) {
                        Modifier.semantics { contentDescription = description }
                    } else {
                        Modifier.clearAndSetSemantics {}
                    }
                    Box(
                        Modifier
                            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                            .then(semantics)
                            .clickable(role = Role.Button) { onSlotClick(slot.part.id) },
                    )
                }
            }
        }
    }
}

private class TemplateSlot(val part: PromptPart.Slot, val range: IntRange, val color: Color)

private class Template(val text: AnnotatedString, val slots: List<TemplateSlot>)

private fun buildTemplate(parts: List<PromptPart>, colors: StudioColors): Template {
    val slots = mutableListOf<TemplateSlot>()
    val text = buildAnnotatedString {
        parts.forEach { part ->
            when (part) {
                is PromptPart.Text -> append(part.text)

                is PromptPart.Slot -> {
                    val color = if (part.value != null) colors.content.primary else colors.content.tertiary
                    val start = length
                    withStyle(SpanStyle(color = color)) { append(part.value ?: part.placeholder) }
                    slots += TemplateSlot(part, start until length, color)
                }
            }
        }
    }
    return Template(text, slots)
}

/** The boxes [range] covers, one per line it spans. */
private fun TextLayoutResult.fragmentsOf(range: IntRange): List<Rect> {
    if (range.isEmpty()) return emptyList()
    return (getLineForOffset(range.first)..getLineForOffset(range.last)).map { line ->
        val start = maxOf(range.first, getLineStart(line))
        val end = minOf(range.last + 1, getLineEnd(line, visibleEnd = true))
        val left = getHorizontalPosition(start, usePrimaryDirection = true)
        val right = getHorizontalPosition(end, usePrimaryDirection = true)
        Rect(minOf(left, right), getLineTop(line), maxOf(left, right), getLineBottom(line))
    }
}

@PreviewLightDark
@Composable
private fun PromptTemplatePreview() {
    PreviewSurface {
        PromptTemplate(
            kind = "New track",
            name = "Genreshift",
            imageSeed = "Genreshift",
            parts = listOf(
                PromptPart.Text("Keep the user's voice and melody, but completely transform the genre into "),
                PromptPart.Slot("genre", "New genre"),
                PromptPart.Text(". Use the recording as "),
                PromptPart.Slot("reference", "reference", value = "Rainy Window"),
                PromptPart.Text(" at medium strength."),
            ),
            onSlotClick = {},
        )
    }
}
