package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.feature.library.presentation.LibraryViewModel
import org.koin.core.module.dsl.viewModel
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.presentation.playlist.PlaylistPickerViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Library. */
val libraryModule = module {
    viewModelOf(::LibraryViewModel)
    viewModel { (track: Track) -> PlaylistPickerViewModel(track, get(), get()) }
}
