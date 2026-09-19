package com.svartifoss.snfell.watch.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobvoiPinchEventFilterTest {
    @Test
    fun `value two is already one complete double pinch`() {
        val filter = MobvoiPinchEventFilter()

        assertTrue(filter.accept(1L, floatArrayOf(2f)))
    }

    @Test
    fun `single pinches and unknown values never become a double pinch`() {
        val filter = MobvoiPinchEventFilter()

        assertFalse(filter.accept(1L, floatArrayOf(1f)))
        assertFalse(filter.accept(2L, floatArrayOf(1f)))
        assertFalse(filter.accept(3L, floatArrayOf(0f)))
        assertFalse(filter.accept(4L, floatArrayOf(3f)))
        assertFalse(filter.accept(5L, floatArrayOf(-2f)))
        assertFalse(filter.accept(6L, floatArrayOf(1.999999f)))
        assertFalse(filter.accept(7L, floatArrayOf(2.000001f)))
    }

    @Test
    fun `empty and non finite values are ignored`() {
        val filter = MobvoiPinchEventFilter()

        assertFalse(filter.accept(1L, floatArrayOf()))
        assertFalse(filter.accept(2L, floatArrayOf(Float.NaN)))
        assertFalse(filter.accept(3L, floatArrayOf(Float.POSITIVE_INFINITY)))
        assertFalse(filter.accept(4L, floatArrayOf(Float.NEGATIVE_INFINITY)))
    }

    @Test
    fun `only the first value contains the gesture code`() {
        val filter = MobvoiPinchEventFilter()

        assertFalse(filter.accept(1L, floatArrayOf(0f, 2f)))
        assertTrue(filter.accept(2L, floatArrayOf(2f, 0f, Float.NaN)))
    }

    @Test
    fun `timestamps must be positive`() {
        val filter = MobvoiPinchEventFilter()

        assertFalse(filter.accept(0L, floatArrayOf(2f)))
        assertFalse(filter.accept(-1L, floatArrayOf(2f)))
        assertFalse(filter.accept(Long.MIN_VALUE, floatArrayOf(2f)))
        assertTrue(filter.accept(1L, floatArrayOf(2f)))
    }

    @Test
    fun `duplicate and out of order detections do not repeat an action`() {
        val filter = MobvoiPinchEventFilter()

        assertTrue(filter.accept(100L, floatArrayOf(2f)))
        assertFalse(filter.accept(100L, floatArrayOf(2f)))
        assertFalse(filter.accept(99L, floatArrayOf(2f)))
        assertTrue(filter.accept(101L, floatArrayOf(2f)))
    }

    @Test
    fun `two distinct detections are accepted even less than 100 milliseconds apart`() {
        val filter = MobvoiPinchEventFilter()

        assertTrue(filter.accept(1_000_000_000L, floatArrayOf(2f)))
        assertTrue(filter.accept(1_050_000_000L, floatArrayOf(2f)))
    }

    @Test
    fun `reusing the filter on resubscription suppresses the on change replay`() {
        val filter = MobvoiPinchEventFilter()
        val firstSubscription = { timestamp: Long -> filter.accept(timestamp, floatArrayOf(2f)) }
        assertTrue(firstSubscription(1_000L))

        val resumedSubscription = { timestamp: Long -> filter.accept(timestamp, floatArrayOf(2f)) }
        assertFalse(resumedSubscription(1_000L))
        assertTrue(resumedSubscription(2_000L))
        assertFalse(resumedSubscription(1_000L))
    }

    @Test
    fun `unrecognized values do not consume a valid detection timestamp`() {
        val filter = MobvoiPinchEventFilter()

        assertFalse(filter.accept(1_000L, floatArrayOf(0f)))
        assertTrue(filter.accept(1_000L, floatArrayOf(2f)))
        assertFalse(filter.accept(3_000L, floatArrayOf(Float.NaN)))
        assertTrue(filter.accept(2_000L, floatArrayOf(2f)))
    }

    /**
     * An on-change sensor reports its current value on activation, so a completed double pinch
     * arriving in the first moments of a subscription is the previous gesture replayed. In a fresh
     * process the watermark knows nothing, and a replay carrying a new timestamp would pass it in
     * any process - either way the player would run its action the moment it opened.
     */
    @Test
    fun `a double pinch delivered right after registering is taken for the activation replay`() {
        val filter = MobvoiPinchEventFilter()
        val window = MobvoiPinchEventFilter.REPLAY_WINDOW_MS

        assertFalse(filter.acceptLive(5_000L, floatArrayOf(2f), sinceRegistrationMs = 20L))
        // It still seeds the watermark, so the same event cannot come back later as "new".
        assertFalse(filter.acceptLive(5_000L, floatArrayOf(2f), sinceRegistrationMs = window + 1))
        assertTrue(filter.acceptLive(6_000L, floatArrayOf(2f), sinceRegistrationMs = window))
        assertFalse(filter.acceptLive(7_000L, floatArrayOf(1f), sinceRegistrationMs = 10_000L))
    }
}
