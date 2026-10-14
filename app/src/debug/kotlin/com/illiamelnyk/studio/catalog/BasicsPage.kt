package com.illiamelnyk.studio.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.CreditsCounter
import com.illiamelnyk.studio.core.ui.components.StudioBadge
import com.illiamelnyk.studio.core.ui.components.StudioBadgeStyle
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioChip
import com.illiamelnyk.studio.core.ui.components.StudioFilterChip
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioNavigationBar
import com.illiamelnyk.studio.core.ui.components.StudioNavigationItem
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.components.TopBar
import com.illiamelnyk.studio.core.ui.components.TopBarAvatar
import com.illiamelnyk.studio.core.ui.components.VerifiedMark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * Type, actions and navigation. With a keyboard or a D-pad (Tab, arrows), the focused element shows
 * its focus ring.
 */
@Composable
internal fun BasicsPage() {
    CatalogPageList {
        item { Section("Typography") { TypographySamples() } }
        item { Section("Button") { Buttons() } }
        item { Section("IconButton") { IconButtons() } }
        item { Section("SegmentedControl") { SegmentedControls() } }
        item { Section("Chip") { Chips() } }
        item { Section("Badge") { Badges() } }
        item { Section("TopBar", edgeToEdge = true) { TopBars() } }
        item { Section("NavigationBar", edgeToEdge = true) { NavigationBars() } }
    }
}

@Composable
private fun ColumnScope.TypographySamples() {
    val type = StudioTheme.typography
    listOf(
        "headlineLarge · Library" to type.headlineLarge,
        "titleLarge · Night Drive Through Kyiv" to type.titleLarge,
        "titleMedium · Trending Now" to type.titleMedium,
        "titleSmall · Rust and Redemption" to type.titleSmall,
        "bodyLarge · Describe your track…" to type.bodyLarge,
        "bodyMedium · Music v2.5" to type.bodyMedium,
        "bodySmall · Enrique Flores · 101 BPM · C# Minor" to type.bodySmall,
        "labelLarge · Generate" to type.labelLarge,
        "labelMedium · Listen" to type.labelMedium,
        "labelSmall · SOON · NEW · V2" to type.labelSmall,
    ).forEach { (text, style) -> StudioText(text, style) }
}

@Composable
private fun ColumnScope.Buttons() {
    Specimen {
        StudioButton("Generate", onClick = {}, size = StudioButtonSize.Large)
        StudioButton("Shuffle", onClick = {}, style = StudioButtonStyle.Secondary, icon = StudioIcons.Shuffle)
        StudioButton("Use tool", onClick = {}, enabled = false)
        StudioButton("Creating", onClick = {}, loading = true)
        StudioButton(
            "New playlist",
            onClick = {},
            style = StudioButtonStyle.Secondary,
            size = StudioButtonSize.Small,
            icon = StudioIcons.Add,
        )
        StudioButton("Show more", onClick = {}, style = StudioButtonStyle.Text)
        StudioButton("Delete", onClick = {}, style = StudioButtonStyle.Danger)
        StudioButton("Saving", onClick = {}, style = StudioButtonStyle.Secondary, loading = true)
        StudioButton("Small", onClick = {}, size = StudioButtonSize.Small)
        StudioButton("Disabled text", onClick = {}, style = StudioButtonStyle.Text, enabled = false)
    }
    StudioButton("I want this", onClick = {}, modifier = Modifier.fillMaxWidth(), size = StudioButtonSize.Large)
    MediaBackdrop {
        StudioButton("Remix", onClick = {}, style = StudioButtonStyle.Glass, icon = StudioIcons.AutoFixHigh)
        StudioButton("Add to", onClick = {}, style = StudioButtonStyle.Glass, icon = StudioIcons.Add)
        StudioButton("Shuffle", onClick = {}, style = StudioButtonStyle.Glass, icon = StudioIcons.Shuffle)
    }
}

