package com.svartifoss.snfell.view

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nothing on the phone writes a position or a length by hand.
 *
 * The player dialog and the Watch tab's miniature each carried a `"%d:%02d"` of their own, as the
 * watch's faces did, and none of them knew an hour existed: a four-hour podcast read `240:00` on the
 * wrist and in its own preview. They all go through `TrackTimeFormat` now, and this keeps a new copy
 * from growing back - a call site that formats minutes and seconds itself never fails, it just shows
 * `63:10` for a long enough track. The watch's half of the sweep is `TrackTimeFormatUsageTest` in
 * `wear`.
 */
class TrackTimeFormatUsageTest {

    @Test
    fun `no phone source formats minutes and seconds itself`() {
        val root = listOf(File("src/main/java"), File("mobile/src/main/java"))
                .firstOrNull { it.isDirectory }
                ?: error("Could not locate the phone sources from ${File(".").absolutePath}")

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
