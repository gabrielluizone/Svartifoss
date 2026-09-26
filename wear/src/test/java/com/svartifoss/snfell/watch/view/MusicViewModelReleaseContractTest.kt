package com.svartifoss.snfell.watch.view

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MusicViewModel] lets go of everything it attached to the process-lifetime `PhoneConnection` when
 * it is cleared.
 *
 * The connection opens and closes on whether anything observes its LiveData, and a feed is exactly
 * such an observer: it registers with `observeForever`, which nothing but an explicit release ever
 * removes. The lyrics feed was the one left out - the metadata feed beside it was released - so
 * closing the app on the Verse face kept the connection open for the rest of the process. The phone
 * then never heard that the watch had closed, and its foreground service went on doing per-track
 * work, lyric lookups included, for a screen that was gone.
 *
 * Runnables posted on the ViewModel's handler are the same shape of leak on a shorter fuse: the
 * close timeout can be minutes long, and until it fires it keeps the cleared ViewModel reachable.
 *
 * A source sweep, like [com.svartifoss.snfell.watch.communication.WatchServiceNotificationContractTest]:
 * the ViewModel cannot be built on the JVM, and what goes wrong is a line that is missing, not a
 * behaviour a unit test could observe.
 */
class MusicViewModelReleaseContractTest {

    private companion object {
        const val VIEW_MODEL_PATH =
                "src/main/java/com/svartifoss/snfell/watch/view/MusicViewModel.kt"
    }

    @Test
    fun everyFeedIsReleasedWhenTheViewModelIsCleared() {
        val source = viewModelSource()
        val feeds = Regex("""\bval\s+(\w+)\s*=\s*\w+Feed\(""")
                .findAll(source)
                .map { it.groupValues[1] }
                .toList()
        assertTrue("Found no feeds in MusicViewModel - has the sweep's pattern gone stale?",
                feeds.isNotEmpty())

        val cleared = onClearedBody(source)
        val unreleased = feeds.filterNot { Regex("""\b$it\.release\(\)""").containsMatchIn(cleared) }

        assertTrue(
                "MusicViewModel.onCleared must release every feed it owns. A feed observes " +
                        "PhoneConnection's LiveData with observeForever, so one left attached keeps " +
                        "the watch's phone connection open after the app closes - and the phone's " +
                        "service with it. Not released: $unreleased",
                unreleased.isEmpty())
    }

    @Test
    fun everyPostedRunnableIsRemovedWhenTheViewModelIsCleared() {
        val source = viewModelSource()
        val posted = Regex("""\bhandler\.postDelayed\(\s*(\w+)\s*,""")
                .findAll(source)
                .map { it.groupValues[1] }
                .toSet()
        assertTrue("Found no postDelayed calls in MusicViewModel - has the sweep's pattern gone " +
                "stale?", posted.isNotEmpty())

        val cleared = onClearedBody(source)
        val left = posted.filterNot {
            Regex("""\bhandler\.removeCallbacks\(\s*$it\s*\)""").containsMatchIn(cleared)
        }

        assertTrue(
                "MusicViewModel.onCleared must remove every Runnable it posts on its handler, or " +
                        "the cleared ViewModel stays reachable until the delay runs out. Not " +
                        "removed: $left",
                left.isEmpty())
    }

    private fun viewModelSource(): String {
        val file = listOf(File("."), File("wear"))
                .map { File(it, VIEW_MODEL_PATH) }
                .firstOrNull { it.isFile }
                ?: throw AssertionError(
                        "Could not locate MusicViewModel from ${File(".").absolutePath}")
        return withoutComments(file.readText())
    }

    /** The body of `onCleared`, found by brace counting from its declaration. */
    private fun onClearedBody(source: String): String {
        val start = source.indexOf("override fun onCleared()")
        assertTrue("MusicViewModel no longer overrides onCleared", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(open, index + 1)
            }
        }
        throw AssertionError("Unbalanced braces after onCleared in MusicViewModel")
    }

    /**
     * Comments removed, so the explanation beside a release is not mistaken for the release
     * itself. Block comments go first; a `//` starts a comment only outside a string literal.
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
