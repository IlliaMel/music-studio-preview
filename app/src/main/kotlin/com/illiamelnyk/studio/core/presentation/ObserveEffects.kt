package com.illiamelnyk.studio.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Collects [effects] while the screen is at least started and passes each one to [onEffect].
 *
 * Collection stops in the background, so an effect sent then (navigation, for example) waits in
 * the ViewModel's buffer until the screen is visible again instead of acting on a hidden screen.
 */
@Composable
fun <E> ObserveEffects(effects: Flow<E>, onEffect: (E) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Main.immediate handles an effect in the same frame it is received: with a dispatched
            // hand-off, a collector cancelled in between would take the effect out of the channel
            // and drop it.
            withContext(Dispatchers.Main.immediate) {
                effects.collect { effect -> currentOnEffect(effect) }
            }
        }
    }
}
