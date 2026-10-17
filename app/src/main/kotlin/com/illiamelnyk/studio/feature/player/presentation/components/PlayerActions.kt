package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface

/**
 * The glass pills under the controls: Remix, Add To and Like. Liking fills the heart and pops the
 * pill; the pop is read while drawing, so it doesn't recompose the row. The label stays "Like", so
 * the row doesn't shift; TalkBack hears the pill as selected.
 */
@Composable
fun PlayerActions(
    isLiked: Boolean,
    onRemixClick: () -> Unit,
    onAddToClick: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
    canRemix: Boolean = true,
) {
    val motion = StudioTheme.motion
    val likeScale = remember { Animatable(1f) }
    // Only a like pops: not an unlike, and not the like the player opens with.
    val shownLiked = remember { LikeHolder(isLiked) }
    LaunchedEffect(isLiked) {
        val liked = isLiked && !shownLiked.liked
        shownLiked.liked = isLiked
        if (liked) {
            likeScale.animateTo(LIKE_POP_SCALE, tween(motion.duration.short, easing = motion.easing.standard))
            likeScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        } else {
            likeScale.animateTo(1f, tween(motion.duration.short, easing = motion.easing.standard))
        }
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs, Alignment.CenterHorizontally),
    ) {
        if (canRemix) {
            StudioButton(
                text = stringResource(R.string.player_remix),
                onClick = onRemixClick,
                size = StudioButtonSize.Large,
                style = StudioButtonStyle.Glass,
                icon = StudioIcons.AutoFixHigh,
            )
        }
        StudioButton(
            text = stringResource(R.string.player_add_to),
            onClick = onAddToClick,
            size = StudioButtonSize.Large,
            style = StudioButtonStyle.Glass,
            icon = StudioIcons.Add,
        )
        StudioButton(
            text = stringResource(R.string.player_like),
            onClick = onLikeClick,
            size = StudioButtonSize.Large,
            modifier = Modifier
                .semantics { selected = isLiked }
                .graphicsLayer {
                    scaleX = likeScale.value
                    scaleY = likeScale.value
                },
            style = StudioButtonStyle.Glass,
            icon = if (isLiked) StudioIcons.FavoriteFilled else StudioIcons.Favorite,
        )
    }
}

/** The like the pill last showed; not state, nothing draws from it. */
private class LikeHolder(var liked: Boolean)

private const val LIKE_POP_SCALE = 1.12f

@PreviewLightDark
@Composable
private fun PlayerActionsPreview() {
    PlayerPreviewSurface {
        PlayerActions(isLiked = false, onRemixClick = {}, onAddToClick = {}, onLikeClick = {})
        PlayerActions(isLiked = true, onRemixClick = {}, onAddToClick = {}, onLikeClick = {})
    }
}
