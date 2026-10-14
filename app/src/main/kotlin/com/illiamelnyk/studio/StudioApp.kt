package com.illiamelnyk.studio

import android.app.Application
import com.illiamelnyk.studio.di.studioModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/** Starts dependency injection before any screen needs it. */
class StudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@StudioApp)
            modules(studioModules)
        }
    }
}
