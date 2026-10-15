package com.illiamelnyk.studio.feature.listen.presentation.collection

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.toUi
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import com.illiamelnyk.studio.feature.listen.presentation.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CollectionViewModel(
    private val collectionId: String,
    private val catalog: CatalogRepository,
    private val playback: PlaybackController,
) : MviViewModel<CollectionState, CollectionIntent, CollectionEffect>(CollectionState()) {
    private var queue: List<Track> = emptyList()
    private var loadJob: Job? = null

    init {
        load()
        playback.state.map { it.current?.id }.distinctUntilChanged().onEach {
            setState { copy(playingTrackId = it) }
        }.launchIn(viewModelScope)
        catalog.observeCollections().catch { emit(emptyList()) }.onEach { list ->
            setState {
                copy(
                    related = list.filter {
                        it.id != collectionId && it.id != state.value.collection?.id &&
                            !it.isStation
                    }.take(4).map { it.toUi() }.toImmutableList(),
                )
            }
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: CollectionIntent) = when (intent) {
        CollectionIntent.BackClicked -> sendEffect(CollectionEffect.Close)

        CollectionIntent.PlayClicked -> play(startIndex = 0, shuffle = false)

        CollectionIntent.ShuffleClicked -> play(startIndex = queue.indices.randomOrNull() ?: -1, shuffle = true)

        is CollectionIntent.TrackClicked -> play(
            startIndex = queue.indexOfFirst {
                it.id == intent.id
            },
            shuffle = false,
        )

        is CollectionIntent.TrackMoreClicked -> setState { copy(pickerTrack = queue.find { it.id == intent.id }) }

        CollectionIntent.DismissPicker -> setState { copy(pickerTrack = null) }

        is CollectionIntent.RelatedClicked -> sendEffect(CollectionEffect.OpenCollection(intent.id))

        CollectionIntent.Retry -> load()
    }

    private fun load() {
        loadJob?.cancel()
        setState { copy(isLoading = true, failed = false) }
        loadJob = viewModelScope.launch {
            val timeout = launch {
                delay(8_000)
                setState { copy(isLoading = false, failed = true) }
            }
            try {
                catalog.observeCollection(collectionId).collect { collection ->
                    timeout.cancel()
                    queue = collection?.tracks.orEmpty()
                    setState {
                        copy(
                            collection = collection?.toUi(),
                            tracks = queue.map(Track::toUi).toImmutableList(),
                            versions = queue.associate {
                                it.id to
                                    UiText.Resource(
                                        if (it.model ==
                                            MusicModel.V2
                                        ) {
                                            R.string.listen_model_v2
                                        } else {
                                            R.string.listen_model_v2_5
                                        },
                                    )
                            }.toImmutableMap(),
                            isLoading = false,
                            failed =
                                collection == null,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setState { copy(isLoading = false, failed = true) }
            } finally {
                timeout.cancel()
            }
        }
    }

    private fun play(startIndex: Int, shuffle: Boolean) {
        if (startIndex !in queue.indices) return
        playback.setShuffle(shuffle)
        playback.play(queue, startIndex)
        sendEffect(CollectionEffect.OpenPlayer)
    }
}
