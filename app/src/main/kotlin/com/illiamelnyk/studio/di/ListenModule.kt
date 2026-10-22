package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.feature.listen.data.CachedCatalogRepository
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import com.illiamelnyk.studio.feature.listen.presentation.ListenViewModel
import com.illiamelnyk.studio.feature.listen.presentation.collection.CollectionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Listen tab and its collections. A collection's screen gets the collection id as a parameter. */
val listenModule = module {
    single { CachedCatalogRepository(get(), get(), get(), get(), androidContext()) } bind CatalogRepository::class
    viewModelOf(::ListenViewModel)
    viewModel { (collectionId: String) -> CollectionViewModel(collectionId, get(), get()) }
}
