package com.illiamelnyk.studio.core.presentation

import app.cash.turbine.test
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MviViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `concurrent state updates are all applied`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = CounterViewModel()

        // Real threads: a read-then-write update loses increments here, an atomic one never does.
        withContext(Dispatchers.Default) {
            repeat(UPDATES) { launch { viewModel.onIntent(CounterIntent.Increment) } }
        }

        assertEquals(CounterState(count = UPDATES), viewModel.state.value)
    }

    @Test
    fun `an effect reaches one collector and is not replayed to the next`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = CounterViewModel()

            viewModel.effects.test {
                viewModel.onIntent(CounterIntent.Share)
                assertEquals(CounterEffect.Shared(count = 0), awaitItem())
            }
            // A new screen instance, after rotation for example, must not act on it again.
            viewModel.effects.test {
                expectNoEvents()
            }
        }

    @Test
    fun `effects sent while nobody collects wait and arrive in order`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = CounterViewModel()

        viewModel.onIntent(CounterIntent.Share)
        viewModel.onIntent(CounterIntent.Increment)
        viewModel.onIntent(CounterIntent.Share)

        viewModel.effects.test {
            assertEquals(CounterEffect.Shared(count = 0), awaitItem())
            assertEquals(CounterEffect.Shared(count = 1), awaitItem())
        }
    }

    private data class CounterState(val count: Int = 0)

    private sealed interface CounterIntent {
        data object Increment : CounterIntent

        data object Share : CounterIntent
    }

    private sealed interface CounterEffect {
        data class Shared(val count: Int) : CounterEffect
    }

    private class CounterViewModel : MviViewModel<CounterState, CounterIntent, CounterEffect>(CounterState()) {
        override fun onIntent(intent: CounterIntent) = when (intent) {
            CounterIntent.Increment -> setState { copy(count = count + 1) }
            CounterIntent.Share -> sendEffect(CounterEffect.Shared(state.value.count))
        }
    }

    private companion object {
        const val UPDATES = 10_000
    }
}
