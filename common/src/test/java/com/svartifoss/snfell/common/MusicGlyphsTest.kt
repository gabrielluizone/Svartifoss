package com.svartifoss.snfell.common

import com.svartifoss.snfell.common.MusicGlyphs.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicGlyphsTest {

    private fun str(codePoint: Int) = String(Character.toChars(codePoint))

    @Test
    fun `every listed note is recognised`() {
        MusicGlyphs.CODE_POINTS.forEach { cp ->
            assertTrue("U+%04X should be a note".format(cp), MusicGlyphs.isNote(cp))
            assertTrue(MusicGlyphs.containsNote(str(cp)))
        }
    }

    /**
     * Accidentals are notation *about* a note, and they appear inside real song titles ("C♯
     * minor"). Substituting one would corrupt the text rather than decorate it.
     */
    @Test
    fun `accidentals are left alone`() {
        listOf(0x266D, 0x266E, 0x266F).forEach { cp ->
            assertFalse("U+%04X must not be substituted".format(cp), MusicGlyphs.isNote(cp))
        }
        assertFalse(MusicGlyphs.containsNote("C♯ minor"))
    }

    @Test
    fun `ordinary text is one run and never scanned twice`() {
        assertEquals(
                listOf(Segment.Text("And I thought that I could save you")),
                MusicGlyphs.segments("And I thought that I could save you"))
    }

    @Test
    fun `empty text produces nothing`() {
        assertEquals(emptyList<Segment>(), MusicGlyphs.segments(""))
    }

    @Test
    fun `a note between words splits into three runs`() {
        assertEquals(
                listOf(
                        Segment.Text("la "),
                        Segment.Note("♪"),
                        Segment.Text(" la")),
                MusicGlyphs.segments("la ♪ la"))
    }

    @Test
    fun `a lone marker is a single note run`() {
        assertEquals(
                listOf(Segment.Note("♪")),
                MusicGlyphs.segments(MusicGlyphs.INSTRUMENTAL_MARKER))
    }

    /**
     * The reason [MusicGlyphs.segments] walks code points rather than chars: four of the seven are
     * surrogate pairs, and a per-char walk would cut them in half and emit a lone surrogate - a
     * tofu box sitting next to the mark that replaced its other half.
     */
    @Test
    fun `an astral note is consumed whole`() {
        val note = str(MusicGlyphs.MUSICAL_NOTE)
        assertEquals(2, note.length)
        assertEquals(
                listOf(Segment.Text("hey "), Segment.Note(note)),
                MusicGlyphs.segments("hey $note"))
    }

    @Test
    fun `astral text around an astral note survives intact`() {
        val score = str(MusicGlyphs.MUSICAL_SCORE)
        val unrelatedEmoji = String(Character.toChars(0x1F525))
        assertEquals(
                listOf(
                        Segment.Text(unrelatedEmoji),
                        Segment.Note(score),
                        Segment.Text(unrelatedEmoji)),
                MusicGlyphs.segments("$unrelatedEmoji$score$unrelatedEmoji"))
    }

    /** A file writing `♪♪♪` means three marks, so they stay three segments. */
    @Test
    fun `adjacent notes stay separate`() {
        assertEquals(
                listOf(Segment.Note("♪"), Segment.Note("♫"), Segment.Note("♩")),
                MusicGlyphs.segments("♪♫♩"))
    }

    @Test
    fun `reassembling the segments reproduces the original`() {
        val samples = listOf(
                "",
                "plain line",
                "♪",
                "♪ instrumental ♪",
                "${str(MusicGlyphs.MUSICAL_NOTE)} ooh ${str(MusicGlyphs.MULTIPLE_MUSICAL_NOTES)}",
                "C♯ minor ♬ end")
        samples.forEach { sample ->
            val rebuilt = MusicGlyphs.segments(sample).joinToString("") { segment ->
                when (segment) {
                    is Segment.Text -> segment.text
                    is Segment.Note -> segment.source
                }
            }
            assertEquals("segments() lost or reordered text", sample, rebuilt)
        }
    }

    @Test
    fun `containsNote agrees with segments`() {
        listOf("plain", "♪", "a♫b", "C♯").forEach { sample ->
            assertEquals(
                    sample,
                    MusicGlyphs.containsNote(sample),
                    MusicGlyphs.segments(sample).any { it is Segment.Note })
        }
    }
}
