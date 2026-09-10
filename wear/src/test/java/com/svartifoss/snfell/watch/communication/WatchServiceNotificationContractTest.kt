package com.svartifoss.snfell.watch.communication

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watch publishes exactly one ongoing entry for itself - its own OngoingActivity - and never a
 * media notification.
 *
 * Wear OS turns any MediaStyle notification into an ongoing activity of its own ("Wear creates
 * Ongoing Activities automatically for media apps"). [WatchMusicService] used to bind its foreground
 * notification to the proxy session's token *and* attach an explicit OngoingActivity to it, so the
 * system showed two entries for one app: two Svartifoss items in Samsung's Now Bar, and a chooser
 * between them on every tap. Google's rule for exactly this case is that a watch must not post a
 * media notification while the media plays on the phone - which is always the case here.
 *
 * Both halves are pinned, because the tempting fix in either direction breaks something. Re-adding
 * MediaStyle for the system's media template brings the duplicate back. Dropping the OngoingActivity
 * instead would leave only the system's entry, which not every watch creates - and without an entry
 * the app vanishes from the watch face during an ordinary pause, which is what the paused-track hold
 * exists to prevent.
 *
 * A source sweep, like [com.svartifoss.snfell.watch.view.face.AmbientFaceContractTest]: nothing
 * about the duplicate is visible to a test of the notification object itself.
 */
class WatchServiceNotificationContractTest {

    private companion object {
        const val SERVICE_PATH =
                "src/main/java/com/svartifoss/snfell/watch/communication/WatchMusicService.kt"

        /** Every source set that ends up in a watch APK. */
        val SOURCE_SETS = listOf("src/main/java", "src/github/java", "src/play/java")
    }

    @Test
    fun theWatchNeverPostsAMediaNotification() {
        val mediaStyle = Regex("""\bMediaStyle\b""")
        val offenders = watchSources()
                .filter { mediaStyle.containsMatchIn(withoutComments(it.readText())) }
                .map { it.name }

        assertTrue(
                "A MediaStyle notification on the watch makes Wear OS add a media ongoing activity " +
                        "beside the service's own one, so the app is listed twice (Samsung's Now " +
                        "Bar then asks which of the two to open). The music plays on the phone, and " +
                        "Google's guidance for that case is to post no media notification on the " +
                        "watch at all. Found in: $offenders",
                offenders.isEmpty())
    }

    @Test
    fun theServiceStillPublishesItsOwnOngoingActivity() {
        val service = withoutComments(File(moduleDir(), SERVICE_PATH).readText())

        assertTrue(
                "WatchMusicService must keep building its own OngoingActivity with a touch intent: " +
                        "with no media notification it is the watch's only ongoing entry, and the " +
                        "only way back into a paused track once the screen has turned off.",
                service.contains("OngoingActivity.Builder(") && service.contains(".setTouchIntent("))
    }

    private fun moduleDir(): File =
            listOf(File("."), File("wear"))
                    .firstOrNull { File(it, SERVICE_PATH).isFile }
                    ?: throw AssertionError(
                            "Could not locate the wear module from ${File(".").absolutePath}")

    private fun watchSources(): List<File> {
        val sources = SOURCE_SETS
                .map { File(moduleDir(), it) }
                .filter { it.isDirectory }
                .flatMap { root ->
                    root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
                }
                .sortedBy { it.path }
        assertTrue("Found no watch sources under ${moduleDir().absolutePath}", sources.isNotEmpty())
        return sources
    }

    /**
     * Comments removed, so a line explaining the rule is not read as a breach of it - the comment
     * beside the fix in [WatchMusicService] names MediaStyle, which is exactly the note worth
     * leaving there. Block comments go first; a `//` starts a comment only outside a string
     * literal, so a "content://" URL survives.
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
