package com.svartifoss.snfell.notifications

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Another app's icons are loaded with the platform's `Context.getDrawable`, never with
 * `AppCompatResources`.
 *
 * AppCompat checks that vectors are set up by loading its own test drawable by id through the
 * Context it is given. With another package's Context that id names an unrelated resource of
 * theirs, so the call threw "This app has been built with an incorrect configuration" - until
 * something of ours passed the check with our own Context and it was cached. In the music service,
 * with no screen of ours open, that never happened, and YouTube Music's like, shuffle and repeat
 * reached the watch with no icon at all (Pixel 3, Android 10, 2026-11-14). Nothing fails visibly:
 * the watch draws its own glyphs instead, so a source sweep is the only thing that notices.
 */
class RemoteIconLoaderContractTest {

    private companion object {
        const val SOURCE_PATH =
                "src/main/java/com/svartifoss/snfell/notifications/MediaNotificationActions.kt"
    }

    @Test
    fun publisherResourcesAreNeverLoadedThroughAppCompat() {
        val code = source().lineSequence()
                .map { it.substringBefore("//").trim() }
                .filterNot { it.startsWith("*") || it.startsWith("/*") }
                .joinToString("\n")
        assertFalse(
                "MediaNotificationActions loads other apps' drawables; AppCompatResources fails " +
                        "for a foreign Context until our own Context has passed its vector check.",
                code.contains("AppCompatResources"))
    }

    @Test
    fun customActionIconsUseThePlatformLoaderForBothContexts() {
        val code = source()
        assertTrue(code.contains("contexts.themed?.getDrawable(resourceId)"))
        assertTrue(code.contains("contexts.plain?.getDrawable(resourceId)"))
    }

    private fun source(): String {
        val file = listOf(File("."), File("mobile"))
                .map { File(it, SOURCE_PATH) }
                .firstOrNull { it.isFile }
                ?: throw AssertionError(
                        "Could not locate MediaNotificationActions from ${File(".").absolutePath}")
        return file.readText()
    }
}
