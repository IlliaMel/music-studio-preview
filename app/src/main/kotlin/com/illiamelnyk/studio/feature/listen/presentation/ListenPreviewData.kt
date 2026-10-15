package com.illiamelnyk.studio.feature.listen.presentation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import kotlinx.collections.immutable.persistentListOf

class ListenPreviewData : PreviewParameterProvider<ListenState> {
    override val values = sequenceOf(
        ListenState(
            sections = persistentListOf(
                section(CatalogSectionKind.Stations, "live", "Piano Room", "Live"),
                section(CatalogSectionKind.New, "new", "New This Week", "6 tracks · 19:20"),
            ),
        ),
        ListenState(),
        ListenState(
            sections = persistentListOf(
                CatalogSectionUi(CatalogSectionKind.New, isLoading = false, failed = true),
            ),
        ),
        ListenState(searchVisible = true),
    )

    private fun section(kind: CatalogSectionKind, id: String, title: String, subtitle: String) = CatalogSectionUi(
        kind = kind,
        collections = persistentListOf(CollectionUi(id, title, UiText.Raw(subtitle), title)),
        isLoading = false,
    )
}
