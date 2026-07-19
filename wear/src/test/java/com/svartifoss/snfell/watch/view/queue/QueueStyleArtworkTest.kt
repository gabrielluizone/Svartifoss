package com.svartifoss.snfell.watch.view.queue

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueStyleArtworkTest {
    @Test
    fun artworkShapeFollowsQueueStyleGeometry() {
        assertEquals(15.dp, queueArtworkCorner(QueueStyle.GLASS))
        assertEquals(15.dp, queueArtworkCorner(QueueStyle.TONAL))
        assertEquals(6.dp, queueArtworkCorner(QueueStyle.MATERIAL))
        assertEquals(4.dp, queueArtworkCorner(QueueStyle.CONTRAST))
        assertEquals(0.dp, queueArtworkCorner(QueueStyle.TERMINAL))
    }

    @Test
    fun everyStyleKeepsArtworkInsideItsThirtyDpFrame() {
        QueueStyle.entries.forEach { style ->
            val corner = queueArtworkCorner(style)
            assertTrue("$style corner must be non-negative", corner >= 0.dp)
            assertTrue("$style corner must not exceed a circle", corner <= 15.dp)
        }
    }
}
