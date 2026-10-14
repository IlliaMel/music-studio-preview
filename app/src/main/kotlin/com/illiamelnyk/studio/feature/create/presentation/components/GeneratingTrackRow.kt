package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.StudioLinearProgress
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A track that is being composed: the kit's track row in its generating state (living cover,
 * title, "Composing… 40%"), with a bar that fills to [progress] instead of the kit's endless sweep,
 * so the bar and the percentage agree. While [progress] is `null` (queued) the sweep runs.
 *
 * The kit's `TrackRow` has no way to show known progress yet; this follows its metrics and can
 * become its determinate mode.
 */
@Composable
fun GeneratingTrackRow(track: TrackUi, progress: Float?, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .fillMaxWidth()
            .heightIn(min = dimens.size.component.trackRow),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.coverSeed, dimens.size.cover.s, generating = true)
        Column(Modifier.weight(1f)) {
            StudioText(track.title, StudioTheme.typography.titleSmall, maxLines = 1)
            StudioText(
                text = track.subtitle.asString(),
                style = StudioTheme.typography.bodySmall,
                color = StudioTheme.colors.content.secondary,
                maxLines = 1,
            )
            val barModifier = Modifier.padding(top = dimens.spacing.xxs)
            if (progress == null) StudioLinearProgress(barModifier) else DeterminateProgress(progress, barModifier)
        }
    }
}

/**
 * A 4 dp track filled to [progress] in `content.primary`. A new value is reached with the motion
 * tokens' long, standard tween; the fill is read while drawing, so it moves without recomposing.
 */
@Composable
private fun DeterminateProgress(progress: Float, modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val motion = StudioTheme.motion
    val fill: State<Float> = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(motion.duration.long, easing = motion.easing.standard),
        label = "composingProgress",
    )
    Canvas(
        modifier
            .progressSemantics(value = progress)
            .fillMaxWidth()
            .height(StudioTheme.dimens.size.component.seekTrack)
            .clip(StudioTheme.shapes.full),
    ) {
        drawRect(colors.surface.level4)
        drawRect(colors.content.primary, size = Size(size.width * fill.value.coerceIn(0f, 1f), size.height))
    }
}

@PreviewLightDark
@Composable
private fun GeneratingTrackRowPreview() {
    PreviewScreen {
        Column {
            listOf(0.4f, null).forEach { progress ->
                GeneratingTrackRow(
                    track = TrackUi(
                        id = TrackId("1"),
                        title = "Night train synth-pop",
                        subtitle = if (progress == null) {
                            UiText.Resource(R.string.track_queued)
                        } else {
                            UiText.Resource(R.string.track_composing, 40)
                        },
                        coverSeed = "Night train synth-pop",
                        status = TrackUi.Status.Generating,
                    ),
                    progress = progress,
                )
            }
        }
    }
}
