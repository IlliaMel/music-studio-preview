package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.core.presentation.OfflineViewModel
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.feature.auth.presentation.welcome.WelcomeViewModel
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.presentation.playlist.PlaylistPickerViewModel
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.CreateViewModel
import com.illiamelnyk.studio.feature.create.presentation.tool.ToolViewModel
import com.illiamelnyk.studio.feature.create.presentation.tools.ToolsViewModel
import com.illiamelnyk.studio.feature.library.presentation.LibraryViewModel
import com.illiamelnyk.studio.feature.listen.presentation.ListenViewModel
import com.illiamelnyk.studio.feature.listen.presentation.collection.CollectionViewModel
import com.illiamelnyk.studio.feature.player.presentation.PlayerViewModel
import com.illiamelnyk.studio.feature.player.presentation.mini.MiniPlayerViewModel
import com.illiamelnyk.studio.feature.profile.presentation.ProfileViewModel
import com.illiamelnyk.studio.navigation.AppViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KoinGraphTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every constructor finds its dependencies in the graph`() {
        // One module that includes them all: verified one by one, a feature module would miss the
        // repositories that coreModule provides.
        module { includes(studioModules) }.verify(
            injections = injectedParameters(
                definition<ToolViewModel>(Tool::class),
                definition<CollectionViewModel>(String::class),
                definition<PlaylistPickerViewModel>(Track::class),
            ),
        )
    }

    @Test
    fun `every ViewModel can be created from the graph`() {
        val koin = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(studioModules)
        }.koin

        // Resolving is the check: the static verification above can't see a wrong qualifier, a
        // parameter of the wrong type or a definition that throws, and each of these fails here. The
        // ViewModels need every repository, so the whole graph is built.
        koin.get<AppViewModel>()
        koin.get<OfflineViewModel>()
        koin.get<ToastController>()
        koin.get<WelcomeViewModel>()
        koin.get<CreateViewModel>()
        koin.get<ToolsViewModel>()
        koin.get<ToolViewModel> { parametersOf(Tool.Genreshift) }
        koin.get<ListenViewModel>()
        koin.get<CollectionViewModel> { parametersOf("trending") }
        koin.get<LibraryViewModel>()
        koin.get<PlaylistPickerViewModel> { parametersOf(FakeCatalog.tracks.first()) }
        koin.get<PlayerViewModel>()
        koin.get<MiniPlayerViewModel>()
        koin.get<ProfileViewModel>()
    }
}
