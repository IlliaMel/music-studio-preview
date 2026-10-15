package com.illiamelnyk.studio.feature.create.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolTemplateTest {

    private val genre = ToolSlot("genre", "Genre", listOf("Folk", "Jazz"))
    private val mood = ToolSlot("mood", "Mood", listOf("Warm", "Dark"))

    @Test
    fun `placeholders split the text into text and slots, in order`() {
        val template = ToolTemplate.of("A {genre} song, {mood} and slow", genre, mood)

        assertEquals(
            listOf(
                ToolTemplate.Part.Text("A "),
                ToolTemplate.Part.Slot(genre),
                ToolTemplate.Part.Text(" song, "),
                ToolTemplate.Part.Slot(mood),
                ToolTemplate.Part.Text(" and slow"),
            ),
            template.parts,
        )
        assertEquals(listOf(genre, mood), template.slots)
    }

    @Test
    fun `a template that starts and ends with a slot has no empty text around it`() {
        val template = ToolTemplate.of("{genre} meets {mood}", genre, mood)

        assertEquals(
            listOf(ToolTemplate.Part.Slot(genre), ToolTemplate.Part.Text(" meets "), ToolTemplate.Part.Slot(mood)),
            template.parts,
        )
    }

    @Test
    fun `filling needs every slot and puts each value in its place`() {
        val template = ToolTemplate.of("A {genre} song, {mood} and slow", genre, mood)

        assertNull(template.fill(emptyMap()))
        assertNull(template.fill(mapOf("genre" to "Folk")))
        assertEquals("A Folk song, Dark and slow", template.fill(mapOf("genre" to "Folk", "mood" to "Dark")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a placeholder without its slot is a mistake`() {
        ToolTemplate.of("A {genre} song in {key}", genre)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a slot without its placeholder is a mistake`() {
        ToolTemplate.of("A {genre} song", genre, mood)
    }

    @Test
    fun `every tool fills into a prompt the service accepts`() {
        Tool.entries.forEach { tool ->
            val values = tool.template.slots.associate { slot -> slot.id to slot.options.maxBy(String::length) }

            val prompt = tool.template.fill(values)

            assertTrue("${tool.name}: $prompt", prompt != null && Prompt.isValid(prompt))
            assertTrue(tool.name, tool.defaultDuration in TrackLengths.all)
        }
    }
}
