package com.illiamelnyk.studio.catalog

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.MiniPlayer
import com.illiamelnyk.studio.core.ui.components.Orb
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.foundation.ProvideGlassBackdrop
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.glassBackdrop
import com.illiamelnyk.studio.core.ui.foundation.rememberGlassBackdrop
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * Glass over moving content: covers scroll under a row of glass buttons and the mini player, which
 * blur them on Android 12 and newer. The orbs scroll with the covers: one breathing, one driven by a
 * changing level, one at rest.
 */
@Composable
internal fun GlassPage() {
    val dimens = StudioTheme.dimens
    val backdrop = rememberGlassBackdrop()
    var playing by remember { mutableStateOf(true) }
    val level by rememberInfiniteTransition(label = "level").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LEVEL_SWING_MS), RepeatMode.Reverse),
        label = "level",
    )
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdrop(backdrop),
            contentPadding = PaddingValues(
                start = dimens.layout.screenPadding,
                end = dimens.layout.screenPadding,
                // Room for the glass buttons above and the mini player below, so nothing ends up hidden.
                top = dimens.size.touchTarget + dimens.spacing.m,
                bottom = dimens.size.component.miniPlayer + dimens.spacing.xxl + navigationBar,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.layout.itemGap),
        ) {
            item { Orbs(level = { level }) }
            items(CoverRows, key = { it.first }) { (left, right) ->
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.layout.itemGap)) {
                    Cover(left, dimens.size.cover.l, Modifier.weight(1f))
                    Cover(right, dimens.size.cover.l, Modifier.weight(1f))
                }
            }
        }
        ProvideGlassBackdrop(backdrop) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = dimens.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            ) {
                StudioButton("Remix", onClick = {}, style = StudioButtonStyle.Glass, icon = StudioIcons.AutoFixHigh)
                StudioButton("Add to", onClick = {}, style = StudioButtonStyle.Glass, icon = StudioIcons.Add)
                StudioButton("Like", onClick = {}, style = StudioButtonStyle.Glass, icon = StudioIcons.Favorite)
            }
            MiniPlayer(
                title = "Night Drive Through Kyiv",
                subtitle = "Studio",
                coverSeed = "Night Drive Through Kyiv",
                isPlaying = playing,
                progress = { level },
                onClick = {},
                onPlayPause = { playing = !playing },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.s),
                onNext = {},
            )
        }
    }
}

@Composable
private fun Orbs(level: () -> Float) {
    val dimens = StudioTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.spacing.xl),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        OrbWithCaption("Working") { Orb() }
        OrbWithCaption("Level") { Orb(level = level) }
        OrbWithCaption("At rest") { Orb(active = false) }
    }
}

@Composable
private fun OrbWithCaption(caption: String, orb: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s),
    ) {
        orb()
        StudioText(caption, StudioTheme.typography.labelMedium, color = StudioTheme.colors.content.secondary)
    }
}

private val CoverRows = listOf(
    "Light Through Clouds" to "Rust and Redemption",
    "Night Drive Through Kyiv" to "Lo-fi rainy morning",
    "Ambient" to "Late Night Lo-fi",
    "Morning Jazz" to "Deep Focus",
    "Genreshift" to "Voice to Song",
    "Unplugged" to "Rainy Window",
    "Okean" to "Mira",
    "Summer Song" to "Bridge",
)

private const val LEVEL_SWING_MS = 1_800
