package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.feature.create.domain.GenerationSettings
import com.illiamelnyk.studio.feature.create.domain.LyricsMode
import com.illiamelnyk.studio.feature.create.presentation.ComposerUi
import com.illiamelnyk.studio.feature.create.presentation.toSettingsUi
import com.illiamelnyk.studio.feature.create.presentation.withSettings
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationContractRulesTest {
    private val capabilities = GenerationCapabilities(
        "elevenlabs",
        setOf("auto"),
        true,
        setOf(1, 2),
        true,
        false,
        1,
    )

    @Test
    fun sectionNamesAndLineLengthsFollowUpstreamUnicodeCharacterLimits() {
        fun rejection(lyrics: String) = capabilities.rejection(
            GenerationRequest("Original acoustic piano", customLyrics = lyrics),
            1,
        )
        assertEquals(AppError.UnsupportedMode, rejection("[${"x".repeat(101)}]\nNew day"))
        assertEquals(AppError.UnsupportedMode, rejection("[]\nNew day"))
        assertEquals(AppError.UnsupportedMode, rejection("x".repeat(201)))
        assertNull(rejection("[${"x".repeat(100)}]\nNew day"))
        assertNull(rejection("[${"😀".repeat(100)}]\nNew day"))
        assertNull(rejection("😀".repeat(200)))
    }

    @Test
    fun composerAndSettingsExposeTheSameEffectiveLengthThatGenerationUses() {
        val ordinary = GenerationSettings()
        assertNull(ordinary.effectiveDuration)
        assertNull(ComposerUi().withSettings(ordinary).duration)
        val advanced = listOf(
            ordinary.copy(lyricsMode = LyricsMode.Custom, customLyrics = "New day"),
            ordinary.copy(referenceAudioUri = "file:///reference.wav"),
            ordinary.copy(remixTrackIds = listOf("original")),
        )
        advanced.forEach { settings ->
            assertEquals(2.minutes, settings.effectiveDuration)
            assertEquals(2.minutes, ComposerUi().withSettings(settings).duration?.duration)
            assertEquals(2.minutes, settings.toSettingsUi().duration?.duration)
            val selected = settings.copy(duration = 45.seconds)
            assertEquals(45.seconds, selected.effectiveDuration)
            assertEquals(45.seconds, ComposerUi().withSettings(selected).duration?.duration)
        }
    }
}
