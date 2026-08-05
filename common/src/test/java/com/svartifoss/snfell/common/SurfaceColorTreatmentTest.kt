package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceColorTreatmentTest {
    @Test
    fun componentCanFollowEachGlobalTreatment() {
        assertEquals(
                SurfaceColorTreatment.NORMAL,
                SurfaceColorTreatment.FOLLOW.resolveAgainst(SurfaceColorTreatment.NORMAL))
        assertEquals(
                SurfaceColorTreatment.DESATURATED,
                SurfaceColorTreatment.FOLLOW.resolveAgainst(SurfaceColorTreatment.DESATURATED))
        assertEquals(
                SurfaceColorTreatment.EXPRESSIVE,
                SurfaceColorTreatment.FOLLOW.resolveAgainst(SurfaceColorTreatment.EXPRESSIVE))
    }

    @Test
    fun explicitComponentTreatmentWinsOverGlobal() {
        assertEquals(
                SurfaceColorTreatment.EXPRESSIVE,
                SurfaceColorTreatment.EXPRESSIVE.resolveAgainst(SurfaceColorTreatment.NORMAL))
    }

    @Test
    fun legacyColorModesRemainReadable() {
        assertEquals(
                SurfaceColorTreatment.NORMAL,
                SurfaceColorTreatment.fromPreference("custom"))
        assertEquals(
                SurfaceColorTreatment.NORMAL,
                SurfaceColorTreatment.fromPreference("neutral"))
        assertEquals(
                SurfaceColorTreatment.EXPRESSIVE,
                SurfaceColorTreatment.fromPreference("album"))
        assertEquals(
                SurfaceColorTreatment.DESATURATED,
                SurfaceColorTreatment.fromPreference("album", legacyDesaturated = true))
    }

    @Test
    fun harmonyTreatmentsParseFromTheirPreferenceValues() {
        // These strings are also in the phone's wear_color_treatment_values array; a mismatch
        // would silently render the fallback instead of the treatment the user picked.
        assertEquals(SurfaceColorTreatment.COMPLEMENTARY,
                SurfaceColorTreatment.fromPreference("complementary"))
        assertEquals(SurfaceColorTreatment.TRIADIC,
                SurfaceColorTreatment.fromPreference("triadic"))
        assertEquals(SurfaceColorTreatment.ANALOGOUS,
                SurfaceColorTreatment.fromPreference("analogous"))
        assertEquals(SurfaceColorTreatment.MONOCHROME,
                SurfaceColorTreatment.fromPreference("monochrome"))
        assertEquals(SurfaceColorTreatment.DUOTONE,
                SurfaceColorTreatment.fromPreference("duotone"))
    }

    @Test
    fun anOlderWatchDegradesAnUnknownTreatmentToItsDefault() {
        // A 3.0 watch paired to a 3.1 phone receives e.g. "triadic" and must land on the closest
        // album-derived look rather than dropping to the fixed Normal colour.
        assertEquals(
                SurfaceColorTreatment.EXPRESSIVE,
                SurfaceColorTreatment.fromPreference(
                        "some_future_treatment", default = SurfaceColorTreatment.EXPRESSIVE))
        assertEquals(
                SurfaceColorTreatment.FOLLOW,
                SurfaceColorTreatment.fromPreference("some_future_treatment"))
    }

    @Test
    fun harmonyTreatmentsAreAlbumDerived() {
        // isAlbumDerived gates palette extraction on the watch: a harmony marked otherwise would
        // leave the renderer with no album swatches to build the harmony from.
        for (treatment in listOf(
                SurfaceColorTreatment.EXPRESSIVE, SurfaceColorTreatment.DESATURATED,
                SurfaceColorTreatment.COMPLEMENTARY, SurfaceColorTreatment.TRIADIC,
                SurfaceColorTreatment.ANALOGOUS, SurfaceColorTreatment.MONOCHROME,
                SurfaceColorTreatment.DUOTONE)) {
            assertTrue("$treatment must be album-derived", treatment.isAlbumDerived)
        }
        assertFalse(SurfaceColorTreatment.NORMAL.isAlbumDerived)
        assertFalse(SurfaceColorTreatment.FOLLOW.isAlbumDerived)
    }

    @Test
    fun componentCanFollowAHarmonyGlobal() {
        assertEquals(
                SurfaceColorTreatment.TRIADIC,
                SurfaceColorTreatment.FOLLOW.resolveAgainst(SurfaceColorTreatment.TRIADIC))
    }

    @Test
    fun colorModifiersParseFromTheirPreferenceValues() {
        assertEquals(ColorModifier.NONE, ColorModifier.fromPreference("none"))
        assertEquals(ColorModifier.VIBRANT, ColorModifier.fromPreference("vibrant"))
        assertEquals(ColorModifier.PASTEL, ColorModifier.fromPreference("pastel"))
        assertEquals(ColorModifier.WARM, ColorModifier.fromPreference("warm"))
        assertEquals(ColorModifier.COOL, ColorModifier.fromPreference("cool"))
        // An unset or unknown value must be the identity, never a surprise recolour.
        assertEquals(ColorModifier.NONE, ColorModifier.fromPreference(null))
        assertEquals(ColorModifier.NONE, ColorModifier.fromPreference("future_modifier"))
    }

    @Test
    fun warmAndCoolBiasHueInOppositeDirections() {
        // A green source (120°) sits between the two anchors, so the bias direction is unambiguous.
        val source = 120f
        val warm = ColorModifier.biasHue(source, ColorModifier.WARM_ANCHOR)
        val cool = ColorModifier.biasHue(source, ColorModifier.COOL_ANCHOR)
        assertTrue("warm should pull toward amber", warm < source)
        assertTrue("cool should pull toward azure", cool > source)
        // A bias, not a recolour: the album's own hue still dominates.
        assertTrue(kotlin.math.abs(warm - source) < kotlin.math.abs(source - ColorModifier.WARM_ANCHOR))
    }

    @Test
    fun hueShiftIsExportableAndFaceScoped() {
        // Missing from either registry and the control silently never reaches the watch.
        assertTrue(MiscPreferences.EXPORTABLE.contains(MiscPreferences.WEAR_COLOR_HUE_SHIFT))
        assertTrue(FaceScopedPreferences.SCOPED_KEYS
                .contains(MiscPreferences.WEAR_COLOR_HUE_SHIFT.key))
        // Default must be the exact identity so existing installs render unchanged.
        assertEquals(0, MiscPreferences.WEAR_COLOR_HUE_SHIFT.defaultValue)
    }

    @Test
    fun biasHueTakesTheShortWayAroundTheWheel() {
        // 350° biased toward amber (35°) must cross 0°, not travel backwards through 180°.
        val biased = ColorModifier.biasHue(350f, ColorModifier.WARM_ANCHOR)
        assertTrue("expected to move forward past 0, was $biased", biased >= 350f || biased < 35f)
    }
}
