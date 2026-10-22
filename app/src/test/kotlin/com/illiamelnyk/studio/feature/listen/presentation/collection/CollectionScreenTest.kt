package com.illiamelnyk.studio.feature.listen.presentation.collection

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.presentation.CollectionUi
import java.io.File
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h850dp")
class CollectionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightCollectionDrawsBehindStatusBarWhileKeepingBackSafelyInset() = assertEdgeToEdge(false)

    @Test fun darkCollectionDrawsBehindStatusBarWhileKeepingBackSafelyInset() = assertEdgeToEdge(true)

    private fun assertEdgeToEdge(dark: Boolean) {
        compose.setContent {
            StudioTheme(darkTheme = dark) {
                CollectionScreen(
                    CollectionState(
                        collection = CollectionUi(
                            "second-take",
                            "Second Take",
                            UiText.Raw("Instrumental mix"),
                            "Second Take",
                        ),
                        isLoading = false,
                    ),
                    onIntent = {},
                    contentPadding = PaddingValues(start = 24.dp, top = 32.dp, end = 8.dp, bottom = 88.dp),
                )
            }
        }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val hero = compose.onNodeWithTag("collection-hero").fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithContentDescription(compose.activity.getString(R.string.nav_back))
            .fetchSemanticsNode().boundsInRoot
        assertEquals("The artwork backdrop must reach the top of the viewport", root.top, hero.top, 0.5f)
        assertEquals("Side cutouts must not inset the backdrop", root.left, hero.left, 0.5f)
        assertEquals("Side cutouts must not inset the backdrop", root.right, hero.right, 0.5f)
        val insetPx = with(compose.density) { 32.dp.toPx() }
        assertTrue("Back remains below the status bar", back.top >= root.top + insetPx)
        assertTrue("The back target overlaps the full-bleed hero", back.bottom < hero.bottom)
        assertTrue("Back respects the side cutout", back.left >= root.left + with(compose.density) { 24.dp.toPx() })
        val screenshot = compose.onRoot().captureToImage()
        val output = File("build/test-screenshots/collection-${if (dark) "dark" else "light"}.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { screenshot.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!dark) {
            val pixels = screenshot.toPixelMap()
            assertTrue("The light hero must keep dark status icons legible", pixels[2, 2].luminance() > 0.65f)
        }
    }

    @Test fun collectionActionsScrollAwayWithoutCoveringThePersistentBackTarget() {
        compose.setContent {
            StudioTheme {
                CollectionScreen(
                    CollectionState(
                        collection = CollectionUi(
                            "second-take",
                            "Second Take",
                            UiText.Raw("Instrumental mix"),
                            "Second Take",
                        ),
                        tracks = (1..30).map { index ->
                            TrackUi(
                                TrackId("track-$index"),
                                "Track $index",
                                UiText.Raw("Instrumental"),
                                "Second Take",
                                TrackUi.Status.Ready,
                            )
                        }.toImmutableList(),
                        isLoading = false,
                    ),
                    onIntent = {},
                    contentPadding = PaddingValues(top = 32.dp),
                )
            }
        }
        compose.onNodeWithTag("collection-list").performScrollToNode(hasText("Track 20"))
        compose.onNodeWithText("Track 20").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.collection_play)).assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.nav_back)).assertIsDisplayed()
    }

    @Test fun loadingCollectionKeepsBackAvailableWithoutOfferingPlayback() {
        val intents = mutableListOf<CollectionIntent>()
        compose.setContent { StudioTheme { CollectionScreen(CollectionState(), { intents += it }) } }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        compose.onNodeWithText(compose.activity.getString(R.string.collection_play)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.collection_shuffle)).assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.nav_back)).performClick()
        assertEquals(listOf(CollectionIntent.BackClicked), intents)
    }

    @Test fun missingCollectionOffersRetry() {
        val intents = mutableListOf<CollectionIntent>()
        compose.setContent {
            StudioTheme {
                CollectionScreen(CollectionState(isLoading = false, failed = true), {
                    intents +=
                        it
                })
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.listen_retry)).performClick()
        assertEquals(listOf(CollectionIntent.Retry), intents)
    }
}
