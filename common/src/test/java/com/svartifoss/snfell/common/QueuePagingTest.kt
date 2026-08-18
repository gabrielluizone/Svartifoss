package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
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
}