@Composable
private fun ColumnScope.IconButtons() {
    Specimen {
        StudioIconButton(StudioIcons.Search, "Search", onClick = {})
        StudioIconButton(StudioIcons.Tune, "Settings", onClick = {}, style = StudioIconButtonStyle.Surface)
        StudioIconButton(
            StudioIcons.Add,
            "Add",
            onClick = {},
            style = StudioIconButtonStyle.Surface,
            size = StudioIconButtonSize.Small,
        )
        StudioIconButton(StudioIcons.ArrowUpward, "Send", onClick = {}, style = StudioIconButtonStyle.Primary)
        StudioIconButton(StudioIcons.ChevronLeft, "Back", onClick = {}, style = StudioIconButtonStyle.Surface)
        StudioIconButton(StudioIcons.MoreHoriz, "More", onClick = {}, style = StudioIconButtonStyle.Muted)
        StudioIconButton(
            StudioIcons.ArrowUpward,
            "Send",
            onClick = {},
            style = StudioIconButtonStyle.Primary,
            enabled = false,
        )
    }
    MediaBackdrop {
        StudioIconButton(StudioIcons.Shuffle, "Shuffle", onClick = {}, style = StudioIconButtonStyle.OnMedia)
        StudioIconButton(StudioIcons.MoreHoriz, "More", onClick = {}, style = StudioIconButtonStyle.OnMedia)
    }
}

@Composable
private fun ColumnScope.SegmentedControls() {
    var mode by remember { mutableStateOf("Create") }
    var vocals by remember { mutableStateOf("Vocals") }
    var quality by remember { mutableStateOf("Auto") }
    StudioSegmentedControl(listOf("Create", "Listen"), selected = mode, onSelect = { mode = it }) { it }
    StudioSegmentedControl(listOf("Vocals", "Instrumental"), selected = vocals, onSelect = { vocals = it }) { it }
    StudioSegmentedControl(
        options = listOf("Auto", "Deep", "Fast"),
        selected = quality,
        onSelect = { quality = it },
        compact = true,
    ) { it }
}

@Composable
private fun ColumnScope.Chips() {
    var filter by remember { mutableStateOf("All") }
    var duration by remember { mutableStateOf("2:00") }
    Specimen {
        listOf("All", "Yours", "Liked").forEach { option ->
            StudioFilterChip(option, selected = option == filter, onClick = { filter = option })
        }
    }
    Specimen {
        StudioChip("Muse", onClick = {}, leadingIcon = StudioIcons.Forum)
        StudioChip("Styles", onClick = {}, leadingIcon = StudioIcons.AutoAwesome)
        StudioChip(
            text = "Light Through Clouds",
            onClick = {},
            leadingAvatar = {
                Cover(
                    seed = "Light Through Clouds",
                    size = StudioTheme.dimens.size.avatar.xs,
                    shape = StudioTheme.shapes.full,
                )
            },
            onRemove = {},
        )
    }
    Specimen {
        listOf("1:00", "2:00", "3:00").forEach { option ->
            StudioFilterChip(option, selected = option == duration, onClick = { duration = option })
        }
    }
}

@Composable
private fun ColumnScope.Badges() {
    Specimen {
        VerifiedMark()
        StudioBadge("v2.5")
        StudioBadge("Remix")
        StudioBadge("New", style = StudioBadgeStyle.New)
        StudioBadge("Soon", style = StudioBadgeStyle.Soon)
        StudioBadge("Demo", style = StudioBadgeStyle.Demo)
        CreditsCounter(left = 25)
    }
}

@Composable
private fun ColumnScope.TopBars() {
    var mode by remember { mutableStateOf("Create") }
    TopBar(
        navigation = { TopBarAvatar(seed = "Illia", contentDescription = "Profile", onClick = {}) },
        actions = {
            StudioIconButton(StudioIcons.Edit, "New track", onClick = {})
            CreditsCounter(left = 25)
        },
    ) {
        StudioSegmentedControl(listOf("Create", "Listen"), selected = mode, onSelect = { mode = it }) { it }
    }
    TopBar(
        title = "Library",
        navigation = {
            StudioIconButton(StudioIcons.ChevronLeft, "Back", onClick = {}, style = StudioIconButtonStyle.Surface)
        },
        actions = { StudioIconButton(StudioIcons.IosShare, "Share", onClick = {}) },
    )
}

@Composable
private fun ColumnScope.NavigationBars() {
    var selected by remember { mutableIntStateOf(0) }
    StudioNavigationBar(
        items = listOf(
            StudioNavigationItem("Create", StudioIcons.AutoAwesome, StudioIcons.AutoAwesomeFilled),
            StudioNavigationItem("Listen", StudioIcons.Headphones, StudioIcons.HeadphonesFilled),
            StudioNavigationItem("Library", StudioIcons.LibraryMusic, StudioIcons.LibraryMusicFilled),
        ),
        selectedIndex = selected,
        onSelect = { selected = it },
        // Mid-page, away from the system navigation bar: without its inset.
        modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars),
    )
}
