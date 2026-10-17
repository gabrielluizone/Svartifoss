package com.svartifoss.snfell.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackTimeFormatTest {

    @Test
    fun `under an hour it is minutes and seconds with no hours field`() {
        assertEquals("0:00", TrackTimeFormat.format(0, Locale.US))
        assertEquals("1:07", TrackTimeFormat.format(67_000, Locale.US))
        assertEquals("4:44", TrackTimeFormat.format(284_000, Locale.US))
        assertEquals("59:59", TrackTimeFormat.format(3_599_000, Locale.US))
    }

    /** The reported case: a four-hour podcast read `240:00` because the minutes just kept counting. */
    @Test
    fun `from the first hour it grows an hours field instead of counting minutes`() {
        assertEquals("1:00:00", TrackTimeFormat.format(3_600_000, Locale.US))
        assertEquals("1:03:10", TrackTimeFormat.format(3_790_000, Locale.US))
        assertEquals("4:00:00", TrackTimeFormat.format(14_400_000, Locale.US))
        assertEquals("10:00:00", TrackTimeFormat.format(36_000_000, Locale.US))
    }

    /** `1:00:00` is not `0:60:00`: the minutes field wraps at the hour, it does not carry on. */
    @Test
    fun `the minutes field wraps at the hour`() {
        assertEquals("59:59", TrackTimeFormat.format(3_599_999, Locale.US))
        assertEquals("1:00:00", TrackTimeFormat.format(3_600_000, Locale.US))
        assertEquals("1:59:59", TrackTimeFormat.format(7_199_999, Locale.US))
    }

    /** Milliseconds are dropped, not rounded - a clock must never run ahead of the sound. */
    @Test
    fun `a partial second is truncated`() {
        assertEquals("0:09", TrackTimeFormat.format(9_999, Locale.US))
        assertEquals("1:00:00", TrackTimeFormat.format(3_600_001, Locale.US))
    }

    /** A position extrapolated a moment before the sample that corrects it can be below zero. */
    @Test
    fun `a negative time is shown as zero rather than as 0 colon -1`() {
        assertEquals("0:00", TrackTimeFormat.format(-1, Locale.US))
        assertEquals("0:00", TrackTimeFormat.format(-1_500, Locale.US))
        assertEquals("0:00", TrackTimeFormat.format(Long.MIN_VALUE, Locale.US))
    }

    /** The locale picks the digits and nothing else; the layout is the same in every language. */
    @Test
    fun `the layout does not depend on the locale`() {
        for (locale in listOf(
                Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("pt-BR"), Locale.JAPAN)) {
            assertEquals("0:09", TrackTimeFormat.format(9_000, locale))
            assertEquals("1:03:10", TrackTimeFormat.format(3_790_000, locale))
        }
    }

    /** The metadata face's duration row and the player's readout are one format, not two. */
    @Test
    fun `the metadata duration row agrees with the player readout`() {
        for (ms in longArrayOf(1_000, 187_000, 3_599_000, 3_600_000, 3_790_000, 14_400_000)) {
            assertEquals(TrackTimeFormat.format(ms, Locale.US), TrackMetadataFields.formatDuration(ms))
        }
    }
}
