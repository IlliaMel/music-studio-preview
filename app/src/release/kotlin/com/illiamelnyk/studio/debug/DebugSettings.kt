package com.illiamelnyk.studio.debug

import androidx.compose.runtime.Composable

/** Release builds have no debug settings. */
val debugSettings: (@Composable () -> Unit)? = null
