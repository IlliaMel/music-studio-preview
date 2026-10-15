package com.illiamelnyk.studio.feature.listen.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListenScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun stationAndSearchDispatchTheirIntents() {
        val intents = mutableListOf<ListenIntent>()
        val state =
            ListenState(
                sections = persistentListOf(
                    CatalogSectionUi(
                        CatalogSectionKind.Stations,
                        persistentListOf(CollectionUi("live", "Piano Room", UiText.Raw("Live"), "piano")),
                        isLoading = false,
                    ),
                ),
            )
        compose.setContent { StudioTheme { ListenScreen(state, { intents += it }) } }
        compose.onNodeWithText("Piano Room").performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.listen_search)).performClick()
        assertEquals(listOf(ListenIntent.CollectionClicked("live"), ListenIntent.SearchClicked), intents)
    }
}
