package com.illiamelnyk.studio.navigation

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.debug.debugSettings
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.CreateRoute
import com.illiamelnyk.studio.feature.create.presentation.tool.ToolRoute
import com.illiamelnyk.studio.feature.library.presentation.LibraryRoute
import com.illiamelnyk.studio.feature.listen.presentation.ListenRoute
import com.illiamelnyk.studio.feature.listen.presentation.collection.CollectionRoute
import com.illiamelnyk.studio.feature.player.presentation.PlayerRoute
import com.illiamelnyk.studio.feature.profile.presentation.ProfileRoute

/**
 * The screens of the signed-in app and how they lead to each other: features know nothing about
 * navigation, they get callbacks, and this is where the callbacks become [navigator] calls.
 *
 * @param padding read inside each screen, so a screen follows the mini player and the bars as they
 *   come and go without its entry being recreated.
 */
fun signedInEntries(
    navigator: TabNavigator,
    padding: State<ShellPadding>,
    transitions: ShellTransitions,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    val libraryIsTab = TopLevelTab.Library in navigator.tabs

    entry<Destination.Create>(metadata = transitions.tabRoot) {
        val contentPadding = padding.value.tabRoot
        CreateRoute(
            onOpenTool = { tool -> navigator.navigate(Destination.Tool(tool.name)) },
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
            contentPadding = contentPadding,
        )
    }
    entry<Destination.Listen>(metadata = transitions.tabRoot) {
        val contentPadding = padding.value.tabRoot
        ListenRoute(
            onOpenCollection = { id -> navigator.navigate(Destination.Collection(id)) },
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
            contentPadding = contentPadding,
        )
    }
    entry<Destination.Library>(metadata = if (libraryIsTab) transitions.tabRoot else emptyMap()) {
        val contentPadding = if (libraryIsTab) padding.value.tabRoot else padding.value.screen
        LibraryRoute(
            onBack = if (libraryIsTab) null else ({ navigator.goBack() }),
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
            contentPadding = contentPadding,
        )
    }
    entry<Destination.Collection> { key ->
        val contentPadding = padding.value.screen
        CollectionRoute(
            collectionId = key.id,
            onClose = navigator::goBack,
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
            contentPadding = contentPadding,
        )
    }
    entry<Destination.Tool> { key ->
        val contentPadding = padding.value.screen
        ToolRoute(
            tool = Tool.valueOf(key.id),
            onClose = navigator::goBack,
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
            contentPadding = contentPadding,
        )
    }
    entry<Destination.Player> {
        val contentPadding = padding.value.fullScreen
        PlayerRoute(
            onClose = navigator::goBack,
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
            contentPadding = contentPadding,
        )
    }
    entry<Destination.Profile> {
        val contentPadding = padding.value.screen
        ProfileRoute(
            onClose = navigator::goBack,
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
            contentPadding = contentPadding,
            debugSettings = debugSettings,
        )
    }
}
