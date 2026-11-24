package com.svartifoss.snfell.watch.tile

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tile refreshes go through [TileUpdateRequests], never through `TileService.getUpdater`.
 *
 * The library requester keeps the context it is given, unbinds on a thread of its own, and starts
 * a new thread for every request. Handed a short-lived service as context, its late unbind threw
 * "Service not registered" where nothing could catch it and ended the watch process, the most
 * frequent watch report on 4.3 and 4.3.1. Its threads were also the
 * "OutOfMemoryError: pthread_create" report. Nothing fails at build time when a call comes back,
 * so this reads the sources.
 */
class TileUpdateRequestsContractTest {

    private companion object {
        val SOURCE_ROOTS = listOf(File("src/main/java"), File("src/github/java"), File("src/play/java"))
        val REQUESTS = File("src/main/java/com/svartifoss/snfell/watch/tile/TileUpdateRequests.kt")
    }

    /** The source with comments removed, so a KDoc that names the old call does not count. */
    private fun code(file: File): String = file.readLines()
            .map { it.substringBefore("//") }
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
            .joinToString("\n")

    @Test
    fun `nothing asks the Tiles library for its updater`() {
        val offenders = SOURCE_ROOTS.filter { it.exists() }
                .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" || it.extension == "java" }.toList() }
                .filter { code(it).contains("getUpdater(") }
        assertTrue("TileService.getUpdater is called again in ${offenders.map { it.path }}: " +
                "use TileUpdateRequests.request, see its KDoc for the crash it caused",
                offenders.isEmpty())
    }

    @Test
    fun `the requester binds on the application context and releases each bind safely`() {
        assertTrue("${REQUESTS.path} is missing", REQUESTS.exists())
        val source = code(REQUESTS)
        assertTrue("TileUpdateRequests must bind on the application context, never on the " +
                "caller's (a destroyed service takes its bindings with it)",
                source.contains("context.applicationContext"))
        assertTrue("unbindService must tolerate a binding the system already removed",
                Regex("""try\s*\{\s*app\.unbindService\(this\)\s*}\s*catch\s*\(e: IllegalArgumentException\)""")
                        .containsMatchIn(source))
        assertFalse("TileUpdateRequests must not start threads of its own",
                source.contains("Executors.") || source.contains("Thread("))
        assertTrue("A connection that never arrives must be given up, or the queue stays in flight",
                source.contains("postDelayed(pending.timeout"))
    }
}
