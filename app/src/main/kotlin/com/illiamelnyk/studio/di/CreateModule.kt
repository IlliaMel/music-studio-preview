package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.feature.create.data.fake.FakeStyleCatalog
import com.illiamelnyk.studio.feature.create.domain.GenerateTracks
import com.illiamelnyk.studio.feature.create.domain.PlayNext
import com.illiamelnyk.studio.feature.create.domain.StyleCatalog
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.CreateViewModel
import com.illiamelnyk.studio.feature.create.presentation.tool.ToolViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Create tab and its tools. A tool's screen gets the tool as a parameter. */
val createModule = module {
    single { FakeStyleCatalog() } bind StyleCatalog::class
    factoryOf(::GenerateTracks)
    factoryOf(::PlayNext)
    viewModelOf(::CreateViewModel)
    viewModel { (tool: Tool) -> ToolViewModel(tool, get(), get()) }
}
