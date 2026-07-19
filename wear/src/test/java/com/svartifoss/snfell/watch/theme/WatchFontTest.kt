package com.svartifoss.snfell.watch.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Pins the MiscPreferences.WEAR_FONT key -> FontFamily mapping every layout (classic, expressive,
 *  curated) resolves title/artist text through, so a picker value never silently falls through to
 *  the wrong family. */
class WatchFontTest {
    @Test
    fun everyPickerValueMapsToADistinctFamily() {
        assertEquals(GoogleSansFamily, watchFontFamily("google_sans"))
        assertEquals(MomsTypewriterFamily, watchFontFamily("typewriter"))
        assertEquals(LoveLetterTypewriterFamily, watchFontFamily("love_letter"))

        // "roboto"/"serif"/"monospace"/"cursive" resolve to the platform's built-in families -
        // just assert they differ from the app's own bundled families and from each other's
        // closest neighbour, since the exact FontFamily instances are opaque platform singletons.
        assertNotEquals(GoogleSansFamily, watchFontFamily("roboto"))
        assertNotEquals(GoogleSansFamily, watchFontFamily("serif"))
        assertNotEquals(GoogleSansFamily, watchFontFamily("monospace"))
        assertNotEquals(GoogleSansFamily, watchFontFamily("cursive"))
        assertNotEquals(watchFontFamily("serif"), watchFontFamily("monospace"))
    }

    @Test
    fun unknownOrMissingValueFallsBackToGoogleSans() {
        assertEquals(GoogleSansFamily, watchFontFamily(null))
        assertEquals(GoogleSansFamily, watchFontFamily("nonsense"))
    }
}
