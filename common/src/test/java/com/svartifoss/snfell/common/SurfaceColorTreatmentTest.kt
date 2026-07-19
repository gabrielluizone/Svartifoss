package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
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
}
