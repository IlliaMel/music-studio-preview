package com.illiamelnyk.studio.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OwnerScopedTabsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sameOwnerKeepsPrivateViewModelAndDirectOwnerChangeClearsIt() {
        val owner = mutableStateOf("owner-a")
        val noise = mutableIntStateOf(0)
        val created = mutableListOf<PrivatePromptViewModel>()
        var cleared = 0
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PrivatePromptViewModel { cleared++ }.also { created += it } as T
        }
        composeRule.setContent {
            StudioTheme {
                OwnerScopedTabs(owner.value) {
                    val entries = rememberDecoratedNavEntries(
                        backStack = listOf<NavKey>(Destination.Create),
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = { destination ->
                            NavEntry(destination) {
                                val storeOwner = requireNotNull(LocalViewModelStoreOwner.current)
                                val model = ViewModelProvider(storeOwner, factory)[PrivatePromptViewModel::class.java]
                                StudioText(
                                    model.prompt,
                                    StudioTheme.typography.bodyMedium,
                                    Modifier.testTag("privatePrompt").clickable {
                                        model.prompt = "Private owner A prompt"
                                    },
                                )
                            }
                        },
                    )
                    NavDisplay(entries = entries, onBack = {}, modifier = Modifier.testTag("shell-" + noise.intValue))
                }
            }
        }
        composeRule.onNodeWithTag("privatePrompt").performClick()
        composeRule.onNodeWithTag("privatePrompt").assertTextEquals("Private owner A prompt")
        composeRule.runOnIdle { noise.intValue++ }
        composeRule.onNodeWithTag("privatePrompt").assertTextEquals("Private owner A prompt")
        composeRule.runOnIdle {
            assertEquals(1, created.size)
            assertEquals(0, cleared)
            owner.value = "owner-b"
        }
        composeRule.onNodeWithTag("privatePrompt").assertTextEquals("Empty")
        composeRule.runOnIdle {
            assertEquals(2, created.size)
            assertEquals(1, cleared)
            owner.value = "owner-a"
        }
        composeRule.onNodeWithTag("privatePrompt").assertTextEquals("Empty")
        composeRule.runOnIdle {
            assertEquals(3, created.size)
            assertEquals(2, cleared)
        }
    }

    @Test
    fun nonNavigationPlayerModelSurvivesActivityRecreationButClearsOnOwnerChange() {
        val owner = mutableStateOf("owner-a")
        val created = mutableListOf<PrivatePromptViewModel>()
        var cleared = 0
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PrivatePromptViewModel { cleared++ }.also { created += it } as T
        }
        // Players live outside NavDisplay, so they must receive the signed-in shell's store.
        val content: @Composable () -> Unit = {
            StudioTheme {
                OwnerScopedTabs(owner.value) {
                    val storeOwner = requireNotNull(LocalViewModelStoreOwner.current)
                    val model = ViewModelProvider(storeOwner, factory)[PrivatePromptViewModel::class.java]
                    StudioText(
                        model.prompt,
                        StudioTheme.typography.bodyMedium,
                        Modifier.testTag("privatePlayer").clickable { model.prompt = "Owner A player state" },
                    )
                }
            }
        }
        composeRule.activityRule.scenario.onActivity { it.setContent(content = content) }
        composeRule.onNodeWithTag("privatePlayer").performClick()
        composeRule.onNodeWithTag("privatePlayer").assertTextEquals("Owner A player state")
        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.onActivity { it.setContent(content = content) }
        composeRule.onNodeWithTag("privatePlayer").assertTextEquals("Owner A player state")
        composeRule.runOnIdle {
            assertEquals(1, created.size)
            assertEquals(0, cleared)
            owner.value = "owner-b"
        }
        composeRule.onNodeWithTag("privatePlayer").assertTextEquals("Empty")
        composeRule.runOnIdle {
            assertEquals(2, created.size)
            assertEquals(1, cleared)
            owner.value = "owner-a"
        }
        composeRule.onNodeWithTag("privatePlayer").assertTextEquals("Empty")
        composeRule.runOnIdle {
            assertEquals(3, created.size)
            assertEquals(2, cleared)
        }
    }

    private class PrivatePromptViewModel(private val disposed: () -> Unit) : ViewModel() {
        var prompt by mutableStateOf("Empty")
        override fun onCleared() = disposed()
    }
}
