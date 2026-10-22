package com.illiamelnyk.studio.navigation

import androidx.compose.runtime.mutableStateOf
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
        val navigator = navigator()
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
        val navigator = navigator()
        navigator.select(TopLevelTab.Listen)

        assertTrue(navigator.goBack())

        assertEquals(TopLevelTab.Create, navigator.selectedTab)
        assertEquals(Destination.Create, navigator.current)
    }

    @Test
    fun `back pops the selected tab before leaving it`() {
        val navigator = navigator()
        navigator.select(TopLevelTab.Listen)
        navigator.navigate(Destination.Library)
        navigator.navigate(Destination.Profile)

        navigator.goBack()

        assertEquals(TopLevelTab.Listen, navigator.selectedTab)
        assertEquals(Destination.Library, navigator.current)
    }

    @Test
    fun `back on the first screen of Create leaves the app`() {
        val navigator = navigator()

        assertFalse(navigator.goBack())
        assertEquals(Destination.Create, navigator.current)
    }

    @Test
    fun `the start tab stays under the selected one`() {
        val navigator = navigator()
        assertEquals(listOf(TopLevelTab.Create), navigator.tabsInUse)

        navigator.select(TopLevelTab.Listen)
        navigator.navigate(Destination.Library)

        assertEquals(listOf(TopLevelTab.Create, TopLevelTab.Listen), navigator.tabsInUse)
    }

    @Test
    fun `selecting the shown tab again goes back to its first screen`() {
        val navigator = navigator()
        navigator.select(TopLevelTab.Listen)
        navigator.navigate(collection)
        navigator.navigate(Destination.Profile)

        navigator.select(TopLevelTab.Listen)

        assertEquals(listOf<NavKey>(Destination.Listen), navigator.backStack(TopLevelTab.Listen))
        assertTrue(navigator.isAtTabRoot)
    }

    @Test
    fun `opening a tab root selects that tab instead of stacking it`() {
        val navigator = navigator()
        navigator.navigate(Destination.Profile)

        navigator.navigate(Destination.Listen)

        assertEquals(TopLevelTab.Listen, navigator.selectedTab)
        assertEquals(listOf(Destination.Create, Destination.Profile), navigator.backStack(TopLevelTab.Create))
    }

    @Test
    fun `Library opens on top of Listen and back returns to it`() {
        val navigator = navigator()
        navigator.select(TopLevelTab.Listen)

        navigator.navigate(Destination.Library)
        assertEquals(TopLevelTab.Listen, navigator.selectedTab)
        assertEquals(Destination.Library, navigator.current)

        navigator.goBack()
        assertEquals(Destination.Listen, navigator.current)
    }

    @Test
    fun `a destination opened from another tab moves there, so no screen is shown twice`() {
        val navigator = navigator()
        navigator.navigate(Destination.Profile)
        navigator.select(TopLevelTab.Listen)

        navigator.navigate(Destination.Profile)

        assertEquals(listOf<NavKey>(Destination.Create), navigator.backStack(TopLevelTab.Create))
        assertEquals(listOf(Destination.Listen, Destination.Profile), navigator.backStack(TopLevelTab.Listen))
    }

    @Test
    fun `opening the destination already on top does not stack it twice`() {
        val navigator = navigator()

        navigator.navigate(Destination.Profile)
        navigator.navigate(Destination.Profile)

        assertEquals(listOf(Destination.Create, Destination.Profile), navigator.backStack(TopLevelTab.Create))
    }

    @Test
    fun `old selected Library state restores above Listen with its complete stack`() {
        val navigator = navigator(
            selectedTab = TopLevelTab.Library,
            listen = listOf(Destination.Listen, collection),
            library = listOf(Destination.Library, Destination.Profile),
        )
        assertEquals(TopLevelTab.Listen, navigator.selectedTab)
        assertEquals(
            listOf(Destination.Listen, collection, Destination.Library, Destination.Profile),
            navigator.backStack(TopLevelTab.Listen),
        )
        assertEquals(listOf<NavKey>(Destination.Library), navigator.backStack(TopLevelTab.Library))
        assertEquals(Destination.Profile, navigator.current)
        assertTrue(navigator.goBack())
        assertTrue(navigator.goBack())
        assertEquals(collection, navigator.current)
    }

    @Test
    fun `restoring hidden legacy Library does not change selected Create or its tools`() {
        val navigator = navigator(
            create = listOf(Destination.Create, tool),
            library = listOf(Destination.Library, Destination.Profile),
        )
        assertEquals(TopLevelTab.Create, navigator.selectedTab)
        assertEquals(tool, navigator.current)
        navigator.select(TopLevelTab.Listen)
        assertEquals(Destination.Profile, navigator.current)
        assertEquals(
            listOf(Destination.Listen, Destination.Library, Destination.Profile),
            navigator.backStack(TopLevelTab.Listen),
        )
    }

    @Test
    fun `restoring legacy Library avoids duplicate destinations across visible stacks`() {
        val navigator = navigator(
            selectedTab = TopLevelTab.Library,
            listen = listOf(Destination.Listen, Destination.Library, Destination.Profile),
            library = listOf(Destination.Library, Destination.Profile),
        )
        val shown = navigator.tabsInUse.flatMap { navigator.backStack(it) }
        assertEquals(shown.distinct(), shown)
        assertEquals(
            listOf(Destination.Listen, Destination.Library, Destination.Profile),
            navigator.backStack(TopLevelTab.Listen),
        )
    }

    @Test
    fun `legacy selected Library root alone restores as a pushed screen`() {
        val navigator = navigator(selectedTab = TopLevelTab.Library)
        assertEquals(TopLevelTab.Listen, navigator.selectedTab)
        assertEquals(Destination.Library, navigator.current)
        assertTrue(navigator.goBack())
        assertEquals(Destination.Listen, navigator.current)
    }

    @Test
    fun `saved top switch stacks restore unchanged including Library and collections`() {
        val listen = listOf(Destination.Listen, Destination.Library, collection)
        val navigator = navigator(
            selectedTab = TopLevelTab.Listen,
            create = listOf(Destination.Create, tool),
            listen = listen,
        )
        assertEquals(collection, navigator.current)
        assertEquals(listen, navigator.backStack(TopLevelTab.Listen))
        navigator.select(TopLevelTab.Create)
        assertEquals(tool, navigator.current)
        navigator.select(TopLevelTab.Listen)
        assertEquals(collection, navigator.current)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Library cannot be selected as a primary tab`() {
        navigator().select(TopLevelTab.Library)
    }

    private fun navigator(
        selectedTab: TopLevelTab = TopLevelTab.Start,
        create: List<NavKey> = listOf(Destination.Create),
        listen: List<NavKey> = listOf(Destination.Listen),
        library: List<NavKey> = listOf(Destination.Library),
    ) = TabNavigator(
        backStacks = mapOf(
            TopLevelTab.Create to create.toMutableList(),
            TopLevelTab.Listen to listen.toMutableList(),
            TopLevelTab.Library to library.toMutableList(),
        ),
        selectedTab = mutableStateOf(selectedTab),
    )
}
