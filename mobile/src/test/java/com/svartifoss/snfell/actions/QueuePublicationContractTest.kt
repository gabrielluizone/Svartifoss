package com.svartifoss.snfell.actions

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How [OpenPlaylistAction] publishes the queue - the shape that keeps it cheap and current.
 *
 * It used to publish a prefix of the queue stretched to the playing track (up to two hundred rows,
 * mostly already played), resolve every row's cover concurrently while holding all of the decoded
 * bitmaps, then shrink and encode them all on the main thread - on every track change. And two
 * publications in flight at once could finish out of order, putting an older queue back on the
 * watch. The pure parts are pinned elsewhere (`QueuePaging.window`, `QueueThumbnailCache`); this
 * pins how the handler is put together around them.
 *
 * A source sweep: none of it changes what a single publication contains, only what it costs and
 * which one wins, and neither is visible to a test of its output.
 */
class QueuePublicationContractTest {

    private companion object {
        const val ACTION_PATH = "src/main/java/com/svartifoss/snfell/actions/OpenPlaylistAction.kt"
    }

    @Test
    fun theQueueIsPublishedAsAWindowAroundThePlayingTrack() {
        assertTrue(
                "The handler must publish QueuePaging.window around the playing entry, not a prefix.",
                handleAction().contains("QueuePaging.window("))
    }

    @Test
    fun rowsAreResolvedAndEncodedOffTheMainThread() {
        val body = handleAction()
        assertTrue(
                "Each row must be resolved on a background dispatcher (async(Dispatchers.Default)).",
                body.contains("async(Dispatchers.Default)"))
        listOf("shrinkPreservingRatio(", "encodeThumbnail(", "QueueArtworkResolver.resolve(").forEach {
            assertFalse(
                    "handleAction runs on the main thread; '$it' belongs in the per-row work it " +
                            "hands to a background dispatcher.",
                    body.contains(it))
        }
    }

    @Test
    fun onlyTheLatestPublicationIsSent() {
        val body = handleAction()
        val begin = body.indexOf("beginQueuePublication()")
        val firstRead = body.indexOf("resolvePlaybackQueue()")
        val check = body.indexOf("isLatestQueuePublication(")
        val put = body.indexOf("putDataItem(")

        assertTrue("The publication must be numbered before the queue is read.",
                begin in 0 until firstRead)
        assertTrue(
                "An overtaken publication must be dropped before it is sent, or an older queue can " +
                        "replace a newer one on the watch.",
                check in 0 until put)
    }

    private fun handleAction(): String {
        val file = listOf(File("."), File("mobile"))
                .map { File(it, ACTION_PATH) }
                .firstOrNull { it.isFile }
                ?: throw AssertionError(
                        "Could not locate OpenPlaylistAction from ${File(".").absolutePath}")
        val source = withoutComments(file.readText())
        val start = source.indexOf("override suspend fun handleAction(")
        assertTrue("OpenPlaylistAction no longer declares handleAction", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(open, index + 1)
            }
        }
        throw AssertionError("Unbalanced braces after handleAction in OpenPlaylistAction")
    }

    /** Comments removed, so an explanation that names an old call is not read as code. */
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
