package com.svartifoss.snfell.watch.view.face

import androidx.compose.ui.unit.dp
import com.svartifoss.snfell.common.FaceGeometry
import com.svartifoss.snfell.common.RoundScreenText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextColumnInsetsTest {
    @Test
    fun immersiveArtistFitsTheBottomChordEvenAtDefaultPlacement() {
        val state = NowPlayingFaceState()
        val screen = 200.dp
        val insets = state.blockLineInsets(screen, BlockAnchor.BOTTOM,
                FaceGeometry.Immersive.BOTTOM_PADDING_FRACTION,
                listOf(22.dp, 24.dp, 0.dp), floor = 20.dp, fitToScreen = true)
        val artistInset = (insets.outer + insets.extra(1)) / screen
        val artistBottom = 1f - FaceGeometry.Immersive.BOTTOM_PADDING_FRACTION
        // Both ends of the whole row, including the streaming mark, stay inside the glass.
        assertTrue(.5f - artistInset <= RoundScreenText.halfChordAt(artistBottom))
        assertTrue(artistInset > .20f)
        assertTrue(insets.extra(1) > insets.extra(0))
    }

    @Test
    fun hidingTrackTimeNarrowsTheArtistWithoutBorrowingSpaceFromAnInvisibleRow() {
        val state = NowPlayingFaceState()
        fun artistInset(timeHeight: Int): Float {
            val insets = state.blockLineInsets(200.dp, BlockAnchor.BOTTOM, .085f,
                    listOf(22.dp, 24.dp, timeHeight.dp), floor = 20.dp, fitToScreen = true)
            return (insets.outer + insets.extra(1)).value
        }
        assertTrue(artistInset(0) > artistInset(17))
    }

    @Test
    fun aRectangularImmersiveScreenKeepsItsDesignedMargin() {
        val insets = NowPlayingFaceState().blockLineInsets(200.dp, BlockAnchor.BOTTOM, .085f,
                listOf(22.dp, 24.dp, 0.dp), floor = 20.dp,
                fitToScreen = true, isRound = false)
        assertEquals(20.dp, insets.outer)
        assertEquals(0.dp, insets.extra(1))
    }

    @Test
    fun anAlreadySafeTitleAndArtistKeepTheirDesignedWidth() {
        val insets = BlockLineInsets(20.dp, listOf(10.dp, 0.dp))
                .limitToWidth(200.dp, 120.dp)
        assertEquals(40.dp, insets.outer)
        assertEquals(0.dp, insets.extra(0))
        assertEquals(0.dp, insets.extra(1))
    }

    @Test
    fun onlyTheLineThatReachesTheCurveIsNarrowed() {
        val insets = BlockLineInsets(20.dp, listOf(0.dp, 35.dp))
                .limitToWidth(200.dp, 120.dp)
        assertEquals(40.dp, insets.outer)
        assertEquals(0.dp, insets.extra(0))
        assertEquals(15.dp, insets.extra(1))
        assertEquals(120.dp, 200.dp - (insets.outer + insets.extra(0)) * 2)
        assertEquals(90.dp, 200.dp - (insets.outer + insets.extra(1)) * 2)
    }

    @Test
    fun aBandNarrowerThanTheDesignKeepsItsPerLineClearance() {
        val insets = BlockLineInsets(50.dp, listOf(10.dp, 0.dp))
                .limitToWidth(200.dp, 120.dp)
        assertEquals(50.dp, insets.outer)
        assertEquals(10.dp, insets.extra(0))
        assertEquals(0.dp, insets.extra(1))
    }
}
