package com.illiamelnyk.studio.feature.player.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerQueueContent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class PlayerQueueContentTest {
    @get:Rule val compose = createComposeRule()

    private val upcoming = (0 until 20).map(::queued).toImmutableList()
    private var content by mutableStateOf(QueueContent(upNext = upcoming))

    @Test
    fun previousShowsThePrependedNextTrackWithoutClosingQueue() {
        content = content.copy(upNext = upcoming.drop(1).toImmutableList())
        show()
        compose.onNodeWithText("Upcoming 1").assertIsDisplayed()

        compose.runOnIdle {
            content = content.copy(track = playing("previous"), upNext = upcoming)
        }

        compose.onNodeWithText("Upcoming 0").assertIsDisplayed()
    }

    @Test
    fun shuffleReturnsToTheStartEvenWhenTheFirstUpcomingTrackStaysTheSame() {
        show()
        compose.onNodeWithTag("playerQueue").performScrollToNode(hasText("Upcoming 12"))
        compose.onNodeWithText("Upcoming 12").assertIsDisplayed()

        compose.onNodeWithContentDescription("Shuffle").performClick()

        compose.onNodeWithText("Upcoming 0").assertIsDisplayed()
    }

    @Test
    fun reorderingTheFirstTwoTracksKeepsTheActualNextTrackVisible() {
        show()
        compose.onNodeWithText("Upcoming 0").assertIsDisplayed()

        compose.runOnIdle {
            content = content.copy(upNext = (listOf(upcoming[1], upcoming[0]) + upcoming.drop(2)).toImmutableList())
        }

        compose.onNodeWithText("Upcoming 1").assertIsDisplayed()
    }

    @Test
    fun metadataUpdatesAndTailAppendsPreserveThePositionWhileBrowsing() {
        show()
        compose.onNodeWithTag("playerQueue").performScrollToNode(hasText("Upcoming 12"))
        compose.onNodeWithText("Upcoming 12").assertIsDisplayed()

        compose.runOnIdle {
            content = content.copy(
                track = content.track.copy(title = "Updated title"),
                upNext = (upcoming + queued(20)).toImmutableList(),
            )
        }

        compose.onNodeWithText("Updated title").assertIsDisplayed()
        compose.onNodeWithText("Upcoming 12").assertIsDisplayed()
    }

    @Test
    fun compactQueueReturnsToTheCurrentTrackWhenPlaybackChanges() {
        show(height = 160.dp)
        compose.onNodeWithTag("playerQueue").performScrollToNode(hasText("Upcoming 12"))
        compose.onNodeWithText("Upcoming 12").assertIsDisplayed()

        compose.runOnIdle { content = content.copy(track = playing("previous")) }

        compose.onNodeWithText("Playing previous").assertIsDisplayed()
        compose.onNodeWithContentDescription("Shuffle").assertIsDisplayed()
    }

    private fun show(height: Dp = 400.dp) {
        compose.setContent {
            StudioTheme {
                PlayerQueueContent(
                    track = content.track,
                    upNext = content.upNext,
                    shuffle = content.shuffle,
                    repeat = false,
                    onShuffle = { content = content.copy(shuffle = !content.shuffle) },
                    onRepeat = {},
                    onAutoplay = {},
                    onClear = {},
                    onTrackClick = {},
                    onMove = { _, _ -> },
                    onClose = {},
                    modifier = Modifier.height(height).fillMaxWidth(),
                )
            }
        }
    }

    private data class QueueContent(
        val track: NowPlayingUi = playing("current"),
        val upNext: ImmutableList<TrackUi>,
        val shuffle: Boolean = false,
    )

    private companion object {
        fun playing(id: String) = NowPlayingUi(TrackId(id), "Playing $id", "Artist", id)

        fun queued(index: Int) = TrackUi(
            id = TrackId("upcoming-$index"),
            title = "Upcoming $index",
            subtitle = UiText.Raw("Artist"),
            coverSeed = "upcoming-$index",
            status = TrackUi.Status.Ready,
        )
    }
}
