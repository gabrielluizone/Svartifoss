package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tick and dot scales of the progress ring are laid out in exactly one place, `ProgressScale`.
 *
 * The watch's ring and the phone's miniature each carried a copy of that arithmetic, and the copies
 * disagreed on the open layouts: the watch spread a ring's number of marks across the arc, which
 * left "Open at top" starting on a long mark and ending on a short one, and the miniature placed its
 * marks by one fraction and lit them by another, so the Watch tab showed a scale the wrist did not
 * draw. Both now ask `ProgressScale`. This fails on a renderer that goes back to doing its own sums,
 * which compiles, draws, and is wrong only on the layouts nobody happened to be looking at.
 */
class ProgressScaleUsageTest {

    private class Renderer(val name: String, val path: String, val scaleFunctions: List<String>)

    private val renderers = listOf(
            Renderer(
                    "the watch's ring",
                    "wear/src/main/java/com/svartifoss/snfell/watch/view/CircularProgressSeekBar.kt",
                    listOf("forEachScaleMark", "drawWatchDots", "drawWatchTicks")),
            Renderer(
                    "the phone's miniature",
                    "mobile/src/main/java/com/svartifoss/snfell/view/watchface/WatchPreviewView.kt",
                    listOf("drawPreviewWatchDots", "drawPreviewWatchTicks")))

    @Test
    fun `both renderers lay their scales out through ProgressScale`() {
        for (renderer in renderers) {
            val source = repoFile(renderer.path).readText()
            for (call in listOf("divisions", "markCount", "fraction", "isMajor", "isPlayed")) {
                assertTrue(
                        "${renderer.name} never calls ProgressScale.$call",
                        source.contains("ProgressScale.$call("))
            }
        }
    }

    @Test
    fun `neither renderer keeps its own copy of the arithmetic`() {
        // Scoped to the scale functions: other layouts in the same files divide by a count minus one
        // for their own reasons.
        val copies = listOf(
                Regex("""\(count - 1\)\.coerceAtLeast"""),
                Regex("""index\.toFloat\(\)\s*/\s*count"""),
                Regex("""index \* sweepDegrees"""))
        for (renderer in renderers) {
            val source = repoFile(renderer.path).readText()
            for (function in renderer.scaleFunctions) {
                val body = functionBody(source, function)
                for (copy in copies) {
                    assertFalse(
                            "${renderer.name}: $function lays marks out by hand again " +
                                    "(${copy.pattern}); use ProgressScale",
                            copy.containsMatchIn(body))
                }
            }
        }
    }

    /** Gradle can execute JVM tests from the module or repository directory. */
    private fun repoFile(relative: String): File =
            listOf(File("../$relative"), File(relative))
                    .firstOrNull { it.exists() }
                    ?: throw AssertionError(
                            "Could not locate $relative from ${File(".").absolutePath}")

    private fun functionBody(source: String, function: String): String {
        val signature = Regex("""fun\s+$function\s*\(""").find(source)
                ?: throw AssertionError("$function not found")
        val open = source.indexOf('{', signature.range.last)
        if (open < 0) throw AssertionError("$function has no block body")
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, index + 1)
                }
            }
        }
        throw AssertionError("Unbalanced braces from offset $open")
    }
}
