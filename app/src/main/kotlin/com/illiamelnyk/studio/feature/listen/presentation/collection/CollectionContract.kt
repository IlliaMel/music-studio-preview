package com.illiamelnyk.studio.feature.listen.presentation.collection

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.feature.listen.presentation.CollectionUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * A collection of the catalog: its header and its tracks.
 *
 * @param collection `null` until it arrives.
 */
@Immutable
data class CollectionState(
    val collection: CollectionUi? = null,
    val tracks: ImmutableList<TrackUi> = persistentListOf(),
    val versions: ImmutableMap<TrackId, UiText> = persistentMapOf(),
    val playingTrackId: TrackId? = null,
    val related: ImmutableList<CollectionUi> = persistentListOf(),
    val pickerTrack: Track? = null,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
)

sealed interface CollectionIntent {
    data object BackClicked : CollectionIntent

    data object PlayClicked : CollectionIntent

    data object ShuffleClicked : CollectionIntent

    data object Retry : CollectionIntent
    data object DismissPicker : CollectionIntent
    data class TrackMoreClicked(val id: TrackId) : CollectionIntent
    data class RelatedClicked(val id: String) : CollectionIntent

    data class TrackClicked(val id: TrackId) : CollectionIntent
}

sealed interface CollectionEffect {
    data object Close : CollectionEffect
    data object OpenPlayer : CollectionEffect
    data class OpenCollection(val id: String) : CollectionEffect
}
