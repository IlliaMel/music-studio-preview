package com.illiamelnyk.studio.feature.listen.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ListenViewModel(private val catalog: CatalogRepository, private val playback: PlaybackController) :
    MviViewModel<ListenState, ListenIntent, ListenEffect>(ListenState()) {
    private val jobs = mutableMapOf<CatalogSectionKind, Job>()
    private val collections = mutableMapOf<String, CatalogCollection>()

    init {
        CatalogSectionKind.entries.forEach { load(it) }
    }

    override fun onIntent(intent: ListenIntent) = when (intent) {
        is ListenIntent.CollectionClicked -> open(intent.id)
        is ListenIntent.RetrySection -> load(intent.kind, forceRefresh = true)
        ListenIntent.SearchClicked -> setState { copy(searchVisible = true) }
        ListenIntent.DismissSearch -> setState { copy(searchVisible = false) }
    }

    private fun load(kind: CatalogSectionKind, forceRefresh: Boolean = false) {
        jobs.remove(kind)?.cancel()
        updateSection(CatalogSectionUi(kind))
        jobs[kind] = viewModelScope.launch {
            val watchdog = launch {
                delay(8_000)
                updateSection(CatalogSectionUi(kind, isLoading = false, failed = true))
            }
            try {
                if (forceRefresh) catalog.refresh()
                catalog.observeSection(kind).collect { section ->
                    watchdog.cancel()
                    section.collections.forEach { collections[it.id] = it }
                    updateSection(
                        CatalogSectionUi(
                            kind,
                            section.collections.map(CatalogCollection::toUi).toImmutableList(),
                            false,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateSection(CatalogSectionUi(kind, isLoading = false, failed = true))
            } finally {
                watchdog.cancel()
            }
        }
    }

    private fun updateSection(section: CatalogSectionUi) = setState {
        copy(sections = sections.map { if (it.kind == section.kind) section else it }.toImmutableList())
    }

    private fun open(id: String) {
        val collection = collections[id] ?: return
        if (collection.isStation && collection.tracks.isNotEmpty()) {
            playback.setShuffle(true)
            playback.playStation(collection.title, collection.tracks)
            sendEffect(ListenEffect.OpenPlayer)
        } else {
            sendEffect(ListenEffect.OpenCollection(id))
        }
    }
}
