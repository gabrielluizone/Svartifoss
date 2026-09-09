package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The `play` artifact must never tell anyone to install an app from outside Google Play.
 *
 * This is the same policy that forced the self-updater's flavour split
 * ([FlavorSelfUpdateIsolationTest]), but the wording half of it is easier to reintroduce: the
 * self-updater is a package of classes somebody would have to move, while this is one sentence in
 * one string, and the string sits among 45 translations of itself. It shipped that way - the "watch
 * app is missing" dialog, which is the *first* thing a reviewer installing only the phone app
 * sees, read "Svartifoss isn't on the Play Store - the watch app is sideloaded separately" and
 * offered a GitHub releases link.
 *
 * On Play the instruction is also simply wrong: phone and watch are one listing sharing an
 * `applicationId`, so Play delivers the watch app itself.
 *
 * The split is by resource folder, so the check is that no `src/main` or `src/play` string points
 * anywhere but Play. Reads the tree off disk like its siblings in this package.
 */
class FlavorSideloadGuidanceIsolationTest {

    /** Wording that is only ever correct for a build distributed outside Play. */
    private val sideloadMarkers = listOf(
            "github.com",
            "sideload",
            "Wear Installer",
            "watch APK",
            "isn't on the Play Store")

    /**
     * Strings that legitimately name GitHub without directing an install there.
     *
     * The About screen's link to the project is not an install route - an open-source app may say
     * where its source lives. Only instructions to *get the app* from outside Play are the problem.
     */
    private val allowedKeys = setOf(
            "about_developer_github",
            "drawer_about_github",
            "about_github_url")

    private fun repoFile(path: String): File {
        listOf(File(path), File("../$path")).forEach { if (it.exists()) return it }
        fail("Not found from either module dir or repo root: $path")
        error("unreachable")
    }

    private fun stringsIn(dir: File): List<Pair<String, String>> =
            (dir.listFiles()?.filter { it.isDirectory && it.name.startsWith("values") } ?: emptyList())
                    .mapNotNull { File(it, "strings.xml").takeIf(File::exists) }
                    .flatMap { file ->
                        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                                .findAll(file.readText())
                                .map { "${file.parentFile.name}/${it.groupValues[1]}" to it.groupValues[2] }
                                .toList()
                    }

    private fun offendersIn(resPath: String): List<String> {
        val dir = repoFile(resPath)
        return stringsIn(dir)
                .filterNot { (id, _) -> id.substringAfter('/') in allowedKeys }
                .filter { (_, value) -> sideloadMarkers.any { value.contains(it, ignoreCase = true) } }
                .map { it.first }
                .sorted()
    }

    @Test
    fun theSharedPhoneStringsNameNoSideloadRoute() {
        assertEquals(
                "src/main strings reach the play build. Sideload wording belongs in " +
                        "mobile/src/github/res, which overrides these names for that flavour only.",
                emptyList<String>(),
                offendersIn("mobile/src/main/res"))
    }

    @Test
    fun theSharedWatchStringsNameNoSideloadRoute() {
        assertEquals(
                "wear/src/main strings reach the play build. Put sideload wording in " +
                        "wear/src/github/res instead.",
                emptyList<String>(),
                offendersIn("wear/src/main/res"))
    }

    @Test
    fun thePlayFlavourAddsNoSideloadRouteOfItsOwn() {
        listOf("mobile/src/play/res", "wear/src/play/res").forEach { path ->
            if (File(path).exists() || File("../$path").exists()) {
                assertEquals("$path must never name an install route outside Play",
                        emptyList<String>(), offendersIn(path))
            }
        }
    }

    /**
     * The watch notice is the one whose text lives in `wearutils`, a submodule this repo forks but
     * shares with another app - so the play override has to exist here rather than being fixed
     * upstream, and its absence would silently fall back to the library's "View on GitHub".
     */
    @Test
    fun thePlayWatchBuildOverridesTheLibrarysGithubNotice() {
        val overridden = stringsIn(repoFile("wear/src/play/res")).map { it.first.substringAfter('/') }
        listOf("no_phone_app_button", "no_phone_app_github_url", "no_phone_app_description").forEach {
            assertTrue(
                    "wear/src/play/res does not override '$it', so the play watch build shows " +
                            "wearutils' GitHub wording",
                    it in overridden)
        }
    }

    /**
     * Both flavours must define the phone dialog's text, in every locale the other one has: a name
     * present in `src/main` for 45 locales and in `src/github` for one would leave a translated
     * github build showing the play text, which is the drift this split exists to prevent.
     */
    @Test
    fun bothFlavoursWordThePhoneNoticeInEveryLocale() {
        val keys = listOf("no_watch_app_description", "no_watch_app_button")
        val mainLocales = localesDefining(repoFile("mobile/src/main/res"), keys)
        val githubLocales = localesDefining(repoFile("mobile/src/github/res"), keys)
        assertEquals(
                "The github flavour must restate the notice in exactly the locales src/main does, " +
                        "or a translated sideload build falls back to the Play wording.",
                mainLocales,
                githubLocales)
        assertTrue("Expected the full translation set, got ${mainLocales.size}", mainLocales.size >= 40)
    }

    private fun localesDefining(dir: File, keys: List<String>): Set<String> =
            stringsIn(dir)
                    .filter { it.first.substringAfter('/') in keys }
                    .map { it.first.substringBefore('/') }
                    .toSet()
}
