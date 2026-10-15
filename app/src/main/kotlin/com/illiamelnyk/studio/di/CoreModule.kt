package com.illiamelnyk.studio.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakeLyricsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.data.network.AndroidNetworkMonitor
import com.illiamelnyk.studio.core.data.settings.DataStoreThemePreferenceRepository
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.network.NetworkMonitor
import com.illiamelnyk.studio.core.domain.playback.LyricsRepository
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.session.DeleteAccount
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.session.SignOut
import com.illiamelnyk.studio.core.domain.settings.ThemePreferenceRepository
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.presentation.OfflineViewModel
import com.illiamelnyk.studio.core.presentation.ToastController
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * What several features share: the session, tracks, credits, playback and settings. The fakes
 * stand in for the backend until it is wired up; each is bound to its domain interface, so
 * swapping one for the real implementation changes only this module.
 */
val coreModule = module {
    // Work that outlives every screen, such as generations and the player's clock. A failure in one
    // job doesn't cancel the others.
    single(named(APP_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<Clock> { Clock.System }

    singleOf(::FakeSessionRepository) bind SessionRepository::class
    singleOf(::FakeCreditsRepository) bind CreditsRepository::class
    single { FakeTrackRepository(get(), get(), get(named(APP_SCOPE)), get()) } bind TrackRepository::class
    single { FakePlaybackController(get(named(APP_SCOPE))) } bind PlaybackController::class
    singleOf(::FakeLyricsRepository) bind LyricsRepository::class
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
