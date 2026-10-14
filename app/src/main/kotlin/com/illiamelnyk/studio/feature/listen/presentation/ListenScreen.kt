package com.illiamelnyk.studio.feature.listen.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioBadge
import com.illiamelnyk.studio.core.ui.components.StudioBadgeStyle
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.presentation.components.CollectionRow

/** The Listen tab: the demo catalog's collections, one row each. */
@Composable
fun ListenScreen(
    state: ListenState,
    onIntent: (ListenIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val dimens = StudioTheme.dimens
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = dimens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        item(key = "badge", contentType = "badge") {
            StudioBadge(
                text = stringResource(R.string.listen_demo_catalog),
                modifier = Modifier.padding(horizontal = dimens.layout.screenPadding),
                style = StudioBadgeStyle.Demo,
            )
        }
        items(state.collections, key = { it.id }, contentType = { "collection" }) { collection ->
            CollectionRow(
                collection = collection,
                onClick = { onIntent(ListenIntent.CollectionClicked(collection.id)) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ListenScreenPreview(@PreviewParameter(ListenPreviewData::class) state: ListenState) {
    PreviewScreen { ListenScreen(state = state, onIntent = {}) }
}
