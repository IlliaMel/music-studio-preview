package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.core.data.local.ApiAuthMode
import com.illiamelnyk.studio.core.data.local.DurableStudioRepository
import com.illiamelnyk.studio.core.data.local.StudioApi
import com.illiamelnyk.studio.feature.auth.data.DevelopmentAuthRepository
import com.illiamelnyk.studio.feature.auth.data.FirebaseClient
import com.illiamelnyk.studio.feature.auth.data.FirebaseConfiguration
import com.illiamelnyk.studio.feature.auth.data.FirebaseGoogleAuthRepository
import com.illiamelnyk.studio.feature.auth.data.FirebaseSdkClient
import com.illiamelnyk.studio.feature.auth.domain.AuthRepository
import com.illiamelnyk.studio.feature.auth.presentation.welcome.GoogleCredentialRequest
import com.illiamelnyk.studio.feature.auth.presentation.welcome.WelcomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Credential UI lives in WelcomeRoute; the Firebase SDK initializes only when used. */
val authModule = module {
    single { FirebaseConfiguration.fromBuild() }
    single<FirebaseClient> { FirebaseSdkClient(androidContext(), get()) }
    factory { GoogleCredentialRequest(get<FirebaseConfiguration>().webClientId) }
    single<AuthRepository> {
        val data: DurableStudioRepository = get()
        if (get<StudioApi>().configuration.authMode == ApiAuthMode.Development) {
            DevelopmentAuthRepository(data)
        } else {
            FirebaseGoogleAuthRepository(
                get(),
                get<FirebaseConfiguration>().isConfigured && get<StudioApi>().isAvailable,
                data::completeFirebaseSignIn,
                data::isFirebaseSessionActive,
                retainForDeletion = data::needsFirebaseDeletionCredentials,
                prepareSwitch = data::prepareFirebaseSignIn,
            )
        }
    }
    viewModelOf(::WelcomeViewModel)
}
