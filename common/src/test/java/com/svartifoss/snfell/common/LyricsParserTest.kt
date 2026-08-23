package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the LRC decisions that a malformed file gets wrong silently - a lyric that drifts or shows
 * the artist's name as its first verse never reports an error, it just looks broken on the wrist.
 */
class LyricsParserTest {

    @Test
    fun `parses minutes seconds and hundredths`() {
        val lines = LyricsParser.parseSynced("[01:02.50]hello")
        assertEquals(1, lines.size)
        assertEquals(62_500L, lines[0].timeMs)
        assertEquals("hello", lines[0].text)
    }

    @Test
    fun `scales the fraction by its digit count, not by assuming hundredths`() {
        // The same nominal "5" means a different offset at each width. Assuming hundredths on the
        // 3-digit form divides every offset by ten and the whole lyric runs early.
        assertEquals(500L, LyricsParser.parseSynced("[00:00.5]a")[0].timeMs)
        assertEquals(50L, LyricsParser.parseSynced("[00:00.05]a")[0].timeMs)
        assertEquals(5L, LyricsParser.parseSynced("[00:00.005]a")[0].timeMs)
    }

    @Test
    fun `accepts a colon as the fraction separator`() {
        assertEquals(1_250L, LyricsParser.parseSynced("[00:01:25]a")[0].timeMs)
    }

    @Test
    fun `a line with several timestamps becomes one entry per timestamp`() {
        // How LRC files avoid repeating a chorus.
        val lines = LyricsParser.parseSynced("[00:12.00][01:30.00]chorus")
        assertEquals(2, lines.size)
        assertEquals(listOf(12_000L, 90_000L), lines.map { it.timeMs })
        assertTrue(lines.all { it.text == "chorus" })
    }

    @Test
    fun `metadata tags do not become lyric lines`() {
        val lines = LyricsParser.parseSynced(
                """
                [ar:Some Artist]
                [ti:Some Title]
                [length:03:21]
                [00:10.00]first real line
                """.trimIndent())
        assertEquals(1, lines.size)
        assertEquals("first real line", lines[0].text)
    }

    @Test
    fun `an empty line is kept as an instrumental gap`() {
        // Dropping it makes the previous line appear to hold until the next verse.
        val lines = LyricsParser.parseSynced("[00:01.00]sing\n[00:05.00]\n[00:09.00]again")
        assertEquals(3, lines.size)
        assertEquals("", lines[1].text)
    }

    @Test
    fun `a timestamp inside the lyric is text, not timing`() {
        val lines = LyricsParser.parseSynced("[00:03.00]we met at [12:30] sharp")
        assertEquals(1, lines.size)
        assertEquals(3_000L, lines[0].timeMs)
        assertEquals("we met at [12:30] sharp", lines[0].text)
    }

    @Test
    fun `lines come back sorted even when the file is not`() {
        val lines = LyricsParser.parseSynced("[00:30.00]late\n[00:10.00]early")
        assertEquals(listOf("early", "late"), lines.map { it.text })
    }

    @Test
    fun `plain text yields no lines`() {
        assertTrue(LyricsParser.parseSynced("just some words\nand more").isEmpty())
    }

    @Test
    fun `a positive offset tag shifts the lyric earlier`() {
        // The spec's "shifts time up": the words are due sooner than the timestamps say.
        val lines = LyricsParser.parseSynced("[offset:+500]\n[00:10.00]a")
        assertEquals(9_500L, lines[0].timeMs)
    }

    @Test
    fun `a negative offset tag shifts the lyric later`() {
        val lines = LyricsParser.parseSynced("[offset:-1500]\n[00:10.00]a")
        assertEquals(11_500L, lines[0].timeMs)
    }

    @Test
    fun `an offset with no sign is treated as positive`() {
        val lines = LyricsParser.parseSynced("[offset:250]\n[00:10.00]a")
        assertEquals(9_750L, lines[0].timeMs)
    }

    @Test
    fun `an offset never drives a timestamp below zero`() {
        val lines = LyricsParser.parseSynced("[offset:+9000]\n[00:01.00]a\n[00:20.00]b")
        assertEquals(0L, lines[0].timeMs)
        assertEquals(11_000L, lines[1].timeMs)
    }

    @Test
    fun `the offset tag itself is not a lyric line`() {
        val lines = LyricsParser.parseSynced("[offset:+500]\n[00:10.00]only line")
        assertEquals(1, lines.size)
        assertEquals("only line", lines[0].text)
    }

    @Test
    fun `a file with no offset tag is unaffected`() {
        assertEquals(10_000L, LyricsParser.parseSynced("[00:10.00]a")[0].timeMs)
    }

