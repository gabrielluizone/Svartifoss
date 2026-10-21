package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportGlyphsTest {

    @Test
    fun `the picker offers the face's own first and then each style in order`() {
        assertEquals(listOf("follow", "a", "b", "c", "d", "e"), TransportGlyphs.CHOICES)
        assertEquals("follow", MiscPreferences.WEAR_PLAY_ICON.defaultValue)
        assertEquals("follow", MiscPreferences.WEAR_PAUSE_ICON.defaultValue)
    }

    @Test
    fun `every style resolves to itself for both marks`() {
        TransportGlyph.entries.forEach { glyph ->
            TransportMark.entries.forEach { mark ->
                assertEquals(glyph, TransportGlyphs.resolve(mark, glyph.preferenceValue, "material"))
            }
        }
    }

    /** Follow is "what the face was designed with", which is not the same glyph on every face. */
    @Test
    fun `follow keeps the glyph each face was designed with`() {
        TransportGlyphs.FACES.forEach { face ->
            assertEquals(TransportGlyph.D, TransportGlyphs.resolve(TransportMark.PLAY, "follow", face))
        }
        assertEquals(TransportGlyph.D, TransportGlyphs.resolve(TransportMark.PAUSE, "follow", "material"))
        // Expressive's pause has always been the sharp outline, and "follow" must not flatten it.
        assertEquals(TransportGlyph.C, TransportGlyphs.resolve(TransportMark.PAUSE, "follow", "expressive"))
    }

    @Test
    fun `missing and unknown values are follow, never a glyph nobody chose`() {
        listOf(null, "", "follow", "neon", "A", "f").forEach { value ->
            TransportMark.entries.forEach { mark ->
                assertEquals(
                        "$value on $mark",
                        TransportGlyphs.designed(mark, "expressive"),
                        TransportGlyphs.resolve(mark, value, "expressive"))
            }
        }
    }

    @Test
    fun `each style has its own drawing for each mark`() {
        TransportMark.entries.forEach { mark ->
            val ids = TransportGlyph.entries.map { TransportGlyphs.drawable(mark, it) }
            assertEquals("two styles share a drawable for $mark", ids.size, ids.toSet().size)
        }
        assertNotEquals(
                TransportGlyphs.drawable(TransportMark.PLAY, TransportGlyph.A),
                TransportGlyphs.drawable(TransportMark.PAUSE, TransportGlyph.A))
    }

    @Test
    fun `the faces that draw a transport mark are registered faces`() {
        assertTrue(ThemeAppearance.ALLOWED_BASE_FACES.containsAll(TransportGlyphs.FACES))
        // Classic's centre control is the action's own icon, not a mark that follows the player.
        assertTrue("classic" !in TransportGlyphs.FACES)
        assertTrue("matejdro" !in TransportGlyphs.FACES)
    }
}
