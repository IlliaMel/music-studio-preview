package com.illiamelnyk.studio.feature.listen.presentation.collection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.presentation.CollectionUi

/** The collection's cover, title and size, with Play and Shuffle. */
@Composable
fun CollectionHeader(
    collection: CollectionUi,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(seed = collection.coverSeed, size = dimens.size.cover.l)
        StudioText(collection.title, StudioTheme.typography.titleLarge, textAlign = TextAlign.Center)
        StudioText(
            text = collection.subtitle.asString(),
            style = StudioTheme.typography.bodySmall,
            color = StudioTheme.colors.content.secondary,
        )
        Row(
            modifier = Modifier.padding(top = dimens.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        ) {
            StudioButton(text = stringResource(R.string.collection_play), onClick = onPlayClick)
            StudioButton(
                text = stringResource(R.string.collection_shuffle),
                onClick = onShuffleClick,
                style = StudioButtonStyle.Secondary,
                icon = StudioIcons.Shuffle,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun CollectionHeaderPreview() {
    PreviewScreen {
        CollectionHeader(
            collection = CollectionUi(
                id = "trending",
                title = "Trending Now",
                subtitle = UiText.Plural(R.plurals.listen_collection_summary, 6, 6, "19:20"),
                coverSeed = "Trending Now",
            ),
            onPlayClick = {},
            onShuffleClick = {},
        )
    }
}
