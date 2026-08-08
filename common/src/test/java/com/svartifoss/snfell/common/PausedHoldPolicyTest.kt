package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PausedHoldPolicyTest {
    @Test
    fun `minute values decode to milliseconds`() {
        assertEquals(5 * 60_000L, PausedHoldPolicy.holdMillis("5"))
        assertEquals(30 * 60_000L, PausedHoldPolicy.holdMillis("30"))
        assertEquals(60 * 60_000L, PausedHoldPolicy.holdMillis("60"))
    }

    @Test
    fun `zero means no extra hold beyond the plain idle timeout`() {
        assertEquals(PausedHoldPolicy.NO_HOLD, PausedHoldPolicy.holdMillis("0"))
    }

    @Test
    fun `always never expires`() {
        assertEquals(PausedHoldPolicy.FOREVER, PausedHoldPolicy.holdMillis("always"))
        assertEquals(PausedHoldPolicy.FOREVER, PausedHoldPolicy.holdMillis("-1"))
    }

    /**
     * A value can arrive from an imported backup or a newer build on the phone, so it must not be
     * able to silently restore the pre-fix behaviour of evicting the app on pause.
     */
    @Test
    fun `unparseable values fall back to the default rather than to no hold`() {
        assertEquals(30 * 60_000L, PausedHoldPolicy.holdMillis(null))
        assertEquals(30 * 60_000L, PausedHoldPolicy.holdMillis(""))
        assertEquals(30 * 60_000L, PausedHoldPolicy.holdMillis("forever"))
    }

    @Test
    fun `the default value is what the default constant claims`() {
        assertEquals(
                PausedHoldPolicy.holdMillis(PausedHoldPolicy.DEFAULT_VALUE),
                PausedHoldPolicy.holdMillis("30"))
    }
}
