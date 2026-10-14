package com.illiamelnyk.studio.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabNavigatorTest {

    private val collection = Destination.Collection("trending")
    private val tool = Destination.Tool("Genreshift")

    @Test
    fun `switching tabs keeps each stack as it was`() {
        val navigator = navigator(ShellVariant.BottomBar)
        navigator.navigate(tool)
        navigator.select(TopLevelTab.Listen)
        navigator.navigate(collection)

        navigator.select(TopLevelTab.Create)
        assertEquals(tool, navigator.current)

        navigator.select(TopLevelTab.Listen)
        assertEquals(collection, navigator.current)
        assertEquals(listOf(Destination.Listen, collection), navigator.backStack(TopLevelTab.Listen))
    }

    @Test
    fun `back on the first screen of another tab goes to Create`() {
        val navigator = navigator(ShellVariant.BottomBar)
        navigator.select(TopLevelTab.Listen)

        assertTrue(navigator.goBack())

        assertEquals(TopLevelTab.Create, navigator.selectedTab)
        assertEquals(Destination.Create, navigator.current)
    }

    @Test
    fun `back pops the selected tab before leaving it`() {
        val navigator = navigator(ShellVariant.BottomBar)
        navigator.select(TopLevelTab.Library)
        navigator.navigate(Destination.Profile)

        navigator.goBack()

        assertEquals(TopLevelTab.Library, navigator.selectedTab)
        assertEquals(Destination.Library, navigator.current)
    }

    @Test
    fun `back on the first screen of Create leaves the app`() {
        val navigator = navigator(ShellVariant.BottomBar)

        assertFalse(navigator.goBack())
        assertEquals(Destination.Create, navigator.current)
    }

    @Test
    fun `the start tab stays under the selected one`() {
        val navigator = navigator(ShellVariant.BottomBar)
        assertEquals(listOf(TopLevelTab.Create), navigator.tabsInUse)

        navigator.select(TopLevelTab.Library)

        assertEquals(listOf(TopLevelTab.Create, TopLevelTab.Library), navigator.tabsInUse)
    }

    @Test
    fun `selecting the shown tab again goes back to its first screen`() {
        val navigator = navigator(ShellVariant.BottomBar)
        navigator.select(TopLevelTab.Listen)
        navigator.navigate(collection)
        navigator.navigate(Destination.Player)

        navigator.select(TopLevelTab.Listen)

        assertEquals(listOf<NavKey>(Destination.Listen), navigator.backStack(TopLevelTab.Listen))
        assertTrue(navigator.isAtTabRoot)
    }

    @Test
    fun `opening a tab root selects that tab instead of stacking it`() {
        val navigator = navigator(ShellVariant.BottomBar)
        navigator.navigate(Destination.Profile)

        navigator.navigate(Destination.Library)

        assertEquals(TopLevelTab.Library, navigator.selectedTab)
        assertEquals(listOf(Destination.Create, Destination.Profile), navigator.backStack(TopLevelTab.Create))
    }

    @Test
    fun `with the top switch Library opens on top of Listen and back returns to it`() {
        val navigator = navigator(ShellVariant.TopSwitch)
        navigator.select(TopLevelTab.Listen)

        navigator.navigate(Destination.Library)
        assertEquals(TopLevelTab.Listen, navigator.selectedTab)
        assertEquals(Destination.Library, navigator.current)

        navigator.goBack()
        assertEquals(Destination.Listen, navigator.current)
    }

    @Test
    fun `a destination opened from another tab moves there, so no screen is shown twice`() {
        val navigator = navigator(ShellVariant.BottomBar)
        navigator.navigate(Destination.Player)
        navigator.select(TopLevelTab.Listen)

        navigator.navigate(Destination.Player)

        assertEquals(listOf<NavKey>(Destination.Create), navigator.backStack(TopLevelTab.Create))
        assertEquals(listOf(Destination.Listen, Destination.Player), navigator.backStack(TopLevelTab.Listen))
    }

    @Test
    fun `opening the destination already on top does not stack it twice`() {
        val navigator = navigator(ShellVariant.BottomBar)

        navigator.navigate(Destination.Profile)
        navigator.navigate(Destination.Profile)

        assertEquals(listOf(Destination.Create, Destination.Profile), navigator.backStack(TopLevelTab.Create))
    }

    private fun navigator(variant: ShellVariant) = TabNavigator(
        tabs = variant.tabs,
        backStacks = variant.tabs.associateWith { NavBackStack<NavKey>(it.root) },
        selectedTab = mutableStateOf(TopLevelTab.Start),
    )
}
