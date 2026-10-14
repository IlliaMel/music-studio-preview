package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.navigation.ShellVariant
import com.illiamelnyk.studio.navigation.ShellVariantSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.dsl.module

/** Release builds: always the default shell variant. */
val buildTypeModule = module {
    single<ShellVariantSource> { FixedShellVariant }
}

private object FixedShellVariant : ShellVariantSource {
    override val variant: Flow<ShellVariant> = flowOf(ShellVariant.Default)
}
