package com.svartifoss.snfell.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverShapeTest {
    /** Saved themes and every install already carry these, so they must draw exactly as before. */
    @Test
    fun `the original shapes keep their uniform corners`() {
        mapOf(
                CoverShape.ROUNDED to 0.08f,
                CoverShape.SQUARE to 0f,
                CoverShape.SQUIRCLE to 0.18f,
                CoverShape.SAMSUNG to 0.30f,
                CoverShape.CIRCLE to 0.5f
        ).forEach { (shape, corner) ->
            assertTrue("$shape", shape.isUniform)
            assertArrayEquals("$shape", FloatArray(8) { corner * 100f }, shape.radii(100f), 1e-4f)
        }
    }

    /**
     * Two neighbouring corners wider than the side they share cannot both be drawn, and the two
     * renderers resolve that differently - Compose and Skia each scale the radii down their own
     * way - so an entry like that would be the one place the preview and the watch disagree.
     */
    @Test
    fun `no two neighbouring corners overlap`() {
        CoverShape.entries.forEach { shape ->
            listOf(
                    shape.topLeft + shape.topRight,
                    shape.topRight + shape.bottomRight,
                    shape.bottomRight + shape.bottomLeft,
                    shape.bottomLeft + shape.topLeft
            ).forEach { side ->
                assertTrue("$shape overlaps on a side ($side)", side <= 1f + 1e-6f)
            }
            listOf(shape.topLeft, shape.topRight, shape.bottomRight, shape.bottomLeft).forEach {
                assertTrue("$shape has a corner outside 0..0.5", it in 0f..0.5f)
            }
        }
    }

    @Test
    fun `radii follow the canvas corner order`() {
        assertArrayEquals(
                floatArrayOf(50f, 50f, 50f, 50f, 6f, 6f, 6f, 6f),
                CoverShape.ARCH.radii(100f), 1e-4f)
        assertArrayEquals(
                floatArrayOf(6f, 6f, 50f, 50f, 50f, 50f, 50f, 50f),
                CoverShape.DROP.radii(100f), 1e-4f)
    }

    @Test
    fun `values round trip and an unknown one lands on the caller's default`() {
        assertEquals(CoverShape.entries.size,
                CoverShape.entries.map { it.preferenceValue }.toSet().size)
        CoverShape.entries.forEach {
            assertEquals(it, CoverShape.fromPreference(it.preferenceValue, CoverShape.SQUARE))
        }
        assertEquals(CoverShape.CIRCLE, CoverShape.fromPreference("hexagon", CoverShape.CIRCLE))
        assertEquals(CoverShape.ROUNDED, CoverShape.fromPreference(null))
    }
}