    @Test
    fun `a malformed offset tag is ignored rather than shifting anything`() {
        assertEquals(10_000L, LyricsParser.parseSynced("[offset:abc]\n[00:10.00]a")[0].timeMs)
    }

    @Test
    fun `enhanced LRC word timings are stripped from the text`() {
        // Rendered literally these turn a verse into visible markup.
        val lines = LyricsParser.parseSynced("[00:12.00]<00:12.00>Hello <00:12.40>world")
        assertEquals(1, lines.size)
        assertEquals(12_000L, lines[0].timeMs)
        assertEquals("Hello world", lines[0].text)
    }

    @Test
    fun `stripping word timings does not leave doubled spaces`() {
        val lines = LyricsParser.parseSynced("[00:01.00]<00:01.00>a <00:01.50>b <00:02.00>c")
        assertEquals("a b c", lines[0].text)
    }

    @Test
    fun `a line that is only word timings becomes an empty gap, not markup`() {
        val lines = LyricsParser.parseSynced("[00:05.00]<00:05.00>")
        assertEquals("", lines[0].text)
    }

    @Test
    fun `ordinary angle brackets in a lyric survive`() {
        val lines = LyricsParser.parseSynced("[00:03.00]a <3 you")
        assertEquals("a <3 you", lines[0].text)
    }

    @Test
    fun `indexAt reports -1 before the first line`() {
        // A real state, not an error: LRC files routinely open with a several-second intro.
        val lines = LyricsParser.parseSynced("[00:10.00]a\n[00:20.00]b")
        assertEquals(-1, LyricsParser.indexAt(lines, 0L))
        assertEquals(-1, LyricsParser.indexAt(lines, 9_999L))
    }

    @Test
    fun `indexAt holds a line until the next one starts`() {
        val lines = LyricsParser.parseSynced("[00:10.00]a\n[00:20.00]b\n[00:30.00]c")
        assertEquals(0, LyricsParser.indexAt(lines, 10_000L))
        assertEquals(0, LyricsParser.indexAt(lines, 19_999L))
        assertEquals(1, LyricsParser.indexAt(lines, 20_000L))
        assertEquals(2, LyricsParser.indexAt(lines, 999_999L))
    }

    // ---- lineProgress ----

    private val threeLines = LyricsParser.parseSynced("[00:10.00]a\n[00:20.00]b\n[00:30.00]c")

    @Test
    fun `a line's progress is measured against the gap to the next line`() {
        assertEquals(0f, LyricsParser.lineProgress(threeLines, 0, 10_000L, 200_000L), 0.001f)
        assertEquals(0.5f, LyricsParser.lineProgress(threeLines, 0, 15_000L, 200_000L), 0.001f)
        assertEquals(1f, LyricsParser.lineProgress(threeLines, 0, 20_000L, 200_000L), 0.001f)
    }

    @Test
    fun `the last line runs to the end of the track`() {
        assertEquals(0.5f, LyricsParser.lineProgress(threeLines, 2, 35_000L, 40_000L), 0.001f)
    }

    @Test
    fun `the last line reports zero when the track length is unknown`() {
        // No next line and no duration means no span to be part-way through.
        assertEquals(0f, LyricsParser.lineProgress(threeLines, 2, 35_000L, 0L), 0.001f)
    }

    @Test
    fun `a duration shorter than the line's own start is not a span`() {
        assertEquals(0f, LyricsParser.lineProgress(threeLines, 2, 35_000L, 5_000L), 0.001f)
    }

    @Test
    fun `two lines on the same timestamp do not divide by zero`() {
        val degenerate = LyricsParser.parseSynced("[00:10.00]a\n[00:10.00]b")
        assertEquals(0f, LyricsParser.lineProgress(degenerate, 0, 10_000L, 200_000L), 0.001f)
    }

    @Test
    fun `an index outside the list reports zero`() {
        // Includes the -1 indexAt reports during an intro: nothing is being sung.
        assertEquals(0f, LyricsParser.lineProgress(threeLines, -1, 5_000L, 200_000L), 0.001f)
        assertEquals(0f, LyricsParser.lineProgress(threeLines, 9, 5_000L, 200_000L), 0.001f)
        assertEquals(0f, LyricsParser.lineProgress(emptyList(), 0, 5_000L, 200_000L), 0.001f)
    }

    @Test
    fun `progress is clamped rather than running past its line`() {
        assertEquals(1f, LyricsParser.lineProgress(threeLines, 0, 999_000L, 200_000L), 0.001f)
        assertEquals(0f, LyricsParser.lineProgress(threeLines, 1, 1_000L, 200_000L), 0.001f)
    }

    @Test
    fun `indexAt on an empty list is -1 rather than a crash`() {
        assertEquals(-1, LyricsParser.indexAt(emptyList(), 1_000L))
    }
}
