package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins how the drawer's support section is split between the `github` and `play` builds.
 *
 * Google Play's Payments policy forbids an in-app button, link or message that leads to any way of
 * paying other than Play Billing. The github build links to Buy Me a Coffee and Ko-fi; a Play build
 * that still carried those links would be pulled, and "hiding them at runtime" does not count - a
 * link that is merely invisible is still shipped. So the links, their strings and their icons live
 * in `src/github`, and the Play build sells a tip through Play Billing instead (`src/play`).
 *
 * That split exists only by where files sit, which nothing else checks, and which a well-meaning
 * "put the drawer strings back with the rest" would undo without a single compile error - exactly
 * the failure [FlavorSelfUpdateIsolationTest] guards for the self-updater.
 *
 * It also pins the other half of a tip's contract: **nothing in the app can know a tip happened**.
 * The listing is paid and every feature belongs to whoever bought it, so a tip must never gate
 * anything; the cheapest way to keep that true is that no code outside the Play flavor can even
 * name the billing library.
 *
 * Reads the tree off disk like its siblings in this package; no Android context.
 */
class FlavorSupportIsolationTest {

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

    /** What would send a person to pay somewhere other than Play, or draw the services' marks. */
    private val externalPaymentMarkers = listOf(
            "buymeacoffee.com",
            "ko-fi.com",
            "ic_buy_me_a_coffee",
            "ic_kofi",
            "drawer_kofi_button",
            "drawer_support_button",
            "drawer_support_summary")

    private val externalPaymentBrands = Regex("""buy ?me ?a ?coffee|ko-?fi""", RegexOption.IGNORE_CASE)

    private fun offendersIn(path: String, markers: List<String>): List<String> =
            sourcesUnder(path, "kt", "java", "xml", "json", "properties")
                    .filter { file -> markers.any { file.readText().contains(it) } }
                    .map { it.path.substringAfter("mobile/") }
                    .sorted()

    /**
     * Comments are not compiled into the APK, and the play files explain in comments why the links
     * are absent, so only what a resource actually contains counts.
     */
    private val xmlComment = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

    private fun brandOffendersIn(path: String): List<String> =
            sourcesUnder(path, "xml")
                    .filter { externalPaymentBrands.containsMatchIn(it.readText().replace(xmlComment, "")) }
                    .map { it.path.substringAfter("mobile/") }
                    .sorted()

    // ---- the Play build carries no other way of paying ----

    @Test
    fun theSharedSourcesNameNoOtherWayOfPaying() {
        assertEquals(
                "src/main reaches the play build. The Buy Me a Coffee and Ko-fi links, strings and " +
                        "icons belong in mobile/src/github.",
                emptyList<String>(),
                offendersIn("mobile/src/main", externalPaymentMarkers))
        assertEquals(
                "src/main strings or layouts name a payment service the play build must not show.",
                emptyList<String>(),
                brandOffendersIn("mobile/src/main/res"))
    }

    @Test
    fun thePlayFlavorNamesNoOtherWayOfPaying() {
        assertEquals(emptyList<String>(), offendersIn("mobile/src/play", externalPaymentMarkers))
        assertEquals(emptyList<String>(), brandOffendersIn("mobile/src/play/res"))
    }

    @Test
    fun theGithubFlavorKeepsTheLinksAndEverythingTheyNeed() {
        val section = text("mobile/src/github/res/layout/drawer_support_section.xml")
        listOf("drawer_support_button", "drawer_kofi_button", "ic_buy_me_a_coffee", "ic_kofi").forEach {
            assertTrue("the github drawer section must still use $it", section.contains(it))
        }
        val seam = text("mobile/src/github/java/com/svartifoss/snfell/support/DrawerSupportSection.kt")
        listOf("https://buymeacoffee.com/", "https://ko-fi.com/").forEach {
            assertTrue("the github DrawerSupportSection must still open $it", seam.contains(it))
        }
        listOf("ic_buy_me_a_coffee.xml", "ic_kofi.xml").forEach {
            assertTrue("mobile/src/github/res/drawable/$it is missing",
                    exists("mobile/src/github/res/drawable/$it"))
        }
    }

