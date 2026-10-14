package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.feature.player.presentation.PlayerViewModel
import com.illiamelnyk.studio.feature.player.presentation.mini.MiniPlayerViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The full player and the mini player. */
val playerModule = module {
    viewModelOf(::PlayerViewModel)
    viewModelOf(::MiniPlayerViewModel)
}
