package com.svartifoss.snfell.common

/** One timed line of a synced lyric. [timeMs] is the offset from the start of the track. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Turns the LRC text the phone fetched into timed lines the watch screen can follow.
 *
 * Every decision here is one a malformed file can get wrong, and the screen has no way to report
 * that it guessed - a lyric that drifts just looks broken. So this is a pure function with a JVM
 * test rather than logic buried in a Composable, the same shape as [QueuePaging] and friends:
 *
 *  - a line may carry **several** timestamps (`[00:12.00][01:30.00] same words`), which is how LRC
 *    files avoid repeating a chorus; each one becomes its own entry.
 *  - the fraction is hundredths in the common case but thousandths appear in the wild, so it is
 *    scaled by its own digit count instead of assumed - guessing hundredths on a 3-digit file
 *    divides every offset by ten and the whole lyric runs early.
 *  - metadata lines (`[ar:...]`, `[ti:...]`) look exactly like timestamps to a naive regex and
 *    must not become lyric lines at 0 ms, which would show the artist's name as the opening verse.
 *  - an empty text after a timestamp is **kept**: it is how an LRC marks an instrumental gap, and
 *    dropping it makes the previous line appear to hold all the way to the next verse.
 *
 * Returns lines sorted by time. A text with no usable timestamp yields an empty list, which the
 * caller should treat as "unsynced" and show as a plain block.
 */
object LyricsParser {

    /** `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss:xx]`. Minutes run to three digits for very long tracks. */
    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /**
     * `[offset:+/-milliseconds]`, the format's own global timing correction.
     *
     * Part of the LRC spec since Winamp's original implementation and carried by a real share of
     * the files in community databases, usually because whoever timed them was working from a
     * different master or a release with a different amount of leading silence. Ignoring it is
     * indistinguishable, from the outside, from the whole app being late: the lyric is simply
     * wrong by a fixed amount for that one track, and every other track is fine.
     *
     * A **positive** offset means the words come *earlier*, so it is subtracted from each
     * timestamp - the direction the spec words as "shifts time up" and the one every player that
     * implements it uses.
     */
    private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d{1,7})\s*]""", RegexOption.IGNORE_CASE)

    /**
     * Per-word timing from "enhanced" LRC: `[00:12.00]<00:12.00>Hello <00:12.40>world`.
     *
     * Angle brackets, not square ones, so the line-level parser above does not see them - they sit
     * inside what it takes to be the lyric text and would be rendered literally, turning a verse
     * into a wall of visible markup. Stripped rather than used: this screen highlights a line at a
     * time, so word timings have nothing to drive yet, and showing them raw is the one outcome
     * that is definitely wrong.
     */
    private val WORD_TIMESTAMP = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun parseSynced(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val offsetMs = OFFSET_TAG.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        for (raw in lrc.lineSequence()) {
            val stamps = TIMESTAMP.findAll(raw).toList()
            if (stamps.isEmpty()) continue

            // Only the timestamps forming an unbroken run from the start of the line are prefixes.
            // Anything later sits inside the lyric itself and is text, not timing - a line like
            // "we met at [12:30] sharp" must keep those words rather than be re-timed by them.
            var cursor = 0
            val prefixes = stamps.takeWhile { match ->
                (match.range.first == cursor).also { if (it) cursor = match.range.last + 1 }
            }
            if (prefixes.isEmpty()) continue

            // Collapse the whitespace the stripped word tags leave behind, so a karaoke-timed
            // line does not come out double-spaced between every word.
            val text = WORD_TIMESTAMP.replace(raw.substring(cursor), "")
                    .replace(WHITESPACE_RUN, " ")
                    .trim()
            for (stamp in prefixes) {
                // Clamped at zero: a large positive offset on a track whose first line is already
                // near the start would otherwise produce negative times, which indexAt reads as
                // "before the first line" and would leave the opening verse unreachable.
                val timeMs = (toMillis(stamp) - offsetMs).coerceAtLeast(0L)
                lines += LyricLine(timeMs = timeMs, text = text)
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    private val WHITESPACE_RUN = Regex("""\s{2,}""")

    private fun toMillis(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
        // "5" is 500ms, "05" is 50ms, "005" is 5ms - scale by how many digits were actually
        // written rather than assuming hundredths.
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return minutes * 60_000 + seconds * 1_000 + fractionMs
    }

    /**
     * How far through the line at [index] playback has reached, 0f..1f.
     *
     * A *line's* progress, not the track's, which is the only figure that answers "how long has
     * this line got left" - and on a screen where a line can hold for eight seconds, that is the
     * difference between a still image and something visibly alive.
     *
     * How long a line lasts is not stored anywhere: it is the gap to the *next* line's timestamp.
     * The last line has no next, so it runs to the end of the track - and when the track length is
     * unknown, or the file gives two lines the same timestamp (which malformed ones do), there is
     * no span to be part-way through and this reports 0 rather than dividing by zero.
     *
     * Returns 0 for an [index] outside the list, including the -1 [indexAt] reports during an
     * intro: nothing is being sung, so nothing is part-way done.
     */
    fun lineProgress(
            lines: List<LyricLine>,
            index: Int,
            positionMs: Long,
            durationMs: Long,
    ): Float {
        val line = lines.getOrNull(index) ?: return 0f
        val end = lines.getOrNull(index + 1)?.timeMs
                ?: durationMs.takeIf { it > line.timeMs }
                ?: return 0f
        val span = end - line.timeMs
        if (span <= 0L) return 0f
        return ((positionMs - line.timeMs).toFloat() / span).coerceIn(0f, 1f)
    }

    /**
     * Index of the line playing at [positionMs], or -1 before the first one.
     *
     * -1 is a real state, not an error: LRC files routinely open with a several-second intro, and
     * the screen shows that as "nothing highlighted yet" instead of pinning the first verse.
     *
     * Binary search rather than a scan - this runs off a ticker while the screen is visible and a
     * lyric can run to hundreds of lines.
     */
    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < lines[0].timeMs) return -1
        var low = 0
        var high = lines.size - 1
        var answer = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }
}
