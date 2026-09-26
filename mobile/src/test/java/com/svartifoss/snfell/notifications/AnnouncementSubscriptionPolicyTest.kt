package com.svartifoss.snfell.notifications

import com.svartifoss.snfell.notifications.AnnouncementSubscriptionPolicy.REFRESH_INTERVAL_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementSubscriptionPolicyTest {
    private val confirmedAt = 1_000_000_000L

    @Test
    fun `an install that has never had its choice confirmed sends it`() {
        assertTrue(AnnouncementSubscriptionPolicy.needsApplying(true, null, 0L, confirmedAt))
        assertTrue(AnnouncementSubscriptionPolicy.needsApplying(false, null, 0L, confirmedAt))
    }

    /** The case this exists for: the phone process restarting with nothing changed. */
    @Test
    fun `a confirmed choice is not resent on the next process start`() {
        assertFalse(AnnouncementSubscriptionPolicy.needsApplying(true, true, confirmedAt, confirmedAt + 60_000L))
        assertFalse(AnnouncementSubscriptionPolicy.needsApplying(false, false, confirmedAt, confirmedAt + 60_000L))
    }

    @Test
    fun `a changed choice is always sent`() {
        assertTrue(AnnouncementSubscriptionPolicy.needsApplying(false, true, confirmedAt, confirmedAt + 1L))
        assertTrue(AnnouncementSubscriptionPolicy.needsApplying(true, false, confirmedAt, confirmedAt + 1L))
    }

    @Test
    fun `a confirmed choice is refreshed once the interval has passed`() {
        assertFalse(AnnouncementSubscriptionPolicy.needsApplying(
                true, true, confirmedAt, confirmedAt + REFRESH_INTERVAL_MS - 1))
        assertTrue(AnnouncementSubscriptionPolicy.needsApplying(
                true, true, confirmedAt, confirmedAt + REFRESH_INTERVAL_MS))
    }

    @Test
    fun `a clock set backwards does not postpone the refresh`() {
        assertTrue(AnnouncementSubscriptionPolicy.needsApplying(true, true, confirmedAt, confirmedAt - 1L))
    }
}
