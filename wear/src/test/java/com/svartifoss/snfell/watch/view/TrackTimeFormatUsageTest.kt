package com.svartifoss.snfell.watch.view

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nothing on the watch writes a position or a length by hand.
 *
 * Track time was formatted with a `"%d:%02d"` of its own in eight places across the two apps, none
 * of which knew an hour existed, so a four-hour podcast read `240:00`. They all go through
 * `TrackTimeFormat` now, and this is the check that keeps a ninth copy from growing back: a call site
 * that formats minutes and seconds itself never fails, it just shows `63:10` for a long enough track.
 *
 * A source sweep rather than a rendering assertion because what went wrong was never the drawing,
 * it was each surface deciding for itself what a time looks like.
 */
class TrackTimeFormatUsageTest {

    @Test
    fun `no watch source formats minutes and seconds itself`() {
        val root = listOf(File("src/main/java"), File("wear/src/main/java"))
                .firstOrNull { it.isDirectory }
                ?: error("Could not locate the watch sources from ${File(".").absolutePath}")

        val offenders = root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file -> file.readLines().any { HAND_ROLLED.containsMatchIn(it) } }
                .map { it.relativeTo(root).path }
                .toList()

        assertTrue(
                "These files format track time themselves instead of calling " +
                        "TrackTimeFormat.format, so they will show 63:10 where the player says 1:03:10: " +
                        offenders.joinToString(),
                offenders.isEmpty())
    }

    private companion object {
        /** The format string every copy shared. */
        val HAND_ROLLED = Regex("""%d:%02d""")
    }
}
