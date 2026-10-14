package com.illiamelnyk.studio.feature.listen.presentation.collection

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.track.toUi
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import com.illiamelnyk.studio.feature.listen.presentation.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class CollectionViewModel(collectionId: String, catalog: CatalogRepository, private val playback: PlaybackController) :
    MviViewModel<CollectionState, CollectionIntent, CollectionEffect>(CollectionState()) {

    /** The tracks as the catalog last sent them: the queue when one is played. */
    private var queue: List<Track> = emptyList()

    init {
        combine(
            catalog.observeCollection(collectionId),
            playback.state.map { it.current?.id }.distinctUntilChanged(),
        ) { collection, playingId -> collection to playingId }
            .onEach { (collection, playingId) ->
                queue = collection?.tracks.orEmpty()
                setState {
                    copy(
                        collection = collection?.toUi(),
                        tracks = queue.map(Track::toUi).toImmutableList(),
                        playingTrackId = playingId,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: CollectionIntent) = when (intent) {
        CollectionIntent.BackClicked -> sendEffect(CollectionEffect.Close)
        CollectionIntent.PlayClicked -> play(startIndex = 0, shuffle = false)
        CollectionIntent.ShuffleClicked -> play(startIndex = queue.indices.randomOrNull() ?: -1, shuffle = true)
        is CollectionIntent.TrackClicked -> play(startIndex = indexOf(intent.id), shuffle = false)
    }

    private fun play(startIndex: Int, shuffle: Boolean) {
        if (startIndex !in queue.indices) return
        playback.setShuffle(shuffle)
        playback.play(queue, startIndex)
    }

    private fun indexOf(id: TrackId): Int = queue.indexOfFirst { it.id == id }
}
