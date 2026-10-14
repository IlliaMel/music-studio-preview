package com.illiamelnyk.studio.feature.listen.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.presentation.CollectionUi

/** A collection in the catalog: its cover, title and size. Opens the collection. */
@Composable
fun CollectionRow(collection: CollectionUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Row(
        // clickable before the padding, so the press scale shrinks the whole row.
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .fillMaxWidth()
            .heightIn(min = dimens.size.component.trackRow)
            .padding(horizontal = dimens.layout.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(seed = collection.coverSeed, size = dimens.size.cover.m)
        Column {
            StudioText(collection.title, StudioTheme.typography.titleSmall, maxLines = 1)
            StudioText(
                text = collection.subtitle.asString(),
                style = StudioTheme.typography.bodySmall,
                color = StudioTheme.colors.content.secondary,
                maxLines = 1,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun CollectionRowPreview() {
    PreviewScreen {
        CollectionRow(
            collection = CollectionUi(
                id = "trending",
                title = "Trending Now",
                subtitle = UiText.Plural(R.plurals.listen_collection_summary, 6, 6, "19:20"),
                coverSeed = "Trending Now",
            ),
            onClick = {},
        )
    }
}
