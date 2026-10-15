package com.illiamelnyk.studio.feature.create.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * [value], or the last value it had before it turned `null`: what a sheet keeps showing while it
 * slides away after its state has closed it. It changes only when [value] does, so a plain holder
 * is enough; nothing has to observe it.
 */
@Composable
fun <T : Any> rememberLatest(value: T?): T? {
    val latest = remember { LatestHolder<T>() }
    if (value != null) latest.value = value
    return latest.value
}

private class LatestHolder<T : Any> {
    var value: T? = null
}
