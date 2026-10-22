package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.navigation.AppViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The state around the screens: which flow the session allows, the theme, the shell. */
val navigationModule = module {
    viewModelOf(::AppViewModel)
}

/** Every module of the app, for `startKoin` and the graph test. */
val studioModules = listOf(
    coreModule,
    navigationModule,
    authModule,
    createModule,
    listenModule,
    libraryModule,
    playerModule,
    profileModule,
)
