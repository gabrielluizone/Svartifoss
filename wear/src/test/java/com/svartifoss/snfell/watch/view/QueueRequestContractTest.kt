package com.svartifoss.snfell.watch.view

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player only asks the phone for the queue on its own when it has a reason the phone does not.
 *
 * A queue publication is the most expensive message in the app: the phone resolves and encodes a
 * cover for every row, the Data Layer carries them across Bluetooth, and the watch decodes them.
 * The phone already republishes the queue on every track change, which is the only thing that moves
 * Up Next on - yet the player also asked for it on every track change, on every entry into the
 * always-on display and, on the faces that draw the queue, on every wake. That was two to four full
 * publications per track, most of them while nobody was looking.
 *
 * What is left is two conditional requests: when no queue has arrived at all, and when a face that
 * draws the covers is chosen (it needs larger thumbnails than a list). Requests a person makes -
 * opening the queue or the quick panel - are not automatic and are not in scope here.
 *
 * A source sweep, like the other contracts in this package: what goes wrong is an unconditional
 * call reappearing, which no test of the drawing can see.
 */
class QueueRequestContractTest {

    private companion object {
        const val ACTIVITY_PATH = "src/main/java/com/svartifoss/snfell/watch/view/MainActivity.kt"
        const val REQUEST = "refreshPlaybackQueueSilently()"

        /** Conditions that justify an automatic request - see the class doc. */
        val GUARDS = listOf("hasQueueSnapshot", "queueRequestedForFace")

        /** How many code lines above a request its guard may sit. */
        const val GUARD_LOOKBACK = 3
    }

    @Test
    fun everyAutomaticQueueRequestIsConditional() {
        val lines = activityCodeLines()
        val calls = lines.indices.filter { lines[it].contains(REQUEST) }
        assertTrue("Found no automatic queue requests - has the sweep gone stale?", calls.isNotEmpty())

        val unguarded = calls.filterNot { index ->
            (maxOf(0, index - GUARD_LOOKBACK)..index).any { line ->
                GUARDS.any { lines[line].contains(it) }
            }
        }.map { lines[it].trim() }

        assertTrue(
                "The phone republishes the queue on every track change, so the player may only ask " +
                        "for it when that cannot have happened yet ($GUARDS). Unconditional: " +
                        "$unguarded",
                unguarded.isEmpty())
    }

    /** Comment-free, blank-free code lines, so a guard is counted only when it is code. */
    private fun activityCodeLines(): List<String> {
        val file = listOf(File("."), File("wear"))
                .map { File(it, ACTIVITY_PATH) }
                .firstOrNull { it.isFile }
                ?: throw AssertionError("Could not locate MainActivity from ${File(".").absolutePath}")
        return file.readText()
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
                .lines()
                .map { line ->
                    var quotes = 0
                    line.forEachIndexed { index, ch ->
                        if (ch == '"' && (index == 0 || line[index - 1] != '\\')) quotes++
                        if (ch == '/' && index > 0 && line[index - 1] == '/' && quotes % 2 == 0) {
                            return@map line.substring(0, index - 1)
                        }
                    }
                    line
                }
                .filter { it.isNotBlank() }
    }
}
