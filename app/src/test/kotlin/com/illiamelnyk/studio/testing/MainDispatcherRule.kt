package com.illiamelnyk.studio.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Makes [testDispatcher] the main dispatcher, which `viewModelScope` runs on. Pass it to
 * `runTest(mainDispatcherRule.testDispatcher)` so the test and the ViewModel share one scheduler.
 */
class MainDispatcherRule(val testDispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
