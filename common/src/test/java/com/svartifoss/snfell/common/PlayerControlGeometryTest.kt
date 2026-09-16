package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlGeometryTest {
    @Test
    fun crowdedRowsNeverShrinkBelowTheirReadableFloor() {
        for (screen in listOf(160f, 192f, 224f, 225f, 240f)) {
            for (face in listOf("expressive", "material")) {
                for (rise in listOf(0f, 18f, 36f)) {
                    val scale = PlayerControlGeometry.miniRowScale(face, screen,
                            screen - 34f, 38f + rise)
                    assertTrue(scale >= PlayerControlGeometry.MIN_MINI_SCALE)
                    assertTrue(scale <= 1f)
                    assertTrue("38dp mini buttons remain at least 29dp high", 38f * scale >= 29f)
                }
            }
        }
    }

    @Test
    fun metadataRingAndTimeNeverShareTheMiniButtonBand() {
        for (screen in listOf(160f, 192f, 224f, 225f, 240f)) {
            for (face in listOf("expressive", "material")) {
                for (lowerFraction in listOf(.56f, .66f, .8f, 1f)) {
                    for (time in listOf(false, true)) {
                        for (position in TextBlockPosition.entries) {
                            val lowerTop = screen * lowerFraction
                            val layout = PlayerControlGeometry.transportLayout(
                                    face, screen, lowerTop, time, position)
                            val ringTop = layout.centerY - layout.diameter / 2f
                            val ringBottom = layout.centerY + layout.diameter / 2f
                            val metadataBottom = layout.metadataTop + layout.metadataHeight
                            val timeTop = layout.timeCenterY - layout.timeHeight / 2f
                            val timeBottom = layout.timeCenterY + layout.timeHeight / 2f
                            assertTrue(layout.metadataHeight > 0f)
                            assertTrue(layout.diameter > 0f)
                            assertTrue(ringTop >= 0f)
                            assertTrue(ringBottom <= lowerTop - 5.99f)
                            assertTrue(metadataBottom <= lowerTop - 5.99f)
                            assertTrue(timeBottom <= lowerTop - 5.99f)
                            if (position == TextBlockPosition.BOTTOM) {
                                assertTrue(timeBottom <= layout.metadataTop - 5.99f)
                            } else {
                                assertTrue(metadataBottom <= ringTop - 5.99f)
                            }
                            // Requesting the readout is not the same as getting it: the
                            // allocation drops it when the band cannot hold it alongside a
                            // tappable control and a line of text. What it must never do is
                            // place it on top of the ring.
                            if (layout.timeHeight > 0f) {
                                assertTrue(timeTop >= ringBottom + 3.99f)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun expressive192WithBottomHintAndMinisReservesTimeOutsideTheRow() {
        val rowTop = 192f - 4f - 16f - 6f - 38f
        val layout = PlayerControlGeometry.transportLayout("expressive", 192f, rowTop, true)
        assertTrue(layout.timeCenterY + layout.timeHeight / 2f <= rowTop - 6f)
        assertTrue(layout.metadataTop + layout.metadataHeight < layout.centerY - layout.diameter / 2f)
    }


    @Test
    fun hidingTheTransportGivesItsBandBack() {
        for (screen in listOf(160f, 192f, 225f, 240f)) {
            for (face in listOf("expressive", "material")) {
                val shown = PlayerControlGeometry.transportLayout(
                        face, screen, screen, true, showTransport = true)
                val hidden = PlayerControlGeometry.transportLayout(
                        face, screen, screen, true, showTransport = false)
                assertEquals("$face/$screen keeps a transport it does not draw",
                        0f, hidden.diameter, .0001f)
                assertTrue("$face/$screen must give the text more room, not the same",
                        hidden.metadataHeight > shown.metadataHeight)
                // Centred in what is free, rather than pinned above a ring that is gone.
                val top = screen * .17f
                val bottom = minOf(screen - 6f, screen - 6f)
                val group = hidden.metadataHeight + hidden.timeHeight + 4f
                assertEquals("$face/$screen must centre the group in the freed area",
                        top + ((bottom - top) - group) / 2f, hidden.metadataTop, .01f)
            }
        }
    }

    @Test
    fun aHiddenTransportStillLeavesTheTimeBelowTheText() {
        val hidden = PlayerControlGeometry.transportLayout(
                "expressive", 192f, 192f, true, showTransport = false)
        assertTrue(hidden.timeCenterY - hidden.timeHeight / 2f >=
                hidden.metadataTop + hidden.metadataHeight - .01f)
    }

    @Test
    fun aHiddenTransportStillHonoursAMovedTextBlock() {
        val screen = 192f
        val top = PlayerControlGeometry.transportLayout("expressive", screen, screen, false,
                TextBlockPosition.TOP, showTransport = false)
        val bottom = PlayerControlGeometry.transportLayout("expressive", screen, screen, false,
                TextBlockPosition.BOTTOM, showTransport = false)
        assertEquals(screen * .17f, top.metadataTop, .01f)
        assertTrue(bottom.metadataTop > top.metadataTop)
        assertTrue(bottom.metadataTop + bottom.metadataHeight <= screen - 6f + .01f)
    }

    @Test
    fun theControlKeepsATappableMinimumBeforeTheTextYields() {
        // A band that cannot hold everything: the metadata gives up its designed share first.
        val bands = PlayerControlGeometry.allocateTransportBands(
                area = 76f, preferredDiameter = 78f, wantsTime = false)
        assertEquals(PlayerControlGeometry.TRANSPORT_MIN_DIAMETER, bands.diameter, .01f)
        assertTrue("the text yields before the control stops being a touch target",
                bands.metadata < PlayerControlGeometry.METADATA_MAX)
        assertTrue(bands.metadata >= PlayerControlGeometry.METADATA_MIN)
    }

    @Test
    fun theTimeIsTheFirstThingToGo() {
        val roomy = PlayerControlGeometry.allocateTransportBands(140f, 78f, wantsTime = true)
        assertEquals(PlayerControlGeometry.TIME_HEIGHT, roomy.timeHeight, .01f)

        val tight = PlayerControlGeometry.allocateTransportBands(80f, 78f, wantsTime = true)
        assertEquals("the readout goes before the control or the text does", 0f, tight.timeHeight, .01f)
        assertEquals(PlayerControlGeometry.TRANSPORT_MIN_DIAMETER, tight.diameter, .01f)
        assertTrue(tight.metadata >= PlayerControlGeometry.METADATA_MIN)
    }

    @Test
    fun nothingIsAskedToYieldWhileEverythingFits() {
        val bands = PlayerControlGeometry.allocateTransportBands(160f, 78f, wantsTime = true)
        assertEquals(PlayerControlGeometry.METADATA_MAX, bands.metadata, .01f)
        assertEquals(78f, bands.diameter, .01f)
        assertEquals(PlayerControlGeometry.TIME_HEIGHT, bands.timeHeight, .01f)
    }

    @Test
    fun theTextWinsTheRemainderOnceThereIsNothingLeftToYield() {
        val bands = PlayerControlGeometry.allocateTransportBands(34f, 78f, wantsTime = true)
        assertEquals(0f, bands.timeHeight, .01f)
        assertTrue("one title line survives", bands.metadata >= minOf(
                PlayerControlGeometry.METADATA_MIN, 34f - PlayerControlGeometry.METADATA_GAP))
        assertTrue("and the control takes what is left, without going negative", bands.diameter >= 0f)
    }

    /**
     * Monotonic *within one time decision*, and never overcommitted.
     *
     * Deliberately not monotonic across that decision: the moment the band grows enough to hold the
     * readout again, the readout takes its 18dp back and the control returns to its minimum. That
     * is the order doing what it says - the time is what the user asked for, and the control has a
     * floor it is already sitting on - not a bug to smooth over.
     */
    @Test
    fun theAllocationIsMonotonicWithinOneTimeDecision() {
        for (wantsTime in listOf(false, true)) {
            var previousDiameter = -1f
            var previousTime = -1f
            for (area in 40..220 step 2) {
                val bands = PlayerControlGeometry.allocateTransportBands(
                        area.toFloat(), 78f, wantsTime)
                if (bands.timeHeight == previousTime) {
                    assertTrue("more room must never produce a smaller control " +
                            "(area=$area, wantsTime=$wantsTime)",
                            bands.diameter >= previousDiameter - .01f)
                }
                previousDiameter = bands.diameter
                previousTime = bands.timeHeight
                assertTrue("bands must stay disjoint and non-negative (area=$area)",
                        bands.metadata >= 0f && bands.diameter >= 0f &&
                                bands.metadata + bands.diameter + bands.timeHeight +
                                bands.timeGap + PlayerControlGeometry.METADATA_GAP <= area + .01f)
            }
        }
    }

    /** Once the band can hold the minimums at all, the control is never below its floor. */
    @Test
    fun theControlNeverDropsBelowItsFloorWhileTheBandCanHoldIt() {
        for (area in 80..220 step 2) {
            for (wantsTime in listOf(false, true)) {
                val bands = PlayerControlGeometry.allocateTransportBands(
                        area.toFloat(), 78f, wantsTime)
                assertTrue("area=$area wantsTime=$wantsTime produced ${bands.diameter}dp",
                        bands.diameter >= PlayerControlGeometry.TRANSPORT_MIN_DIAMETER - .01f)
            }
        }
    }

    @Test
    fun theRowProtectsTheMinimumItCanActuallyReach() {
        // The three copies of this used to quote the *preferred* transport size, so the row was
        // scaled back to protect a control that had room to shrink first.
        for (screen in listOf(160f, 192f, 225f, 240f)) {
            val minimum = PlayerControlGeometry.minimumContentBottom(screen)
            assertTrue("$screen must reserve less than the preferred transport asks for",
                    minimum < screen * .17f + 78f + PlayerControlGeometry.METADATA_MIN)
            assertTrue(minimum > screen * .17f)
        }
    }

    @Test
    fun coverRailsLeaveTitleAndBottomControlsSeparate() {
        for (screen in listOf(160f, 192f, 224f, 225f, 240f)) {
            for (lowerFraction in listOf(.56f, .66f, .8f, 1f)) {
                for (ribbon in listOf(false, true)) {
                    val layout = PlayerControlGeometry.coverRailLayout(screen, screen * lowerFraction,
                            if (ribbon) .28f else .215f,
                            if (ribbon) .72f else .735f,
                            if (ribbon) .77f else .749f,
                            if (ribbon) 29f else 25f)
                    assertTrue(layout.artHeight > 0f)
                    assertTrue(layout.titleHeight > 0f)
                    assertTrue(layout.artTop + layout.artHeight < layout.titleTop)
                    assertTrue(layout.titleTop + layout.titleHeight <= screen * lowerFraction - 5.99f)
                }
            }
        }
    }

    @Test
    fun uncenteredFacesKeepTheirMiniButtonScale() {
        assertEquals(1f, PlayerControlGeometry.miniRowScale("note", 160f, 60f, 80f), 0f)
    }
}
