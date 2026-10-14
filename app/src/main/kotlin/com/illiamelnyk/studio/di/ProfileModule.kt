package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.feature.profile.presentation.ProfileViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The profile. */
val profileModule = module {
    viewModelOf(::ProfileViewModel)
}
