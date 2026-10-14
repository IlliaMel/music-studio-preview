package com.illiamelnyk.studio.di

import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.CreateViewModel
import com.illiamelnyk.studio.feature.create.presentation.tool.ToolViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The Create tab and its tools. A tool's screen gets the tool as a parameter. */
val createModule = module {
    viewModelOf(::CreateViewModel)
    viewModel { (tool: Tool) -> ToolViewModel(tool, get()) }
}
