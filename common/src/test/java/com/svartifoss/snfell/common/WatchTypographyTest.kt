package com.svartifoss.snfell.common

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
        assertEquals(400, MiscPreferences.WEAR_TITLE_FONT_WEIGHT.defaultValue)
        assertEquals(400, MiscPreferences.WEAR_ARTIST_FONT_WEIGHT.defaultValue)
        assertEquals(100, MiscPreferences.WEAR_TITLE_FONT_SCALE.defaultValue)
        assertEquals(100, MiscPreferences.WEAR_TITLE_FONT_OPACITY.defaultValue)
        assertEquals(0, MiscPreferences.WEAR_TITLE_FONT_TRACKING.defaultValue)
        assertEquals(100, MiscPreferences.WEAR_SOURCE_ICON_SCALE.defaultValue)
        assertEquals(100, MiscPreferences.WEAR_SOURCE_ICON_OPACITY.defaultValue)
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
                MiscPreferences.WEAR_TITLE_FONT_WEIGHT, MiscPreferences.WEAR_TITLE_FONT_ITALIC,
                MiscPreferences.WEAR_TITLE_FONT_SCALE, MiscPreferences.WEAR_TITLE_FONT_OPACITY,
                MiscPreferences.WEAR_TITLE_FONT_TRACKING,
                MiscPreferences.WEAR_ARTIST_FONT_WEIGHT, MiscPreferences.WEAR_ARTIST_FONT_ITALIC,
                MiscPreferences.WEAR_ARTIST_FONT_SCALE, MiscPreferences.WEAR_ARTIST_FONT_OPACITY,
                MiscPreferences.WEAR_ARTIST_FONT_TRACKING,
                MiscPreferences.WEAR_SOURCE_ICON_SCALE, MiscPreferences.WEAR_SOURCE_ICON_OPACITY,
                MiscPreferences.WEAR_COLOR_MODIFIER,
                MiscPreferences.WEAR_FONT_FLEX_WIDTH, MiscPreferences.WEAR_FONT_FLEX_OPTICAL_SIZE,
                MiscPreferences.WEAR_FONT_FLEX_GRADE, MiscPreferences.WEAR_FONT_FLEX_ROUNDNESS)
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
    fun flexVariationSettingsCarriesTheSharedAxes() {
        val axes = WatchTypography.FlexAxes(width = 80f, opticalSize = 96f, grade = 25f, roundness = 70f)
        val settings = WatchTypography.flexVariationSettings(WatchTypography.IDENTITY_TEXT, axes)
        assertTrue(settings.contains("'wdth' 80.0"))
        assertTrue(settings.contains("'opsz' 96.0"))
        assertTrue(settings.contains("'GRAD' 25.0"))
        assertTrue(settings.contains("'ROND' 70.0"))
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
}
