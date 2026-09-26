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

    // ---- window -----------------------------------------------------------

    private val page = QueuePaging.PAGE_SIZE
    private val before = QueuePaging.ENTRIES_BEFORE_ACTIVE

    /**
     * The gap the window closes. The published slice used to be a prefix, so deep in a long queue
     * it was mostly tracks already played - and past the ceiling the playing one was not in it at
     * all. The window always holds the playing entry, a few before it and a page after it.
     */
    @Test
    fun `a distant playing track is published with a page of what follows it`() {
        val window = QueuePaging.window(page, activeIndex = 80, queueSize = 500)

        assertEquals(80 - before, window.start)
        assertEquals(80 + 1 + page, window.endExclusive)
    }

    @Test
    fun `the playing track is included however deep in the queue it sits`() {
        val window = QueuePaging.window(page, activeIndex = 4_321, queueSize = 5_000)

        assertTrue("active entry outside $window", 4_321 in window.start until window.endExclusive)
        assertTrue("window of ${window.size} rows", window.size <= QueuePaging.MAX_ENTRIES)
    }

    /**
     * What keeps the change invisible to a watch that predates it: "load more" is offered while the
     * rows held are fewer than the total, and asks for [QueuePaging.nextLimit] of them. Measured from
     * the window's start, that grows the same window downwards until it runs out.
     */
    @Test
    fun `load more grows the window from the same start`() {
        val first = QueuePaging.window(page, activeIndex = 80, queueSize = 500)
        assertTrue("an old watch would offer no more rows", first.size < first.totalEntryCount)

        val second = QueuePaging.window(QueuePaging.nextLimit(first.size), 80, 500)
        assertEquals(first.start, second.start)
        assertEquals(first.size + page, second.size)
    }

    @Test
    fun `the total counts from the start of the window`() {
        val window = QueuePaging.window(page, activeIndex = 80, queueSize = 120)

        assertEquals(120 - window.start, window.totalEntryCount)
    }

    @Test
    fun `a playing track near the top keeps the head of the queue`() {
        val window = QueuePaging.window(page, activeIndex = 2, queueSize = 500)

        assertEquals(0, window.start)
        assertEquals(2 + 1 + page, window.endExclusive)
    }

    /** An unlocatable playing row must not move the request - it is published from the head. */
    @Test
    fun `an unknown playing row publishes the head of the queue as asked`() {
        assertEquals(QueuePaging.Window(0, page, QueuePaging.MAX_ENTRIES),
                QueuePaging.window(page, activeIndex = -1, queueSize = 500))
        // An index past the end of the queue is as unlocatable as a negative one.
        assertEquals(QueuePaging.Window(0, page, 150),
                QueuePaging.window(page, activeIndex = 150, queueSize = 150))
    }

    @Test
    fun `a short queue is published whole`() {
        assertEquals(QueuePaging.Window(0, 7, 7), QueuePaging.window(page, activeIndex = 3, queueSize = 7))
        assertEquals(QueuePaging.Window(0, 0, 0), QueuePaging.window(page, activeIndex = 0, queueSize = 0))
    }

    /** Whatever the position, one publication never exceeds the transfer ceiling. */
    @Test
    fun `the window never exceeds the ceiling and always follows the playing row`() {
        (0..1_000 step 13).forEach { activeIndex ->
            listOf(page, 60, QueuePaging.MAX_ENTRIES, 9_999).forEach { limit ->
                val window = QueuePaging.window(limit, activeIndex, queueSize = 1_000)
                assertTrue("active=$activeIndex limit=$limit overshot: $window",
                        window.size <= QueuePaging.MAX_ENTRIES)
                assertTrue("active=$activeIndex limit=$limit missed the playing row: $window",
                        activeIndex in window.start until window.endExclusive)
                if (activeIndex + 1 < 1_000) {
                    assertTrue("active=$activeIndex limit=$limit left nothing after it: $window",
                            window.endExclusive > activeIndex + 1)
                }
            }
        }
    }

    @Test
    fun `a nonsensical request is clamped into range`() {
        assertEquals(1, QueuePaging.window(0, activeIndex = -1, queueSize = 500).size)
        assertEquals(QueuePaging.MAX_ENTRIES,
                QueuePaging.window(9_999, activeIndex = -1, queueSize = 500).size)
    }
}
