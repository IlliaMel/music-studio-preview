package com.illiamelnyk.studio.feature.listen.presentation.collection

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.presentation.CollectionUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
