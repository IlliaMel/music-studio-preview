package com.illiamelnyk.studio.navigation

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.CreateRoute
import com.illiamelnyk.studio.feature.create.presentation.tool.ToolRoute
import com.illiamelnyk.studio.feature.create.presentation.tools.ToolsRoute
import com.illiamelnyk.studio.feature.library.presentation.LibraryRoute
import com.illiamelnyk.studio.feature.listen.presentation.ListenRoute
import com.illiamelnyk.studio.feature.listen.presentation.collection.CollectionRoute
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
    onOpenPlayer: () -> Unit,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<Destination.Create>(metadata = transitions.tabRoot) {
        val contentPadding = padding.value.tabRoot
        CreateRoute(
            onOpenTool = { tool -> navigator.navigate(Destination.Tool(tool.name)) },
            onOpenTools = { navigator.navigate(Destination.Tools) },
            onOpenPlayer = onOpenPlayer,
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding).drawWithContent {
                drawContent()
                if (navigator.current == Destination.Create) NavigationRenderProbe.firstDraw(TopLevelTab.Create)
            },
            contentPadding = contentPadding,
        )
    }
    entry<Destination.Listen>(metadata = transitions.tabRoot) {
        val contentPadding = padding.value.tabRoot
        ListenRoute(
            onOpenCollection = { id -> navigator.navigate(Destination.Collection(id)) },
            onOpenPlayer = onOpenPlayer,
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding).drawWithContent {
                drawContent()
                if (navigator.current == Destination.Listen) NavigationRenderProbe.firstDraw(TopLevelTab.Listen)
            },
            contentPadding = contentPadding,
        )
    }
    entry<Destination.Library> {
        val contentPadding = padding.value.screen
        LibraryRoute(
            onBack = { navigator.goBack() },
            onOpenPlayer = onOpenPlayer,
            onExplore = {
                navigator.goBack()
                navigator.select(TopLevelTab.Listen)
            },
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
            contentPadding = contentPadding,
        )
    }
    entry<Destination.Collection> { key ->
        val contentPadding = padding.value.screen
        CollectionRoute(
            collectionId = key.id,
            onOpenPlayer = onOpenPlayer,
            onOpenCollection = { id -> navigator.navigate(Destination.Collection(id)) },
            onClose = navigator::goBack,
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
            contentPadding = contentPadding,
        )
    }
    entry<Destination.Tools> {
        val contentPadding = padding.value.screen
        ToolsRoute(
            onClose = navigator::goBack,
            onOpenTool = { tool -> navigator.navigate(Destination.Tool(tool.name)) },
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
    entry<Destination.Profile> {
        val contentPadding = padding.value.screen
        ProfileRoute(
            onClose = navigator::goBack,
            modifier = Modifier.destination(StudioTheme.colors.background, contentPadding),
            contentPadding = contentPadding,
        )
    }
}
