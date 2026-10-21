package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins how the rating prompt is split between the `github` and `play` builds, and the one rule
 * about how it may be reached.
 *
 * The in-app review card is drawn by the Google Play Store app and only a build the Store knows
 * can show it, so the library that talks to it is a `play`-only dependency and the github build
 * gets an inert twin - the same arrangement as the self-updater and the tip jar, and for the same
 * reason: the split exists only by where files sit, which nothing else checks, and a tidy-minded
 * "put it back in main" would undo it without a compile error.
 *
 * The other half is about people, not builds. Play's guidance is that no button should raise the
 * card (a quota can silently refuse it, and a button that does nothing reads as broken) and that
 * nothing of the app's own should come before it. So the prompt is wired once, at start-up, and
 * the only other caller allowed is the Developer row that exists to test it.
 *
 * Reads the tree off disk like its siblings in this package; no Android context.
 */
class FlavorReviewIsolationTest {

    private fun repoFile(path: String): File {
        listOf(File(path), File("../$path")).forEach { if (it.exists()) return it }
        fail("Not found from either module dir or repo root: $path")
        error("unreachable")
    }

    private fun exists(path: String): Boolean = File(path).exists() || File("../$path").exists()

    private fun text(path: String): String = repoFile(path).readText()

    private fun sourcesUnder(path: String, vararg extensions: String): List<File> {
        if (!exists(path)) return emptyList()
        return repoFile(path).walkTopDown()
                .filter { it.isFile && it.extension in extensions }
                .toList()
    }

    private fun offendersIn(path: String, markers: List<String>): List<String> =
            sourcesUnder(path, "kt", "java", "xml", "json", "properties")
                    .filter { file -> markers.any { file.readText().contains(it) } }
                    .map { it.path.substringAfter("mobile/") }
                    .sorted()

    private val reviewLibraryMarkers = listOf(
            "com.google.android.play",
            "ReviewManager",
            "ReviewInfo",
            "ReviewException")

    // ---- the library is a play-only dependency ----

    @Test
    fun theReviewLibraryIsAPlayOnlyDependency() {
        val lines = text("mobile/build.gradle").lines()
                .map { it.trim() }
                .filter { !it.startsWith("//") && it.contains("google.play.review") }
        assertTrue("mobile/build.gradle must declare the review library", lines.isNotEmpty())
        lines.forEach {
            assertTrue("the review library must be declared with playImplementation only, found: $it",
                    it.startsWith("playImplementation"))
        }
    }

    @Test
    fun noSharedOrGithubCodeCanNameTheReviewLibrary() {
        listOf("mobile/src/main", "mobile/src/github").forEach { root ->
            assertEquals(
                    "$root must not reference the review library: only a build from the Store can show " +
                            "its card, and the github build has no Store to post a review to.",
                    emptyList<String>(),
                    offendersIn(root, reviewLibraryMarkers))
        }
    }

    // ---- the seam ----

    @Test
    fun bothFlavorsSupplyTheSameSeam() {
        listOf("github" to "false", "play" to "true").forEach { (flavor, supported) ->
            val seam = text("mobile/src/$flavor/java/com/svartifoss/snfell/review/ReviewPrompt.kt")
            assertTrue("$flavor ReviewPrompt must expose attach(activity)",
                    seam.contains("fun attach(activity: AppCompatActivity)"))
            assertTrue("$flavor ReviewPrompt must expose showNow(activity)",
                    seam.contains("fun showNow(activity: AppCompatActivity)"))
            assertTrue("$flavor ReviewPrompt must say whether it can ask at all",
                    seam.contains("const val SUPPORTED = $supported"))
        }
        assertFalse("a shared review package would clash with the flavors' ReviewPrompt",
                exists("mobile/src/main/java/com/svartifoss/snfell/review"))
    }

    // ---- who may raise the card ----

    @Test
    fun theCardIsWiredAtStartUpAndNotBehindAnyButton() {
        val callers = sourcesUnder("mobile/src/main/java", "kt")
                .filter { it.readText().contains("ReviewPrompt") }
                .map { it.name }
                .sorted()
        assertEquals(
                "Only MainActivity (start-up) and the Developer row may touch the prompt. Play advises " +
                        "against any button that raises the card: the quota can refuse it silently.",
                listOf("MainActivity.kt", "MiscSettingsFragment.kt"),
                callers)

        assertTrue(text("mobile/src/main/java/com/svartifoss/snfell/view/mainactivity/MainActivity.kt")
                .contains("ReviewPrompt.attach(this)"))

        val showers = sourcesUnder("mobile/src/main/java", "kt")
                .filter { it.readText().contains("ReviewPrompt::showNow") || it.readText().contains("ReviewPrompt.showNow") }
                .map { it.name }
        assertEquals("only the Developer row may skip the policy", listOf("MiscSettingsFragment.kt"), showers)
    }

    @Test
    fun theDeveloperRowIsHiddenWhereThereIsNoCardToShow() {
        val fragment = text("mobile/src/main/java/com/svartifoss/snfell/view/settings/MiscSettingsFragment.kt")
        assertTrue(fragment.contains("\"dev_show_rating_card\""))
        assertTrue("the row must follow ReviewPrompt.SUPPORTED", fragment.contains("row.isVisible = ReviewPrompt.SUPPORTED"))

        val settings = text("mobile/src/main/res/xml/settings.xml")
        val developerCategory = settings.substringAfter("android:key=\"cat_developer\"")
        assertTrue("the row belongs under the Developer category, which only appears in developer mode",
                developerCategory.contains("android:key=\"dev_show_rating_card\""))
    }

    @Test
    fun theDeveloperRowIsWordedInTheLocalesTheProjectWritesByHand() {
        val keys = listOf("dev_show_rating_card", "dev_show_rating_card_description", "dev_show_rating_card_unavailable")
        listOf("values", "values-pt-rBR", "values-pt-rPT").forEach { dir ->
            val strings = text("mobile/src/main/res/$dir/strings.xml")
            keys.forEach {
                assertTrue("mobile/src/main/res/$dir/strings.xml is missing $it", strings.contains("name=\"$it\""))
            }
        }
    }
}
