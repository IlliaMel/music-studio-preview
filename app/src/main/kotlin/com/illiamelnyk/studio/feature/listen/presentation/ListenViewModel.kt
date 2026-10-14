package com.illiamelnyk.studio.feature.listen.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ListenViewModel(catalog: CatalogRepository) :
    MviViewModel<ListenState, ListenIntent, ListenEffect>(ListenState()) {

    init {
        catalog.observeCollections()
            .onEach { list ->
                setState { copy(collections = list.map(CatalogCollection::toUi).toImmutableList()) }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: ListenIntent) = when (intent) {
        is ListenIntent.CollectionClicked -> sendEffect(ListenEffect.OpenCollection(intent.id))
    }
}
