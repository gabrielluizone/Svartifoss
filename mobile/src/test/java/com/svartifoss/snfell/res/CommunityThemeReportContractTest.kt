package com.svartifoss.snfell.res

import com.svartifoss.snfell.view.watchface.theme.COMMUNITY_THEME_REPORT_DETAILS_MAX_LENGTH
import com.svartifoss.snfell.view.watchface.theme.CommunityThemeReportReason
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report contract is written twice and neither copy can execute the other: Kotlin decides what
 * the phone offers, `firestore.rules` decides what may be stored.
 *
 * The failure this guards is the one the base-face registry already produced once. Every local
 * gate passes — the picker builds the reason, the dialog accepts the text — and the write is then
 * denied, which the app can only report as an ordinary "could not send". Nothing points at the
 * reason value or the length, so a report button that has quietly stopped working looks exactly
 * like a network problem.
 *
 * The subcollection name is checked for the opposite reason. `firestore.rules` authorises the
 * moderator queue with a recursive-wildcard rule, which applies to *every* same-named subcollection
 * in the database; renaming it on one side alone either breaks the queue or silently widens a
 * wildcard over something else.
 */
class CommunityThemeReportContractTest {

    @Test
    fun `the rules accept exactly the reasons the phone can send`() {
        val declared = Regex("""data\.reason in \[([^]]*)]""")
                .find(reportRule())
                ?.groupValues
                ?.get(1)
                ?: throw AssertionError("firestore.rules declares no report reason list")
        val reasons = Regex("\"([a-z_]+)\"").findAll(declared).map { it.groupValues[1] }.toList()
        assertEquals(
                "firestore.rules and CommunityThemeReportReason disagree",
                CommunityThemeReportReason.entries.map { it.wireValue }.sorted(),
                reasons.sorted())
    }

    @Test
    fun `the rules bound the reporter's own words at exactly what the field accepts`() {
        val bound = Regex("""validShortText\(data\.details, (\d+)\)""")
                .find(reportRule())
                ?.groupValues
                ?.get(1)
                ?.toInt()
                ?: throw AssertionError("firestore.rules declares no report details bound")
        assertEquals(
                "firestore.rules and COMMUNITY_THEME_REPORT_DETAILS_MAX_LENGTH disagree",
                COMMUNITY_THEME_REPORT_DETAILS_MAX_LENGTH,
                bound)
    }

    @Test
    fun `the moderator queue's wildcard rule names the same subcollection the phone writes to`() {
        val rules = read("firestore.rules")
        assertTrue(
                "firestore.rules no longer authorises the moderator collection-group query",
                rules.contains("match /{path=**}/themeReporters/{uid}"))
        assertTrue(
                "firestore.rules no longer declares the concrete report path",
                rules.contains("match /communityThemeReports/{themeId}/themeReporters/{uid}"))
        assertTrue(
                "the phone writes to a subcollection the rules do not name",
                read("mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/" +
                        "CommunityThemeReport.kt")
                        .contains("REPORTERS_COLLECTION = \"themeReporters\""))
    }

    /** The one rule function, isolated so a bound elsewhere in the file cannot satisfy this test. */
    private fun reportRule(): String {
        val source = read("firestore.rules")
        val start = source.indexOf("function validNewCommunityThemeReport(")
        assertTrue("firestore.rules declares no validNewCommunityThemeReport", start >= 0)
        val end = source.indexOf("\n    }", start)
        assertTrue("validNewCommunityThemeReport is not terminated", end > start)
        return source.substring(start, end)
    }

    private fun read(relative: String): String {
        val file = listOf(File("../$relative"), File(relative)).firstOrNull(File::exists)
                ?: throw AssertionError("Could not locate $relative")
        return file.readText()
    }
}
