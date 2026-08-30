package com.svartifoss.snfell.common

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the normalisation applied to every per-element typography value. These are the guards that
 * stop a value from another build (or a hand-edited backup) reaching a renderer as something a
 * font family cannot select or as text nobody can see.
 */
class WatchTypographyTest {

    @Test
    fun defaultsAreTheExactIdentity() {
        // An install that never opens these controls must render as it did before they existed.
        assertTrue(WatchTypography.IDENTITY_TEXT.isIdentity)
        assertTrue(WatchTypography.IDENTITY_ICON.isIdentity)
        assertEquals(WatchTypography.TITLE_FONT_FOLLOW,
                MiscPreferences.WEAR_TITLE_FONT.defaultValue)
        assertEquals(WatchTypography.ARTIST_FONT_FOLLOW,
                MiscPreferences.WEAR_ARTIST_FONT.defaultValue)
        assertEquals(400, MiscPreferences.WEAR_TITLE_FONT_WEIGHT.defaultValue)
        assertEquals(400, MiscPreferences.WEAR_ARTIST_FONT_WEIGHT.defaultValue)
        assertEquals(100, MiscPreferences.WEAR_TITLE_FONT_SCALE.defaultValue)
        assertEquals(100, MiscPreferences.WEAR_TITLE_FONT_OPACITY.defaultValue)
        assertEquals(0, MiscPreferences.WEAR_TITLE_FONT_TRACKING.defaultValue)
        assertEquals(100, MiscPreferences.WEAR_SOURCE_ICON_SCALE.defaultValue)
        assertEquals(100, MiscPreferences.WEAR_SOURCE_ICON_OPACITY.defaultValue)
        assertEquals(WatchTypography.TRACK_TIME_FONT_FOLLOW,
                MiscPreferences.WEAR_TRACK_TIME_FONT.defaultValue)
        assertEquals(400, MiscPreferences.WEAR_TRACK_TIME_FONT_WEIGHT.defaultValue)
        assertEquals(100, MiscPreferences.WEAR_TRACK_TIME_FONT_SCALE.defaultValue)
        assertEquals(100, MiscPreferences.WEAR_TRACK_TIME_FONT_OPACITY.defaultValue)
        assertEquals(0, MiscPreferences.WEAR_TRACK_TIME_FONT_TRACKING.defaultValue)
    }

    @Test
    fun weightIsContinuousAcrossTheFullOpenTypeRange() {
        // Not stepped to the nine CSS keyword weights - both Typeface.create's synthetic weight
        // matching and Google Sans Flex's own wght axis accept any value in 1..1000.
        assertEquals(400, WatchTypography.normalizeWeight(400))
        assertEquals(470, WatchTypography.normalizeWeight(470))
        assertEquals(749, WatchTypography.normalizeWeight(749))
        // Off-scale values from a stale sync must still land in the range a Typeface can select.
        assertEquals(1, WatchTypography.normalizeWeight(0))
        assertEquals(1000, WatchTypography.normalizeWeight(4000))
        assertEquals(1, WatchTypography.normalizeWeight(-200))
    }

    @Test
    fun scaleClampsToTheBandTheFaceLayoutsCanAbsorb() {
        assertEquals(1f, WatchTypography.normalizeScale(100), 1e-4f)
        assertEquals(
                MiscPreferences.TYPOGRAPHY_MIN_SCALE / 100f,
                WatchTypography.normalizeScale(0), 1e-4f)
        assertEquals(
                MiscPreferences.TYPOGRAPHY_MAX_SCALE / 100f,
                WatchTypography.normalizeScale(500), 1e-4f)
    }

    @Test
    fun opacityNeverReachesFullyInvisible() {
        // Text configured to alpha 0 reads as a rendering bug, not as a setting.
        assertEquals(
                MiscPreferences.TYPOGRAPHY_MIN_OPACITY / 100f,
                WatchTypography.normalizeOpacity(0), 1e-4f)
        assertTrue(WatchTypography.normalizeOpacity(0) > 0f)
        assertEquals(1f, WatchTypography.normalizeOpacity(100), 1e-4f)
        assertEquals(1f, WatchTypography.normalizeOpacity(400), 1e-4f)
    }

