package com.illiamelnyk.studio.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.illiamelnyk.studio.core.ui.theme.StudioMotion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TabTransitionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun selectingListenShowsItsLoadingShellWithoutWaitingForTheOldFade() {
        var listen by mutableStateOf(false)
        val transitions = ShellTransitions(StudioMotion(), LayoutDirection.Ltr)
        composeRule.setContent {
            val entries = buildList {
                add(NavEntry<NavKey>(Destination.Create, metadata = transitions.tabRoot) { BasicText("Create") })
                if (listen) {
                    add(
                        NavEntry<NavKey>(Destination.Listen, metadata = transitions.tabRoot) {
                            BasicText("Listen skeleton")
                        },
                    )
                }
            }
            NavDisplay(
                entries = entries,
                modifier = Modifier.fillMaxSize(),
                onBack = {},
                transitionSpec = transitions.push,
                popTransitionSpec = transitions.pop,
            )
        }
        composeRule.onNodeWithText("Create").assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { listen = true }
        repeat(6) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("Listen skeleton").assertIsDisplayed()
        composeRule.onNodeWithText("Create").assertDoesNotExist()
    }
}
