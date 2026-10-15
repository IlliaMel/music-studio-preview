package com.illiamelnyk.studio.feature.listen.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.ComingSoonSheetContent
import com.illiamelnyk.studio.core.ui.components.StudioBadge
import com.illiamelnyk.studio.core.ui.components.StudioBadgeStyle
import com.illiamelnyk.studio.core.ui.components.StudioBottomSheet
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.presentation.components.CatalogSectionRow

@Composable
fun ListenScreen(
    state: ListenState,
    onIntent: (ListenIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val dimens = StudioTheme.dimens
    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = contentPadding + PaddingValues(vertical = dimens.spacing.m)) {
            item(key = "badge") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = dimens.layout.screenPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StudioBadge(stringResource(R.string.listen_demo_catalog), style = StudioBadgeStyle.Demo)
                    StudioIconButton(StudioIcons.Search, stringResource(R.string.listen_search), onClick = {
                        onIntent(ListenIntent.SearchClicked)
                    })
                }
            }
            items(state.sections, key = { it.kind.name }, contentType = { "section" }) { section ->
                CatalogSectionRow(section, onOpen = {
                    onIntent(ListenIntent.CollectionClicked(it))
                }, onRetry = {
                    onIntent(ListenIntent.RetrySection(section.kind))
                }, modifier = Modifier.padding(vertical = dimens.spacing.s))
            }
        }
        StudioBottomSheet(state.searchVisible, onDismissRequest = { onIntent(ListenIntent.DismissSearch) }) {
            ComingSoonSheetContent(
                stringResource(
                    R.string.listen_search,
                ),
                stringResource(
                    R.string.listen_search_soon,
                ),
                emptyList(),
                stringResource(R.string.listen_close),
                onAction = {
                    onIntent(ListenIntent.DismissSearch)
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ListenScreenPreview(@PreviewParameter(ListenPreviewData::class) state: ListenState) {
    PreviewScreen { ListenScreen(state = state, onIntent = {}) }
}
