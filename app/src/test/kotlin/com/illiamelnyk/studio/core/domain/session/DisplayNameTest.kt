package com.illiamelnyk.studio.core.domain.session

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameTest {

    @Test
    fun `a name is trimmed`() {
        assertEquals("Illia", (DisplayName.parse("  Illia \n") as DisplayName.Parsed.Valid).name.value)
    }

    @Test
    fun `an empty or whitespace-only name is blank`() {
        assertEquals(DisplayName.Parsed.Blank, DisplayName.parse(""))
        assertEquals(DisplayName.Parsed.Blank, DisplayName.parse(" \t "))
    }

    @Test
    fun `forty characters are allowed and forty-one are not`() {
        val forty = "a".repeat(40)

        assertEquals(forty, (DisplayName.parse(forty) as DisplayName.Parsed.Valid).name.value)
        assertEquals(DisplayName.Parsed.TooLong, DisplayName.parse(forty + "a"))
    }

    @Test
    fun `the length counts after trimming`() {
        val forty = "a".repeat(40)

        assertEquals(forty, (DisplayName.parse("  $forty  ") as DisplayName.Parsed.Valid).name.value)
    }
}
