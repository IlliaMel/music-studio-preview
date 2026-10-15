package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Composer
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.Prompt
import com.illiamelnyk.studio.feature.create.presentation.ComposerUi
import com.illiamelnyk.studio.feature.create.presentation.CreatePreviewData

/**
 * The composer at the bottom of Create, over a fade of the screen color so the list scrolls away
 * under it. It expands while it has focus or a prompt: then the [options] row shows above
 * the field, with Enhance, Lyrics and the character count in the actions. [ComposerUi.notice] says
 * why a prompt can't be sent, such as no credits left.
 *
 * The caller keeps it above the keyboard with `imePadding()`.
 */
@Composable
fun ComposerBar(
    composer: ComposerUi,
    onPromptChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit,
    onEnhanceClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMuseClick: () -> Unit,
    onNoticeClick: () -> Unit,
    modifier: Modifier = Modifier,
    options: @Composable () -> Unit = {},
) {
    val dimens = StudioTheme.dimens
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val expanded = focused || composer.prompt.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fadeBehind(StudioTheme.colors.background, dimens.spacing.m)
            .padding(start = dimens.layout.screenPadding, end = dimens.layout.screenPadding, top = dimens.spacing.m)
            .padding(bottom = dimens.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        composer.notice?.let { notice ->
            StudioText(
                text = notice.asString(),
                style = StudioTheme.typography.bodySmall,
                modifier = Modifier.clickable(onClick = onNoticeClick).padding(horizontal = dimens.spacing.xs),
                color = StudioTheme.colors.content.secondary,
            )
        }
        if (composer.prompt.length >= Prompt.MAX_LENGTH - 100) {
            StudioText(
                stringResource(R.string.ui_character_count, composer.prompt.length, Prompt.MAX_LENGTH),
                StudioTheme.typography.bodySmall,
                color = if (composer.prompt.length >
                    Prompt.MAX_LENGTH
                ) {
                    StudioTheme.colors.error.content
                } else {
                    StudioTheme.colors.content.secondary
                },
            )
        }
        Composer(
            value = composer.prompt,
            onValueChange = onPromptChange,
            onSend = onSendClick,
            expanded = expanded,
            placeholder = stringResource(R.string.create_composer_placeholder),
            sendEnabled = composer.canSend,
            showSend = expanded,
            characterLimit = null,
            interactionSource = interactionSource,
            options = options,
            leadingActions = {
                ComposerAction(
                    StudioIcons.Add,
                    R.string.create_composer_upload,
                    onAttachClick,
                    StudioIconButtonStyle.Surface,
                )
                if (expanded) {
                    StudioSegmentedControl(
                        options = listOf(R.string.create_mode_create, R.string.create_soon_muse),
                        selected = R.string.create_mode_create,
                        onSelect = { if (it == R.string.create_soon_muse) onMuseClick() },
                        compact = true,
                    ) { stringResource(it) }
                }
            },
            trailingActions = {
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(StudioTheme.motion.duration.medium)) + expandHorizontally(),
                    exit = fadeOut(tween(StudioTheme.motion.duration.short)) + shrinkHorizontally(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
                        ComposerAction(StudioIcons.AutoAwesome, R.string.create_composer_enhance, onEnhanceClick)
                        ComposerAction(StudioIcons.Notes, R.string.create_composer_lyrics, onLyricsClick)
                    }
                }
                if (!expanded) {
                    ComposerAction(
                        StudioIcons.Tune,
                        R.string.create_composer_settings,
                        onSettingsClick,
                        StudioIconButtonStyle.Surface,
                    )
                }
            },
        )
    }
}

@Composable
private fun ComposerAction(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    onClick: () -> Unit,
    style: StudioIconButtonStyle = StudioIconButtonStyle.Muted,
) {
    StudioIconButton(
        icon = icon,
        contentDescription = stringResource(description),
        onClick = onClick,
        style = style,
        size = StudioIconButtonSize.Small,
    )
}

/** Paints [color] behind the content, fading in over its top [fade], so what scrolls under it fades out. */
private fun Modifier.fadeBehind(color: Color, fade: Dp): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(
        0f to color.copy(alpha = 0f),
        (fade.toPx() / size.height).coerceIn(0f, 1f) to color,
    )
    onDrawBehind { drawRect(brush) }
}

private class ComposerPreviewData : PreviewParameterProvider<ComposerUi> {
    override val values = CreatePreviewData.composers.asSequence()
}

@PreviewLightDark
@Composable
private fun ComposerBarPreview(@PreviewParameter(ComposerPreviewData::class) composer: ComposerUi) {
    PreviewScreen {
        ComposerBar(
            composer = composer,
            onPromptChange = {},
            onSendClick = {},
            onAttachClick = {},
            onEnhanceClick = {},
            onLyricsClick = {},
            onSettingsClick = {},
            onMuseClick = {},
            onNoticeClick = {},
            options = {
                ComposerOptions(
                    quality = composer.quality,
                    lyricsMode = composer.lyricsMode,
                    duration = composer.duration,
                    variants = composer.variants,
                    onQualitySelect = {},
                    onStylesClick = {},
                    onInstrumentalRemove = {},
                    onDurationSelect = {},
                    onVariantsClick = {},
                )
            },
        )
    }
}
