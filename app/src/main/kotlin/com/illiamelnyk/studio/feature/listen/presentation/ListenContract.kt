package com.illiamelnyk.studio.feature.listen.presentation

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class CatalogSectionUi(
    val kind: CatalogSectionKind,
    val collections: ImmutableList<CollectionUi> = persistentListOf(),
    val isLoading: Boolean = true,
    val failed: Boolean = false,
)

@Immutable
data class ListenState(
    val sections: ImmutableList<CatalogSectionUi> = CatalogSectionKind.entries.map { CatalogSectionUi(it) }.toImmutableList(),
    val searchVisible: Boolean = false,
)

sealed interface ListenIntent {
    data class CollectionClicked(val id: String) : ListenIntent
    data class RetrySection(val kind: CatalogSectionKind) : ListenIntent
    data object SearchClicked : ListenIntent
    data object DismissSearch : ListenIntent
}

sealed interface ListenEffect {
    data class OpenCollection(val id: String) : ListenEffect
    data object OpenPlayer : ListenEffect
}
