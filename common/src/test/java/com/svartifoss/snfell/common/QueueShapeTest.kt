package com.svartifoss.snfell.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueShapeTest {

    @Test
    fun `strata stack upwards from the foot of the row`() {
        val tops = QueueShape.strataLineTops(40f, 1.6f, 1.4f)
        assertEquals(QueueShape.STRATA_LAYERS, tops.size)
        assertEquals(38.4f, tops[0], 1e-4f)
        assertEquals(35.4f, tops[1], 1e-4f)
        assertEquals(32.4f, tops[2], 1e-4f)
        assertTrue(tops[0] > tops[1] && tops[1] > tops[2])
    }

    @Test
    fun `the playing entry is the landmark and the past is the smallest`() {
        assertEquals(QueueShape.Node.PAST, QueueShape.node(-3))
        assertEquals(QueueShape.Node.NOW, QueueShape.node(0))
        assertEquals(QueueShape.Node.UPCOMING, QueueShape.node(4))
        assertTrue(QueueShape.nodeScale(QueueShape.Node.NOW) >
                QueueShape.nodeScale(QueueShape.Node.UPCOMING))
        assertTrue(QueueShape.nodeScale(QueueShape.Node.UPCOMING) >
                QueueShape.nodeScale(QueueShape.Node.PAST))
    }

    @Test
    fun `a station on an unscaled row sits where the row draws its cover`() {
        // A 200 x 200 viewport with 10 px of side padding: rows are 180 wide and start at 10.
        val station = QueueShape.station(
                index = 2, viewportWidth = 200f, viewportHeight = 200f, itemWidth = 180f,
                stationX = 27f, itemOffset = 0f, scale = 1f)
        assertEquals(10f + 27f, station.x, 1e-4f)
        assertEquals(100f, station.y, 1e-4f)
    }

    @Test
    fun `a scaled row draws its station closer to the centre line`() {
        val full = QueueShape.station(0, 200f, 200f, 180f, 27f, 60f, 1f)
        val scaled = QueueShape.station(0, 200f, 200f, 180f, 27f, 60f, .8f, alpha = .5f)
        // Scaled about the row's own centre, so a point left of centre moves right.
        assertEquals(100f + (27f - 90f) * .8f, scaled.x, 1e-4f)
        assertTrue(scaled.x > full.x)
        // The list reports the row's centre already scaled; a station is halfway down its row.
        assertEquals(160f, scaled.y, 1e-4f)
        assertEquals(.5f, scaled.alpha, 0f)
    }

    @Test
    fun `the route joins neighbouring stations and splits at the playing one`() {
        val stations = listOf(
                QueueShape.Station(3, 30f, 60f),
                QueueShape.Station(4, 28f, 110f),
                QueueShape.Station(5, 30f, 160f))
        val segments = QueueShape.routeSegments(stations, itemCount = 10, playingIndex = 4,
                continuesBelow = false, tail = 20f)
        // A tail above (the queue starts before entry 3), two joins, a tail below.
        assertEquals(4, segments.size)
        val (above, into, outOf, below) = segments
        assertTrue(above.fadesAtStart && !above.fadesAtEnd)
        assertEquals(40f, above.fromY, 0f)
        assertTrue("the stretch above the playing entry is travelled", above.travelled)
        assertTrue(into.travelled)
        assertEquals(28f, into.toX, 0f)
        assertFalse("the stretch below it is still to come", outOf.travelled)
        assertTrue(below.fadesAtEnd && !below.fadesAtStart)
        assertFalse(below.travelled)
        assertEquals(180f, below.toY, 0f)
    }

    @Test
    fun `the route starts and ends at the queue's ends`() {
        val stations = listOf(QueueShape.Station(0, 30f, 60f), QueueShape.Station(1, 30f, 110f))
        val closed = QueueShape.routeSegments(stations, itemCount = 2, playingIndex = 0,
                continuesBelow = false, tail = 20f)
        assertEquals("no tail before the first entry or after the last", 1, closed.size)
        val more = QueueShape.routeSegments(stations, itemCount = 2, playingIndex = 0,
                continuesBelow = true, tail = 20f)
        assertEquals("Load more promises entries below", 2, more.size)
        assertTrue(more.last().fadesAtEnd)
    }

    @Test
    fun `with nothing playing nothing is travelled`() {
        val stations = listOf(QueueShape.Station(2, 30f, 60f), QueueShape.Station(3, 30f, 110f))
        val segments = QueueShape.routeSegments(stations, itemCount = 9, playingIndex = -1,
                continuesBelow = false, tail = 20f)
        assertTrue(segments.none { it.travelled })
    }

    @Test
    fun `the route is travelled all the way when the playing entry is below the screen`() {
        val stations = listOf(QueueShape.Station(2, 30f, 60f), QueueShape.Station(3, 30f, 110f))
        val segments = QueueShape.routeSegments(stations, itemCount = 9, playingIndex = 7,
                continuesBelow = false, tail = 20f)
        assertTrue(segments.all { it.travelled })
    }

    @Test
    fun `stations that are not neighbours are not joined, and a stretch fades with its rows`() {
        val stations = listOf(
                QueueShape.Station(1, 30f, 60f, alpha = 1f),
                QueueShape.Station(2, 30f, 110f, alpha = .4f),
                QueueShape.Station(4, 30f, 160f))
        val joins = QueueShape.routeSegments(stations, itemCount = 5, playingIndex = 2,
                continuesBelow = false, tail = 20f).filter { !it.fadesAtStart && !it.fadesAtEnd }
        assertEquals(1, joins.size)
        assertEquals(.4f, joins.single().alpha, 0f)
        assertTrue(QueueShape.routeSegments(emptyList(), 5, 2, false, 20f).isEmpty())
    }

    @Test
    fun `only entries that already played are dimmed, and only on request`() {
        assertEquals(0.5f, QueueShape.rowAlpha(-1, true), 0f)
        assertEquals(1f, QueueShape.rowAlpha(-1, false), 0f)
        assertEquals(1f, QueueShape.rowAlpha(0, true), 0f)
        assertEquals(1f, QueueShape.rowAlpha(2, true), 0f)
    }

    @Test
    fun `offsets are relative to the first playing entry or absent`() {
        assertArrayEquals(
                intArrayOf(-2, -1, 0, 1),
                QueueShape.offsets(listOf(false, false, true, false))!!.toIntArray())
        assertNull(QueueShape.offsets(listOf(false, false)))
        assertNull(QueueShape.offsets(emptyList()))
    }
}
