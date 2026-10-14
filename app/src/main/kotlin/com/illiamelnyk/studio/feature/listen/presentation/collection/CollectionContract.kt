package com.illiamelnyk.studio.feature.listen.presentation.collection

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.feature.listen.presentation.CollectionUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A collection of the catalog: its header and its tracks.
 *
 * @param collection `null` until it arrives.
 */
@Immutable
data class CollectionState(
    val collection: CollectionUi? = null,
    val tracks: ImmutableList<TrackUi> = persistentListOf(),
    val playingTrackId: TrackId? = null,
)

sealed interface CollectionIntent {
    data object BackClicked : CollectionIntent

    data object PlayClicked : CollectionIntent

    data object ShuffleClicked : CollectionIntent

    data class TrackClicked(val id: TrackId) : CollectionIntent
}

sealed interface CollectionEffect {
    data object Close : CollectionEffect
}
