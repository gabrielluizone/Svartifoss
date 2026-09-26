package com.svartifoss.snfell.view.mainactivity

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone's mini player does its twice-a-second work only while the screen is visible, and that
 * work is cheap.
 *
 * Its progress tick used to be stopped only in onDestroy, so with the app merely in the background
 * it ran for as long as music played - and every tick asked the session for its metadata, which
 * hands the cover back as a new Bitmap each time. The accent was matched to the cover by
 * reference, so that never matched: every tick ran a Palette extraction and re-tinted every view
 * in the activity, and with the queue sheet open re-read the whole queue as well.
 *
 * A source sweep, because none of it is visible to a test of any one function: the tick still
 * draws the same bar, it just used to cost a great deal to do it.
 */
class MiniPlayerTickContractTest {

    private companion object {
        const val ACTIVITY_PATH =
                "src/main/java/com/svartifoss/snfell/view/mainactivity/MainActivity.kt"
    }

    @Test
    fun theProgressTickMakesNoBinderCallsAndExtractsNoColour() {
        val tick = bodyOf(activitySource(), "private fun updateMiniPlayerProgress()")
        listOf(".metadata", ".playbackState", ".queue", "Palette", "updateDynamicAccentFromArt",
                "applyAccentColor").forEach {
            assertFalse(
                    "updateMiniPlayerProgress runs twice a second while music plays and must work " +
                            "from the cached playback state and duration. Found '$it' in it.",
                    tick.contains(it))
        }
    }

    @Test
    fun theProgressTickStopsWithTheScreen() {
        val source = activitySource()
        val onStop = bodyOf(source, "override fun onStop()")
        assertTrue("onStop must stop the progress ticker.", onStop.contains("stopProgressUpdates()"))
        assertTrue(
                "onStop must unregister the controller callback, or its next playback-state change " +
                        "restarts the ticker for a screen nobody can see.",
                onStop.contains("unregisterCallback(miniPlayerCallback)"))
        assertTrue(
                "startProgressUpdates must refuse to start while the screen is stopped.",
                bodyOf(source, "private fun startProgressUpdates()").contains("miniPlayerStarted"))
        assertTrue(
                "The tick must only reschedule itself while the screen is started.",
                bodyOf(source, "private fun updateMiniPlayerProgress()").contains("miniPlayerStarted"))
    }

    @Test
    fun theCoverIsComparedByContentBeforeTheAccentIsExtractedAgain() {
        val accent = bodyOf(activitySource(), "private fun updateDynamicAccentFromArt(")
        assertTrue(
                "Every metadata read unparcels the cover into a new Bitmap, so the check that skips " +
                        "an unchanged cover has to compare content (isSameArtwork), not references.",
                accent.contains("isSameArtwork(art, lastPaletteArt)"))
    }

    private fun activitySource(): String {
        val file = listOf(File("."), File("mobile"))
                .map { File(it, ACTIVITY_PATH) }
                .firstOrNull { it.isFile }
                ?: throw AssertionError(
                        "Could not locate MainActivity from ${File(".").absolutePath}")
        return withoutComments(file.readText())
    }

    /** The body of the function declared by [signature], found by brace counting. */
    private fun bodyOf(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("MainActivity no longer declares '$signature'", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(open, index + 1)
            }
        }
        throw AssertionError("Unbalanced braces after '$signature' in MainActivity")
    }

    /**
     * Comments removed, so an explanation that names the old calls is not read as code. Block
     * comments go first; a `//` starts a comment only outside a string literal.
     */
    private fun withoutComments(source: String): String =
            source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
                    .lineSequence()
                    .joinToString("\n") { line ->
                        var quotes = 0
                        line.forEachIndexed { index, ch ->
                            if (ch == '"' && (index == 0 || line[index - 1] != '\\')) quotes++
                            if (ch == '/' && index > 0 && line[index - 1] == '/' &&
                                    quotes % 2 == 0) {
                                return@joinToString line.substring(0, index - 1)
                            }
                        }
                        line
                    }
}
