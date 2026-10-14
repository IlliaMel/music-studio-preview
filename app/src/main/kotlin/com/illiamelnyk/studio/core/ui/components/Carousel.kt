package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A horizontal row of cards that snaps each card to the start, `layout.itemGap` apart. The first
 * card starts on the screen padding and the row scrolls to the screen edges, so place it full width,
 * without horizontal padding.
 *
 * @param key a stable key for each item, as in `LazyRow`.
 */
@Composable
fun <T> Carousel(
    items: List<T>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    key: ((item: T) -> Any)? = null,
    itemContent: @Composable LazyItemScope.(item: T) -> Unit,
) {
    val dimens = StudioTheme.dimens
    LazyRow(
        modifier = modifier,
        state = state,
        contentPadding = PaddingValues(horizontal = dimens.layout.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.layout.itemGap),
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
    ) {
        items(items, key = key) { item -> itemContent(item) }
    }
}

@PreviewLightDark
@Composable
private fun CarouselPreview() {
    PreviewSurface {
        Carousel(listOf("Rust", "Night Drive", "Lo-fi", "Ambient"), key = { it }) { title ->
            TrackCard(title, "Studio", title, onClick = {})
        }
    }
}
