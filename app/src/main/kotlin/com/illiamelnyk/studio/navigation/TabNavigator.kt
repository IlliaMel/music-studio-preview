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
 * Create and Listen are the selectable tabs. The legacy Library stack is kept only to restore
 * saved navigation from older app versions; it is folded into Listen before screens are shown.
 *
 * @param backStacks a saved stack for every enum value, including the legacy Library slot.
 */
@Stable
class TabNavigator(
    private val backStacks: Map<TopLevelTab, MutableList<NavKey>>,
    selectedTab: MutableState<TopLevelTab>,
) {
    init {
        require(backStacks.keys == TopLevelTab.entries.toSet()) { "Every tab needs a back stack" }
        require(backStacks.all { (tab, stack) -> stack.firstOrNull() == tab.root }) {
            "Every stack starts with its tab's root"
        }
    }

    var selectedTab: TopLevelTab by selectedTab
        private set

    init {
        restoreLegacyLibraryStack()
    }

    /** The tabs whose screens are shown, bottom to top: the start tab, then the selected one. */
    val tabsInUse: List<TopLevelTab>
        get() = if (selectedTab == TopLevelTab.Start) listOf(selectedTab) else listOf(TopLevelTab.Start, selectedTab)

    /** The destination on screen. */
    val current: NavKey get() = backStacks.getValue(selectedTab).last()

    /** Whether the selected tab shows its first screen, which the shell's bars belong to. */
    val isAtTabRoot: Boolean get() = backStacks.getValue(selectedTab).size == 1

    /** Shows [tab] as it was left. Selecting the tab that is already shown goes back to its root. */
    fun select(tab: TopLevelTab) {
        require(tab in TopLevelTab.primaryTabs) { "$tab is not a selectable tab" }
        if (tab == selectedTab) {
            backStacks.getValue(tab).popToRoot()
        } else {
            if (backStacks.getValue(tab).size == 1) {
                NavigationRenderProbe.request(tab)
            } else {
                NavigationRenderProbe.cancel()
            }
            selectedTab = tab
        }
    }

    /**
     * Opens [destination]: the root of a tab selects that tab, anything else goes on top of the
     * selected tab. A destination is in one stack at a time, so opening it from another tab moves it.
     */
    fun navigate(destination: NavKey) {
        val tab = TopLevelTab.primaryTabs.firstOrNull { it.root == destination }
        if (tab != null) {
            selectedTab = tab
            return
        }
        val stack = backStacks.getValue(selectedTab)
        if (stack.last() == destination) return
        // A root stays where it is: it is its tab's first screen even while that tab isn't shown.
        backStacks.values.forEach { it.removeUnlessRoot(destination) }
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

    private fun restoreLegacyLibraryStack() {
        val library = backStacks.getValue(TopLevelTab.Library)
        val listen = backStacks.getValue(TopLevelTab.Listen)
        val wasShown = selectedTab == TopLevelTab.Library
        if (library.size == 1 && !wasShown) return
        listen.removeAll { it in library }
        listen.addAll(library)
        library.popToRoot()
        if (wasShown) selectedTab = TopLevelTab.Listen
    }

    private fun MutableList<NavKey>.popToRoot() = popTo(size = 1)

    private fun MutableList<NavKey>.popTo(size: Int) {
        while (this.size > size) removeAt(lastIndex)
    }

    private fun MutableList<NavKey>.removeUnlessRoot(destination: NavKey) {
        val index = indexOf(destination)
        if (index > 0) removeAt(index)
    }
}

/**
 * A [TabNavigator] whose stacks and selection survive configuration changes and process death.
 * The unused legacy Library saveable slot remains registered for backwards-compatible restoration.
 */
@Composable
fun rememberTabNavigator(): TabNavigator {
    val selectedTab = rememberSaveable { mutableStateOf(TopLevelTab.Start) }
    // Each tab's stack is a separate saveable, keyed by tab so each one is restored under its own key.
    val backStacks = TopLevelTab.entries.associateWith { tab -> key(tab) { rememberNavBackStack(tab.root) } }
    return remember { TabNavigator(backStacks, selectedTab) }
}
