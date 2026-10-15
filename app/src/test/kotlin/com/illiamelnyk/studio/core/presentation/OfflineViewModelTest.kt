package com.illiamelnyk.studio.core.presentation

import app.cash.turbine.test
import com.illiamelnyk.studio.core.domain.network.NetworkMonitor
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OfflineViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `returning network recovers automatically and Retry refreshes connectivity`() = runTest(main.testDispatcher) {
        val monitor = object : NetworkMonitor {
            override val isOnline = MutableStateFlow(false)
            var refreshCount = 0
            override fun refresh() {
                refreshCount++
            }
        }
        val vm = OfflineViewModel(monitor)
        vm.isOnline.test {
            assertEquals(true, awaitItem())
            assertEquals(false, awaitItem())
            vm.retry()
            assertEquals(1, monitor.refreshCount)
            monitor.isOnline.value = true
            assertEquals(true, awaitItem())
        }
    }
}
