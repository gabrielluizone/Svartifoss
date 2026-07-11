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
        assertEquals(RingStyle.SOLID, RingStyle.fromPref(null))
        assertEquals(RingStyle.SOLID, RingStyle.fromPref("nonsense"))
    }

    @Test
    fun volumeStyleMapsTheNewPickerValues() {
        assertEquals(VolumeStyle.SEGMENTS, VolumeStyle.fromPref("segments"))
        assertEquals(VolumeStyle.AURORA, VolumeStyle.fromPref("aurora"))
        assertEquals(VolumeStyle.INK, VolumeStyle.fromPref("ink"))
        assertEquals(VolumeStyle.GLASS, VolumeStyle.fromPref(null))
    }
}
