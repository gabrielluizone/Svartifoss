package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure-math parts of [PaletteTransforms] shared by the watch and the phone preview. The
 * color transforms delegate to androidx ColorUtils (Android runtime), so they are exercised by the
 * wear/mobile renderers instead; here we lock the blur-scale curve both sides must agree on.
 */
class PaletteTransformsTest {
    @Test
    fun legacyBlurScaleSpansTheFullDownscaleRange() {
        assertEquals(0.58f, PaletteTransforms.legacyBlurScale(0f), 1e-6f)
        assertEquals(0.18f, PaletteTransforms.legacyBlurScale(120f), 1e-6f)
        // Midpoint radius sits halfway down the range.
        assertEquals(0.38f, PaletteTransforms.legacyBlurScale(60f), 1e-6f)
    }

    @Test
    fun legacyBlurScaleClampsOutOfRangeRadii() {
        assertEquals(0.58f, PaletteTransforms.legacyBlurScale(-10f), 1e-6f)
        assertEquals(0.18f, PaletteTransforms.legacyBlurScale(999f), 1e-6f)
    }

    @Test
    fun legacyBlurScaleIsMonotonicallyDecreasing() {
        var previous = PaletteTransforms.legacyBlurScale(0f)
        var radius = 10f
        while (radius <= 120f) {
            val current = PaletteTransforms.legacyBlurScale(radius)
            assertTrue("scale should shrink as radius grows", current < previous)
            previous = current
            radius += 10f
        }
    }

    @Test
    fun faceSaturationBandIsWiderThanTheChromeDefault() {
        // The overlay chrome default band is .25-.60; the face band must be wider so album-tinted
        // overlays match the faces they open over instead of looking washed out.
        assertTrue(PaletteTransforms.FACE_MIN_SAT >= 0.25f)
        assertTrue(PaletteTransforms.FACE_MAX_SAT > 0.60f)
    }
}