    @Test
    fun trackingSupportsTighteningAndLoosening() {
        assertEquals(0f, WatchTypography.normalizeTracking(0), 1e-4f)
        assertEquals(0.1f, WatchTypography.normalizeTracking(10), 1e-4f)
        assertEquals(
                MiscPreferences.TYPOGRAPHY_MIN_TRACKING / 100f,
                WatchTypography.normalizeTracking(-999), 1e-4f)
        assertTrue(WatchTypography.normalizeTracking(-999) < 0f)
    }

    @Test
    fun specAppliesScaleAndAlphaToRendererInputs() {
        val spec = WatchTypography.TextSpec(700, true, 1.25f, 0.5f, 0.05f)
        assertFalse(spec.isIdentity)
        assertEquals(20f, spec.scaled(16f), 1e-4f)
        assertEquals(127, spec.applyAlpha(255))
        assertEquals(0, spec.applyAlpha(0))
    }

    @Test
    fun alphaApplicationStaysInChannelRange() {
        val opaque = WatchTypography.TextSpec(400, false, 1f, 1f, 0f)
        assertEquals(255, opaque.applyAlpha(255))
        assertEquals(255, opaque.applyAlpha(400))
        assertEquals(0, opaque.applyAlpha(-5))
    }

    @Test
    fun everyTypographyKeyIsExportableAndFaceScoped() {
        // A key missing from either registry silently fails to reach the watch, or leaks across
        // faces - both are invisible until a user reports the wrong font on one face.
        val keys = listOf(
                MiscPreferences.WEAR_TITLE_FONT,
                MiscPreferences.WEAR_TITLE_FONT_WEIGHT, MiscPreferences.WEAR_TITLE_FONT_ITALIC,
                MiscPreferences.WEAR_TITLE_FONT_SCALE, MiscPreferences.WEAR_TITLE_FONT_OPACITY,
                MiscPreferences.WEAR_TITLE_FONT_TRACKING,
                MiscPreferences.WEAR_ARTIST_FONT,
                MiscPreferences.WEAR_ARTIST_FONT_WEIGHT, MiscPreferences.WEAR_ARTIST_FONT_ITALIC,
                MiscPreferences.WEAR_ARTIST_FONT_SCALE, MiscPreferences.WEAR_ARTIST_FONT_OPACITY,
                MiscPreferences.WEAR_ARTIST_FONT_TRACKING,
                MiscPreferences.WEAR_TRACK_TIME_FONT,
                MiscPreferences.WEAR_TRACK_TIME_FONT_WEIGHT,
                MiscPreferences.WEAR_TRACK_TIME_FONT_ITALIC,
                MiscPreferences.WEAR_TRACK_TIME_FONT_SCALE,
                MiscPreferences.WEAR_TRACK_TIME_FONT_OPACITY,
                MiscPreferences.WEAR_TRACK_TIME_FONT_TRACKING,
                MiscPreferences.WEAR_SOURCE_ICON_SCALE, MiscPreferences.WEAR_SOURCE_ICON_OPACITY,
                MiscPreferences.WEAR_COLOR_MODIFIER,
                MiscPreferences.WEAR_FONT_FLEX_WIDTH, MiscPreferences.WEAR_FONT_FLEX_OPTICAL_SIZE,
                MiscPreferences.WEAR_FONT_FLEX_GRADE, MiscPreferences.WEAR_FONT_FLEX_ROUNDNESS,
                MiscPreferences.WEAR_TITLE_FONT_FLEX_WIDTH,
                MiscPreferences.WEAR_TITLE_FONT_FLEX_OPTICAL_SIZE,
                MiscPreferences.WEAR_TITLE_FONT_FLEX_GRADE,
                MiscPreferences.WEAR_TITLE_FONT_FLEX_ROUNDNESS,
                MiscPreferences.WEAR_ARTIST_FONT_FLEX_WIDTH,
                MiscPreferences.WEAR_ARTIST_FONT_FLEX_OPTICAL_SIZE,
                MiscPreferences.WEAR_ARTIST_FONT_FLEX_GRADE,
                MiscPreferences.WEAR_ARTIST_FONT_FLEX_ROUNDNESS,
                MiscPreferences.WEAR_CLOCK_FONT_FLEX_WIDTH,
                MiscPreferences.WEAR_CLOCK_FONT_FLEX_OPTICAL_SIZE,
                MiscPreferences.WEAR_CLOCK_FONT_FLEX_GRADE,
                MiscPreferences.WEAR_CLOCK_FONT_FLEX_ROUNDNESS,
                MiscPreferences.WEAR_LYRICS_FONT_FLEX_WIDTH,
                MiscPreferences.WEAR_LYRICS_FONT_FLEX_OPTICAL_SIZE,
                MiscPreferences.WEAR_LYRICS_FONT_FLEX_GRADE,
                MiscPreferences.WEAR_LYRICS_FONT_FLEX_ROUNDNESS,
                MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_WIDTH,
                MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_OPTICAL_SIZE,
                MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_GRADE,
                MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_ROUNDNESS)
        for (definition in keys) {
            assertTrue("${definition.key} must be exportable to reach the watch",
                    MiscPreferences.EXPORTABLE.contains(definition))
            assertTrue("${definition.key} must be face-scoped",
                    FaceScopedPreferences.SCOPED_KEYS.contains(definition.key))
        }
    }

