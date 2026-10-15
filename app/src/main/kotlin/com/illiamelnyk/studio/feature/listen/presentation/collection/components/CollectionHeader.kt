package com.illiamelnyk.studio.feature.listen.presentation.collection.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.ui.components.CollectionHeader as KitCollectionHeader
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.presentation.CollectionUi

@Composable
fun CollectionHeader(collection: CollectionUi, modifier: Modifier = Modifier) {
    KitCollectionHeader(
        collection.title,
        collection.subtitle.asString(),
        collection.coverSeed,
        modifier,
        artworkSize =
            StudioTheme.dimens.size.cover.l + StudioTheme.dimens.spacing.giant,
    )
}
