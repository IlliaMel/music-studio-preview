package com.illiamelnyk.studio.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * Navigation of the signed-in app: one back stack per tab and the selected tab, in Compose state,
 * so whatever reads them recomposes when they change.
 *
 * The app is left through the start tab ("exit through home"): back on the first screen of
 * another tab goes to [TopLevelTab.Start], and back on the start tab's first screen leaves.
 * Switching tabs keeps each tab's stack as it was.
 *
 * @param tabs the tabs of the current [ShellVariant]; the start tab comes first.
 * @param backStacks one stack per tab in [tabs], each starting with the tab's root.
 */
@Stable
class TabNavigator(
    val tabs: List<TopLevelTab>,
    private val backStacks: Map<TopLevelTab, MutableList<NavKey>>,
    selectedTab: MutableState<TopLevelTab>,
) {
    init {
        require(tabs.firstOrNull() == TopLevelTab.Start) { "The start tab must come first: $tabs" }
        require(backStacks.keys == tabs.toSet()) { "Every tab needs a back stack: $tabs" }
    }

    var selectedTab: TopLevelTab by selectedTab
        private set

    /** The tabs whose screens are shown, bottom to top: the start tab, then the selected one. */
    val tabsInUse: List<TopLevelTab>
        get() = if (selectedTab == TopLevelTab.Start) listOf(selectedTab) else listOf(TopLevelTab.Start, selectedTab)

    /** The destination on screen. */
    val current: NavKey get() = backStacks.getValue(selectedTab).last()

    /** Whether the selected tab shows its first screen, which the shell's bars belong to. */
    val isAtTabRoot: Boolean get() = backStacks.getValue(selectedTab).size == 1

    /** Shows [tab] as it was left. Selecting the tab that is already shown goes back to its root. */
    fun select(tab: TopLevelTab) {
        require(tab in tabs) { "$tab is not a tab of $tabs" }
        if (tab == selectedTab) {
            backStacks.getValue(tab).popToRoot()
        } else {
            selectedTab = tab
        }
    }

    /**
     * Opens [destination]: the root of a tab selects that tab, anything else goes on top of the
     * selected tab. A destination is in one stack at a time, so opening it from another tab moves it.
     */
    fun navigate(destination: NavKey) {
        val tab = tabs.firstOrNull { it.root == destination }
        if (tab != null) {
            selectedTab = tab
            return
        }
        val stack = backStacks.getValue(selectedTab)
        if (stack.last() == destination) return
        backStacks.values.forEach { it.remove(destination) }
        stack.add(destination)
    }

    /** Goes one step back. `false` when there is nowhere to go: the app should close. */
    fun goBack(): Boolean {
        val stack = backStacks.getValue(selectedTab)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            selectedTab != TopLevelTab.Start -> selectedTab = TopLevelTab.Start
            else -> return false
        }
        return true
    }

    /** The back stack of [tab], to turn into screens. */
    fun backStack(tab: TopLevelTab): List<NavKey> = backStacks.getValue(tab)

    private fun MutableList<NavKey>.popToRoot() {
        while (size > 1) removeAt(lastIndex)
    }
}

/**
 * A [TabNavigator] for [tabs] whose stacks and selection survive configuration changes and process
 * death.
 */
@Composable
fun rememberTabNavigator(tabs: List<TopLevelTab>): TabNavigator {
    val selectedTab = rememberSaveable { mutableStateOf(TopLevelTab.Start) }
    // Each tab's stack is a separate saveable, so a tab keeps its screens while another is shown;
    // keyed by tab so each one is saved and restored under its own key.
    val backStacks = tabs.associateWith { tab -> key(tab) { rememberNavBackStack(tab.root) } }
    return remember(tabs) { TabNavigator(tabs, backStacks, selectedTab) }
}
