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
 * There is a stack for every [TopLevelTab], whichever of them the shell shows as [tabs]; see
 * [useTabs] for what happens to the screens when that changes.
 *
 * @param backStacks a stack for every tab, each starting with the tab's root.
 * @param tabs the tabs of the current [ShellVariant]; the start tab comes first.
 */
@Stable
class TabNavigator(
    private val backStacks: Map<TopLevelTab, MutableList<NavKey>>,
    selectedTab: MutableState<TopLevelTab>,
    tabs: List<TopLevelTab>,
) {
    init {
        require(backStacks.keys == TopLevelTab.entries.toSet()) { "Every tab needs a back stack" }
        require(backStacks.all { (tab, stack) -> stack.firstOrNull() == tab.root }) {
            "Every stack starts with its tab's root"
        }
    }

    var selectedTab: TopLevelTab by selectedTab
        private set

    /** The tabs the shell shows. */
    var tabs: List<TopLevelTab> by mutableStateOf(emptyList())
        private set

    init {
        // Restored state may come from another variant, so it is fitted to these tabs right away.
        useTabs(tabs)
    }

    /** The tabs whose screens are shown, bottom to top: the start tab, then the selected one. */
    val tabsInUse: List<TopLevelTab>
        get() = if (selectedTab == TopLevelTab.Start) listOf(selectedTab) else listOf(TopLevelTab.Start, selectedTab)

    /** The destination on screen. */
    val current: NavKey get() = backStacks.getValue(selectedTab).last()

    /** Whether the selected tab shows its first screen, which the shell's bars belong to. */
    val isAtTabRoot: Boolean get() = backStacks.getValue(selectedTab).size == 1

    /**
     * Changes the tabs the shell shows, when the shell variant changes, keeping the screens:
     * - Library loses its tab: its screens go on top of Listen, and if Library was shown, Listen is.
     * - Library gets its tab: Library and the screens above it move from Listen to that tab, and if
     *   Listen was shown, Library is.
     *
     * Either way no screen ends up in two stacks that are shown together.
     */
    fun useTabs(tabs: List<TopLevelTab>) {
        require(tabs.firstOrNull() == TopLevelTab.Start) { "The start tab must come first: $tabs" }
        if (tabs == this.tabs) return
        if (TopLevelTab.Library in tabs) liftLibraryOffListen() else putLibraryOnListen()
        this.tabs = tabs
        if (selectedTab !in tabs) selectedTab = TopLevelTab.Start
    }

    /** Shows [tab] as it was left. Selecting the tab that is already shown goes back to its root. */
    fun select(tab: TopLevelTab) {
        require(tab in tabs) { "$tab is not a tab of $tabs" }
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
        val tab = tabs.firstOrNull { it.root == destination }
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

    private fun putLibraryOnListen() {
        val library = backStacks.getValue(TopLevelTab.Library)
        val listen = backStacks.getValue(TopLevelTab.Listen)
        val wasShown = selectedTab == TopLevelTab.Library
        if (library.size == 1 && !wasShown) return
        listen.removeAll { it in library }
        listen.addAll(library)
        library.popToRoot()
        if (wasShown) selectedTab = TopLevelTab.Listen
    }

    private fun liftLibraryOffListen() {
        val listen = backStacks.getValue(TopLevelTab.Listen)
        val start = listen.indexOf(TopLevelTab.Library.root)
        if (start <= 0) return
        val moved = listen.subList(start, listen.size).toList()
        listen.popTo(size = start)
        val library = backStacks.getValue(TopLevelTab.Library)
        library.clear()
        library.addAll(moved)
        if (selectedTab == TopLevelTab.Listen) selectedTab = TopLevelTab.Library
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
 * When [tabs] change, the same navigator moves its screens to fit them; see [TabNavigator.useTabs].
 */
@Composable
fun rememberTabNavigator(tabs: List<TopLevelTab>): TabNavigator {
    val selectedTab = rememberSaveable { mutableStateOf(TopLevelTab.Start) }
    // Each tab's stack is a separate saveable, keyed by tab so each one is restored under its own key.
    val backStacks = TopLevelTab.entries.associateWith { tab -> key(tab) { rememberNavBackStack(tab.root) } }
    val navigator = remember { TabNavigator(backStacks, selectedTab, tabs) }
    // Before anything below reads the stacks, so no frame shows them in the old arrangement.
    navigator.useTabs(tabs)
    return navigator
}
