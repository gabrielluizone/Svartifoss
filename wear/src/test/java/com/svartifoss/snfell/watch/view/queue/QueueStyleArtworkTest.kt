package com.svartifoss.snfell.watch.view.queue

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueStyleArtworkTest {

    /**
     * The corners moved from dp to a fraction of the cover when the cover stopped being a fixed
     * 30dp. These assertions are the old dp values re-expressed against that 30dp reference, so
     * they pin that the shape family of each style survived the change.
     */
    @Test
    fun artworkShapeFollowsQueueStyleGeometry() {
        assertEquals(15f, cornerAt(30.dp, QueueStyle.GLASS), TOLERANCE)
        assertEquals(15f, cornerAt(30.dp, QueueStyle.TONAL), TOLERANCE)
        assertEquals(6f, cornerAt(30.dp, QueueStyle.MATERIAL), TOLERANCE)
        assertEquals(4f, cornerAt(30.dp, QueueStyle.CONTRAST), TOLERANCE)
        assertEquals(0f, cornerAt(30.dp, QueueStyle.TERMINAL), TOLERANCE)
    }

    @Test
    fun everyStyleKeepsArtworkInsideItsOwnFrame() {
        QueueStyle.entries.forEach { style ->
            val fraction = queueArtworkCornerFraction(style)
            assertTrue("$style corner must be non-negative", fraction >= 0f)
            assertTrue("$style corner must not exceed a circle", fraction <= 0.5f)
        }
    }

    /**
     * The point of the fraction: a pill-shaped style stays a circle at every cover size. With the
     * old fixed 15dp radius a tall row's larger cover silently became a rounded square.
     */
    @Test
    fun pillStylesStayCircularAtEveryCoverSize() {
        listOf(22.dp, 30.dp, 44.dp, 64.dp).forEach { size ->
            assertEquals(
                    "Glass must stay a circle at $size",
                    size.value / 2f,
                    cornerAt(size, QueueStyle.GLASS),
                    TOLERANCE)
        }
    }

    /** The cover grows with the row instead of leaving a bigger gap around a constant thumbnail. */
    @Test
    fun coverGrowsWithTheRowSize() {
        val padding = 12.dp
        val compact = queueArtworkSize(QueueRowSize.COMPACT, padding)
        val normal = queueArtworkSize(QueueRowSize.NORMAL, padding)
        val tall = queueArtworkSize(QueueRowSize.TALL, padding)
        assertTrue("compact ($compact) should be smaller than normal ($normal)", compact < normal)
        assertTrue("normal ($normal) should be smaller than tall ($tall)", normal < tall)
        // The regression this fixes: the default row used to show a 30dp cover in a 54dp pill.
        assertTrue("normal cover should be larger than the old fixed 30dp", normal > 30.dp)
    }

    /** A tall row must not hand the whole width to the cover and squeeze the text off screen. */
    @Test
    fun coverIsCappedSoTheTextColumnSurvives() {
        val xtall = queueArtworkSize(QueueRowSize.XTALL, 16.dp)
        assertTrue("cover should be capped, was $xtall", xtall <= 64.dp)
    }

    /** Whatever the style's padding, the cover never outgrows the pill containing it. */
    @Test
    fun coverAlwaysFitsInsideItsRow() {
        listOf(10.dp, 12.dp, 14.dp, 16.dp).forEach { padding ->
            QueueRowSize.entries.forEach { size ->
                val rowHeight = size.contentHeight + padding * 2
                assertTrue(
                        "$size at $padding padding overflowed its row",
                        queueArtworkSize(size, padding) <= rowHeight)
            }
        }
    }

    /**
     * The shared pill height must not be built by reading back through [QueueRowSize].
     *
     * That enum takes its NORMAL height from a top-level constant in QueueScreen.kt, so a top-level
     * property in the same file that touches the enum makes the two initialise each other. The JVM
     * answers that cycle with a zeroed value rather than an error, which on the watch would be a
     * silently 24dp-tall menu and quick-panel row - this pins the real number instead.
     */
    @Test
    fun sharedListRowHeightMatchesTheDefaultQueueRow() {
        assertEquals(
                QueueRowSize.NORMAL.contentHeight.value + 24f,
                LIST_ROW_HEIGHT.value,
                TOLERANCE)
        assertTrue("LIST_ROW_HEIGHT must not initialise to zero", LIST_ROW_HEIGHT.value > 0f)
    }

    private fun cornerAt(size: androidx.compose.ui.unit.Dp, style: QueueStyle): Float =
            size.value * queueArtworkCornerFraction(style)

    private companion object {
        const val TOLERANCE = 0.05f
    }
}