    @Test
    fun flexFontKeyIsRecognisedAndNothingElseIs() {
        assertTrue(WatchTypography.isFlexFont("google_sans_flex"))
        assertFalse(WatchTypography.isFlexFont("google_sans"))
        assertFalse(WatchTypography.isFlexFont(null))
    }

    @Test
    fun flexAxesDefaultsMatchTheBundledFontsFvarTable() {
        // Pinned to fonttools' dump of the actual bundled google_sans_flex.ttf, not guessed -
        // drifting from the real font's defaults would make "untouched" render differently from
        // what the font itself considers its own regular instance.
        assertTrue(WatchTypography.IDENTITY_FLEX_AXES.isIdentity)
        assertEquals(100f, WatchTypography.FLEX_WIDTH_DEFAULT)
        assertEquals(18f, WatchTypography.FLEX_OPTICAL_SIZE_DEFAULT)
        assertEquals(0f, WatchTypography.FLEX_GRADE_DEFAULT)
        assertEquals(0f, WatchTypography.FLEX_ROUNDNESS_DEFAULT)
    }

    @Test
    fun flexAxesIsIdentityOnlyAtTheDefaultPoint() {
        assertTrue(WatchTypography.FlexAxes(100f, 18f, 0f, 0f).isIdentity)
        assertFalse(WatchTypography.FlexAxes(120f, 18f, 0f, 0f).isIdentity)
        assertFalse(WatchTypography.FlexAxes(100f, 24f, 0f, 0f).isIdentity)
        assertFalse(WatchTypography.FlexAxes(100f, 18f, 40f, 0f).isIdentity)
        assertFalse(WatchTypography.FlexAxes(100f, 18f, 0f, 60f).isIdentity)
    }

    @Test
    fun flexVariationSettingsCarriesEachElementsOwnWeightAndSlant() {
        val upright = WatchTypography.TextSpec(650, false, 1f, 1f, 0f)
        val settings = WatchTypography.flexVariationSettings(upright, WatchTypography.IDENTITY_FLEX_AXES)
        assertTrue(settings.contains("'wght' 650"))
        assertTrue(settings.contains("'slnt' ${WatchTypography.FLEX_SLANT_UPRIGHT}"))

        val italic = WatchTypography.TextSpec(400, true, 1f, 1f, 0f)
        val italicSettings =
                WatchTypography.flexVariationSettings(italic, WatchTypography.IDENTITY_FLEX_AXES)
        assertTrue(italicSettings.contains("'slnt' ${WatchTypography.FLEX_SLANT_ITALIC}"))
    }

    @Test
    fun flexVariationSettingsCarriesTheSelectedAxes() {
        val axes = WatchTypography.FlexAxes(width = 80f, opticalSize = 96f, grade = 25f, roundness = 70f)
        val settings = WatchTypography.flexVariationSettings(WatchTypography.IDENTITY_TEXT, axes)
        assertTrue(settings.contains("'wdth' 80.0"))
        assertTrue(settings.contains("'opsz' 96.0"))
        assertTrue(settings.contains("'GRAD' 25.0"))
        assertTrue(settings.contains("'ROND' 70.0"))
    }

