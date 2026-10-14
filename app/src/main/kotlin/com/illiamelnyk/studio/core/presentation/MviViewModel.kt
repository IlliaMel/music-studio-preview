package com.illiamelnyk.studio.core.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The base of every screen's ViewModel: one immutable [state] the screen draws, one entry point
 * [onIntent] for what the user did, and [effects] for one-off events such as navigation.
 *
 * @param S the screen state, an immutable data class.
 * @param I what the user can do on the screen.
 * @param E one-off events for the screen's Route; `Nothing` when there are none.
 */
abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel() {

    private val mutableState = MutableStateFlow(initialState)

    /** What the screen draws now; a new collector gets the current value first. */
    val state: StateFlow<S> = mutableState.asStateFlow()

    // A buffered channel hands each effect to exactly one collector and keeps it while there is
    // none (during a configuration change, for example), so an effect is neither lost nor replayed.
    private val effectChannel = Channel<E>(Channel.BUFFERED)

    /** One-off events, each delivered once. Collect them with [ObserveEffects]. */
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    /** Handles what the user did. The only way the screen talks to its ViewModel. */
    abstract fun onIntent(intent: I)

    /**
     * Replaces the state with [reduce] applied to the current one. The update is atomic: concurrent
     * calls never lose each other's changes, so [reduce] may run more than once and must be pure.
     */
    protected fun setState(reduce: S.() -> S) {
        mutableState.update(reduce)
    }

    /** Sends [effect] to the Route; it waits in the buffer until the Route collects it. */
    protected fun sendEffect(effect: E) {
        viewModelScope.launch { effectChannel.send(effect) }
    }
}
