package com.illiamelnyk.studio.feature.create.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.ui.components.StudioChip
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature
import com.illiamelnyk.studio.feature.create.presentation.components.ComposerBar
import com.illiamelnyk.studio.feature.create.presentation.components.ComposerOptions

/** Part of [CreateScreen]: the composer and its options row, which map their callbacks to intents. */
@Composable
fun CreateComposer(composer: ComposerUi, onIntent: (CreateIntent) -> Unit, modifier: Modifier = Modifier) {
    ComposerBar(
        composer = composer,
        onPromptChange = { onIntent(CreateIntent.PromptChanged(it)) },
        onLyricsChange = { onIntent(CreateIntent.CustomLyricsChanged(it)) },
        onSendClick = { onIntent(CreateIntent.SendClicked) },
        onAttachClick = { onIntent(CreateIntent.ReferenceClicked) },
        onEnhanceClick = { onIntent(CreateIntent.ComingSoonClicked(ComingSoonFeature.Enhance)) },
        onLyricsClick = { onIntent(CreateIntent.LyricsClicked) },
        onMuseClick = { onIntent(CreateIntent.ComingSoonClicked(ComingSoonFeature.Muse)) },
        onSettingsClick = { onIntent(CreateIntent.SettingsClicked) },
        onNoticeClick = { onIntent(CreateIntent.QuotaClicked) },
        modifier = modifier,
    ) {
        Column {
            composer.referenceLabel?.let { title ->
                StudioChip(title, onClick = {
                    onIntent(CreateIntent.ReferenceClicked)
                }, onRemove = { onIntent(CreateIntent.ReferenceRemoved) })
            }
            if (composer.lyricsMode == com.illiamelnyk.studio.feature.create.domain.LyricsMode.Custom) {
                StudioChip(
                    androidx.compose.ui.res.stringResource(
                        com.illiamelnyk.studio.R.string.create_lyrics_custom,
                    ),
                    onClick = {
                        onIntent(CreateIntent.CustomLyricsClicked)
                    },
                )
            }
            ComposerOptions(
                quality = composer.quality,
                lyricsMode = composer.lyricsMode,
                duration = composer.duration,
                variants = composer.variants,
                onQualitySelect = { onIntent(CreateIntent.QualitySelected(it)) },
                onStylesClick = { onIntent(CreateIntent.StylesClicked) },
                onInstrumentalRemove = { onIntent(CreateIntent.InstrumentalRemoved) },
                // The length is one of the settings, next to the model and the vocals.
                onDurationSelect = { onIntent(CreateIntent.DurationSelected(it)) },
                onVariantsClick = { onIntent(CreateIntent.VariantsClicked) },
            )
        }
    }
}
