package com.illiamelnyk.studio.feature.create.presentation

import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.create.domain.Prompt
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerRulesTest {

    @Test
    fun `a prompt can be sent when a credit is left`() {
        val composer = ComposerUi(prompt = "Lo-fi beat").checked(creditsLeft = 1)

        assertEquals(ComposerUi(prompt = "Lo-fi beat", canSend = true), composer)
    }

    @Test
    fun `an empty, blank or too long prompt can't be sent`() {
        listOf("", "  \n ", "a".repeat(Prompt.MAX_LENGTH + 1)).forEach { prompt ->
            assertEquals(prompt, false, ComposerUi(prompt = prompt).checked(creditsLeft = 8).canSend)
        }
        assertEquals(true, ComposerUi(prompt = "a".repeat(Prompt.MAX_LENGTH)).checked(creditsLeft = 8).canSend)
    }

    @Test
    fun `nothing is sent while a prompt is on its way`() {
        val composer = ComposerUi(prompt = "Lo-fi beat", isSending = true).checked(creditsLeft = 8)

        assertEquals(false, composer.canSend)
    }

    @Test
    fun `until the credits are known it waits without a word`() {
        val composer = ComposerUi(prompt = "Lo-fi beat").checked(creditsLeft = null)

        assertEquals(ComposerUi(prompt = "Lo-fi beat", canSend = false, notice = null), composer)
    }

    @Test
    fun `without credits it can't send and says why, even before a prompt is typed`() {
        assertEquals(
            ComposerUi(notice = UiText.Resource(R.string.error_quota_exceeded)),
            ComposerUi().checked(creditsLeft = 0),
        )
        assertEquals(
            ComposerUi(prompt = "Lo-fi", notice = UiText.Resource(R.string.error_quota_exceeded)),
            ComposerUi(prompt = "Lo-fi").checked(creditsLeft = 0),
        )
    }

    @Test
    fun `two variants are allowed with the last credit`() {
        val composer = ComposerUi(prompt = "Lo-fi", variants = 2).checked(creditsLeft = 1)

        assertEquals(
            ComposerUi(prompt = "Lo-fi", variants = 2, canSend = true),
            composer,
        )
    }

    @Test
    fun `a notice goes once there are credits again`() {
        val composer = ComposerUi(prompt = "Lo-fi", notice = UiText.Resource(R.string.error_quota_exceeded))
            .checked(creditsLeft = 8)

        assertEquals(ComposerUi(prompt = "Lo-fi", canSend = true), composer)
    }
}
