package com.illiamelnyk.studio.core.ui.artwork

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RememberArtworkTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a decoded large cover replaces the preview with no placeholder frame in between`() {
        val assets = RuntimeEnvironment.getApplication().assets
        val preview = runBlocking { requireNotNull(ArtworkLibraryCache.load(assets)).resolve(STATION) }
        val detail = runBlocking { requireNotNull(ArtworkDetailCache.loader.load(assets, preview)) }
        var side by mutableIntStateOf(0)
        val frames = mutableListOf<ArtworkTile?>()
        composeRule.setContent { frames += rememberArtwork(STATION, side) }

        composeRule.runOnIdle { side = LARGE_SIDE_PX }
        composeRule.waitForIdle()

        assertEquals(preview.size, frames.first()?.size)
        assertTrue("A frame fell back to the placeholder: $frames", frames.none { it == null })
        assertEquals(detail.size, frames.last()?.size)
    }

    @Test
    fun `a large cover still decoding shows its preview from the first frame`() {
        val assets = RuntimeEnvironment.getApplication().assets
        val preview = runBlocking { requireNotNull(ArtworkLibraryCache.load(assets)).resolve(COLD_STATION) }
        val frames = mutableListOf<ArtworkTile?>()
        composeRule.setContent { frames += rememberArtwork(COLD_STATION, LARGE_SIDE_PX) }
        composeRule.waitForIdle()

        assertEquals(preview.size, frames.first()?.size)
        assertTrue("A frame fell back to the placeholder: $frames", frames.none { it == null })
    }

    private companion object {
        const val STATION = "Piano Room"

        /** On a pair of its own that no other test decodes first. */
        const val COLD_STATION = "Ambient Spaces"
        const val LARGE_SIDE_PX = 2000
    }
}
