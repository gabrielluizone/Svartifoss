package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePagingTest {

    @Test
    fun `each request asks for one page more than is already loaded`() {
        assertEquals(40, QueuePaging.nextLimit(20))
        assertEquals(60, QueuePaging.nextLimit(40))
    }

    /**
     * Requests are cumulative, not incremental - the phone replaces the whole list rather than
     * appending to it, so the next limit must cover what is already on screen as well. Asking for
     * only the new page would silently shrink the queue back to one page.
     */
    @Test
    fun `the next limit includes the entries already loaded`() {
        val loaded = 37
        assertEquals(loaded + QueuePaging.PAGE_SIZE, QueuePaging.nextLimit(loaded))
    }

    /**
     * A short final page (the phone had fewer entries left than a full page) must not make the next
     * request smaller than what is already displayed, which would drop rows the user can see.
     */
    @Test
    fun `a partial page still grows the request`() {
        assertEquals(45, QueuePaging.nextLimit(25))
    }

    @Test
    fun `the ceiling is never exceeded`() {
        assertEquals(QueuePaging.MAX_ENTRIES, QueuePaging.nextLimit(QueuePaging.MAX_ENTRIES))
        assertEquals(QueuePaging.MAX_ENTRIES, QueuePaging.nextLimit(QueuePaging.MAX_ENTRIES - 1))
    }

    // ---- limitCoveringUpcoming --------------------------------------------

    /**
     * The gap this closes: a page is a prefix, so listening to the eightieth track of a playlist
     * meant the watch was sent tracks 1-20 and could not show the one playing at all - the user
     * paged down by hand until it appeared.
     */
    @Test
    fun `the page stretches to reach a distant playing track`() {
        assertEquals(81 + QueuePaging.PAGE_SIZE,
                QueuePaging.limitCoveringUpcoming(QueuePaging.PAGE_SIZE, activeIndex = 80))
    }

    /**
     * The correction this exists for. Stretching to exactly the playing track does put it in the
     * list - as the last row, with nothing after it - so a screen whose entire subject is what
     * comes next showed only what came before.
     */
    @Test
    fun `the stretch reaches past the playing track, not up to it`() {
        val activeIndex = 80
        val limit = QueuePaging.limitCoveringUpcoming(QueuePaging.PAGE_SIZE, activeIndex)
        assertTrue(
                "the playing track must not be the last row (limit=$limit)",
                limit > activeIndex + 1)
        assertEquals(QueuePaging.PAGE_SIZE, limit - (activeIndex + 1))
    }

    @Test
    fun `a playing track near the top still gets a page of what follows`() {
        assertEquals(6 + QueuePaging.PAGE_SIZE,
                QueuePaging.limitCoveringUpcoming(QueuePaging.PAGE_SIZE, activeIndex = 5))
    }

    /** Stretching can only spend the budget that already existed. */
    @Test
    fun `stretching never exceeds the transfer ceiling`() {
        assertEquals(QueuePaging.MAX_ENTRIES,
                QueuePaging.limitCoveringUpcoming(QueuePaging.PAGE_SIZE, activeIndex = 5_000))
    }

    /** An unlocatable playing row must not quietly change the size that was asked for. */
    @Test
    fun `an unknown playing row leaves the request exactly as asked`() {
        assertEquals(QueuePaging.PAGE_SIZE,
                QueuePaging.limitCoveringUpcoming(QueuePaging.PAGE_SIZE, activeIndex = -1))
    }

    /** A page already paged past the playing track keeps its size - "load more" must not shrink. */
    @Test
    fun `a larger request is never shrunk to the playing row`() {
        assertEquals(120, QueuePaging.limitCoveringUpcoming(120, activeIndex = 3))
    }

    /** Whatever the position, the playing row always has somewhere to be other than the end. */
    @Test
    fun `the playing row is never the last one unless the ceiling forces it`() {
        (0..QueuePaging.MAX_ENTRIES + 50 step 7).forEach { activeIndex ->
            val limit = QueuePaging.limitCoveringUpcoming(QueuePaging.PAGE_SIZE, activeIndex)
            if (activeIndex + 1 < QueuePaging.MAX_ENTRIES) {
                assertTrue(
                        "active=$activeIndex limit=$limit leaves nothing after the playing row",
                        limit > activeIndex + 1)
            }
            assertTrue("active=$activeIndex overshot the ceiling",
                    limit <= QueuePaging.MAX_ENTRIES)
        }
    }

    @Test
    fun `a nonsensical request is clamped into range`() {
        assertEquals(1, QueuePaging.limitCoveringUpcoming(0, activeIndex = -1))
        assertEquals(QueuePaging.MAX_ENTRIES,
                QueuePaging.limitCoveringUpcoming(9_999, activeIndex = -1))
    }
}
