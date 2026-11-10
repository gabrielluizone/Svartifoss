package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleBandTest {

    @Test
    fun `the ceiling is what the band holds after the artist row`() {
        // 65dp band, 16dp artist row, 24dp lines: two lines (48dp) fit, three (72dp) do not.
        assertEquals(2, TitleBand.lineCeiling(65f, 24f, artistRowDp = 16f))
        // Without an artist row the band holds 2.7 lines - still two whole ones.
        assertEquals(2, TitleBand.lineCeiling(65f, 24f))
        assertEquals(3, TitleBand.lineCeiling(80f, 24f))
    }

    @Test
    fun `a larger title holds fewer lines`() {
        assertEquals(2, TitleBand.lineCeiling(65f, 24f, titleScale = 1f, artistRowDp = 16f))
        assertEquals(1, TitleBand.lineCeiling(65f, 24f, titleScale = 1.6f, artistRowDp = 16f))
    }

    @Test
    fun `a larger artist row leaves the title less room`() {
        assertEquals(2, TitleBand.lineCeiling(70f, 24f, artistRowDp = 16f, artistScale = 1f))
        assertEquals(1, TitleBand.lineCeiling(70f, 24f, artistRowDp = 16f, artistScale = 1.6f))
    }

    @Test
    fun `there is always at least one line`() {
        // A title that does not fit even once is clipped by its block, never dropped.
        assertEquals(1, TitleBand.lineCeiling(10f, 24f, artistRowDp = 16f))
        assertEquals(1, TitleBand.lineCeiling(0f, 24f))
        assertEquals(1, TitleBand.lineCeiling(50f, 0f))
    }

    @Test
    fun `the design sizes never shrink, even where the reference metrics overflow the band`() {
        // Expressive's band is about 27dp at 192dp, while its 21dp title line and 13dp artist row
        // add up to 34: the glyphs fit at 100%, so 100% must stay exactly as designed.
        assertEquals(1f, TitleBand.fitScale(27f, 21f, artistRowDp = 13f), 0f)
        assertEquals(1f, TitleBand.fitScale(70f, 26f, artistRowDp = 19f), 0f)
    }

    @Test
    fun `smaller text never grows`() {
        assertEquals(1f, TitleBand.fitScale(27f, 21f, .8f, 13f, .8f), 0f)
    }

    @Test
    fun `a roomy band lets the text grow until it is full`() {
        // 45dp of text at 100% in a 70dp band: 140% (63dp) still fits, 160% (72dp) stops at 70.
        assertEquals(1f, TitleBand.fitScale(70f, 26f, 1.4f, 19f, 1.4f), 0f)
        assertEquals(70f / 72f, TitleBand.fitScale(70f, 26f, 1.6f, 19f, 1.6f), 1e-5f)
    }

    @Test
    fun `a band already full at 100 percent holds the text at the design height`() {
        val fit = TitleBand.fitScale(27f, 21f, 1.4f, 13f, 1.4f)
        assertEquals(1f / 1.4f, fit, 1e-5f)
        // Fitted, one title line and the artist row are exactly as tall as the design.
        assertEquals(34f, 21f * 1.4f * fit + 13f * 1.4f * fit, 1e-4f)
    }

    @Test
    fun `one factor for both keeps the proportion the user chose`() {
        val fit = TitleBand.fitScale(27f, 21f, titleScale = 1.4f, artistRowDp = 13f, artistScale = 1f)
        assertEquals(34f / (21f * 1.4f + 13f), fit, 1e-5f)
        // The title still grows past 100% - the artist gives up the room it takes.
        assertTrue(1.4f * fit > 1f)
        assertTrue(1f * fit < 1f)
        assertEquals(1.4f, (1.4f * fit) / (1f * fit), 1e-5f)
    }

    @Test
    fun `an element that is not drawn takes no room`() {
        // Ribbon's artist alone, between its top and the cover rail: 23dp for a 22dp row.
        assertEquals(1f, TitleBand.fitScale(23f, 0f, artistRowDp = 22f), 0f)
        assertEquals(23f / (22f * 1.4f), TitleBand.fitScale(23f, 0f, artistRowDp = 22f, artistScale = 1.4f), 1e-5f)
        // A title alone in a band it fits at 100% only.
        assertEquals(21f / (21f * 1.5f), TitleBand.fitScale(20f, 21f, titleScale = 1.5f), 1e-5f)
    }

    @Test
    fun `Ribbon's title band ends where its 100 percent line does`() {
        // On the 192dp reference the 29dp line starting at 77% ends at the floor, so 100% is
        // drawn as designed and anything larger stops at it rather than going deeper.
        val referenceDepth = FaceGeometry.Ribbon.TITLE_TOP_FRACTION +
                FaceGeometry.Ribbon.TITLE_LINE_HEIGHT_DP / 192f
        assertEquals(referenceDepth, TitleBand.RIBBON_TITLE_FLOOR, .002f)
        val band = 192f * (TitleBand.RIBBON_TITLE_FLOOR - FaceGeometry.Ribbon.TITLE_TOP_FRACTION)
        val line = FaceGeometry.Ribbon.TITLE_LINE_HEIGHT_DP
        assertEquals(1f, TitleBand.fitScale(band, line), 0f)
        assertEquals(1f / 1.2f, TitleBand.fitScale(band, line, titleScale = 1.2f), 1e-5f)
        // A larger watch holds a proportionally larger title at the same depth.
        val large = 225f * (TitleBand.RIBBON_TITLE_FLOOR - FaceGeometry.Ribbon.TITLE_TOP_FRACTION)
        assertTrue(TitleBand.fitScale(large, line, titleScale = 1.15f) == 1f)
    }

    @Test
    fun `nothing to measure leaves the text alone`() {
        assertEquals(1f, TitleBand.fitScale(30f, 0f, artistRowDp = 0f), 0f)
        assertEquals(1f, TitleBand.fitScale(0f, 0f), 0f)
        assertEquals(1f, TitleBand.fitScale(30f, 21f, Float.NaN, 13f, 1f), 0f)
    }

    @Test
    fun `a centred block may grow twice the distance from the centre to the floor`() {
        assertEquals(.34f, TitleBand.centredBand(.67f), 1e-4f)
        assertEquals(0f, TitleBand.centredBand(.4f), 0f)
        assertEquals(.45f, TitleBand.movedBand(.22f, .67f), 1e-4f)
        assertEquals(0f, TitleBand.movedBand(.7f, .6f), 0f)
    }

    @Test
    fun `Poster stops at its play mark and drops lower as its controls go`() {
        // The mark sits higher when the progress line is drawn under it.
        assertEquals(.71f, TitleBand.posterFloor(controls = true, progressLine = true, trackTime = true), 1e-4f)
        assertEquals(.67f, TitleBand.posterFloor(controls = true, progressLine = false, trackTime = true), 1e-4f)
        val withoutMark = TitleBand.posterFloor(controls = false, progressLine = true, trackTime = true)
        val withoutLine = TitleBand.posterFloor(controls = false, progressLine = false, trackTime = true)
        val withNothing = TitleBand.posterFloor(controls = false, progressLine = false, trackTime = false)
        assertTrue(withoutMark > .71f && withoutLine > withoutMark && withNothing >= withoutLine)
    }

    @Test
    fun `Studio stops at its progress orb`() {
        assertEquals(.64f, TitleBand.studioFloor(progressOrb = true, controls = true, trackTime = true), 1e-4f)
        assertTrue(TitleBand.studioFloor(progressOrb = false, controls = true, trackTime = true) > .85f)
    }

    @Test
    fun `on a 192dp watch the reference titles fit as many lines as they did`() {
        // The default composition of each face, at 100%: Poster and Studio keep their two lines,
        // Material's band above the disc only ever held one with the artist under it.
        val screen = 192f
        val poster = TitleBand.lineCeiling(
                screen * TitleBand.centredBand(TitleBand.posterFloor(true, false, true)),
                FaceGeometry.CuratedText.POSTER_TITLE_LINE_DP,
                artistRowDp = FaceGeometry.CuratedText.POSTER_ARTIST_ROW_DP)
        val studio = TitleBand.lineCeiling(
                screen * TitleBand.centredBand(TitleBand.studioFloor(true, true, true)),
                FaceGeometry.CuratedText.STUDIO_TITLE_LINE_DP,
                artistRowDp = FaceGeometry.CuratedText.STUDIO_ARTIST_ROW_DP)
        val material = TitleBand.lineCeiling(
                screen * (TitleBand.MATERIAL_FLOOR - TitleBand.MATERIAL_TOP),
                FaceGeometry.CuratedText.MATERIAL_TITLE_LINE_DP,
                artistRowDp = FaceGeometry.CuratedText.MATERIAL_ARTIST_ROW_DP)
        assertEquals(2, poster)
        assertEquals(2, studio)
        assertEquals(1, material)
    }

    @Test
    fun `the shared chrome in the bottom band stops a block before the face's own floor does`() {
        // Poster with its controls hidden stops at its track time, near the bottom of the screen;
        // a mini-button row or the Up Next pill starting higher is where the block has to stop.
        val posterWithoutMark = TitleBand.posterFloor(controls = false, progressLine = false, trackTime = true)
        assertEquals(.66f, TitleBand.effectiveFloor(posterWithoutMark, .66f), 1e-4f)
        // Chrome below the face's own controls changes nothing.
        assertEquals(.71f, TitleBand.effectiveFloor(.71f, .78f), 1e-4f)
        assertEquals(.71f, TitleBand.effectiveFloor(.71f, 1f), 1e-4f)
    }

    @Test
    fun `the track time is lifted above the shared chrome but never over the middle`() {
        val gap = 10f / 192f
        // Nothing in the bottom band: the time stays where the face put it.
        assertEquals(.38f, TitleBand.footerOffset(.38f, 1f, gap), 1e-4f)
        // The Up Next pill: lifted to its floor, .16 below the centre, which is above the pill.
        assertEquals(TitleBand.FOOTER_MIN_OFFSET, TitleBand.footerOffset(.38f, TitleBand.UP_NEXT_PILL_TOP, gap), 1e-4f)
        // A mini-button row starting at .80: lifted to just above it.
        assertEquals(.80f - .5f - gap, TitleBand.footerOffset(.42f, .80f, gap), 1e-4f)
        // A time the face already put higher than that is left alone.
        assertEquals(.12f, TitleBand.footerOffset(.12f, .66f, gap), 1e-4f)
    }

    @Test
    fun `a block moved to the top starts under the clock only when the clock is shown`() {
        assertEquals(TitleBand.CLOCK_CLEARANCE, TitleBand.movedTop(clockShown = true), 0f)
        assertEquals(TitleBand.EDGE_MARGIN, TitleBand.movedTop(clockShown = false), 0f)
        assertTrue(TitleBand.CLOCK_CLEARANCE > TitleBand.EDGE_MARGIN)
    }

    @Test
    fun `Material's floor is its disc's top`() {
        assertEquals(.17f, TitleBand.MATERIAL_TOP, 0f)
        assertEquals(FaceGeometry.CuratedText.MATERIAL_TOP_FRACTION, TitleBand.MATERIAL_TOP, 0f)
        assertEquals(.35f, TitleBand.MATERIAL_FLOOR, 1e-4f)
    }
}
