package com.svartifoss.snfell.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaActionIconRenderTest {
    @Test
    fun theFirstCandidateWinsWheneverEveryRenderAgrees() {
        // Every loading path drawing the same thing is the ordinary case, and it must resolve to
        // the publisher-themed context this app has always used.
        val same = GlyphCoverage(boundsArea = 900, inkPixels = 260)
        assertEquals(0, bestGlyphRender(listOf(same, same, same)))
    }

    @Test
    fun aRenderThatLostPathsLosesToTheOneThatDrewThemAll() {
        // YouTube Music's shuffle: the publisher-themed context dropped both arrowheads, leaving
        // two strokes and the "on" dot inside a much smaller box than the whole glyph occupies.
        val fragment = GlyphCoverage(boundsArea = 425, inkPixels = 120)
        val whole = GlyphCoverage(boundsArea = 1024, inkPixels = 310)
        assertEquals(1, bestGlyphRender(listOf(fragment, whole)))
    }

    @Test
    fun sameBoundsFallBackToWhicheverDrewMoreInk() {
        // A glyph can lose an interior path without its bounding box moving - a repeat badge, the
        // dot inside a shuffle - so area alone is not the whole comparison.
        val hollow = GlyphCoverage(boundsArea = 1024, inkPixels = 240)
        val complete = GlyphCoverage(boundsArea = 1024, inkPixels = 305)
        assertEquals(1, bestGlyphRender(listOf(hollow, complete)))
        assertEquals(0, bestGlyphRender(listOf(complete, hollow)))
    }

    @Test
    fun anEmptyRenderNeverWinsAndAllEmptyMeansSendNoIcon() {
        val blank = GlyphCoverage(boundsArea = 0, inkPixels = 0)
        // A vector that resolved to nothing at all reports no ink; it must not beat a real render
        // just by being listed first, and it must never be serialized as a "valid" icon.
        assertEquals(1, bestGlyphRender(listOf(blank, GlyphCoverage(400, 90))))
        assertEquals(-1, bestGlyphRender(listOf(blank, null, blank)))
        assertEquals(-1, bestGlyphRender(emptyList()))
    }

    @Test
    fun aCandidateThatFailedToLoadIsSkippedRatherThanTakenAsEmpty() {
        // Nulls are loading failures (no such resource, package context refused), not renders.
        assertEquals(2, bestGlyphRender(listOf(null, null, GlyphCoverage(600, 150))))
    }
}
