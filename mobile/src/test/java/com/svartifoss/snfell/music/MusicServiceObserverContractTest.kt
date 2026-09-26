package com.svartifoss.snfell.music

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MusicService] registers its volume `ContentObserver` exactly once per service, in `onCreate`.
 *
 * It used to register inside `promoteToForeground()`, which also runs at the top of every
 * `onStartCommand` - it has to, since each `startForegroundService()` must be answered. The content
 * service keeps one entry per registration without deduplicating, and each unregister removes only
 * one of them, so every start after the first left an entry that `onDestroy` never took back. While
 * the service lived, any change to a system setting fired the observer once per start; afterwards
 * the dead entries went on calling into the process for as long as it survived.
 *
 * A source sweep: the leak is invisible to anything but the system's own observer table, and what
 * goes wrong is a registration moving back into a function that runs more than once.
 */
class MusicServiceObserverContractTest {

    private companion object {
        const val SERVICE_PATH = "src/main/java/com/svartifoss/snfell/music/MusicService.kt"
        const val REGISTER = "registerContentObserver("
        const val UNREGISTER = "unregisterContentObserver("
    }

    @Test
    fun theVolumeObserverIsRegisteredOnceAndOnlyInOnCreate() {
        val source = serviceSource()
        val registrations = Regex("""(?<!un)registerContentObserver\(""").findAll(source).count()

        assertEquals(
                "MusicService should register its content observer at exactly one site, so each " +
                        "service instance holds exactly one registration for onDestroy to undo.",
                1, registrations)
        assertTrue(
                "The content observer must be registered in onCreate, which runs once per service.",
                bodyOf(source, "override fun onCreate()").contains(REGISTER))
        listOf("private fun promoteToForeground()", "override fun onStartCommand(").forEach {
            val body = bodyOf(source, it)
            assertTrue(
                    "'$it' runs on every start of the service. A registration there adds an " +
                            "entry per start that the single unregister in onDestroy leaves behind.",
                    !Regex("""(?<!un)registerContentObserver\(""").containsMatchIn(body))
        }
    }

    @Test
    fun theVolumeObserverIsUnregisteredInOnDestroy() {
        assertTrue(
                "onDestroy must unregister the content observer onCreate registered.",
                bodyOf(serviceSource(), "override fun onDestroy()").contains(UNREGISTER))
    }

    private fun serviceSource(): String {
        val file = listOf(File("."), File("mobile"))
                .map { File(it, SERVICE_PATH) }
                .firstOrNull { it.isFile }
                ?: throw AssertionError(
                        "Could not locate MusicService from ${File(".").absolutePath}")
        return withoutComments(file.readText())
    }

    /** The body of the function declared by [signature], found by brace counting. */
    private fun bodyOf(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("MusicService no longer declares '$signature'", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(open, index + 1)
            }
        }
        throw AssertionError("Unbalanced braces after '$signature' in MusicService")
    }

    /**
     * Comments removed, so the note explaining where the registration lives is not read as the
     * registration. Block comments go first; a `//` starts a comment only outside a string literal.
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
