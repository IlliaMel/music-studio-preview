package com.illiamelnyk.studio

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.illiamelnyk.studio.core.data.local.DurableStudioRepository
import com.illiamelnyk.studio.core.data.playback.PlaybackAutoplayCoordinator
import com.illiamelnyk.studio.di.studioModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/** Starts dependency injection before any screen needs it. */
class StudioApp : Application() {
    internal val startupStartedAt = SystemClock.elapsedRealtime()
    internal lateinit var startup: StudioStartup
        private set

    override fun onCreate() {
        super.onCreate()
        val container = startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@StudioApp)
            modules(studioModules)
        }.koin
        startup = StudioStartup(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            dataDispatcher = Dispatchers.IO,
            loadData = {
                container.get<DurableStudioRepository>()
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "StudioStartup",
                        "dataReadyMs=" + (SystemClock.elapsedRealtime() - startupStartedAt),
                    )
                }
            },
            startPlayback = {
                container.get<PlaybackAutoplayCoordinator>()
                if (BuildConfig.DEBUG) {
                    Log.d("StudioStartup", "playbackReadyMs=" + (SystemClock.elapsedRealtime() - startupStartedAt))
                }
            },
            onFailure = { Log.e("StudioStartup", "Initialization failed: " + it.javaClass.simpleName) },
        )
    }
}
