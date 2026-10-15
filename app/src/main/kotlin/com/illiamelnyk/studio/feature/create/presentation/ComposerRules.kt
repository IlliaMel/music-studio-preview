package com.illiamelnyk.studio.feature.create.presentation

import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.create.domain.GenerationSettings
import com.illiamelnyk.studio.feature.create.domain.Prompt
import com.illiamelnyk.studio.feature.create.presentation.common.toOptionUi

/** The composer showing the options of [settings]. */
fun ComposerUi.withSettings(settings: GenerationSettings): ComposerUi = copy(
    quality = settings.quality,
    lyricsMode = settings.lyricsMode,
    duration = settings.duration?.toOptionUi(),
    variants = settings.variants,
    customLyrics = settings.customLyrics,
)

/**
 * The composer with [ComposerUi.canSend] and [ComposerUi.notice] worked out: it can send a valid
 * prompt when at least one credit remains and nothing is being sent already. Without
 * credits it says why, whatever the prompt; until the credits are known it waits silently.
 *
 * @param creditsLeft `null` until the credits are known.
 */
fun ComposerUi.checked(creditsLeft: Int?): ComposerUi {
    val notice = when {
        creditsLeft == null -> null
        creditsLeft == 0 -> UiText.Resource(R.string.error_quota_exceeded)
        else -> null
    }
    val enoughCredits = creditsLeft != null && creditsLeft > 0
    return copy(
        canSend = Prompt.isValid(prompt) && enoughCredits && !isSending &&
            (lyricsMode != com.illiamelnyk.studio.feature.create.domain.LyricsMode.Custom || customLyrics.isNotBlank()),
        notice = notice,
    )
}
