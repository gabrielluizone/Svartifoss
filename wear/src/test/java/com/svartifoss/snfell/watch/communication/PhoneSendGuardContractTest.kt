package com.svartifoss.snfell.watch.communication

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every send to the phone made from outside [PhoneConnection] is launched with its failure caught.
 *
 * The connection caches the phone's node id, so a send issued in the moment between the phone
 * going out of range and the connection noticing is a message to a node that is no longer there,
 * and the Data Layer fails it with an ApiException. Most callers already dealt with that - through
 * `launchWithErrorHandling`, which shows the failure, or a local try/catch - but the queue screen,
 * the menu and the proxy media session launched their sends bare. In a ViewModel scope that
 * crashed the app; in the media session, whose scope is the service's, it took the whole watch
 * process down, from a button press on a system surface the app was not even showing.
 *
 * A source sweep: the send list is read from [PhoneConnection] itself, so a new public send is
 * covered the moment it exists. A call site counts as guarded when, walking back from it to the
 * `launch` it runs in, a try block or one of the guarding helpers is found first - and a second
 * test checks that those helpers really do catch.
 */
class PhoneSendGuardContractTest {

    private companion object {
        const val CONNECTION = "PhoneConnection.kt"
        const val SOURCE_ROOT = "src/main/java/com/svartifoss/snfell/watch"

        /** Helpers whose whole job is to run a send and catch what it throws. */
        val GUARD_HELPERS = listOf("launchSilently", "reachedPhone", "forward")

        /** Markers that mean "the failure of this send is handled". */
        val GUARD_MARKERS = listOf("try {", "launchWithErrorHandling") +
                GUARD_HELPERS.map { "$it {" }

        /** How far back to look for the enclosing launch before giving up. */
        const val LOOKBACK_LINES = 12
    }

    @Test
    fun everySendToThePhoneIsLaunchedWithItsFailureCaught() {
        val sends = publicSuspendSends()
        assertTrue("Found no public suspend sends in $CONNECTION - has the sweep gone stale?",
                sends.isNotEmpty())
        val call = Regex("""\bphoneConnection\.(${sends.joinToString("|")})\(""")
        val bareLaunch = Regex("""\.launch\s*[({]""")
        val declaration = Regex("""\bfun\s""")

        val unguarded = mutableListOf<String>()
        sources().filter { it.name != CONNECTION }.forEach { file ->
            val lines = withoutComments(file.readText()).lines()
            lines.forEachIndexed { index, line ->
                if (!call.containsMatchIn(line)) return@forEachIndexed
                for (back in index downTo maxOf(0, index - LOOKBACK_LINES)) {
                    val candidate = lines[back]
                    if (GUARD_MARKERS.any { candidate.contains(it) }) return@forEachIndexed
                    if (bareLaunch.containsMatchIn(candidate)) {
                        unguarded += "${file.name}:${index + 1}"
                        return@forEachIndexed
                    }
                    // Inside a suspend function rather than a launch: its caller owns the failure.
                    if (back != index && declaration.containsMatchIn(candidate)) {
                        return@forEachIndexed
                    }
                }
            }
        }

        assertTrue(
                "A send to the phone launched without catching its failure crashes the watch app " +
                        "whenever the phone has just gone out of range. Wrap it in try/catch, " +
                        "launchWithErrorHandling, or one of $GUARD_HELPERS. Unguarded: $unguarded",
                unguarded.isEmpty())
    }

    @Test
    fun theGuardingHelpersCatchWhatTheSendThrows() {
        val missing = mutableListOf<String>()
        sources().forEach { file ->
            val source = withoutComments(file.readText())
            GUARD_HELPERS.forEach { helper ->
                val start = Regex("""\bfun\s+$helper\(""").find(source)?.range?.first
                        ?: return@forEach
                if (!declarationFrom(source, start).contains("catch (e: Exception)")) {
                    missing += "${file.name}#$helper"
                }
            }
        }

        assertTrue(
                "These helpers are trusted by the sweep above to catch a failed send, so each must " +
                        "catch Exception (rethrowing CancellationException first). Not catching: " +
                        "$missing",
                missing.isEmpty())
    }

    private fun moduleDir(): File =
            listOf(File("."), File("wear"))
                    .firstOrNull { File(it, SOURCE_ROOT).isDirectory }
                    ?: throw AssertionError(
                            "Could not locate the wear module from ${File(".").absolutePath}")

    private fun sources(): List<File> =
            File(moduleDir(), SOURCE_ROOT).walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .sortedBy { it.path }
                    .toList()

    /** Names of the suspend functions [PhoneConnection] exposes - every one of them sends. */
    private fun publicSuspendSends(): List<String> {
        val connection = sources().firstOrNull { it.name == CONNECTION }
                ?: throw AssertionError("Could not find $CONNECTION")
        return Regex("""^\s*suspend fun (\w+)\(""", RegexOption.MULTILINE)
                .findAll(withoutComments(connection.readText()))
                .map { it.groupValues[1] }
                .toList()
    }

    /**
     * The function declared at [start], up to the next function declaration. Not brace counting:
     * a helper written as `= try { ... } catch ...` has its catch *after* the first block closes.
     */
    private fun declarationFrom(source: String, start: Int): String {
        val next = Regex("""\bfun\s""").find(source, start + 1)?.range?.first ?: source.length
        return source.substring(start, next)
    }

    /**
     * Comments removed, so a note that names a send is not read as a call. Block comments go first,
     * keeping their line breaks so the line numbers in a failure still point at the source; a `//`
     * starts a comment only outside a string literal.
     */
    private fun withoutComments(source: String): String =
            source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { comment ->
                comment.value.filter { it == '\n' }
            }
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
