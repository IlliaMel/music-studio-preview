package com.illiamelnyk.studio.feature.create.presentation.common

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.ui.components.StudioToastKind

@Immutable
data class ToastUi(val title: UiText, val message: UiText, val kind: StudioToastKind = StudioToastKind.Info)
