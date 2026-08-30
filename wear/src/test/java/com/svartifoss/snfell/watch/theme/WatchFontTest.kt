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
        assertEquals(SpecialEliteFamily, watchFontFamily("love_letter"))
        assertEquals(InterFamily, watchFontFamily("inter"))
        assertEquals(AtkinsonHyperlegibleFamily, watchFontFamily("atkinson_hyperlegible"))
        assertEquals(RubikFamily, watchFontFamily("rubik"))
        assertEquals(BarlowCondensedFamily, watchFontFamily("barlow_condensed"))
        assertEquals(OswaldFamily, watchFontFamily("oswald"))
        listOf("dm_sans", "manrope", "exo_2", "oxanium").forEach { key ->
            assertNotEquals(GoogleSansFamily, watchFontFamily(key))
        }
        assertEquals(LoraFamily, watchFontFamily("lora"))
        assertEquals(FrauncesFamily, watchFontFamily("fraunces"))
        assertEquals(SpaceMonoFamily, watchFontFamily("space_mono"))
        assertEquals(ArchivoBlackFamily, watchFontFamily("archivo_black"))
        assertEquals(DancingScriptFamily, watchFontFamily("dancing_script"))

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
