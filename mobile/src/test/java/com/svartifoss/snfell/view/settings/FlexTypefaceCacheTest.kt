package com.svartifoss.snfell.view.settings

import com.svartifoss.snfell.common.WatchTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FlexTypefaceCacheTest {
    private val spec = WatchTypography.IDENTITY_TEXT
    private val axes = WatchTypography.IDENTITY_FLEX_AXES

    @Test
    fun `animated preview reuses fonts across frames and text elements`() {
        val cache = FlexTypefaceCache<Any>()
        var allocations = 0
        val weights = listOf(400, 500, 600, 700, 800)
        val firstFrame = weights.map { weight ->
            cache.getOrLoad(spec.copy(weight = weight), axes) { Any().also { allocations++ } }
        }

        repeat(600) {
            weights.forEachIndexed { index, weight ->
                val font = cache.getOrLoad(spec.copy(weight = weight), axes) {
                    Any().also { allocations++ }
                }
                assertSame(firstFrame[index], font)
            }
        }

        assertEquals(weights.size, allocations)
    }

    @Test
    fun `paint changes do not allocate another native font`() {
        val cache = FlexTypefaceCache<Any>()
        val original = cache.getOrLoad(spec, axes) { Any() }
        val resized = cache.getOrLoad(
                spec.copy(scale = 1.5f, alpha = .5f, trackingEm = .1f), axes) { Any() }

        assertSame(original, resized)
    }

    @Test
    fun `font axes and slant select distinct instances`() {
        val cache = FlexTypefaceCache<Any>()
        val original = cache.getOrLoad(spec, axes) { Any() }
        val variants = listOf(
                axes.copy(width = 75f),
                axes.copy(opticalSize = 48f),
                axes.copy(grade = 50f),
                axes.copy(roundness = 50f))

        variants.forEach { variant ->
            var passedSettings: String? = null
            val font = cache.getOrLoad(spec, variant) { settings ->
                passedSettings = settings
                Any()
            }
            assertNotSame(original, font)
            assertEquals(WatchTypography.flexVariationSettings(spec, variant), passedSettings)
        }
        assertNotSame(original, cache.getOrLoad(spec.copy(italic = true), axes) { Any() })
    }

    @Test
    fun `slider history evicts old fonts while retaining the recently drawn font`() {
        val cache = FlexTypefaceCache<Any>(maxEntries = 2)
        val current = cache.getOrLoad(spec, axes) { Any() }
        val old = cache.getOrLoad(spec.copy(weight = 500), axes) { Any() }

        // Draw the current element while another element's weight slider changes many times.
        for (weight in 501..1000) {
            assertSame(current, cache.getOrLoad(spec, axes) { Any() })
            cache.getOrLoad(spec.copy(weight = weight), axes) { Any() }
        }

        assertNotSame(old, cache.getOrLoad(spec.copy(weight = 500), axes) { Any() })
    }

    @Test
    fun `equivalent clamped weights share a native font`() {
        val cache = FlexTypefaceCache<Any>()
        val maximum = cache.getOrLoad(spec.copy(weight = 1000), axes) { Any() }

        assertSame(maximum, cache.getOrLoad(spec.copy(weight = 1001), axes) { Any() })
    }

    @Test
    fun `unsupported font fallback is remembered instead of retried every frame`() {
        val cache = FlexTypefaceCache<Any?>()
        var attempts = 0

        repeat(600) {
            assertNull(cache.getOrLoad(spec, axes) {
                attempts++
                null
            })
        }

        assertEquals(1, attempts)
    }
}
