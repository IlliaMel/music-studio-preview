package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.feature.create.data.DataStoreNotificationChoice
import com.illiamelnyk.studio.feature.create.domain.GenerateTracks
import com.illiamelnyk.studio.feature.create.domain.NotificationChoice
import com.illiamelnyk.studio.feature.create.domain.PlayNext
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.domain.TrackRenames
import com.illiamelnyk.studio.feature.create.presentation.CreateViewModel
import com.illiamelnyk.studio.feature.create.presentation.tool.ToolViewModel
import com.illiamelnyk.studio.feature.create.presentation.tools.ToolsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Create tab and its tools. A tool's screen gets the tool as a parameter. */
val createModule = module {
    single { TrackRenames() }
    single { DataStoreNotificationChoice(get(named("settings"))) } bind NotificationChoice::class
    factoryOf(::GenerateTracks)
    factoryOf(::PlayNext)
    viewModelOf(::ToolsViewModel)
    viewModelOf(::CreateViewModel)
    viewModel { (tool: Tool) -> ToolViewModel(tool) }
}