    @Test
    fun explicitElementsReadTheirOwnScopedFlexAxes() {
        val prefs = MapPreferences(mapOf(
                "wear_font_flex_width@classic" to "80",
                "wear_font_flex_optical_size@classic" to "96",
                "wear_font_flex_grade@classic" to "20",
                "wear_font_flex_roundness@classic" to "30",
                "wear_title_font_flex_width@classic" to "75",
                "wear_title_font_flex_optical_size@classic" to "36",
                "wear_title_font_flex_grade@classic" to "15",
                "wear_title_font_flex_roundness@classic" to "45",
                "wear_artist_font_flex_width@classic" to "110",
                "wear_artist_font_flex_optical_size@classic" to "120",
                "wear_artist_font_flex_grade@classic" to "55",
                "wear_artist_font_flex_roundness@classic" to "20",
                "wear_clock_font_flex_width@classic" to "120",
                "wear_clock_font_flex_optical_size@classic" to "48",
                "wear_clock_font_flex_grade@classic" to "40",
                "wear_clock_font_flex_roundness@classic" to "60",
                "wear_lyrics_font_flex_width@classic" to "60",
                "wear_lyrics_font_flex_optical_size@classic" to "72",
                "wear_lyrics_font_flex_grade@classic" to "80",
                "wear_lyrics_font_flex_roundness@classic" to "10",
                "wear_track_time_font_flex_width@classic" to "140",
                "wear_track_time_font_flex_optical_size@classic" to "24",
                "wear_track_time_font_flex_grade@classic" to "10",
                "wear_track_time_font_flex_roundness@classic" to "90"))
        val context = AppearanceContext.BuiltIn("classic")

        assertEquals(
                WatchTypography.FlexAxes(80f, 96f, 20f, 30f),
                WatchTypography.flexAxes(prefs, context))
        assertEquals(
                WatchTypography.FlexAxes(75f, 36f, 15f, 45f),
                WatchTypography.flexAxes(prefs, context, WatchTypography.FlexAxesTarget.TITLE))
        assertEquals(
                WatchTypography.FlexAxes(110f, 120f, 55f, 20f),
                WatchTypography.flexAxes(prefs, context, WatchTypography.FlexAxesTarget.ARTIST))
        assertEquals(
                WatchTypography.FlexAxes(120f, 48f, 40f, 60f),
                WatchTypography.flexAxes(prefs, context, WatchTypography.FlexAxesTarget.CLOCK))
        assertEquals(
                WatchTypography.FlexAxes(60f, 72f, 80f, 10f),
                WatchTypography.flexAxes(prefs, context, WatchTypography.FlexAxesTarget.LYRICS))
        assertEquals(
                WatchTypography.FlexAxes(140f, 24f, 10f, 90f),
                WatchTypography.flexAxes(
                        prefs, context, WatchTypography.FlexAxesTarget.TRACK_TIME))
    }

    @Test
    fun flexVariationSettingsClampsAnOutOfRangeWeightIntoTheAxisTheFontDefines() {
        // A weight from a stale/foreign sync must not reach the platform parser as a value the
        // font's fvar table does not define.
        val tooHeavy = WatchTypography.TextSpec(5000, false, 1f, 1f, 0f)
        val settings =
                WatchTypography.flexVariationSettings(tooHeavy, WatchTypography.IDENTITY_FLEX_AXES)
        assertTrue(settings.contains("'wght' ${WatchTypography.FLEX_WEIGHT_MAX}"))
    }

    @Test
    fun clockFontFollowsTheTrackFontUntilOneIsPicked() {
        assertEquals("orbitron", WatchTypography.clockFontKey("follow", "orbitron"))
        assertEquals("orbitron", WatchTypography.clockFontKey(null, "orbitron"))
        assertEquals("orbitron", WatchTypography.clockFontKey("", "orbitron"))
    }

