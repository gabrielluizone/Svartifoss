package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueuePositionTest {

    @Test
    fun `a playing entry inside the queue is described`() {
        assertEquals(QueuePosition(3, 47), QueuePosition.of(3, 47))
        assertEquals(QueuePosition(1, 1), QueuePosition.of(1, 1))
        assertEquals(QueuePosition(47, 47), QueuePosition.of(47, 47))
    }

    @Test
    fun `an older phone's absent fields, and anything out of range, say nothing`() {
        assertNull(QueuePosition.of(0, 0))
        assertNull(QueuePosition.of(0, 47))
        assertNull(QueuePosition.of(48, 47))
        assertNull(QueuePosition.of(-1, 47))
        assertNull(QueuePosition.of(3, 0))
    }
}
