package com.illiamelnyk.studio.feature.create.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature
import com.illiamelnyk.studio.feature.create.presentation.components.CreateChips
import com.illiamelnyk.studio.feature.create.presentation.components.CreateTrackItem
import com.illiamelnyk.studio.feature.create.presentation.components.PromptIdeas
import com.illiamelnyk.studio.feature.create.presentation.components.PublishedSoon
import com.illiamelnyk.studio.feature.create.presentation.components.ToolsGrid
import com.illiamelnyk.studio.feature.create.presentation.components.YourTracksHeader
import com.illiamelnyk.studio.feature.create.presentation.components.trackSkeletons
import kotlinx.collections.immutable.ImmutableList

/** What the feed of Create draws: everything but the composer and the overlays. */
@Immutable
data class FeedUi(
    val tools: ImmutableList<ToolUi>,
    val filter: TracksFilter,
    val tracks: ImmutableList<CreateTrackUi>,
    val isLoading: Boolean,
    val ideas: ImmutableList<PromptIdeaUi>,
    val playingTrackId: TrackId?,
    val menuTrackId: TrackId?,
)

/** The part of the state the feed draws; typing in the composer leaves it equal, so the feed skips. */
val CreateState.feed: FeedUi
    get() = FeedUi(tools, filter, tracks, isLoading, ideas, playingTrackId, menuTrackId)

/** The positions of the feed's fixed items, to scroll to them. */
object CreateFeedItems {
    const val TRACKS_HEADER = 2
}

/**
 * Part of [CreateScreen]: the scrolling feed of shortcuts, tools and the user's tracks, which maps
 * its sections' callbacks to intents.
 */
@Composable
fun CreateFeed(
    state: FeedUi,
    onIntent: (CreateIntent) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        item(key = "chips", contentType = "chips") {
            CreateChips(
                onMuseClick = { onIntent(CreateIntent.ComingSoonClicked(ComingSoonFeature.Muse)) },
                onStylesClick = { onIntent(CreateIntent.StylesClicked) },
                onSoundsClick = { onIntent(CreateIntent.ComingSoonClicked(ComingSoonFeature.Sounds)) },
                onRecordClick = { onIntent(CreateIntent.ComingSoonClicked(ComingSoonFeature.Record)) },
            )
        }
        item(key = "tools", contentType = "tools") {
            ToolsGrid(state.tools, onToolClick = { onIntent(CreateIntent.ToolClicked(it)) })
        }
        item(key = "tracksHeader", contentType = "tracksHeader") {
            YourTracksHeader(state.filter, onFilterSelect = { onIntent(CreateIntent.FilterSelected(it)) })
        }
        when {
            state.filter == TracksFilter.Published -> item(key = "published", contentType = "published") {
                PublishedSoon(onHowClick = { onIntent(CreateIntent.ComingSoonClicked(ComingSoonFeature.Publish)) })
            }

            state.isLoading -> trackSkeletons()

            state.tracks.isEmpty() -> item(key = "ideas", contentType = "ideas") {
                PromptIdeas(state.ideas, onIdeaClick = { onIntent(CreateIntent.IdeaClicked(it)) })
            }

            else -> items(state.tracks, key = { it.row.id.value }, contentType = { "track" }) { track ->
                val id = track.row.id
                CreateTrackItem(
                    track = track,
                    isPlaying = id == state.playingTrackId,
                    menuExpanded = id == state.menuTrackId,
                    onClick = { onIntent(CreateIntent.TrackClicked(id)) },
                    onMoreClick = { onIntent(CreateIntent.MoreClicked(id)) },
                    onRetryClick = { onIntent(CreateIntent.RetryClicked(id)) },
                    onFixClick = { onIntent(CreateIntent.FixClicked(id)) },
                    onDeleteClick = { onIntent(CreateIntent.DeleteClicked(id)) },
                    onMenuAction = { onIntent(CreateIntent.MenuActionClicked(it)) },
                    onMenuDismiss = { onIntent(CreateIntent.MenuDismissed) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}
