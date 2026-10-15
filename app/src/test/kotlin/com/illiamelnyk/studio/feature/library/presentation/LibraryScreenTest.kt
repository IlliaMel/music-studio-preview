package com.illiamelnyk.studio.feature.library.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun emptyLibraryExploresAndPlaylistsTabCanBeSelected() {
        val intents = mutableListOf<LibraryIntent>()
        compose.setContent { StudioTheme { LibraryScreen(LibraryState(isLoading = false), { intents += it }) } }
        compose.onNodeWithText(compose.activity.getString(R.string.library_explore)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.library_playlists)).performClick()
        assertEquals(listOf(LibraryIntent.ExploreClicked, LibraryIntent.TabSelected(LibraryTab.Playlists)), intents)
    }
}
