package com.illiamelnyk.studio.feature.listen.presentation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import kotlinx.collections.immutable.persistentListOf

/** Listen with the catalog, and before it arrives. */
class ListenPreviewData : PreviewParameterProvider<ListenState> {
    override val values = sequenceOf(
        ListenState(
            collections = persistentListOf(
                collection("trending", "Trending Now", 6, "19:20"),
                collection("new", "New This Week", 6, "19:41"),
                collection("vocals", "Sing Along", 7, "23:22"),
            ),
        ),
        ListenState(),
    )

    private fun collection(id: String, title: String, count: Int, duration: String) = CollectionUi(
        id = id,
        title = title,
        subtitle = UiText.Plural(R.plurals.listen_collection_summary, count, count, duration),
        coverSeed = title,
    )
}
