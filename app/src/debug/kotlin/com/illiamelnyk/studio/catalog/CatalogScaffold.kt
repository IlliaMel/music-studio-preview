package com.illiamelnyk.studio.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.illiamelnyk.studio.core.ui.components.coverArt
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/** A page of sections, `layout.sectionGap` apart, that scrolls behind the navigation bar. */
@Composable
internal fun CatalogPageList(content: LazyListScope.() -> Unit) {
    val dimens = StudioTheme.dimens
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = dimens.spacing.m, bottom = dimens.spacing.m + navigationBar),
        verticalArrangement = Arrangement.spacedBy(dimens.layout.sectionGap),
        content = content,
    )
}

/**
 * A titled group of specimens. The specimen functions are [ColumnScope] extensions because each one
 * stacks several components in this column. Bars and carousels span the screen ([edgeToEdge]) and
 * pad themselves; everything else sits on the screen padding.
 */
@Composable
internal fun Section(title: String, edgeToEdge: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val dimens = StudioTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.s)) {
        StudioText(
            text = title,
            style = StudioTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = dimens.layout.screenPadding),
            color = StudioTheme.colors.content.secondary,
        )
        Column(
            modifier = Modifier.padding(
                horizontal = if (edgeToEdge) dimens.spacing.none else dimens.layout.screenPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
            content = content,
        )
    }
}

/** Components laid out like the specimens of ui-kit.html: wrapped, with the kit's 12 dp gap. */
@Composable
internal fun Specimen(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** A surface of generated cover art, for the components that only appear over media. */
@Composable
internal fun MediaBackdrop(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(StudioTheme.shapes.l)
            .coverArt("Ambient")
            .padding(StudioTheme.dimens.spacing.m),
    ) { Specimen(content) }
}
