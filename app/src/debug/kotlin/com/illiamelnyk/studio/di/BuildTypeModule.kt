package com.illiamelnyk.studio.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.illiamelnyk.studio.debug.DebugShellVariantStore
import com.illiamelnyk.studio.navigation.ShellVariantSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** Debug builds: the shell variant can be switched, and the choice is kept in its own file. */
val buildTypeModule = module {
    single(named(DEBUG_SETTINGS)) {
        PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile(DEBUG_SETTINGS) }
    }
    single { DebugShellVariantStore(get(named(DEBUG_SETTINGS))) } bind ShellVariantSource::class
}

private const val DEBUG_SETTINGS = "debug_settings"
