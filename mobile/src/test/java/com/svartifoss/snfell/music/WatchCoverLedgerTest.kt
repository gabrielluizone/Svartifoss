package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WatchCoverLedgerTest {

    private val size = 64

    /** A flat picture of one grey [level], nudged by [noise] - enough to tell pictures apart and
     *  to make two "scalings" of one. */
    private fun picture(level: Int, noise: Int = 0, side: Int = size): CoverSignature {
        val grey = (level + noise).coerceIn(0, 255)
        val pixel = (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
        return CoverSignature.of(IntArray(side * side) { pixel }, side, side)!!
    }

    private val sentAhead = byteArrayOf(1, 2, 3)

    /** The case this exists for: the copy sent ahead from the queue row, then the player's own. */
    @Test
    fun theSameTracksSamePictureComesBackAsTheBytesAlreadySent() {
        val ledger = WatchCoverLedger()
        ledger.remember("Explorers", picture(100), sentAhead)

        val held = ledger.find("Explorers", picture(100, noise = 2))

        assertNotNull(held)
        assertSame(sentAhead, held!!.bytes)
        assertEquals(2f, held.distance.mean, 0.001f)
    }

    /** A queue row and the player rarely agree on capitals or stray spaces; the watch matches a
     *  neighbouring cover to its track the same way. */
    @Test
    fun theTitleIsComparedWithoutCaseOrSurroundingSpace() {
        val ledger = WatchCoverLedger()
        ledger.remember("  Hunger For Your Heart ", picture(100), sentAhead)

        assertSame(sentAhead, ledger.find("hunger for your heart", picture(100))!!.bytes)
    }

    /**
     * Two black sleeves with different small lettering fingerprint alike. Only being the same
     * track makes "alike" mean "the same cover", so another track never borrows one.
     */
    @Test
    fun anotherTracksLookAlikeIsNotReused() {
        val ledger = WatchCoverLedger()
        ledger.remember("Intro", picture(8), sentAhead)

        assertNull(ledger.find("Outro", picture(8)))
    }

    @Test
    fun theSameTrackWithAnotherPictureIsNotReused() {
        val ledger = WatchCoverLedger()
        ledger.remember("Explorers", picture(100), sentAhead)

        assertNull(ledger.find("Explorers", picture(160)))
    }

    /** A thumbnail sent because nothing larger could be found must not stand in for the full
     *  cover that turns up later: the sharper one has to replace it. */
    @Test
    fun aSmallerEncodingIsNotReusedForAFullSizeOne() {
        val ledger = WatchCoverLedger()
        ledger.remember("Explorers", picture(100, side = 32), sentAhead)

        assertNull(ledger.find("Explorers", picture(100, side = 64)))
    }

    @Test
    fun aTrackWithNoTitleIsNeitherRememberedNorFound() {
        val ledger = WatchCoverLedger()
        ledger.remember("   ", picture(100), sentAhead)

        assertEquals(0, ledger.size())
        assertNull(ledger.find("", picture(100)))
        assertNull(ledger.find("   ", picture(100)))
    }

    @Test
    fun ofTwoEncodingsForOneTrackTheCloserPictureIsReturned() {
        val ledger = WatchCoverLedger()
        val further = byteArrayOf(1)
        val closer = byteArrayOf(2)
        // Far enough apart to be two pictures, each close enough to the one asked about. The
        // further one is remembered first, so returning the first match would fail this.
        ledger.remember("Explorers", picture(100), further)
        ledger.remember("Explorers", picture(104), closer)

        assertEquals(2, ledger.size())
        assertSame(closer, ledger.find("Explorers", picture(103))!!.bytes)
    }

    /** One encoding per picture per track: encoding it again (the ledger having missed, or been
     *  cleared) replaces the entry rather than adding a second that could never win. */
    @Test
    fun rememberingTheSamePictureAgainReplacesItsEncoding() {
        val ledger = WatchCoverLedger()
        val newer = byteArrayOf(9)
        ledger.remember("Explorers", picture(100), sentAhead)
        ledger.remember("Explorers", picture(101), newer)

        assertEquals(1, ledger.size())
        assertSame(newer, ledger.find("Explorers", picture(100))!!.bytes)
    }

    @Test
    fun theOldestEncodingIsDroppedPastTheCapacity() {
        val ledger = WatchCoverLedger(capacity = 3)
        ledger.remember("One", picture(10), byteArrayOf(1))
        ledger.remember("Two", picture(60), byteArrayOf(2))
        ledger.remember("Three", picture(110), byteArrayOf(3))
        ledger.remember("Four", picture(160), byteArrayOf(4))

        assertEquals(3, ledger.size())
        assertNull(ledger.find("One", picture(10)))
        assertNotNull(ledger.find("Two", picture(60)))
    }

    /** The cover behind the one playing is asked about on every track change; being used is what
     *  keeps it, not how long ago it was first encoded. */
    @Test
    fun anEncodingThatIsReusedIsTheLastToBeDropped() {
        val ledger = WatchCoverLedger(capacity = 3)
        ledger.remember("One", picture(10), byteArrayOf(1))
        ledger.remember("Two", picture(60), byteArrayOf(2))
        ledger.remember("Three", picture(110), byteArrayOf(3))

        assertNotNull(ledger.find("One", picture(10)))
        ledger.remember("Four", picture(160), byteArrayOf(4))

        assertNotNull(ledger.find("One", picture(10)))
        assertNull(ledger.find("Two", picture(60)))
    }

    @Test
    fun clearingForgetsEverything() {
        val ledger = WatchCoverLedger()
        ledger.remember("Explorers", picture(100), sentAhead)

        ledger.clear()

        assertEquals(0, ledger.size())
        assertNull(ledger.find("Explorers", picture(100)))
    }
}
