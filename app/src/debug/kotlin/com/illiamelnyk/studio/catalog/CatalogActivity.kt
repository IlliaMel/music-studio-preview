package com.illiamelnyk.studio.catalog

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.CreditsCounter
import com.illiamelnyk.studio.core.ui.components.EmptyState
import com.illiamelnyk.studio.core.ui.components.Orb
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
import com.illiamelnyk.studio.core.ui.components.StudioToast
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.core.ui.components.TopBar
import com.illiamelnyk.studio.core.ui.components.TopBarAvatar
import com.illiamelnyk.studio.core.ui.components.TrackRow
import com.illiamelnyk.studio.core.ui.components.TrackRowState
import com.illiamelnyk.studio.core.ui.components.VerifiedMark
import com.illiamelnyk.studio.core.ui.components.coverArt
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.skeleton
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * Every component of the design system in every state, on a device, in both schemes. Debug builds
 * only: it has its own launcher entry, "Studio UI Kit", and isn't part of the release build.
 */
class CatalogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(systemDark) }
            // The scheme can differ from the system setting here, so the bar icons follow the switch.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            StudioTheme(darkTheme) {
                Catalog(darkTheme = darkTheme, onDarkThemeChange = { darkTheme = it })
            }
        }
    }
}

private enum class Scheme(val label: String, val dark: Boolean) {
    Dark("Dark", dark = true),
    Light("Light", dark = false),
}

@Composable
private fun Catalog(darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit) {
    val dimens = StudioTheme.dimens
    Column(
        Modifier
            .fillMaxSize()
            .background(StudioTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        TopBar(
            actions = {
                StudioSegmentedControl(
                    options = Scheme.entries,
                    selected = if (darkTheme) Scheme.Dark else Scheme.Light,
                    onSelect = { onDarkThemeChange(it.dark) },
                ) { it.label }
            },
        ) {
            StudioText("Studio UI Kit", StudioTheme.typography.titleMedium)
        }
        LazyColumn(
            contentPadding = PaddingValues(vertical = dimens.spacing.m),
            verticalArrangement = Arrangement.spacedBy(dimens.layout.sectionGap),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item { Section("Typography") { TypographySamples() } }
            item { Section("Button") { Buttons() } }
            item { Section("IconButton") { IconButtons() } }
            item { Section("SegmentedControl") { SegmentedControls() } }
            item { Section("Chip") { Chips() } }
            item { Section("Badge") { Badges() } }
            item { Section("Cover") { Covers() } }
            item { Section("TrackRow") { TrackRows() } }
            item { Section("Skeleton") { Skeletons() } }
            item { Section("TopBar", edgeToEdge = true) { TopBars() } }
            item { Section("NavigationBar", edgeToEdge = true) { NavigationBars() } }
            item { Section("EmptyState · Orb") { EmptyStateAndOrb() } }
            item { Section("Toast") { Toasts() } }
        }
    }
}

/**
 * A titled group of specimens. The specimen functions are [ColumnScope] extensions because each one
 * stacks several components in this column.
 */
@Composable
private fun Section(title: String, edgeToEdge: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val screenPadding = StudioTheme.dimens.layout.screenPadding
    Column(verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s)) {
        StudioText(
            text = title,
            style = StudioTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = screenPadding),
            color = StudioTheme.colors.content.secondary,
        )
        // Bars span the screen and pad themselves; everything else sits on the screen padding.
        Column(
            modifier = Modifier.padding(
                horizontal = if (edgeToEdge) StudioTheme.dimens.spacing.none else screenPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s),
            content = content,
        )
    }
}

/** Components laid out like the specimens of ui-kit.html: wrapped, with the kit's 12 dp gap. */
@Composable
private fun Specimen(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** A surface of generated cover art, for the components that only appear over media. */
@Composable
private fun MediaBackdrop(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(StudioTheme.shapes.l)
            .coverArt("Ambient")
            .padding(StudioTheme.dimens.spacing.m),
    ) { Specimen(content) }
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
        StudioButton("Remix", onClick = {}, style = StudioButtonStyle.Glass, icon = StudioIcons.AutoAwesome)
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
private fun ColumnScope.Covers() {
    val size = StudioTheme.dimens.size
    Specimen {
        Cover("Night Drive Through Kyiv", size.cover.xs)
        Cover("Rust and Redemption", size.cover.s)
        Cover("Lo-fi rainy morning", size.cover.m, generating = true)
        Cover("Lei Swan", size.avatar.l, shape = StudioTheme.shapes.full)
        Cover("Light Through Clouds", size.cover.l)
        Cover("Late Night Lo-fi", size.cover.l, generating = true)
    }
}

@Composable
private fun ColumnScope.TrackRows() {
    TrackRow(
        title = "Rust and Redemption",
        subtitle = "Enrique Flores",
        coverSeed = "Rust and Redemption",
        onClick = {},
        onMore = {},
        badges = {
            VerifiedMark()
            StudioBadge("v2")
        },
    )
    TrackRow(
        title = "Night Drive Through Kyiv",
        subtitle = "Playing · 2:00",
        coverSeed = "Night Drive Through Kyiv",
        onClick = {},
        state = TrackRowState.Playing,
        onMore = {},
    )
    TrackRow(
        title = "Lo-fi rainy morning",
        subtitle = "Composing… 0:42",
        coverSeed = "Lo-fi rainy morning",
        onClick = {},
        state = TrackRowState.Generating,
    )
    TrackRow(
        title = "Song in the style of…",
        subtitle = "",
        coverSeed = "Song in the style of…",
        onClick = {},
        state = TrackRowState.Failed("Prompt rejected · tap for a suggestion"),
        onRetry = {},
    )
}

@Composable
private fun ColumnScope.Skeletons() {
    val dimens = StudioTheme.dimens
    val shapes = StudioTheme.shapes
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.size.component.trackRow),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dimens.size.cover.s).skeleton(visible = true, shape = shapes.s))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
            Box(Modifier.fillMaxWidth(LINE_LONG).height(dimens.spacing.s).skeleton(visible = true))
            Box(Modifier.fillMaxWidth(LINE_SHORT).height(dimens.spacing.xs).skeleton(visible = true))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.layout.itemGap)) {
        repeat(2) { Box(Modifier.size(dimens.size.cover.l).skeleton(visible = true, shape = shapes.m)) }
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
    )
}

@Composable
private fun ColumnScope.EmptyStateAndOrb() {
    EmptyState(
        icon = StudioIcons.AutoAwesome,
        title = "Create with Studio",
        message = "Transform your ideas into full tracks.",
        modifier = Modifier.fillMaxWidth(),
    ) {
        StudioButton("Create a track", onClick = {}, style = StudioButtonStyle.Secondary)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xxl)) {
        Orb()
        Orb(active = false)
    }
}

@Composable
private fun ColumnScope.Toasts() {
    StudioToast(
        title = "Error",
        message = "You can't remix a track that's already a remix",
        kind = StudioToastKind.Error,
    )
    StudioToast(title = "Your track is ready", message = "Lo-fi rainy morning · 1:30")
}

// Widths of the two skeleton lines, as in the kit: a title and a shorter subtitle.
private const val LINE_LONG = 0.7f
private const val LINE_SHORT = 0.4f
