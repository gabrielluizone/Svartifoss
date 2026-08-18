package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the mini-button arrangements. Two sides read this - the watch lays out real Views, the
 * phone's preview draws a miniature - and they used to each carry their own `when` over the raw
 * preference string, so a value one of them had not learned rendered as something else entirely.
 */
class MiniButtonPlacementTest {

    @Test
    fun unknownAndMissingValuesFallBackToFlat() {
        // A value can arrive from an imported backup or a newer phone build. A straight row is the
        // only arrangement that is correct on every screen shape, so it is the safe landing.
        assertEquals(MiniButtonPlacement.FLAT, MiniButtonPlacement.fromPreference(null))
        assertEquals(MiniButtonPlacement.FLAT, MiniButtonPlacement.fromPreference(""))
        assertEquals(MiniButtonPlacement.FLAT, MiniButtonPlacement.fromPreference("orbit"))
        // The stored default must decode to itself, or every install silently changes arrangement.
        assertEquals(
                MiniButtonPlacement.FLAT,
                MiniButtonPlacement.fromPreference(
                        MiscPreferences.WEAR_SCREEN_BUTTONS_CURVE_STYLE.defaultValue))
    }

    @Test
    fun everyPickerValueDecodesToItsOwnCase() {
        // The picker pairs entries with entryValues by index; a value with no case here would show
        // in the list and then render as Flat.
        for (placement in MiniButtonPlacement.values()) {
            assertEquals(placement, MiniButtonPlacement.fromPreference(placement.value))
        }
    }

    @Test
    fun onlyFlatOptsOutOfTheCurve() {
        // followsCurve is what every caller uses to skip the round-screen geometry entirely.
        for (placement in MiniButtonPlacement.values()) {
            assertEquals(
                    "$placement",
                    placement != MiniButtonPlacement.FLAT,
                    placement.followsCurve)
        }
    }

    @Test
    fun railsAreExactlyTheWallArrangements() {
        assertTrue(MiniButtonPlacement.SIDE_LEFT.isRail)
        assertTrue(MiniButtonPlacement.SIDE_RIGHT.isRail)
        assertTrue(MiniButtonPlacement.SIDE_SPLIT.isRail)
        // Spread stays a bottom row - it widens, it does not move to a wall. Getting this wrong
        // would send it down the absolute-placement path and off the bottom of the screen.
        assertFalse(MiniButtonPlacement.SPREAD.isRail)
        assertFalse(MiniButtonPlacement.FLAT.isRail)
        assertFalse(MiniButtonPlacement.CURVED_EXTREME.isRail)
    }

    @Test
    fun extremeRaisesTheAngleAndNotTheClearance() {
        // Multiplying both lifted the outer pills off the glass instead of seating them on it.
        assertEquals(1.0f, MiniButtonPlacement.CURVED_EXTREME.riseScale, 1e-4f)
        assertTrue(MiniButtonPlacement.CURVED_EXTREME.tiltFraction >
                MiniButtonPlacement.CURVED.tiltFraction)
        assertTrue(MiniButtonPlacement.CURVED_EXTREME.maxRotationDegrees >
                MiniButtonPlacement.CURVED.maxRotationDegrees)
        assertTrue(MiniButtonPlacement.CURVED_EXTREME.maxRiseDp >
                MiniButtonPlacement.CURVED.maxRiseDp)
    }

    @Test
    fun curveStrengthIncreasesMonotonically() {
        // The picker lists these as a progression, so the tilt has to actually be one.
        val ladder = listOf(
                MiniButtonPlacement.ARC,
                MiniButtonPlacement.CURVED_GENTLE,
                MiniButtonPlacement.CURVED_SOFT,
                MiniButtonPlacement.CURVED_MEDIUM,
                MiniButtonPlacement.CURVED,
                MiniButtonPlacement.CURVED_EXTREME)
        for (i in 1 until ladder.size) {
            assertTrue(
                    "${ladder[i]} should tilt more than ${ladder[i - 1]}",
                    ladder[i].tiltFraction > ladder[i - 1].tiltFraction)
        }
        // Flat is the only one with no rise at all; arc raises without tilting.
        assertEquals(0f, MiniButtonPlacement.FLAT.riseScale, 1e-4f)
        assertEquals(0f, MiniButtonPlacement.ARC.tiltFraction, 1e-4f)
        assertTrue(MiniButtonPlacement.ARC.riseScale > 0f)
    }

    @Test
    fun splitPutsOneOnTheLeftAndTheRestOnTheRight() {
        // An even split would put two pills on the left of a three-button row, which reads as a
        // mistake rather than as a choice.
        assertTrue(MiniButtonPlacement.splitSideIsLeft(0))
        assertFalse(MiniButtonPlacement.splitSideIsLeft(1))
        assertFalse(MiniButtonPlacement.splitSideIsLeft(2))
    }

    @Test
    fun theCurveStyleIsExportableAndFaceScoped() {
        // Missing from either registry and the arrangement never reaches the watch, or changes
        // every face at once.
        assertTrue(MiscPreferences.EXPORTABLE
                .contains(MiscPreferences.WEAR_SCREEN_BUTTONS_CURVE_STYLE))
        assertTrue(FaceScopedPreferences.SCOPED_KEYS
                .contains(MiscPreferences.WEAR_SCREEN_BUTTONS_CURVE_STYLE.key))
    }
}