    @Test
    fun theGithubStringsAreTranslatedInEveryLocaleTheAppShips() {
        val keys = listOf("drawer_support_summary", "drawer_support_button", "drawer_kofi_button")
        val localesWithStrings = repoFile("mobile/src/github/res").listFiles().orEmpty()
                .filter { dir -> File(dir, "strings.xml").exists() }
                .filter { dir -> keys.all { File(dir, "strings.xml").readText().contains("name=\"$it\"") } }
                .map { it.name }
                .toSet()
        val allLocales = repoFile("mobile/src/main/res").listFiles().orEmpty()
                .filter { it.name == "values" || (it.name.startsWith("values-") && File(it, "strings.xml").exists()) }
                .filter { it.name != "values-night" }
                .map { it.name }
                .toSet()
        assertEquals(
                "Every locale that has strings must carry the github support strings, or a translated " +
                        "sideload build shows English where it used to be translated.",
                allLocales,
                localesWithStrings)
    }

    // ---- the billing library is a play-only dependency ----

    @Test
    fun billingIsAPlayOnlyDependency() {
        val lines = text("mobile/build.gradle").lines()
                .map { it.trim() }
                .filter { !it.startsWith("//") && it.contains("billing", ignoreCase = true) }
        assertTrue("mobile/build.gradle must declare the billing library", lines.isNotEmpty())
        lines.forEach {
            assertTrue("billing must be declared with playImplementation only, found: $it",
                    it.startsWith("playImplementation"))
        }
    }

    @Test
    fun noSharedOrGithubCodeCanNameTheBillingLibraryOrTheTipClasses() {
        val tokens = listOf("com.android.billingclient", "TipJar", "TipLedger", "TipProducts", "TipDialog",
                "TipCatalogue", "BillingClient")
        listOf("mobile/src/main", "mobile/src/github").forEach { root ->
            assertEquals(
                    "$root must not reference the billing library or the tip classes: a tip that " +
                            "nothing outside the play flavor can see is a tip that cannot gate anything.",
                    emptyList<String>(),
                    offendersIn(root, tokens))
        }
    }

    // ---- the seam ----

    @Test
    fun bothFlavorsSupplyTheSeamAndTheSection() {
        listOf("github", "play").forEach { flavor ->
            val seam = text("mobile/src/$flavor/java/com/svartifoss/snfell/support/DrawerSupportSection.kt")
            assertTrue("$flavor DrawerSupportSection must expose bind(activity, header)",
                    seam.contains("fun bind(activity: AppCompatActivity, header: View)"))
            val layout = text("mobile/src/$flavor/res/layout/drawer_support_section.xml")
            assertTrue("$flavor drawer_support_section.xml must declare the section id",
                    layout.contains("@+id/drawer_support_section"))
        }
        assertFalse("a shared drawer_support_section.xml would hide that a flavor forgot to supply its own",
                exists("mobile/src/main/res/layout/drawer_support_section.xml"))
        assertFalse("a shared support package would clash with the flavors' DrawerSupportSection",
                exists("mobile/src/main/java/com/svartifoss/snfell/support"))
    }

    @Test
    fun theDrawerIncludesTheSectionAndMainActivityBindsIt() {
        assertTrue(text("mobile/src/main/res/layout/nav_header_main.xml")
                .contains("@layout/drawer_support_section"))
        assertTrue(text("mobile/src/main/java/com/svartifoss/snfell/view/mainactivity/MainActivity.kt")
                .contains("DrawerSupportSection.bind(this, header)"))
    }

    // ---- the tip copy ----

    @Test
    fun theTipStringsExistInEveryLocaleTheProjectWritesByHand() {
        val keys = listOf("drawer_tip_summary", "drawer_tip_button", "tip_dialog_title", "tip_dialog_message",
                "tip_thanks", "tip_pending", "tip_failed")
        listOf("values", "values-pt-rBR", "values-pt-rPT").forEach { dir ->
            val strings = text("mobile/src/play/res/$dir/strings.xml")
            keys.forEach {
                assertTrue("mobile/src/play/res/$dir/strings.xml is missing $it",
                        strings.contains("name=\"$it\""))
            }
        }
    }

    @Test
    fun theConsoleSetupGuideListsEveryProductTheAppAsksFor() {
        val ids = Regex("""listOf\(((?:\s*"[a-z0-9_.]+",?)+)\s*\)""")
                .find(text("mobile/src/play/java/com/svartifoss/snfell/support/TipProducts.kt"))
                ?.groupValues?.get(1)
                ?.let { Regex(""""([a-z0-9_.]+)"""").findAll(it).map { m -> m.groupValues[1] }.toList() }
                ?: emptyList()
        assertTrue("could not read the product ids from TipProducts.kt", ids.size >= 3)
        val guide = text("docs/play-tips.md")
        ids.forEach {
            assertTrue("docs/play-tips.md must tell whoever sets up Play Console to create $it", guide.contains(it))
        }
    }
}
