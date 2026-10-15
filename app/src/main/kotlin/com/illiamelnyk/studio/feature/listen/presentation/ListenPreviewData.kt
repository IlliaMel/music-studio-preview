package com.illiamelnyk.studio.feature.listen.presentation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import kotlinx.collections.immutable.persistentListOf

class ListenPreviewData : PreviewParameterProvider<ListenState> {
    override val values = sequenceOf(
        ListenState(sections = persistentListOf(
            CatalogSectionUi(CatalogSectionKind.Stations, persistentListOf(CollectionUi("live", "Piano Room", UiText.Raw("Live"), "Piano Room")), isLoading = false),
            CatalogSectionUi(CatalogSectionKind.New, persistentListOf(CollectionUi("new", "New This Week", UiText.Raw("6 tracks · 19:20"), "New This Week")), isLoading = false),
        )),
        ListenState(),
        ListenState(sections = persistentListOf(CatalogSectionUi(CatalogSectionKind.New, isLoading = false, failed = true))),
        ListenState(searchVisible = true),
    )
}
