package com.svartifoss.snfell.watch.theme

import android.content.SharedPreferences
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.svartifoss.snfell.common.WatchTypography
import com.svartifoss.snfell.watch.view.face.NowPlayingFaceState
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Pins the MiscPreferences.WEAR_FONT key -> FontFamily mapping every layout (classic, expressive,
 *  curated) resolves title/artist text through, so a picker value never silently falls through to
 *  the wrong family. */
class WatchFontTest {
    @Test
    fun queueTrackFontMatchesPlayerWithoutAddingSyntheticBold() {
        val styles = watchTrackTextStyles(preferences(mapOf(
                "wear_screen_face" to "expressive",
                "wear_font_all_screens@expressive" to true,
                "wear_font@expressive" to "google_sans_flex",
                "wear_font_flex_roundness@expressive" to 100,
                "wear_title_font_weight@expressive" to 515,
                "wear_artist_font_weight@expressive" to 450)))
        val player = NowPlayingFaceState(
                fontKey = "google_sans_flex",
                flexAxes = WatchTypography.IDENTITY_FLEX_AXES.copy(roundness = 100f),
                titleTypography = WatchTypography.IDENTITY_TEXT.copy(weight = 515),
                artistTypography = WatchTypography.IDENTITY_TEXT.copy(weight = 450))
        assertEquals(player.titleFont, styles.title.fontFamily)
        assertEquals(player.titleFontWeight, styles.title.fontWeight)
        assertEquals(player.artistFont, styles.artist.fontFamily)
        assertEquals(player.artistFontWeight, styles.artist.fontWeight)
        assertNotEquals(FontWeight.Bold, styles.title.fontWeight)
    }

    @Test
    fun queueTrackFontsRespectIndependentElementOverrides() {
        val styles = watchTrackTextStyles(preferences(mapOf(
                "wear_screen_face" to "expressive",
                "wear_font_all_screens@expressive" to true,
                "wear_font@expressive" to "inter",
                "wear_title_font@expressive" to "google_sans_flex",
                "wear_title_font_flex_roundness@expressive" to 100,
                "wear_title_font_weight@expressive" to 515,
                "wear_title_font_italic@expressive" to true,
                "wear_artist_font@expressive" to "lora")))
        val player = NowPlayingFaceState(
                fontKey = "inter",
                titleFontKey = "google_sans_flex",
                titleFlexAxes = WatchTypography.IDENTITY_FLEX_AXES.copy(roundness = 100f),
                titleTypography = WatchTypography.IDENTITY_TEXT.copy(weight = 515, italic = true),
                artistFontKey = "lora")
        assertEquals(player.titleFont, styles.title.fontFamily)
        assertEquals(FontStyle.Italic, styles.title.fontStyle)
        assertEquals(player.artistFont, styles.artist.fontFamily)
    }

    @Test
    fun queueTrackFontsKeepDefaultsWhenFontEverywhereIsOff() {
        assertEquals(WatchTrackTextStyles(), watchTrackTextStyles(preferences(mapOf(
                "wear_screen_face" to "expressive",
                "wear_font_all_screens@expressive" to false,
                "wear_title_font@expressive" to "google_sans_flex",
                "wear_title_font_weight@expressive" to 515))))
    }

    @Test
    fun uiFontPreservesTheActiveThemesFlexAxes() {
        val base = mapOf<String, Any>(
                "wear_screen_face" to "expressive",
                "wear_font_all_screens@expressive" to true,
                "wear_font@expressive" to "google_sans_flex")
        val defaultAxes = watchUiFontFamily(preferences(base))
        val rounded = watchUiFontFamily(preferences(
                base + ("wear_font_flex_roundness@expressive" to 100)))
        val wider = watchUiFontFamily(preferences(
                base + ("wear_font_flex_width@expressive" to 125)))

        assertNotEquals(defaultAxes, rounded)
        assertNotEquals(defaultAxes, wider)
        // An inactive theme's axes must not leak into the queue of the current theme.
        assertEquals(defaultAxes, watchUiFontFamily(preferences(
                base + ("wear_font_flex_roundness@classic" to 100))))
    }

    @Test
    fun uiFontKeepsGoogleSansWhenUseFontEverywhereIsOff() {
        assertEquals(GoogleSansFamily, watchUiFontFamily(preferences(mapOf(
                "wear_screen_face" to "expressive",
                "wear_font_all_screens@expressive" to false,
                "wear_font@expressive" to "google_sans_flex",
                "wear_font_flex_roundness@expressive" to 100))))
    }

    @Test
    fun uiFontKeepsStaticFamiliesWhenFlexAxesAreConfigured() {
        assertEquals(InterFamily, watchUiFontFamily(preferences(mapOf(
                "wear_screen_face" to "expressive",
                "wear_font_all_screens@expressive" to true,
                "wear_font@expressive" to "inter",
                "wear_font_flex_roundness@expressive" to 100))))
    }

    private fun preferences(values: Map<String, Any>): SharedPreferences =
            Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
                    arrayOf(SharedPreferences::class.java)) { _, method, args ->
                when (method.name) {
                    "getAll" -> values
                    "contains" -> values.containsKey(args!![0])
                    "getString", "getBoolean", "getInt" -> values[args!![0]] ?: args[1]
                    else -> error("Unexpected preference call: ${method.name}")
                }
            } as SharedPreferences

    @Test
    fun everyPickerValueMapsToADistinctFamily() {
        assertEquals(GoogleSansFamily, watchFontFamily("google_sans"))
        // "typewriter" lost its bundled font (Mom's Typewriter, no redistribution license this
        // project ever held) and is now a retired alias to the same family "love_letter" uses.
        assertEquals(SpecialEliteFamily, watchFontFamily("typewriter"))
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