    @Test
    fun anExplicitClockFontWinsOverTheTrackFont() {
        assertEquals("google_sans", WatchTypography.clockFontKey("google_sans", "caveat"))
    }

    /**
     * An unknown key resolves to itself rather than to a hardcoded family: the downstream catalogs
     * already fall back to Google Sans for anything they do not recognise, and swallowing it here
     * would hide a newer build's font behind a silent substitution.
     */
    @Test
    fun anUnknownClockFontIsPassedThroughForTheCatalogToResolve() {
        assertEquals("font_from_a_newer_build",
                WatchTypography.clockFontKey("font_from_a_newer_build", "caveat"))
    }

    @Test
    fun titleAndArtistFontsFollowTheGlobalFamilyUntilPicked() {
        listOf("google_sans", "orbitron", null).forEach { globalFont ->
            assertEquals(globalFont, WatchTypography.titleFontKey(
                    WatchTypography.TITLE_FONT_FOLLOW, globalFont))
            assertEquals(globalFont, WatchTypography.artistFontKey(
                    WatchTypography.ARTIST_FONT_FOLLOW, globalFont))
        }
        assertEquals("caveat", WatchTypography.titleFontKey(null, "caveat"))
        assertEquals("caveat", WatchTypography.artistFontKey("   ", "caveat"))
    }

    @Test
    fun explicitTitleAndArtistFontsWinOverTheGlobalFamily() {
        assertEquals("bebas_neue", WatchTypography.titleFontKey("bebas_neue", "caveat"))
        assertEquals("marcellus", WatchTypography.artistFontKey("marcellus", "orbitron"))
    }

    // ---- lyricsFontKey ----------------------------------------------------

    /**
     * The regression this pins, which shipped once and was reported as "the control does nothing":
     * "follow" used to mean *the font this surface was designed with* - Marcellus on Verse, the UI
     * family on the lyrics screen. That preserved every existing theme perfectly and, in doing so,
     * preserved the complaint, because changing the theme's typeface left the words of the song as
     * the one element that did not follow.
     */
    @Test
    fun `follow means the theme's own typeface`() {
        assertEquals("orbitron", WatchTypography.lyricsFontKey(
                WatchTypography.LYRICS_FONT_FOLLOW, trackFontKey = "orbitron"))
    }

    @Test
    fun `an explicit choice overrides the track typeface`() {
        assertEquals("marcellus", WatchTypography.lyricsFontKey(
                "marcellus", trackFontKey = "orbitron"))
    }

    /** A value can arrive from an imported backup or a newer build; falling back to the user's own
     *  chosen face beats reverting to something they never picked. */
    @Test
    fun `a blank or missing choice falls back to the track typeface`() {
        assertEquals("orbitron", WatchTypography.lyricsFontKey(null, "orbitron"))
        assertEquals("orbitron", WatchTypography.lyricsFontKey("", "orbitron"))
        assertEquals("orbitron", WatchTypography.lyricsFontKey("   ", "orbitron"))
    }

    /** The clock and the lyrics resolve "follow" the same way, deliberately: one word, one meaning
     *  across the app. */
    @Test
    fun `lyrics and clock follow resolve identically`() {
        listOf("google_sans", "orbitron", null).forEach { track ->
            assertEquals(
                    WatchTypography.clockFontKey(WatchTypography.CLOCK_FONT_FOLLOW, track),
                    WatchTypography.lyricsFontKey(WatchTypography.LYRICS_FONT_FOLLOW, track))
        }
    }

    @Test
    fun `track-time follow keeps the face design until a font is picked`() {
        assertEquals(null, WatchTypography.trackTimeFontKey(WatchTypography.TRACK_TIME_FONT_FOLLOW))
        assertEquals(null, WatchTypography.trackTimeFontKey(null))
        assertEquals(null, WatchTypography.trackTimeFontKey("   "))
        assertEquals("google_sans_flex",
                WatchTypography.trackTimeFontKey("google_sans_flex"))
    }

    private class MapPreferences(values: Map<String, Any>) : SharedPreferences {
        private val data = values.toMap()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun getString(key: String?, defValue: String?): String? =
                (data[key] as? String) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
                (data[key] as? Boolean) ?: defValue
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
                (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun registerOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
}
