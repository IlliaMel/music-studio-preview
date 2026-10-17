package com.illiamelnyk.studio.feature.listen.presentation.components
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import com.illiamelnyk.studio.feature.listen.presentation.CatalogSectionUi
import com.illiamelnyk.studio.feature.listen.presentation.CollectionUi
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class CatalogLoadingLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun stationsKeepTheirLayoutWhenLoadingFinishes() = assertStableSection(CatalogSectionKind.Stations)

    @Test fun trackCardsKeepTheirLayoutWhenLoadingFinishes() = assertStableSection(CatalogSectionKind.New)

    @Test fun genreCardsKeepTheirLayoutWhenLoadingFinishes() = assertStableSection(CatalogSectionKind.Genres)
    private fun assertStableSection(kind: CatalogSectionKind) {
        val section = mutableStateOf(CatalogSectionUi(kind))
        compose.setContent { StudioTheme { CatalogSectionRow(section.value, {}, {}, Modifier.testTag("section")) } }
        val loadingHeight = compose.onNodeWithTag("section").fetchSemanticsNode().size.height
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        compose.runOnIdle {
            section.value = CatalogSectionUi(
                kind,
                persistentListOf(CollectionUi("sample", "Sample", UiText.Raw("Artist"), "sample")),
                isLoading = false,
            )
        }
        val title = compose.onNodeWithText("Sample", useUnmergedTree = true).fetchSemanticsNode().size.height
        assertEquals(
            "kind=$kind titleHeight=$title",
            loadingHeight,
            compose.onNodeWithTag("section").fetchSemanticsNode().size.height,
        )
    }
}
