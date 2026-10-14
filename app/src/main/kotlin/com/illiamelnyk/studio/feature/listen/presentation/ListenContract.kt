package com.illiamelnyk.studio.feature.listen.presentation

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The Listen tab: the demo catalog's collections. */
@Immutable
data class ListenState(val collections: ImmutableList<CollectionUi> = persistentListOf())

sealed interface ListenIntent {
    data class CollectionClicked(val id: String) : ListenIntent
}

sealed interface ListenEffect {
    data class OpenCollection(val id: String) : ListenEffect
}
