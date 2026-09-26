package com.svartifoss.snfell.watch.communication

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Closing a [PhoneConnection] session can never reach into the session that replaces it.
 *
 * `stop()` used to send the "watch closed" message from a coroutine and cancel the session from
 * that coroutine's `finally`, by reading the `scope` field again. When the app reopened while the
 * message was still in flight - `start()` needs nothing more than an observer coming back - the
 * field by then held the new session's scope, and the old close cancelled it halfway through
 * starting: its listeners were never registered and every later send quietly did nothing, until
 * the connection happened to be closed and reopened again - not before the music stopped, while
 * playback kept it open.
 *
 * So `stop()` detaches the field before anything is launched, and nothing it launches reads the
 * field at all. A source sweep, because the failure is an interleaving no unit test can build
 * without a Data Layer.
 */
class PhoneConnectionStopContractTest {

    private companion object {
        const val CONNECTION_PATH =
                "src/main/java/com/svartifoss/snfell/watch/communication/PhoneConnection.kt"

        /**
         * Any coroutine builder called on a scope - `launch` and the app's own `launch*` wrappers,
         * `launchWithErrorHandling` among them, which is what the original stop() used.
         */
        val LAUNCH = Regex("""\.launch\w*""")

        /** A read or write of the session field itself - not of closeMessageScope or a local. */
        val SCOPE_FIELD = Regex("""(?<![\w.])scope\b(?!\s*=\s*CoroutineScope)""")
    }

    @Test
    fun stopDetachesTheSessionScopeBeforeLaunchingAnything() {
        val stop = stopBody()
        val detach = stop.indexOf("scope = null")
        val firstLaunch = LAUNCH.find(stop)?.range?.first ?: stop.length

        assertTrue("stop() must clear the session scope field (scope = null).", detach >= 0)
        assertTrue(
                "stop() must clear the session scope field before it launches anything, so no " +
                        "coroutine it starts can ever find a newer session's scope there.",
                detach < firstLaunch)
    }

    @Test
    fun nothingLaunchedByStopReadsTheSessionScopeField() {
        val stop = stopBody()
        val launched = LAUNCH.findAll(stop).map { blockFrom(stop, it.range.last) }
        val offenders = launched.filter { SCOPE_FIELD.containsMatchIn(it) }.toList()

        assertTrue(
                "A coroutine launched from stop() reads the session scope field after it may have " +
                        "suspended - by then a reopened connection can own that field, and the " +
                        "old close would act on the new session. Offending block(s): $offenders",
                offenders.isEmpty())
    }

    private fun stopBody(): String {
        val file = listOf(File("."), File("wear"))
                .map { File(it, CONNECTION_PATH) }
                .firstOrNull { it.isFile }
                ?: throw AssertionError(
                        "Could not locate PhoneConnection from ${File(".").absolutePath}")
        val source = withoutComments(file.readText())
        val start = source.indexOf("private fun stop()")
        assertTrue("PhoneConnection no longer declares stop()", start >= 0)
        return blockFrom(source, start)
    }

    /** The first brace-delimited block at or after [start]. */
    private fun blockFrom(source: String, start: Int): String {
        val open = source.indexOf('{', start)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(open, index + 1)
            }
        }
        throw AssertionError("Unbalanced braces in PhoneConnection")
    }

    /**
     * Comments removed, so the explanation beside the fix - which names the field - is not read as
     * code. Block comments go first; a `//` starts a comment only outside a string literal.
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
