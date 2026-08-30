package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The title text mode is decoded by three renderers, and each used to hold its own copy of this
 * table - which is how `static`, `wrap3` and `wrap5` came to work on every Compose face while the
 * classic face quietly treated all three as "smart".
 */
class TitleTextModeTest {

    @Test
    fun wrappingModesReportTheirLineCap() {
        assertEquals(1, TitleTextMode.wrapLines("static"))
        assertEquals(2, TitleTextMode.wrapLines("wrap"))
        assertEquals(3, TitleTextMode.wrapLines("wrap3"))
        assertEquals(5, TitleTextMode.wrapLines("wrap5"))
    }

    @Test
    fun nonWrappingModesReportNoCap() {
        assertNull(TitleTextMode.wrapLines("smart"))
        assertNull(TitleTextMode.wrapLines("marquee"))
        assertNull(TitleTextMode.wrapLines("shrink"))
    }

    /**
     * An unknown value has to fall through to the caller's own cascade rather than to a cap.
     *
     * These strings arrive from an imported backup, a published theme or a newer build, so "a value
     * this build has never heard of" is a normal state and not a corruption. Degrading to the smart
     * cascade shows the whole title; degrading to a cap would silently truncate it.
     */
    @Test
    fun unknownAndAbsentValuesFallThrough() {
        assertNull(TitleTextMode.wrapLines(null))
        assertNull(TitleTextMode.wrapLines(""))
        assertNull(TitleTextMode.wrapLines("wrap7"))
        assertNull(TitleTextMode.wrapLines("WRAP"))
    }

    @Test
    fun theScrollingAndShrinkingModesAreDistinct() {
        assertTrue(TitleTextMode.isMarquee("marquee"))
        assertFalse(TitleTextMode.isMarquee("shrink"))
        assertFalse(TitleTextMode.isMarquee(null))

        assertTrue(TitleTextMode.isShrink("shrink"))
        assertFalse(TitleTextMode.isShrink("marquee"))
        assertFalse(TitleTextMode.isShrink(null))
    }

    /** Every value the picker offers must resolve to exactly one behaviour. */
    @Test
    fun everyOfferedValueResolvesToOneBehaviour() {
        listOf("smart", "marquee", "shrink", "static", "wrap", "wrap3", "wrap5").forEach { value ->
            val behaviours = listOf(
                    TitleTextMode.wrapLines(value) != null,
                    TitleTextMode.isMarquee(value),
                    TitleTextMode.isShrink(value)
            ).count { it }
            assertTrue(
                    "$value resolves to $behaviours behaviours; smart is the only value that " +
                            "legitimately resolves to none",
                    behaviours == 1 || (behaviours == 0 && value == "smart"))
        }
    }
}
