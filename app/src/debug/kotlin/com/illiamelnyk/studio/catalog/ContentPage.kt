package com.illiamelnyk.studio.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.ui.components.ArtistAvatar
import com.illiamelnyk.studio.core.ui.components.Carousel
import com.illiamelnyk.studio.core.ui.components.CollectionHeader
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.EmptyState
import com.illiamelnyk.studio.core.ui.components.SectionHeader
import com.illiamelnyk.studio.core.ui.components.SettingRow
import com.illiamelnyk.studio.core.ui.components.SoundRow
import com.illiamelnyk.studio.core.ui.components.StationCard
import com.illiamelnyk.studio.core.ui.components.StudioBadge
import com.illiamelnyk.studio.core.ui.components.StudioBadgeStyle
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonStyle
import com.illiamelnyk.studio.core.ui.components.ToolTile
import com.illiamelnyk.studio.core.ui.components.TrackCard
import com.illiamelnyk.studio.core.ui.components.TrackRow
import com.illiamelnyk.studio.core.ui.components.TrackRowState
import com.illiamelnyk.studio.core.ui.components.VerifiedMark
import com.illiamelnyk.studio.core.ui.foundation.skeleton
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/** Covers, rows, cards, carousels and the states of content that is loading or empty. */
@Composable
internal fun ContentPage() {
    CatalogPageList {
        item { Section("Cover") { Covers() } }
        item { Section("TrackRow") { TrackRows() } }
        item { Section("TrackCard · Carousel", edgeToEdge = true) { TrackCards() } }
        item { Section("StationCard · ArtistAvatar", edgeToEdge = true) { StationsAndArtists() } }
        item { Section("ToolTile") { ToolTiles() } }
        item { Section("SoundRow · SettingRow") { ListRows() } }
        item { Section("CollectionHeader", edgeToEdge = true) { Collection() } }
        item { Section("Skeleton") { Skeletons() } }
        item { Section("EmptyState") { Empty() } }
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

private val Titles =
    listOf("Light Through Clouds", "Rust and Redemption", "Night Drive Through Kyiv", "Lo-fi rainy morning", "Ambient")

@Composable
private fun ColumnScope.TrackCards() {
    SectionHeader(
        title = "Trending Now",
        modifier = Modifier.padding(horizontal = StudioTheme.dimens.layout.screenPadding),
        onTitleClick = {},
    )
    Carousel(Titles, key = { it }) { title ->
        TrackCard(
            title = title,
            subtitle = "DEREK",
            coverSeed = title,
            onClick = {},
            generating = title == "Lo-fi rainy morning",
            badges = { VerifiedMark() },
        )
    }
}

@Composable
private fun ColumnScope.StationsAndArtists() {
    SectionHeader(
        title = "Stations",
        modifier = Modifier.padding(horizontal = StudioTheme.dimens.layout.screenPadding),
    ) {
        StudioButton("Show more", onClick = {}, style = StudioButtonStyle.Text, size = StudioButtonSize.Small)
    }
    Carousel(listOf("Late Night Lo-fi", "Morning Jazz", "Deep Focus"), key = { it }) { title ->
        StationCard(title, "Studio", title, onClick = {})
    }
    Carousel(listOf("Lei Swan", "Enrique Flores", "DEREK", "Mira", "Okean"), key = { it }) { name ->
        ArtistAvatar(name, name, onClick = {}, verified = name == "Lei Swan")
    }
}

@Composable
private fun ColumnScope.ToolTiles() {
    val gap = StudioTheme.dimens.layout.itemGap
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        ToolTile("Voice to Song", "Voice to Song", onClick = {}, modifier = Modifier.weight(1f))
        ToolTile("Genreshift", "Genreshift", onClick = {}, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        ToolTile("Sample to Song", "Sample to Song", onClick = {}, modifier = Modifier.weight(1f))
        ToolTile("Unplugged", "Unplugged", onClick = {}, modifier = Modifier.weight(1f), comingSoon = true)
    }
}

@Composable
private fun ColumnScope.ListRows() {
    var playing by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }
    SoundRow(
        title = "Rally Bassground",
        subtitle = "808 Bass · 101 BPM · C# Minor",
        coverSeed = "Rally Bassground",
        playing = playing,
        onPlayClick = { playing = !playing },
    ) {
        StudioIconButton(
            icon = if (liked) StudioIcons.FavoriteFilled else StudioIcons.Favorite,
            contentDescription = "Like",
            onClick = { liked = !liked },
            style = StudioIconButtonStyle.Muted,
        )
    }
    SettingRow("Default model", value = "Music v2.5", onClick = {})
    SettingRow("Backend") { StudioBadge("Mock", style = StudioBadgeStyle.Demo) }
}

@Composable
private fun ColumnScope.Collection() {
    CollectionHeader("Ambient", "Ambient Mix · 111 tracks · 6:00:39", "Ambient") {
        StudioButton("Play", onClick = {}, style = StudioButtonStyle.Glass, icon = StudioIcons.PlayArrowFilled)
        StudioButton("Shuffle", onClick = {}, style = StudioButtonStyle.Glass, icon = StudioIcons.Shuffle)
    }
}

@Composable
private fun ColumnScope.Empty() {
    EmptyState(
        icon = StudioIcons.AutoAwesome,
        title = "Create with Studio",
        message = "Transform your ideas into full tracks.",
        modifier = Modifier.fillMaxWidth(),
    ) {
        StudioButton("Create a track", onClick = {}, style = StudioButtonStyle.Secondary)
    }
}

// Widths of the two skeleton lines, as in the kit: a title and a shorter subtitle.
private const val LINE_LONG = 0.7f
private const val LINE_SHORT = 0.4f
