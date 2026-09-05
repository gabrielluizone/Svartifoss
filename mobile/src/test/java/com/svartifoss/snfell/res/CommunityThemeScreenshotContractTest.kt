package com.svartifoss.snfell.res

import com.svartifoss.snfell.common.CommunityThemeScreenshots
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The author-screenshot contract is written three times, in three languages: Kotlin decides what
 * the phone produces, `firestore.rules` decides what may be stored, and the Node publisher decides
 * what may be committed. None of the three can execute the others.
 *
 * A drift is quiet in the worst direction. Loosening Kotlin past what the rules accept turns a
 * legitimate submission into a bare `PERMISSION_DENIED` after the author has already signed in;
 * loosening it past what the publisher accepts drops the picture at publication time, days later,
 * with only a line in a workflow log. Neither says which number disagreed, which is what this test
 * is for.
 */
class CommunityThemeScreenshotContractTest {

    @Test
    fun `the rules accept exactly the surfaces the phone can attach`() {
        val screenshotRule = screenshotRule()
        val declared = Regex("""surface in \[([^\]]*)]""")
                .find(screenshotRule)
                ?.groupValues
                ?.get(1)
                ?: throw AssertionError("firestore.rules declares no screenshot surface list")
        val surfaces = Regex("\"([a-z]+)\"").findAll(declared).map { it.groupValues[1] }.toList()
        assertEquals(
                "firestore.rules and CommunityThemeScreenshots.SURFACES disagree",
                CommunityThemeScreenshots.SURFACES,
                surfaces)
    }

    @Test
    fun `the rules bound the envelope at exactly what the phone will send`() {
        val screenshotRule = screenshotRule()
        val bound = Regex("""webpBase64\.size\(\) <= (\d+)""")
                .find(screenshotRule)
                ?.groupValues
                ?.get(1)
                ?.toInt()
                ?: throw AssertionError("firestore.rules declares no screenshot size bound")
        assertEquals(
                "firestore.rules and CommunityThemeScreenshots.MAX_BASE64_LENGTH disagree",
                CommunityThemeScreenshots.MAX_BASE64_LENGTH,
                bound)
    }

    @Test
    fun `the publisher accepts exactly what the phone produces`() {
        val publisher = publisherSource()
        assertEquals(
                "SHOT_SURFACES disagrees with CommunityThemeScreenshots.SURFACES",
                CommunityThemeScreenshots.SURFACES,
                Regex("""const SHOT_SURFACES = \[([^\]]*)]""")
                        .find(publisher)
                        ?.groupValues
                        ?.get(1)
                        ?.let { list -> Regex("\"([a-z]+)\"").findAll(list).map { it.groupValues[1] }.toList() })
        assertEquals(
                "MAX_SHOT_BASE64_LENGTH disagrees with the phone's envelope",
                CommunityThemeScreenshots.MAX_BASE64_LENGTH,
                publisherConstant(publisher, "MAX_SHOT_BASE64_LENGTH"))
        assertEquals(
                "MAX_SHOT_BYTES disagrees with the phone's byte budget",
                CommunityThemeScreenshots.MAX_BYTES,
                publisherConstant(publisher, "MAX_SHOT_BYTES"))
        assertEquals(
                "MIN_SHOT_PIXELS disagrees with the phone's smallest accepted source",
                CommunityThemeScreenshots.MIN_PIXELS,
                publisherConstant(publisher, "MIN_SHOT_PIXELS"))
        assertEquals(
                "MAX_SHOT_PIXELS disagrees with the phone's largest encoded square",
                CommunityThemeScreenshots.MAX_PIXELS,
                publisherConstant(publisher, "MAX_SHOT_PIXELS"))
    }

    @Test
    fun `the publisher commits screenshots where the gallery looks for them`() {
        val publisher = publisherSource()
        assertEquals(
                "SHOTS_DIRECTORY disagrees with the path the gallery fetches from",
                CommunityThemeScreenshots.SHOTS_DIRECTORY,
                Regex("const SHOTS_DIRECTORY = \"([^\"]+)\";")
                        .find(publisher)
                        ?.groupValues
                        ?.get(1))
        // A file name built differently on either side is not an error anywhere: the fetch 404s,
        // the gallery reports "no picture", and every screenshot quietly stops appearing while the
        // catalogue, the commit and the detail screen all look completely correct.
        assertTrue(
                "the publisher no longer names screenshot files <id>-<surface>.webp",
                publisher.contains("resolve(directory, `\${id}-\${surface}.webp`)"))
        assertEquals(
                "the gallery no longer builds <id>-<surface>.webp",
                "abc-player.webp",
                CommunityThemeScreenshots.fileName("abc", "player"))
    }

    @Test
    fun `the phone never encodes a square the publisher would refuse`() {
        assertTrue(
                "TARGET_PIXELS must sit inside the publisher's accepted range",
                CommunityThemeScreenshots.TARGET_PIXELS in
                        CommunityThemeScreenshots.MIN_PIXELS..CommunityThemeScreenshots.MAX_PIXELS)
        // 3 bytes encode to 4 base64 characters, so the byte budget must not exceed the envelope
        // the rules enforce -- a picture that passes the local check and fails at the write is the
        // failure this whole contract exists to prevent.
        val encodedLength = 4 * ((CommunityThemeScreenshots.MAX_BYTES + 2) / 3)
        assertTrue(
                "MAX_BYTES ($encodedLength encoded) exceeds MAX_BASE64_LENGTH",
                encodedLength <= CommunityThemeScreenshots.MAX_BASE64_LENGTH)
    }

    /** The one rule function, isolated so a bound elsewhere in the file cannot satisfy this test. */
    private fun screenshotRule(): String {
        val source = read("firestore.rules")
        val start = source.indexOf("function validNewThemeScreenshot(")
        assertTrue("firestore.rules declares no validNewThemeScreenshot", start >= 0)
        val end = source.indexOf("\n    }", start)
        assertTrue("validNewThemeScreenshot is not terminated", end > start)
        return source.substring(start, end)
    }

    private fun publisherSource(): String = read(".github/community-theme-publisher/publisher.mjs")

    private fun publisherConstant(source: String, name: String): Int? =
            Regex("""const $name = ([0-9 *]+);""")
                    .find(source)
                    ?.groupValues
                    ?.get(1)
                    ?.split("*")
                    ?.map { it.trim().toInt() }
                    ?.reduce(Int::times)

    private fun read(relative: String): String {
        val file = listOf(File("../$relative"), File(relative)).firstOrNull(File::exists)
                ?: throw AssertionError("Could not locate $relative")
        return file.readText()
    }
}
