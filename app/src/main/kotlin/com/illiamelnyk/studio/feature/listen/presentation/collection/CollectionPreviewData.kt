package com.illiamelnyk.studio.feature.listen.presentation.collection

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.feature.listen.presentation.CollectionUi
import kotlinx.collections.immutable.persistentListOf

/** A collection with one track playing, and before it arrives. */
class CollectionPreviewData : PreviewParameterProvider<CollectionState> {
    override val values = sequenceOf(
        CollectionState(
            collection = CollectionUi(
                id = "trending",
                title = "Trending Now",
                subtitle = UiText.Plural(R.plurals.listen_collection_summary, 2, 2, "6:42"),
                coverSeed = "Trending Now",
            ),
            tracks = persistentListOf(track("glass-harbor", "Glass Harbor", "Mira Vale · 3:12")),
            playingTrackId = TrackId("glass-harbor"),
        ),
        CollectionState(),
    )

    private fun track(id: String, title: String, subtitle: String) = TrackUi(
        id = TrackId(id),
        title = title,
        subtitle = UiText.Raw(subtitle),
        coverSeed = title,
        status = TrackUi.Status.Ready,
    )
}
