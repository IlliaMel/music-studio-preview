package com.illiamelnyk.studio.feature.auth.presentation.welcome.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * Bundled covers keep a fixed crop and position from the first visible frame.
 */
@Composable
fun WelcomeCollage(modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    val background = StudioTheme.colors.background
    Box(
        modifier = modifier.fillMaxWidth().clipToBounds().drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, background), startY = size.height / 2))
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .graphicsLayer {
                    rotationZ = COLLAGE_ANGLE
                },
            verticalArrangement = Arrangement.spacedBy(dimens.layout.itemGap),
        ) {
            COVER_ROWS.forEach { seeds ->
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.layout.itemGap)) {
                    seeds.forEach { seed -> Cover(seed = seed, size = dimens.size.cover.l) }
                }
            }
        }
    }
}

private const val COLLAGE_ANGLE = -8f

/** Seeds that give a varied mix of the palette's gradients. */
private val COVER_ROWS = listOf(
    listOf("Glass Harbor", "Paper Moons", "Signal Fire", "Copper Sky", "Static Bloom"),
    listOf("Night Drive", "Cold Brew", "Lanterns", "Afterglow", "Slow Tide"),
    listOf("Kinetic", "Low Orbit", "Neon tram", "Rainy window", "Kyiv after rain"),
)

@PreviewLightDark
@Composable
private fun WelcomeCollagePreview() {
    PreviewScreen { WelcomeCollage() }
}
