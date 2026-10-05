package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A seek chip is a picture of "N seconds back/forward", so the failure to pin is the quiet one: a
 * jump the panel offers that has no picture, or a picture that says a different number.
 */
class SeekGlyphsTest {

    @Test
    fun `every jump the seek block may offer has an arrow in both directions`() {
        QuickPanelStack.SEEK_STEP_VOCABULARY.forEach { seconds ->
            assertNotNull("no back arrow for $seconds s", SeekGlyphs.back(seconds))
            assertNotNull("no forward arrow for $seconds s", SeekGlyphs.forward(seconds))
        }
    }

    @Test
    fun `the arrows of different jumps are different pictures`() {
        val steps = QuickPanelStack.SEEK_STEP_VOCABULARY
        assertEquals(steps.size, steps.map { SeekGlyphs.back(it) }.toSet().size)
        assertEquals(steps.size, steps.map { SeekGlyphs.forward(it) }.toSet().size)
        // And going back is not going forward.
        steps.forEach { assertTrue(SeekGlyphs.back(it) != SeekGlyphs.forward(it)) }
    }

    @Test
    fun `an amount without an arrow gets none rather than a near miss`() {
        assertNull(SeekGlyphs.back(15))
        assertNull(SeekGlyphs.forward(60))
        assertNull(SeekGlyphs.forOffset(0))
    }

    @Test
    fun `a signed offset picks its direction from the sign`() {
        assertEquals(SeekGlyphs.back(10), SeekGlyphs.forOffset(-10))
        assertEquals(SeekGlyphs.forward(30), SeekGlyphs.forOffset(30))
    }
}
