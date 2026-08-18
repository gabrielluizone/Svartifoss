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
    fun rotatingHarmoniesTurnTheirPrimaryByTheirOwnSignatureAngle() {
        // The bug this pins: with the primary anchored to the album hue, every harmony rendered
        // identically to Expressive on any surface that paints only the primary - which is most of
        // them, and all of the Note face. Each rotating harmony must turn by the same angle that
        // names it, or the accent stops matching the treatment the user picked.
        assertEquals(ColorHarmony.COMPLEMENTARY_ROTATION,
                SurfaceColorTreatment.COMPLEMENTARY.primaryRotationDegrees, 1e-4f)
        assertEquals(ColorHarmony.TRIADIC_SECOND_ROTATION,
                SurfaceColorTreatment.TRIADIC.primaryRotationDegrees, 1e-4f)
        assertEquals(ColorHarmony.ANALOGOUS_ROTATION,
                SurfaceColorTreatment.ANALOGOUS.primaryRotationDegrees, 1e-4f)
    }

    @Test
    fun treatmentsThatMustNotInventAHueDoNotRotate() {
        // Monochrome is every harmony's near-neutral fallback, and Duotone's slots are two colours
        // that genuinely appear in the cover - rotating either substitutes a hue the artwork never
        // had. Expressive/Desaturated report the album's own colours, and Normal/Follow are not
        // album-derived at all, so none of them may drift either.
        for (treatment in listOf(
                SurfaceColorTreatment.MONOCHROME, SurfaceColorTreatment.DUOTONE,
                SurfaceColorTreatment.EXPRESSIVE, SurfaceColorTreatment.DESATURATED,
                SurfaceColorTreatment.NORMAL, SurfaceColorTreatment.FOLLOW)) {
            assertEquals("$treatment must not rotate its primary",
                    0f, treatment.primaryRotationDegrees, 1e-4f)
        }
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




    @Test
    fun turningThePaletteOffKeepsThePrimaryAndDropsTheCompanions() {
        val triad = ColorHarmony.Triad(0x112233, 0x445566, 0x778899)
        val single = SurfacePaletteResolver.flatten(triad, multiColor = false)
        // The primary is the colour the treatment arrived at; collapsing must not substitute a
        // companion, which would look like the theme changing rather than simplifying.
        assertEquals(triad.primary, single.primary)
        assertEquals(triad.primary, single.secondary)
        assertEquals(triad.primary, single.tertiary)
    }

    @Test
    fun aPaletteIsLeftExactlyAsDerived() {
        val triad = ColorHarmony.Triad(0x112233, 0x445566, 0x778899)
        assertEquals(triad, SurfacePaletteResolver.flatten(triad, multiColor = true))
    }

    @Test
    fun colourPaletteSwitchIsExportableAndFaceScoped() {
        // Missing from either registry and the control silently never reaches the watch, or
        // changes every face at once.
        assertTrue(MiscPreferences.EXPORTABLE.contains(MiscPreferences.WEAR_NORMAL_COLOR_MULTI))
        assertTrue(FaceScopedPreferences.SCOPED_KEYS
                .contains(MiscPreferences.WEAR_NORMAL_COLOR_MULTI.key))
        // A palette is what every existing install already renders.
        assertEquals(true, MiscPreferences.WEAR_NORMAL_COLOR_MULTI.defaultValue)
    }
}
