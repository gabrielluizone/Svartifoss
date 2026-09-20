package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OembedParserTest {
    @Test
    fun readsTitleAndThumbnailFromOneAnswer() {
        // The shape the real services return: YouTube's playlist record, trimmed.
        val info = OembedParser.parse(
                """{"title":"Popular Music Videos","author_name":"Music","type":"video",
                    "thumbnail_url":"https://i.ytimg.com/vi/fOT0BUpITw8/hqdefault.jpg"}""")

        assertEquals("Popular Music Videos", info?.title)
        assertEquals("https://i.ytimg.com/vi/fOT0BUpITw8/hqdefault.jpg", info?.thumbnailUrl)
    }

    @Test
    fun aMissingFieldIsANullMemberNotAFailedParse() {
        // Spotify publishes a title but a service may publish only a cover, or only a name.
        val onlyTitle = OembedParser.parse("""{"title":"Today’s Top Hits"}""")
        assertEquals("Today’s Top Hits", onlyTitle?.title)
        assertNull(onlyTitle?.thumbnailUrl)

        val onlyCover = OembedParser.parse("""{"thumbnail_url":"https://x/y.jpg"}""")
        assertNull(onlyCover?.title)
        assertEquals("https://x/y.jpg", onlyCover?.thumbnailUrl)
    }

    @Test
    fun anythingThatIsNotAJsonObjectIsNoAnswer() {
        assertNull(OembedParser.parse(""))
        assertNull(OembedParser.parse("<html>Service unavailable</html>"))
        assertNull(OembedParser.parse("[1,2,3]"))
    }

    @Test
    fun aBlankTitleIsNoTitle() {
        assertNull(OembedParser.parse("""{"title":"   "}""")?.title)
        assertNull(OembedParser.cleanTitle(null))
        assertNull(OembedParser.cleanTitle(""))
    }

    @Test
    fun decodesTheEntitiesSomeServicesLeaveInTitles() {
        assertEquals("Rock & Roll", OembedParser.cleanTitle("Rock &amp; Roll"))
        assertEquals("\"Live\" at Mom's", OembedParser.cleanTitle("&quot;Live&quot; at Mom&#39;s"))
        assertEquals("A < B > C", OembedParser.cleanTitle("A &lt; B &gt; C"))
    }

    @Test
    fun ampersandIsDecodedLastSoAnEscapedEntityStaysLiteral() {
        // "&amp;lt;" is the text "&lt;", not a less-than sign that was escaped twice.
        assertEquals("&lt;", OembedParser.cleanTitle("&amp;lt;"))
    }

    @Test
    fun collapsesWhitespaceAndTrims() {
        assertEquals("Happy Hits", OembedParser.cleanTitle("  Happy \n\t Hits  "))
    }

    @Test
    fun capsAVeryLongTitleWithAnEllipsis() {
        val title = OembedParser.cleanTitle("word ".repeat(60))!!

        assertEquals(OembedParser.MAX_TITLE_LENGTH, title.length)
        assertTrue(title.endsWith("…"))
        // No dangling space before the ellipsis.
        assertTrue(!title.endsWith(" …"))
    }

    @Test
    fun aTitleExactlyAtTheLimitIsLeftAlone() {
        val exact = "x".repeat(OembedParser.MAX_TITLE_LENGTH)

        assertEquals(exact, OembedParser.cleanTitle(exact))
    }
}
