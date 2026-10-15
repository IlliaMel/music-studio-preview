package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.core.data.local.DurableStudioRepository
import com.illiamelnyk.studio.feature.auth.data.DevelopmentAuthRepository
import com.illiamelnyk.studio.feature.auth.domain.AuthRepository
import com.illiamelnyk.studio.feature.auth.presentation.welcome.WelcomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Local server authentication and the offline demo. */
val authModule = module {
    single { DevelopmentAuthRepository(get<DurableStudioRepository>()) } bind AuthRepository::class
    viewModelOf(::WelcomeViewModel)
}
