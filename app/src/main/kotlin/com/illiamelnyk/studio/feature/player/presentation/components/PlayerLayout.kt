package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
    stationMode: Boolean = false,
    queueMode: Boolean = false,
    details: @Composable ColumnScope.(Boolean) -> Unit,
) {
    val dimens = StudioTheme.dimens
    val fontScale = LocalDensity.current.fontScale
    val detailsArrangement = Arrangement.spacedBy(dimens.spacing.m, Alignment.CenterVertically)
    // Movable, so the artwork and the details keep their state, such as the lyrics' scroll, when
    // the window turns from portrait to landscape without being recreated.
    val movableArtwork =
        remember(artwork) { movableContentOf<Modifier> { artworkModifier -> artwork(artworkModifier) } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val horizontalInset = (maxWidth * PLAYER_SIDE_INSET_FRACTION).coerceAtLeast(dimens.layout.screenPadding)
        val compactArtworkHeight = if (queueMode) {
            dimens.size.touchTarget * 3 + dimens.size.component.trackRow + dimens.spacing.xs * 3 +
                dimens.size.touchTarget * (fontScale - 1f).coerceAtLeast(0f)
        } else {
            minOf(maxWidth - horizontalInset * 2, dimens.size.cover.l)
        }
        val portraitArtworkHeight = compactArtworkHeight + dimens.spacing.m * 2 + dimens.spacing.xl
        val compactHeight = maxHeight < (dimens.size.cover.l + dimens.size.touchTarget * 7) * fontScale
        val compactPortrait = !landscape && compactHeight
        val movableDetails = remember(details, compactHeight) {
            movableContentWithReceiverOf<ColumnScope> { details(compactHeight) }
        }
        val playerModifier = if (compactPortrait) {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxSize()
        }
        Column(playerModifier) {
            topBar()
            if (landscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = dimens.spacing.huge)
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
                val artworkModifier = if (compactPortrait) {
                    Modifier.height(portraitArtworkHeight)
                } else {
                    Modifier.weight(1f)
                }
                movableArtwork(
                    artworkModifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalInset, vertical = dimens.spacing.m)
                        .padding(bottom = dimens.spacing.xl),
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = horizontalInset)
                        .padding(
                            bottom = if (compactPortrait) dimens.spacing.m else dimens.spacing.huge,
                        ),
                    verticalArrangement = if (stationMode) {
                        Arrangement.spacedBy(dimens.spacing.giant + dimens.spacing.s)
                    } else {
                        detailsArrangement
                    },
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

private const val PLAYER_SIDE_INSET_FRACTION = 0.0775f
