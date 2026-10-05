package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicLinkMetadataParserTest {
    @Test
    fun `Open Graph fields are read without relying on the page layout`() {
        val result = PublicLinkMetadataParser.parse("""
            <html><head>
              <meta property="og:title" content="Night drive" />
              <meta property="og:image" content="https://example.com/cover.jpg" />
              <meta property="og:description" content="Late-night electronic selection" />
              <meta name="author" content="Aurora Radio" />
            </head></html>
        """.trimIndent())

        assertEquals("Night drive", result?.title)
        assertEquals("https://example.com/cover.jpg", result?.thumbnailUrl)
        assertEquals("Aurora Radio", result?.creator)
        assertEquals("Late-night electronic selection", result?.description)
    }

    @Test
    fun `JSON LD fills metadata omitted by Open Graph`() {
        val result = PublicLinkMetadataParser.parse("""
            <meta property="og:title" content="Focus" />
            <script type="application/ld+json">
              {"@type":"MusicPlaylist","byArtist":{"name":"The Curator"},
               "description":"A playlist for work"}
            </script>
        """.trimIndent())

        assertEquals("Focus", result?.title)
        assertEquals("The Curator", result?.creator)
        assertEquals("A playlist for work", result?.description)
    }

    @Test
    fun `non web image addresses are not retained`() {
        val result = PublicLinkMetadataParser.parse(
                "<meta property=\"og:image\" content=\"javascript:alert(1)\" />")

        assertNull(result)
    }
}
