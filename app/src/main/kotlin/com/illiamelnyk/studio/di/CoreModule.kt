package com.illiamelnyk.studio.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.illiamelnyk.studio.core.data.generation.AndroidGenerationWorkScheduler
import com.illiamelnyk.studio.core.data.local.BearerTokenProvider
import com.illiamelnyk.studio.core.data.local.DurableStudioRepository
import com.illiamelnyk.studio.core.data.local.StudioApi
import com.illiamelnyk.studio.core.data.local.StudioStore
import com.illiamelnyk.studio.core.data.local.UnavailableBearerTokenProvider
import com.illiamelnyk.studio.core.data.network.AndroidNetworkMonitor
import com.illiamelnyk.studio.core.data.notification.AndroidGenerationNotifications
import com.illiamelnyk.studio.core.data.playback.AndroidAudioExporter
import com.illiamelnyk.studio.core.data.playback.AndroidPlaybackController
import com.illiamelnyk.studio.core.data.playback.LocalLyricsRepository
import com.illiamelnyk.studio.core.data.playback.PlaybackAutoplayCoordinator
import com.illiamelnyk.studio.core.data.settings.DataStoreThemePreferenceRepository
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.network.NetworkMonitor
import com.illiamelnyk.studio.core.domain.notification.GenerationNotificationTargets
import com.illiamelnyk.studio.core.domain.notification.GenerationNotifications
import com.illiamelnyk.studio.core.domain.notification.OpenGenerationNotification
import com.illiamelnyk.studio.core.domain.playback.AudioExporter
import com.illiamelnyk.studio.core.domain.playback.AutoplayCatalog
import com.illiamelnyk.studio.core.domain.playback.LyricsRepository
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playlist.PlaylistRepository
import com.illiamelnyk.studio.core.domain.session.DeleteAccount
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.session.SignOut
import com.illiamelnyk.studio.core.domain.settings.ThemePreferenceRepository
import com.illiamelnyk.studio.core.domain.track.GenerationWorkScheduler
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.presentation.OfflineViewModel
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.core.presentation.audio.AudioActions
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val coreModule = module {
    // Work that outlives every screen, such as generations and the player's clock. A failure in one
    // job doesn't cancel the others.
    single(named(APP_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<Clock> { Clock.System }

    single { StudioStore(androidContext()) }
    single<BearerTokenProvider> { UnavailableBearerTokenProvider }
    single { StudioApi(androidContext(), bearerTokens = get()) }
    single<GenerationWorkScheduler> { AndroidGenerationWorkScheduler(androidContext()) }
    single<GenerationNotifications> { AndroidGenerationNotifications(androidContext(), get()) }
    singleOf(::GenerationNotificationTargets)
    factoryOf(::OpenGenerationNotification)
    single {
        DurableStudioRepository(
            androidContext(),
            get(),
            get(),
            get(named(APP_SCOPE)),
            generationWork = get(),
            notifications = get(),
        )
    }
    single<SessionRepository> { get<DurableStudioRepository>() }
    single<CreditsRepository> { get<DurableStudioRepository>() }
    single<TrackRepository> { get<DurableStudioRepository>() }
    single {
        val repository: DurableStudioRepository = get()
        AndroidPlaybackController(
            androidContext(),
            get(named(APP_SCOPE)),
            ownerId = { repository.activeAccountId },
            audioHeaders = repository::playbackHeaders,
        )
    } bind PlaybackController::class
    single<AutoplayCatalog> {
        val catalog: CatalogRepository = get()
        AutoplayCatalog {
            catalog.observeCollections().first().flatMap { it.tracks }.distinctBy { it.id }
        }
    }
    single(createdAtStart = true) {
        val repository: DurableStudioRepository = get()
        PlaybackAutoplayCoordinator(get(), get(), get(named(APP_SCOPE))) { repository.activeAccountId }
    }
    singleOf(::LocalLyricsRepository) bind LyricsRepository::class
    single<AudioExporter> {
        val repository: DurableStudioRepository = get()
        AndroidAudioExporter(
            androidContext(),
            ownerId = { repository.activeAccountId },
            authorization = repository::exportHeaders,
        )
    }
    factoryOf(::AudioActions)
    single<PlaylistRepository> { get<DurableStudioRepository>() }
    factoryOf(::SignOut)
    factoryOf(::DeleteAccount)
    single { ToastController(get(named(APP_SCOPE))) }
    single { AndroidNetworkMonitor(androidContext()) } bind NetworkMonitor::class
    viewModelOf(::OfflineViewModel)

    single(named(SETTINGS)) {
        PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile(SETTINGS) }
    }
    single { DataStoreThemePreferenceRepository(get(named(SETTINGS))) } bind ThemePreferenceRepository::class
}

private const val APP_SCOPE = "appScope"
private const val SETTINGS = "settings"
