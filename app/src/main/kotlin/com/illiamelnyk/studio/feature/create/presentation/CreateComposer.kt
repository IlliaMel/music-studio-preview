package com.illiamelnyk.studio.feature.create.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature
import com.illiamelnyk.studio.feature.create.presentation.components.ComposerBar
import com.illiamelnyk.studio.feature.create.presentation.components.ComposerOptions

/** Part of [CreateScreen]: the composer and its options row, which map their callbacks to intents. */
@Composable
fun CreateComposer(composer: ComposerUi, onIntent: (CreateIntent) -> Unit, modifier: Modifier = Modifier) {
    ComposerBar(
        composer = composer,
        onPromptChange = { onIntent(CreateIntent.PromptChanged(it)) },
        onSendClick = { onIntent(CreateIntent.SendClicked) },
        onAttachClick = { onIntent(CreateIntent.ComingSoonClicked(ComingSoonFeature.Upload)) },
        onEnhanceClick = { onIntent(CreateIntent.ComingSoonClicked(ComingSoonFeature.Enhance)) },
        onLyricsClick = { onIntent(CreateIntent.ComingSoonClicked(ComingSoonFeature.Lyrics)) },
        onSettingsClick = { onIntent(CreateIntent.SettingsClicked) },
        modifier = modifier,
    ) {
        ComposerOptions(
            quality = composer.quality,
            style = composer.style,
            duration = composer.duration.label,
            variants = composer.variants,
            onQualitySelect = { onIntent(CreateIntent.QualitySelected(it)) },
            onStylesClick = { onIntent(CreateIntent.StylesClicked) },
            onStyleRemove = { onIntent(CreateIntent.StyleRemoved) },
            // The length is one of the settings, next to the model and the vocals.
            onDurationClick = { onIntent(CreateIntent.SettingsClicked) },
            onVariantsClick = { onIntent(CreateIntent.VariantsClicked) },
        )
    }
}
