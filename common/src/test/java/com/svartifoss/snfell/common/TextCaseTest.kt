package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCaseTest {

    @Test
    fun `title case capitalizes every word and lowercases the remaining letters`() {
        assertEquals(
                "Titulo De Um Texto Qualquer",
                TextCase.TITLE_CASE.apply("TITULO DE UM TEXTO QUALQUER"))
    }

    @Test
    fun `title case preserves whitespace and handles punctuation and accents`() {
        assertEquals(
                "Álbum  Do\n\"João\"",
                TextCase.TITLE_CASE.apply("ÁLBUM  DO\n\"JOÃO\""))
    }

    @Test
    fun `title case preference value is parsed`() {
        assertEquals(TextCase.TITLE_CASE, TextCase.fromPreference("title_case"))
    }
}
