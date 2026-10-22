package com.illiamelnyk.studio

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.illiamelnyk.studio.core.data.local.StudioAccountRuntime
import com.illiamelnyk.studio.core.data.playback.PlaybackAutoplayCoordinator
import com.illiamelnyk.studio.di.studioModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.qualifier.named

/** Starts dependency injection before any screen needs it. */
class StudioApp : Application() {
    private val foreground = MutableStateFlow(false)
    private var foregroundJob: Job? = null

    internal fun recordForeground(active: Boolean) {
        foreground.value = active
    }
    internal val startupStartedAt = SystemClock.elapsedRealtime()
    internal lateinit var startup: StudioStartup
        private set

    override fun onCreate() {
        super.onCreate()
        val container = startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@StudioApp)
            workManagerFactory()
            modules(studioModules)
        }.koin
        startup = StudioStartup(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            dataDispatcher = Dispatchers.IO,
            loadData = {
                val data = container.get<StudioAccountRuntime>()
                data.initialize()
                data.state.value?.takeIf { it.token != null && it.tracks.isEmpty() }?.let {
                    data.sync.requestInitialRefresh(it.session.user.id.value)
                }
                // Decode the bundled list artwork behind the launch screen, before the first UI.
                com.illiamelnyk.studio.core.ui.artwork.ArtworkLibraryCache.load(assets)
                data.sync.setForeground(foreground.value)
                data.sync.start(container.get(named("appScope")))
                foregroundJob?.cancel()
                foregroundJob = container.get<CoroutineScope>(named("appScope")).launch {
                    foreground.collect(data.sync::setForeground)
                }
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
            preserveCorruptLocalData = {
                com.illiamelnyk.studio.core.data.local.StudioStore.quarantineLegacyState(this@StudioApp)
            },
        )
    }
}
