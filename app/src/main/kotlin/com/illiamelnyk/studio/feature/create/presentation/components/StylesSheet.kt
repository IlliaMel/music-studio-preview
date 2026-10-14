package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.skeleton
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.CreatePreviewData
import com.illiamelnyk.studio.feature.create.presentation.StyleUi
import com.illiamelnyk.studio.feature.create.presentation.common.OverlaySheet
import kotlinx.collections.immutable.ImmutableList

/**
 * The styles to add to the prompt, two by two, the composer's style selected. Picking one closes
 * the sheet; [styles] is `null` while they load, and their outlines show.
 */
@Composable
fun StylesSheet(
    visible: Boolean,
    styles: ImmutableList<StyleUi>?,
    selectedId: String?,
    onStyleSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlaySheet(visible = visible, onDismissRequest = onDismissRequest, modifier = modifier) {
        StylesContent(styles, selectedId, onStyleSelect)
    }
}

@Composable
private fun StylesContent(
    styles: ImmutableList<StyleUi>?,
    selectedId: String?,
    onStyleSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(dimens.layout.itemGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxs)) {
            StudioText(stringResource(R.string.create_styles_title), StudioTheme.typography.titleMedium)
            StudioText(
                text = stringResource(R.string.create_styles_message),
                style = StudioTheme.typography.bodySmall,
                color = StudioTheme.colors.content.secondary,
            )
        }
        val tiles: List<StyleUi?> = styles ?: List(SKELETON_TILES) { null }
        tiles.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.layout.itemGap)) {
                row.forEach { style ->
                    val tileModifier = Modifier.weight(1f)
                    if (style == null) {
                        Box(
                            tileModifier.heightIn(
                                min = dimens.size.component.toolTile,
                            ).skeleton(true, StudioTheme.shapes.m),
                        )
                    } else {
                        StyleTile(style, style.id == selectedId, onClick = { onStyleSelect(style.id) }, tileModifier)
                    }
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A style as a tile: its art, its name and its tags; outlined while selected. */
@Suppress("ktlint:compose:modifier-clickable-order") // The press scale has to shrink the clipped tile.
@Composable
private fun StyleTile(style: StyleUi, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val shape = StudioTheme.shapes.m
    val outline by animateColorAsState(
        // Clear while unselected, so no line runs over the art.
        targetValue = if (selected) colors.inverse.surface else colors.inverse.surface.copy(alpha = 0f),
        animationSpec = tween(StudioTheme.motion.duration.short, easing = StudioTheme.motion.easing.standard),
        label = "styleOutline",
    )
    Row(
        modifier = modifier
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = studioIndication(shape),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .heightIn(min = dimens.size.component.toolTile)
            .clip(shape)
            .background(colors.surface.level3)
            .border(dimens.stroke.thick, outline, shape)
            .padding(end = dimens.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(style.coverSeed, dimens.size.cover.m, shape = RectangleShape)
        Column(Modifier.weight(1f)) {
            StudioText(style.name, StudioTheme.typography.titleSmall, maxLines = 1)
            StudioText(style.tags, StudioTheme.typography.bodySmall, color = colors.content.secondary, maxLines = 2)
        }
    }
}

private const val COLUMNS = 2
private const val SKELETON_TILES = 6

@PreviewLightDark
@Composable
private fun StylesContentPreview() {
    PreviewScreen {
        Column(Modifier.padding(StudioTheme.dimens.spacing.m)) {
            StylesContent(styles = CreatePreviewData.styles, selectedId = "synthwave", onStyleSelect = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun StylesContentLoadingPreview() {
    PreviewScreen {
        Column(Modifier.padding(StudioTheme.dimens.spacing.m)) {
            StylesContent(styles = null, selectedId = null, onStyleSelect = {})
        }
    }
}
