package com.svartifoss.snfell.notifications

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSessionMatchTest {

    private data class Stored(val name: String, val token: String?, val postedAt: Long)

    private fun pick(
            candidates: List<Stored>,
            session: String?,
            onlySession: Boolean
    ): Stored? = pickNotificationForSession(
            candidates, session, onlySession, tokenOf = { it.token }, postedAtOf = { it.postedAt })

    @Test
    fun `an exact token match wins whatever else is stored`() {
        val other = Stored("other", "b", postedAt = 900)
        val mine = Stored("mine", "a", postedAt = 100)
        assertSame(mine, pick(listOf(other, mine), session = "a", onlySession = true))
        assertSame(mine, pick(listOf(other, mine), session = "a", onlySession = false))
    }

    /** The reported case: one YouTube Music session, its notification's token does not compare
     *  equal to the session's, and the quick panel used to fall to the custom actions. */
    @Test
    fun `the only notification of a package with one session is taken despite its token`() {
        val notification = Stored("youtube", "token from the notification", postedAt = 5)
        assertSame(notification,
                pick(listOf(notification), session = "token from the session", onlySession = true))
    }

    /** The rule that existed before and must survive: with two sessions, a token that differs
     *  means another session, and its notification is not borrowed. */
    @Test
    fun `a token that differs is not borrowed when the package has several sessions`() {
        val notification = Stored("browser session", "b", postedAt = 5)
        assertNull(pick(listOf(notification), session = "a", onlySession = false))
    }

    @Test
    fun `a notification with no token is the compatibility case and is still taken`() {
        val legacy = Stored("legacy", null, postedAt = 5)
        assertSame(legacy, pick(listOf(legacy), session = "a", onlySession = false))
    }

    @Test
    fun `an unknown session takes the newest notification of the package`() {
        val older = Stored("older", "x", postedAt = 1)
        val newer = Stored("newer", "y", postedAt = 2)
        assertSame(newer, pick(listOf(older, newer), session = null, onlySession = false))
    }

    @Test
    fun `with several notifications and one session the newest is taken`() {
        val older = Stored("older", "x", postedAt = 1)
        val newer = Stored("newer", "y", postedAt = 2)
        assertSame(newer, pick(listOf(older, newer), session = "z", onlySession = true))
    }

    @Test
    fun `nothing stored is nothing found`() {
        assertNull(pick(emptyList(), session = "a", onlySession = true))
    }

    @Test
    fun `the predicate agrees with the picker for execute and like lookups`() {
        assertTrue(notificationMayBelongToSession("a", "a", onlySessionOfPackage = false))
        assertTrue(notificationMayBelongToSession(null, "a", onlySessionOfPackage = false))
        assertTrue(notificationMayBelongToSession("a", null, onlySessionOfPackage = false))
        assertFalse(notificationMayBelongToSession("a", "b", onlySessionOfPackage = false))
        assertTrue(notificationMayBelongToSession("a", "b", onlySessionOfPackage = true))
    }

    // ---- RenderedIconCache ---------------------------------------------------------------------

    private class Clock(var now: Long = 0L)

    private fun cache(clock: Clock, maxEntries: Int = 4) =
            RenderedIconCache(maxEntries, retryFailedAfterMs = 60_000L, clock = { clock.now })

    @Test
    fun `a finished render is not drawn again`() {
        val cache = cache(Clock())
        var renders = 0
        val first = cache.getOrRender("pkg/1/0") { renders++; byteArrayOf(1, 2, 3) }
        val second = cache.getOrRender("pkg/1/0") { renders++; byteArrayOf(9) }
        assertEquals(1, renders)
        assertArrayEquals(byteArrayOf(1, 2, 3), first)
        assertArrayEquals(byteArrayOf(1, 2, 3), second)
    }

    /** The per-state redraw this exists to end: an icon that draws to nothing was retried on
     *  every state build because a failure was never remembered. */
    @Test
    fun `a failed render is not retried on every call`() {
        val clock = Clock()
        val cache = cache(clock)
        var renders = 0
        repeat(5) {
            clock.now += 1_000L
            assertNull(cache.getOrRender("pkg/2/0") { renders++; null })
        }
        assertEquals(1, renders)
    }

    @Test
    fun `a failed render is retried once its pause is over`() {
        val clock = Clock()
        val cache = cache(clock)
        var renders = 0
        assertNull(cache.getOrRender("pkg/2/0") { renders++; null })
        clock.now += 59_999L
        assertNull(cache.getOrRender("pkg/2/0") { renders++; null })
        assertEquals(1, renders)

        clock.now += 1L
        val recovered = cache.getOrRender("pkg/2/0") { renders++; byteArrayOf(7) }
        assertEquals(2, renders)
        assertArrayEquals(byteArrayOf(7), recovered)
        // And once it has drawn, the earlier failure no longer matters.
        assertArrayEquals(byteArrayOf(7), cache.getOrRender("pkg/2/0") { renders++; null })
        assertEquals(2, renders)
    }

    @Test
    fun `a clock that moves backwards does not freeze a failure forever`() {
        val clock = Clock(now = 500_000L)
        val cache = cache(clock)
        var renders = 0
        assertNull(cache.getOrRender("k") { renders++; null })
        clock.now = 10L
        val retried = cache.getOrRender("k") { renders++; byteArrayOf(1) }
        assertEquals(2, renders)
        assertArrayEquals(byteArrayOf(1), retried)
    }

    @Test
    fun `the oldest render is dropped when the cache is full`() {
        val cache = cache(Clock(), maxEntries = 2)
        var renders = 0
        cache.getOrRender("a") { renders++; byteArrayOf(1) }
        cache.getOrRender("b") { renders++; byteArrayOf(2) }
        cache.getOrRender("c") { renders++; byteArrayOf(3) }
        assertEquals(3, renders)
        cache.getOrRender("c") { renders++; byteArrayOf(3) }
        assertEquals(3, renders)
        cache.getOrRender("a") { renders++; byteArrayOf(1) }
        assertEquals(4, renders)
    }

    @Test
    fun `a new package version is a new key and draws again`() {
        val cache = cache(Clock())
        var renders = 0
        cache.getOrRender("pkg/1/100") { renders++; byteArrayOf(1) }
        cache.getOrRender("pkg/1/200") { renders++; byteArrayOf(2) }
        assertEquals(2, renders)
    }
}
