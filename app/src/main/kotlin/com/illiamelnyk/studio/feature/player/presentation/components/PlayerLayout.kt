package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface

/**
 * Where the player's parts go: the [topBar] on top, then the [artwork] above the [details] (the
 * title, the controls, the actions) in portrait, or beside them in landscape, where the details
 * scroll if the screen is too low for them.
 *
 * @param artwork gets the modifier with the space it fills.
 */
@Composable
fun PlayerLayout(
    topBar: @Composable () -> Unit,
    artwork: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    details: @Composable ColumnScope.() -> Unit,
) {
    val dimens = StudioTheme.dimens
    val detailsArrangement = Arrangement.spacedBy(dimens.spacing.m, Alignment.CenterVertically)
    // Movable, so the artwork and the details keep their state, such as the lyrics' scroll, when
    // the window turns from portrait to landscape without being recreated.
    val movableArtwork =
        remember(artwork) { movableContentOf<Modifier> { artworkModifier -> artwork(artworkModifier) } }
    val movableDetails = remember(details) { movableContentWithReceiverOf<ColumnScope> { details() } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            topBar()
            if (landscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = dimens.layout.screenPadding)
                        .padding(bottom = dimens.spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xl),
                ) {
                    movableArtwork(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = detailsArrangement,
                    ) { movableDetails() }
                }
            } else {
                movableArtwork(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.m),
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = dimens.layout.screenPadding)
                        .padding(bottom = dimens.spacing.m),
                    verticalArrangement = detailsArrangement,
                ) { movableDetails() }
            }
        }
    }
}

@PreviewLightDark
@PreviewScreenSizes
@Composable
private fun PlayerLayoutPreview() {
    PlayerPreviewSurface {
        PlayerLayout(
            topBar = { StudioText("Top bar", StudioTheme.typography.titleMedium) },
            artwork = { modifier -> Cover("Glass Harbor", StudioTheme.dimens.size.cover.l, modifier) },
        ) {
            StudioText("Details", StudioTheme.typography.titleMedium)
        }
    }
}
