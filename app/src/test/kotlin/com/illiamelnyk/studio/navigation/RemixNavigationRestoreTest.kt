package com.illiamelnyk.studio.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemixNavigationRestoreTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun handledRemixDoesNotOverrideSavedTabWhenShellIsRecreated() {
        val revision = mutableIntStateOf(0)
        val shown = mutableStateOf(true)
        composeRule.setContent {
            StudioTheme {
                val savedState = rememberSaveableStateHolder()
                if (shown.value) {
                    savedState.SaveableStateProvider("owner-shell") {
                        val navigator = rememberTabNavigator()
                        ObserveRemixNavigation(revision.intValue, navigator)
                        StudioText(
                            navigator.selectedTab.name,
                            StudioTheme.typography.bodyMedium,
                            Modifier.testTag("selectedTab").clickable { navigator.select(TopLevelTab.Listen) },
                        )
                    }
                }
            }
        }
        composeRule.runOnIdle { revision.intValue = 1 }
        composeRule.onNodeWithTag("selectedTab").assertTextEquals(TopLevelTab.Create.name)
        composeRule.onNodeWithTag("selectedTab").performClick()
        composeRule.onNodeWithTag("selectedTab").assertTextEquals(TopLevelTab.Listen.name)
        // Recreate all effects and ordinary remember state, restoring saveables as Activity recreation does.
        composeRule.runOnIdle { shown.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { shown.value = true }
        composeRule.onNodeWithTag("selectedTab").assertTextEquals(TopLevelTab.Listen.name)
        composeRule.runOnIdle { revision.intValue = 2 }
        composeRule.onNodeWithTag("selectedTab").assertTextEquals(TopLevelTab.Create.name)
        composeRule.onNodeWithTag("selectedTab").performClick()
        composeRule.runOnIdle { revision.intValue = 0 }
        composeRule.onNodeWithTag("selectedTab").assertTextEquals(TopLevelTab.Listen.name)
        composeRule.runOnIdle { revision.intValue = 1 }
        composeRule.onNodeWithTag("selectedTab").assertTextEquals(TopLevelTab.Create.name)
    }
}
