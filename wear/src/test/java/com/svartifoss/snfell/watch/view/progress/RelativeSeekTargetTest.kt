package com.svartifoss.snfell.watch.view.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeSeekTargetTest {

    @Test
    fun `forward seek advances immediately by requested amount`() {
        assertEquals(70_000L, relativeSeekTarget(60_000L, 10_000L, 180_000L))
    }

    @Test
    fun `backward seek clamps at start`() {
        assertEquals(0L, relativeSeekTarget(4_000L, -10_000L, 180_000L))
    }

    @Test
    fun `forward seek clamps at known duration`() {
        assertEquals(180_000L, relativeSeekTarget(175_000L, 30_000L, 180_000L))
    }

    @Test
    fun `unknown duration still permits forward seek`() {
        assertEquals(35_000L, relativeSeekTarget(30_000L, 5_000L, 0L))
    }
}
