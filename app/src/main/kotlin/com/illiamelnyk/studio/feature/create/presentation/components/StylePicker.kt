package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioFilterChip
import com.illiamelnyk.studio.core.ui.foundation.skeleton
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.CreatePreviewData
import com.illiamelnyk.studio.feature.create.presentation.StyleUi
import com.illiamelnyk.studio.feature.create.presentation.common.bleed
import kotlinx.collections.immutable.ImmutableList

/**
 * One style or none, as a row of chips that scrolls sideways; the outline of the row while
 * [styles] is `null`, loading.
 */
@Composable
fun StylePicker(
    styles: ImmutableList<StyleUi>?,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    if (styles == null) {
        Box(
            modifier
                .fillMaxWidth()
                .height(dimens.size.component.chip)
                .skeleton(visible = true, shape = StudioTheme.shapes.full),
        )
        return
    }
    // The sheet pads its content by spacing.m; the row scrolls on to the sheet's edges.
    LazyRow(
        modifier = modifier
            .bleed(dimens.spacing.m)
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = dimens.spacing.m),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        item(key = NONE) {
            StudioFilterChip(
                text = stringResource(R.string.create_settings_no_style),
                selected = selectedId == null,
                onClick = { onSelect(null) },
            )
        }
        items(styles, key = { it.id }) { style ->
            StudioFilterChip(text = style.name, selected = style.id == selectedId, onClick = { onSelect(style.id) })
        }
    }
}

private const val NONE = "none"

@PreviewLightDark
@Composable
private fun StylePickerPreview() {
    PreviewScreen { StylePicker(styles = CreatePreviewData.styles, selectedId = "synthwave", onSelect = {}) }
}
