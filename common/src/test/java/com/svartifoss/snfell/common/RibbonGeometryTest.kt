package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RibbonGeometryTest {
    private val epsilon = .0001f

    @Test
    fun `queue cards have one shared gap around the hero cover`() {
        val ribbon = FaceGeometry.Ribbon
        val outerRight = ribbon.OUTER_COLUMN_CENTER_X +
                ribbon.COLUMN_WIDTH_FRACTION / 2f
        val innerLeft = ribbon.INNER_COLUMN_CENTER_X -
                ribbon.COLUMN_WIDTH_FRACTION / 2f
        val innerRight = ribbon.INNER_COLUMN_CENTER_X +
                ribbon.COLUMN_WIDTH_FRACTION / 2f
        val heroLeft = .5f - ribbon.CENTER_COVER_WIDTH_FRACTION / 2f

        assertEquals(ribbon.COVER_GAP_FRACTION, innerLeft - outerRight, epsilon)
        assertEquals(ribbon.COVER_GAP_FRACTION, heroLeft - innerRight, epsilon)
    }

    @Test
    fun `all ribbon covers are vertically centered in the dial`() {
        val ribbon = FaceGeometry.Ribbon

        assertEquals(
                .5f,
                ribbon.COLUMN_TOP_FRACTION + ribbon.COLUMN_HEIGHT_FRACTION / 2f,
                epsilon)
        assertEquals(
                .5f,
                ribbon.CENTER_COVER_TOP_FRACTION + ribbon.CENTER_COVER_HEIGHT_FRACTION / 2f,
                epsilon)
    }

    @Test
    fun `hero cover corners are softer than the original rounded rectangle`() {
        assertTrue(FaceGeometry.Ribbon.CENTER_COVER_CORNER_FRACTION > .18f)
    }
}
