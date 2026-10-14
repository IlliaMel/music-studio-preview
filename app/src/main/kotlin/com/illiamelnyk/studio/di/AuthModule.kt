package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.feature.auth.data.fake.FakeAuthRepository
import com.illiamelnyk.studio.feature.auth.domain.AuthRepository
import com.illiamelnyk.studio.feature.auth.presentation.onboarding.OnboardingViewModel
import com.illiamelnyk.studio.feature.auth.presentation.welcome.WelcomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Signing in and onboarding. */
val authModule = module {
    // The fake signs in through the session fake, the way Firebase Auth feeds the real session.
    single { FakeAuthRepository(get<FakeSessionRepository>()) } bind AuthRepository::class
    viewModelOf(::WelcomeViewModel)
    viewModelOf(::OnboardingViewModel)
}
