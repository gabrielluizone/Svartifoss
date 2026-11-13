package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class CoverSignatureTest {

    private val size = 466

    /** A picture with structure in every cell: three overlapping gradients. */
    private fun sleeve(width: Int = size, height: Int = size, seed: Int = 0): IntArray =
            IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                rgb((x * 255 / width + seed * 40) and 0xFF,
                        (y * 255 / height + seed * 90) and 0xFF,
                        ((x + y) * 255 / (width + height) + seed * 20) and 0xFF)
            }

    private fun rgb(red: Int, green: Int, blue: Int): Int =
            (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    private fun signatureOf(pixels: IntArray, width: Int = size, height: Int = size): CoverSignature =
            CoverSignature.of(pixels, width, height)!!

    @Test
    fun thePictureItselfIsTheSamePicture() {
        val pixels = sleeve()
        val distance = signatureOf(pixels).distanceTo(signatureOf(pixels.copyOf()))

        assertEquals(0f, distance.mean, 0f)
        assertEquals(0, distance.largestCell)
        assertTrue(signatureOf(pixels).isSamePictureAs(signatureOf(pixels.copyOf())))
    }

    /** What two resamplers and two encoders do to one sleeve: every pixel a little off, no cell
     *  moved. The whole reason a fingerprint is used instead of the bytes. */
    @Test
    fun noiseInEveryPixelLeavesItTheSamePicture() {
        val original = sleeve()
        val random = Random(7)
        val resampled = IntArray(original.size) { index ->
            val pixel = original[index]
            fun nudge(channel: Int) = (channel + random.nextInt(13) - 6).coerceIn(0, 255)
            rgb(nudge((pixel shr 16) and 0xFF), nudge((pixel shr 8) and 0xFF), nudge(pixel and 0xFF))
        }

        val distance = signatureOf(original).distanceTo(signatureOf(resampled))

        assertTrue("mean was ${distance.mean}", distance.mean < 1f)
        assertTrue(signatureOf(original).isSamePictureAs(signatureOf(resampled)))
    }

    /** The other thing a second scaler does: land the picture a pixel to one side. */
    @Test
    fun aPictureOnePixelOffIsTheSamePicture() {
        val original = sleeve()
        val shifted = IntArray(original.size) { index ->
            val x = index % size
            val y = index / size
            original[y * size + (x - 1).coerceAtLeast(0)]
        }

        assertTrue(signatureOf(original).isSamePictureAs(signatureOf(shifted)))
    }

    @Test
    fun anotherPictureIsNotTheSamePicture() {
        val first = signatureOf(sleeve(seed = 0))
        val second = signatureOf(sleeve(seed = 3))

        assertTrue(first.distanceTo(second).mean > CoverSignature.MAX_MEAN_DIFFERENCE)
        assertFalse(first.isSamePictureAs(second))
    }

    /**
     * Two sleeves from one template, differing in one block of lettering. The mean barely moves -
     * the block is one cell in 256 - and that is exactly the case the largest-cell limit exists
     * for: without it this pair would pass as one picture.
     */
    @Test
    fun aLocalChangeIsCaughtByTheLargestCellEvenThoughTheMeanBarelyMoves() {
        val plain = IntArray(size * size) { rgb(20, 20, 24) }
        val lettered = plain.copyOf()
        for (y in 100 until 124) {
            for (x in 200 until 226) {
                lettered[y * size + x] = rgb(240, 240, 240)
            }
        }

        val distance = signatureOf(plain).distanceTo(signatureOf(lettered))

        assertTrue("mean was ${distance.mean}", distance.mean < CoverSignature.MAX_MEAN_DIFFERENCE)
        assertTrue("largest cell was ${distance.largestCell}",
                distance.largestCell > CoverSignature.MAX_CELL_DIFFERENCE)
        assertFalse(signatureOf(plain).isSamePictureAs(signatureOf(lettered)))
    }

    /** A colour cast over a whole sleeve moves no single cell far, and is caught by the mean. */
    @Test
    fun aUniformShiftIsCaughtByTheMean() {
        val original = sleeve()
        val warmer = IntArray(original.size) { index ->
            val pixel = original[index]
            rgb(((pixel shr 16) and 0xFF).plus(9).coerceAtMost(255),
                    (pixel shr 8) and 0xFF,
                    (pixel and 0xFF).minus(9).coerceAtLeast(0))
        }

        val distance = signatureOf(original).distanceTo(signatureOf(warmer))

        assertTrue(distance.largestCell <= CoverSignature.MAX_CELL_DIFFERENCE)
        assertFalse(signatureOf(original).isSamePictureAs(signatureOf(warmer)))
    }

    /**
     * The encoder never enlarges a source, so a small picture is a small encoding. Two sizes are
     * therefore two different things to send, however alike they look - which is what keeps a soft
     * thumbnail from standing in for a full-size cover.
     */
    @Test
    fun twoSizesAreNeverTheSamePicture() {
        val full = signatureOf(sleeve(466, 466), 466, 466)
        val small = signatureOf(sleeve(128, 128), 128, 128)

        assertTrue(full.distanceTo(small).mean < CoverSignature.MAX_MEAN_DIFFERENCE)
        assertFalse(full.isSamePictureAs(small))
        assertFalse(small.isSamePictureAs(full))
    }

    @Test
    fun aPictureThatIsNotSquareHasAFingerprintToo() {
        val wide = signatureOf(sleeve(466, 262), 466, 262)

        assertTrue(wide.isSamePictureAs(signatureOf(sleeve(466, 262), 466, 262)))
        assertFalse(wide.isSamePictureAs(signatureOf(sleeve(466, 466), 466, 466)))
    }

    @Test
    fun aPictureSmallerThanTheLatticeHasNoFingerprint() {
        val side = CoverSignature.GRID - 1

        assertNull(CoverSignature.of(IntArray(side * side), side, side))
        assertNull(CoverSignature.of(IntArray(side * size), side, size))
        assertNotNull(CoverSignature.of(
                IntArray(CoverSignature.GRID * CoverSignature.GRID),
                CoverSignature.GRID,
                CoverSignature.GRID))
    }

    @Test
    fun pixelsThatDoNotHoldThePictureHaveNoFingerprint() {
        assertNull(CoverSignature.of(IntArray(size * size - 1), size, size))
    }

    /** MusicService reads a bitmap one row at a time; that has to be the same fingerprint as
     *  reading it whole, or the two routes to a cover would never agree. */
    @Test
    fun readingRowByRowGivesTheSameFingerprint() {
        val pixels = sleeve(466, 300, seed = 2)
        val whole = signatureOf(pixels, 466, 300)
        val rows = CoverSignature.of(466, 300) { y, row ->
            System.arraycopy(pixels, y * 466, row, 0, 466)
        }!!

        assertEquals(0f, whole.distanceTo(rows).mean, 0f)
        assertTrue(whole.isSamePictureAs(rows))
    }

    @Test
    fun theDistanceIsWrittenWithADecimalPointWhateverTheLocale() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-BR"))
        try {
            assertEquals("mean 1.50, largest cell 4", CoverSignature.Distance(1.5f, 4).toString())
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
