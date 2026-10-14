package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.feature.library.presentation.LibraryViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Library. */
val libraryModule = module {
    viewModelOf(::LibraryViewModel)
}
