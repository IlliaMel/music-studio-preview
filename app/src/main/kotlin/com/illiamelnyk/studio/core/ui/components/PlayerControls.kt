package com.illiamelnyk.studio.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.core.ui.theme.darkStudioColors

/**
 * The player's transport on cover color: shuffle, previous, the 64 dp play/pause button, next and
 * repeat. Play and pause swap with a quick scale and fade. Shuffle and repeat are switches that
 * TalkBack reads as on or off; on, they are in the primary media color, off in the secondary one.
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    shuffle: Boolean,
    onShuffleChange: (Boolean) -> Unit,
    repeat: Boolean,
    onRepeatChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaToggle(StudioIcons.Shuffle, stringResource(R.string.ui_shuffle), shuffle, onShuffleChange)
        MediaButton(StudioIcons.SkipPreviousFilled, stringResource(R.string.ui_previous), onPrevious)
        PlayButton(isPlaying, onPlayPause)
        MediaButton(StudioIcons.SkipNextFilled, stringResource(R.string.ui_next), onNext)
        MediaToggle(StudioIcons.Repeat, stringResource(R.string.ui_repeat), repeat, onRepeatChange)
    }
}

@Composable
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val motion = StudioTheme.motion
    val description = stringResource(if (isPlaying) R.string.ui_pause else R.string.ui_play)
    Box(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .size(dimens.size.component.playButton)
            .background(colors.overlay.onMediaPrimary, StudioTheme.shapes.full),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                val spec = tween<Float>(motion.duration.short, easing = motion.easing.standard)
                (fadeIn(spec) + scaleIn(spec, motion.pressScale)) togetherWith
                    (fadeOut(spec) + scaleOut(spec, motion.pressScale))
            },
            label = "playPause",
        ) { playing ->
            StudioIcon(
                icon = if (playing) StudioIcons.PauseFilled else StudioIcons.PlayArrowFilled,
                contentDescription = null,
                tint = OnMediaInverse,
                size = dimens.size.icon.xl,
            )
        }
    }
}

@Composable
private fun MediaButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    val dimens = StudioTheme.dimens
    Box(
        modifier = Modifier
            .minimumTouchTarget()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .size(dimens.size.component.iconButton),
        contentAlignment = Alignment.Center,
    ) {
        StudioIcon(
            icon,
            contentDescription = null,
            tint = StudioTheme.colors.overlay.onMediaPrimary,
            size = dimens.size.icon.xl,
        )
    }
}

@Composable
private fun MediaToggle(@DrawableRes icon: Int, description: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val motion = StudioTheme.motion
    val tint by animateColorAsState(
        targetValue = if (on) colors.overlay.onMediaPrimary else colors.overlay.onMediaSecondary,
        animationSpec = tween(motion.duration.short, easing = motion.easing.standard),
        label = "mediaToggle",
    )
    Box(
        modifier = Modifier
            .minimumTouchTarget()
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = description }
            .size(dimens.size.component.iconButton),
        contentAlignment = Alignment.Center,
    ) {
        StudioIcon(icon, contentDescription = null, tint = tint)
    }
}

// The play button is the white of `overlay.onMediaPrimary` in both schemes, like all colors on media,
// so its icon is dark in both too; tokens.json has no "on media, inverse" role, so it is the dark
// scheme's `inverse.content`.
private val OnMediaInverse = darkStudioColors().inverse.content

@PreviewLightDark
@Composable
private fun PlayerControlsPreview() {
    PreviewSurface {
        PlayerControls(
            isPlaying = true,
            onPlayPause = {},
            onPrevious = {},
            onNext = {},
            shuffle = true,
            onShuffleChange = {},
            repeat = false,
            onRepeatChange = {},
        )
    }
}
