package com.svartifoss.snfell.watch.view

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the pref-string → enum mappings the phone's style pickers rely on: every value in the
 *  phone's arrays must decode to its own style (an unknown/missing value falls back safely). */
class SurfaceStyleMappingTest {
    @Test
    fun ringStyleMapsAllPickerValues() {
        assertEquals(RingStyle.SOLID, RingStyle.fromPref("solid"))
        assertEquals(RingStyle.DASHED, RingStyle.fromPref("dashed"))
        assertEquals(RingStyle.DOTS, RingStyle.fromPref("dots"))
        assertEquals(RingStyle.HAIRLINE, RingStyle.fromPref("hairline"))
        assertEquals(RingStyle.COMET, RingStyle.fromPref("comet"))
        assertEquals(RingStyle.DOUBLE, RingStyle.fromPref("double"))
        assertEquals(RingStyle.BEADS, RingStyle.fromPref("beads"))
        assertEquals(RingStyle.DASH_DOT, RingStyle.fromPref("dash_dot"))
        assertEquals(RingStyle.TICKS_24, RingStyle.fromPref("ticks_24"))
        assertEquals(RingStyle.PROGRESS_ONLY, RingStyle.fromPref("progress_only"))
        assertEquals(RingStyle.GLOW, RingStyle.fromPref("glow"))
        assertEquals(RingStyle.DUOTONE, RingStyle.fromPref("duotone"))
        assertEquals(RingStyle.NEEDLE, RingStyle.fromPref("needle"))
        assertEquals(RingStyle.SOLID, RingStyle.fromPref(null))
        assertEquals(RingStyle.SOLID, RingStyle.fromPref("nonsense"))
    }

    @Test
    fun volumeStyleMapsTheNewPickerValues() {
        assertEquals(VolumeStyle.SEGMENTS, VolumeStyle.fromPref("segments"))
        assertEquals(VolumeStyle.AURORA, VolumeStyle.fromPref("aurora"))
        assertEquals(VolumeStyle.INK, VolumeStyle.fromPref("ink"))
        assertEquals(VolumeStyle.BEADS, VolumeStyle.fromPref("beads"))
        assertEquals(VolumeStyle.DUAL, VolumeStyle.fromPref("dual"))
        assertEquals(VolumeStyle.PULSE, VolumeStyle.fromPref("pulse"))
        assertEquals(VolumeStyle.CHROME, VolumeStyle.fromPref("chrome"))
        assertEquals(VolumeStyle.SPECTRUM, VolumeStyle.fromPref("spectrum"))
        assertEquals(VolumeStyle.STEPS, VolumeStyle.fromPref("steps"))
        assertEquals(VolumeStyle.GLASS, VolumeStyle.fromPref(null))
    }

    @Test
    fun progressRingLayoutsMapEveryPickerValue() {
        assertEquals(ProgressRingLayout.EDGE, ProgressRingLayout.fromPref("edge"))
        assertEquals(ProgressRingLayout.INSET, ProgressRingLayout.fromPref("inset"))
        assertEquals(ProgressRingLayout.INNER, ProgressRingLayout.fromPref("inner"))
        assertEquals(ProgressRingLayout.BOLD, ProgressRingLayout.fromPref("bold"))
        assertEquals(ProgressRingLayout.OPEN_BOTTOM, ProgressRingLayout.fromPref("open_bottom"))
        assertEquals(ProgressRingLayout.OPEN_TOP, ProgressRingLayout.fromPref("open_top"))
        assertEquals(ProgressRingLayout.LEFT_ARC, ProgressRingLayout.fromPref("left_arc"))
        assertEquals(ProgressRingLayout.RIGHT_ARC, ProgressRingLayout.fromPref("right_arc"))
        assertEquals(ProgressRingLayout.DOUBLE, ProgressRingLayout.fromPref("double"))
        assertEquals(ProgressRingLayout.EDGE, ProgressRingLayout.fromPref(null))
        assertEquals(ProgressRingLayout.EDGE, ProgressRingLayout.fromPref("nonsense"))
    }

    @Test
    fun progressRingLayoutGeometryKeepsMirroredAndDoubleContracts() {
        assertEquals(160f, ProgressRingLayout.LEFT_ARC.sweepAngle, 0f)
        assertEquals(-160f, ProgressRingLayout.RIGHT_ARC.sweepAngle, 0f)
        assertEquals(270f, ProgressRingLayout.OPEN_BOTTOM.sweepAngle, 0f)
        assertEquals(270f, ProgressRingLayout.OPEN_TOP.sweepAngle, 0f)
        assertEquals(1.8f, ProgressRingLayout.BOLD.strokeScale, 0f)
        assertEquals(true, ProgressRingLayout.DOUBLE.drawsSecondRing)
    }
}
