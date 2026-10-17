package com.illiamelnyk.studio.feature.listen.presentation.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import com.illiamelnyk.studio.core.ui.components.coverArt
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.presentation.CollectionUi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Seeded line art. Only its canvas reads the animation phase. */
@Composable
fun GenreCard(collection: CollectionUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    val phase = rememberInfiniteTransition(label = "genre-art").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(StudioTheme.motion.loop.progress, easing = StudioTheme.motion.loopEasing),
        ),
        label = "phase",
    )
    val line = StudioTheme.colors.overlay.onMediaPrimary
    val stroke = dimens.stroke.hairline
    val seed = collection.coverSeed.hashCode().mod(5) + 2
    Column(
        modifier.width(dimens.size.cover.l).clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        Box(Modifier.size(dimens.size.cover.l).clip(StudioTheme.shapes.m).coverArt(collection.coverSeed)) {
            Box(
                Modifier.matchParentSize().drawWithCache {
                    val paths = Array(8) { Path() }
                    val xCoordinates = FloatArray(41) { it / 40f * size.width }
                    val waveSin = Array(8) { row ->
                        FloatArray(41) { step -> sin(step / 40f * seed * PI.toFloat() + row) }
                    }
                    val waveCos = Array(8) { row ->
                        FloatArray(41) { step -> cos(step / 40f * seed * PI.toFloat() + row) }
                    }
                    val color = line.copy(alpha = 0.7f)
                    val style = Stroke(stroke.toPx())
                    onDrawBehind {
                        val phaseSin = sin(phase.value)
                        val phaseCos = cos(phase.value)
                        paths.forEachIndexed { row, path ->
                            path.reset()
                            repeat(41) { step ->
                                val wave = waveSin[row][step] * phaseCos + waveCos[row][step] * phaseSin
                                val y = ((row + 1) / 10f + wave * 0.045f) * size.height
                                if (step == 0) {
                                    path.moveTo(xCoordinates[step], y)
                                } else {
                                    path.lineTo(xCoordinates[step], y)
                                }
                            }
                            drawPath(path, color, style = style)
                        }
                    }
                },
            )
        }
        StudioText(collection.title, StudioTheme.typography.titleSmall, maxLines = 1)
    }
}
