package com.illiamelnyk.studio.catalog

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.ui.components.ComingSoonSheetContent
import com.illiamelnyk.studio.core.ui.components.StudioBottomSheet
import com.illiamelnyk.studio.core.ui.components.StudioFilterChip
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.components.TopBar
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * Every component of the design system in every state, on a device, in both schemes, one page per
 * part of the kit. Debug builds only: it has its own launcher entry, "Studio UI Kit", and isn't part
 * of the release build.
 *
 * `adb shell am start -n com.illiamelnyk.studio/.catalog.CatalogActivity --es page Overlays` opens
 * a page directly, for recordings.
 */
class CatalogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startPage = intent.getStringExtra(EXTRA_PAGE)
            ?.let { name -> CatalogPage.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
            ?: CatalogPage.Basics
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
                Catalog(darkTheme = darkTheme, onDarkThemeChange = { darkTheme = it }, startPage = startPage)
            }
        }
    }

    private companion object {
        const val EXTRA_PAGE = "page"
    }
}

internal enum class CatalogPage { Basics, Content, Inputs, Overlays, Player, Glass }

private enum class Scheme(val label: String, val dark: Boolean) {
    Dark("Dark", dark = true),
    Light("Light", dark = false),
}

@Composable
private fun Catalog(darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit, startPage: CatalogPage) {
    var page by rememberSaveable { mutableStateOf(startPage) }
    var sheetVisible by rememberSaveable { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(StudioTheme.colors.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
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
            PageTabs(page, onSelect = { page = it })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (page) {
                    CatalogPage.Basics -> BasicsPage()
                    CatalogPage.Content -> ContentPage()
                    CatalogPage.Inputs -> InputsPage()
                    CatalogPage.Overlays -> OverlaysPage(onOpenSheet = { sheetVisible = true })
                    CatalogPage.Player -> PlayerPage()
                    CatalogPage.Glass -> GlassPage()
                }
            }
        }
        // Last in the full-screen box, so the sheet and its scrim cover everything.
        StudioBottomSheet(visible = sheetVisible, onDismissRequest = { sheetVisible = false }) {
            ComingSoonSheetContent(
                title = "Voice to Song",
                description = "Hum or sing a melody, and Studio builds a full track around it.",
                steps = listOf(
                    "Record up to 30 s of your voice",
                    "`upload` the recording",
                    "Pass its id as `conditioning_ref` with the prompt",
                ),
                actionLabel = "I want this",
                onAction = { sheetVisible = false },
                icon = StudioIcons.Mic,
            )
        }
    }
}

@Composable
private fun PageTabs(page: CatalogPage, onSelect: (CatalogPage) -> Unit) {
    val dimens = StudioTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = dimens.layout.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        CatalogPage.entries.forEach { entry ->
            StudioFilterChip(entry.name, selected = entry == page, onClick = { onSelect(entry) })
        }
    }
}
