package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueRowShapeTest {

    @Test
    fun `an unknown or missing shape follows the style`() {
        assertEquals(QueueRowShape.FOLLOW, QueueRowShape.fromPreference(null))
        assertEquals(QueueRowShape.FOLLOW, QueueRowShape.fromPreference("ticket"))
        QueueRowShape.entries.forEach {
            assertEquals(it, QueueRowShape.fromPreference(it.token))
        }
        assertNull("follow leaves the outline to the style", QueueRowShape.FOLLOW.outline)
    }

    @Test
    fun `a capsule has round ends at every row height`() {
        val pill = QueueRowShape.PILL.outline as QueueRowOutline.Rounded
        listOf(40f, 54f, 76f, 102f).forEach { height ->
            pill.radii(200f, height, 1f).forEach { radius ->
                assertEquals("capsule at $height", height / 2f, radius, 1e-4f)
            }
        }
    }

    @Test
    fun `no corner can exceed half the shorter side`() {
        val outline = QueueRowOutline.Rounded.uniform(26f)
        // A normal row keeps the radius it asked for...
        assertEquals(26f, outline.radii(172f, 54f, 1f)[0], 0f)
        // ...a short one is held to a capsule, so neighbouring corners never overlap.
        assertEquals(20f, outline.radii(172f, 40f, 1f)[0], 0f)
        // Density scales the radius before the clamp.
        assertEquals(52f, outline.radii(344f, 108f, 2f)[0], 0f)
    }

    @Test
    fun `a rounded outline runs at the box edge except in its corners`() {
        val outline = QueueRowOutline.Rounded.uniform(20f)
        assertEquals(0f, outline.leftInset(27f, 172f, 54f, 1f), 0f)
        assertEquals(20f, outline.leftInset(0f, 172f, 54f, 1f), 1e-4f)
        assertEquals(20f, outline.rightInset(54f, 172f, 54f, 1f), 1e-4f)
        // Halfway round the corner.
        val inset = outline.leftInset(10f, 172f, 54f, 1f)
        assertEquals(20f - kotlin.math.sqrt(400f - 100f), inset, 1e-4f)
    }

    @Test
    fun `the leaf is round at two opposite corners and tight at the others`() {
        val leaf = QueueRowShape.LEAF.outline as QueueRowOutline.Rounded
        val radii = leaf.radii(172f, 54f, 1f)
        assertEquals(27f, radii[0], 0f)
        assertEquals(6f, radii[1], 0f)
        assertEquals(27f, radii[2], 0f)
        assertEquals(6f, radii[3], 0f)
    }

    @Test
    fun `a hexagon is pointed at the middle of both ends`() {
        val hexagon = QueueRowOutline.Hexagon(12f)
        val points = hexagon.points(0f, 0f, 100f, 40f, 1f)
        assertEquals(12, points.size)
        assertEquals(12f, points[0], 0f); assertEquals(0f, points[1], 0f)
        assertEquals(88f, points[2], 0f); assertEquals(0f, points[3], 0f)
        assertEquals(100f, points[4], 0f); assertEquals(20f, points[5], 0f)
        assertEquals(0f, points[10], 0f); assertEquals(20f, points[11], 0f)
        assertEquals(0f, hexagon.leftInset(20f, 100f, 40f, 1f), 0f)
        assertEquals(12f, hexagon.leftInset(0f, 100f, 40f, 1f), 1e-4f)
        assertEquals(6f, hexagon.rightInset(30f, 100f, 40f, 1f), 1e-4f)
    }

    @Test
    fun `a hexagon's point is held to a third of the width`() {
        val points = QueueRowOutline.Hexagon(50f).points(0f, 0f, 30f, 20f, 1f)
        assertEquals(10f, points[0], 0f)
        assertEquals(20f, points[2], 0f)
    }

    @Test
    fun `a mark down the left edge sits where the edge is deepest over its span`() {
        val outline = QueueRowOutline.Rounded.uniform(STADIUM_ROW / 2f)
        val top = STADIUM_ROW * .18f
        val bottom = STADIUM_ROW * .82f
        val edge = outline.leftEdgeOver(top, bottom, 172f, STADIUM_ROW, 1f)
        assertEquals(outline.leftInset(top, 172f, STADIUM_ROW, 1f), edge, 1e-4f)
        assertTrue("a capsule's keyline steps in from the box edge", edge > 5f)
        // A square row's keyline stays at the edge, where every style drew it.
        assertEquals(0f,
                QueueRowOutline.Rounded.uniform(0f).leftEdgeOver(top, bottom, 172f, 54f, 1f), 0f)
    }

    @Test
    fun `the default circle in the default pill keeps its 5dp inset`() {
        // Glass at the normal row size: 54dp row, 26dp corners, a 44dp circular cover.
        val start = QueueRowOutline.Rounded.uniform(26f)
                .coverStart(216f, 54f, 44f, CoverCorners.uniform(.5f), 5f, CLEARANCE, 1f)
        assertEquals(5f, start, 1e-3f)
    }

    @Test
    fun `every style keeps the inset its own cover was drawn with`() {
        listOf(22f, 30f, 52f, 78f).forEach { content ->
            QueueStyles.ALL.forEach { style ->
                val height = content + style.verticalPaddingDp * 2
                val size = minOf(height - 10f, 64f)
                val start = style.outline.coverStart(
                        height * 4, height, size,
                        CoverCorners.uniform(style.coverCornerFraction), 5f, CLEARANCE, 1f)
                assertEquals("${style.token} at $content moved its cover", 5f, start, 1e-3f)
            }
        }
    }

    @Test
    fun `a square cover steps into a capsule far enough to stay inside it`() {
        val pill = QueueRowShape.PILL.outline!!
        val height = 102f
        val size = 64f
        val square = CoverCorners.uniform(0f)
        val start = pill.coverStart(height * 4, height, size, square, 5f, CLEARANCE, 1f)
        assertTrue("square cover should step in, started at $start", start > 5f)
        // Every point of the cover's left edge now keeps the clearance from the outline.
        val top = (height - size) / 2f
        for (step in 0..64) {
            val y = top + size * step / 64f
            assertTrue(start + 1e-3f >= pill.leftInset(y, height * 4, height, 1f) + CLEARANCE)
        }
    }

    @Test
    fun `text keeps clear of a hexagon's points`() {
        assertEquals(16f, QueueRowOutline.Rounded.uniform(26f).textInsetDp, 0f)
        assertEquals(24f, QueueRowOutline.Hexagon(QueueRowOutline.BASALT_POINT_DP).textInsetDp, 0f)
    }

    @Test
    fun `a cover cut comes from the shared cover vocabulary`() {
        val leaf = CoverCorners.of(CoverShape.LEAF)
        assertEquals(CoverShape.LEAF.topLeft, leaf.topLeft, 0f)
        assertEquals(CoverShape.LEAF.bottomLeft, leaf.bottomLeft, 0f)
        assertTrue(CoverCorners.uniform(.2f).isUniform)
        assertEquals(10f, CoverCorners.uniform(.5f).radii(20f)[0], 0f)
        // A circle's left edge is at its middle and a full radius in at the top.
        assertEquals(0f, CoverCorners.uniform(.5f).leftInset(10f, 20f), 1e-4f)
        assertEquals(10f, CoverCorners.uniform(.5f).leftInset(0f, 20f), 1e-4f)
    }

    private companion object {
        const val STADIUM_ROW = 54f
        const val CLEARANCE = QueueRowOutline.COVER_CLEARANCE_DP
    }
}
