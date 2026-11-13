package com.svartifoss.snfell.music

import kotlin.math.abs

/**
 * A coarse fingerprint of a picture: the mean colour of each cell of a [GRID] x [GRID] lattice
 * laid over it.
 *
 * It answers one question, for [WatchCoverLedger]: **are these two bitmaps the same picture?** Not
 * the same pixels - `Bitmap.sameAs` answers that - but the same cover reached by two routes. A
 * player publishes the playing track's cover as a bitmap of its own (YouTube Music: 544 px), while
 * the cover sent ahead for the same track was fetched from the address on its queue row at the
 * watch's size. Both are the same sleeve, scaled by two different resamplers, so not one byte of
 * their encodings agrees - and the Data Layer, which addresses assets by content, sent every cover
 * to the watch twice.
 *
 * Cell means are what survives a resampler: over a block of several hundred pixels the kernel's
 * ringing and the encoder's noise average out, while a different picture moves whole cells. The
 * thresholds come from measurement (2026-11-11, 26 covers fetched at two sizes and 73 distinct
 * covers from the phone's own cache, all at the 466 px a TicWatch Pro 5 asks for):
 *
 * | pair | mean difference | largest cell |
 * |---|---|---|
 * | same cover, two scalings | 0.04 to 1.6 | 1 to 7 |
 * | same, one of them a pixel off | 0.5 to 2.7 | 3 to 14 |
 * | closest two *different* covers of 2,628 pairs | 19.4 | 178 |
 *
 * so [MAX_MEAN_DIFFERENCE] and [MAX_CELL_DIFFERENCE] sit just above the first two rows and a long
 * way under the third. One measured pair of "same" covers failed them (4.3 / 22: the image host
 * served two visibly different renditions), which is the right answer - a pair this refuses is
 * simply sent as it always was.
 *
 * The largest-cell limit is what catches a change the mean cannot see: two sleeves from one
 * template that differ in a line of lettering agree almost everywhere and disagree strongly in the
 * few cells the lettering crosses. It cannot catch *small* lettering on a flat ground, which is why
 * the ledger also requires the two to be the same track before it compares anything.
 *
 * Pure and free of `android.*`: the caller hands over the pixels.
 */
class CoverSignature private constructor(
        private val cells: ByteArray,
        /** Size of the picture the fingerprint was taken from. Two sizes are never one encoding. */
        val width: Int,
        val height: Int
) {

    /** How far apart two fingerprints are, per colour channel on the 0-255 scale. */
    data class Distance(val mean: Float, val largestCell: Int) {
        // Locale.US: this is a log line, and the device locale would write the mean with a comma.
        override fun toString(): String =
                String.format(java.util.Locale.US, "mean %.2f, largest cell %d", mean, largestCell)
    }

    fun distanceTo(other: CoverSignature): Distance {
        var total = 0L
        var largest = 0
        for (index in cells.indices) {
            val difference = abs((cells[index].toInt() and 0xFF) - (other.cells[index].toInt() and 0xFF))
            total += difference
            if (difference > largest) largest = difference
        }
        return Distance(total.toFloat() / cells.size, largest)
    }

    /** Whether [other] is this picture again - same size, and within both thresholds. */
    fun isSamePictureAs(other: CoverSignature): Boolean {
        if (width != other.width || height != other.height) return false
        val distance = distanceTo(other)
        return distance.mean <= MAX_MEAN_DIFFERENCE && distance.largestCell <= MAX_CELL_DIFFERENCE
    }

    companion object {
        /** Cells per side. At 466 px a cell is 29 px square - see the class comment. */
        const val GRID = 16

        const val MAX_MEAN_DIFFERENCE = 3.0f
        const val MAX_CELL_DIFFERENCE = 20

        /**
         * The fingerprint of a [width] x [height] picture whose [pixels] are ARGB, row by row, as
         * `Bitmap.getPixels` fills them. Null for a picture smaller than the lattice, or when
         * [pixels] does not hold that picture: nothing meaningful can be said about either.
         */
        fun of(pixels: IntArray, width: Int, height: Int): CoverSignature? {
            if (pixels.size < width.toLong() * height) return null
            return of(width, height) { y, row -> System.arraycopy(pixels, y * width, row, 0, width) }
        }

        /**
         * The fingerprint of a [width] x [height] picture read one row at a time: [readRow] fills
         * the array it is handed (exactly [width] long, reused for every row) with row `y`.
         *
         * Row by row because the caller is `MusicService`, which fingerprints every cover it
         * encodes for the watch: reading a 466 px cover whole is a 0.9 MB array per cover, five
         * covers a track change, all of it garbage a moment later.
         */
        fun of(width: Int, height: Int, readRow: (y: Int, row: IntArray) -> Unit): CoverSignature? {
            if (width < GRID || height < GRID) return null

            val cellOfColumn = IntArray(width) { it * GRID / width }
            val red = IntArray(GRID * GRID)
            val green = IntArray(GRID * GRID)
            val blue = IntArray(GRID * GRID)
            val count = IntArray(GRID * GRID)
            val row = IntArray(width)

            for (y in 0 until height) {
                readRow(y, row)
                val rowStart = (y * GRID / height) * GRID
                for (x in 0 until width) {
                    val pixel = row[x]
                    val cell = rowStart + cellOfColumn[x]
                    red[cell] += (pixel shr 16) and 0xFF
                    green[cell] += (pixel shr 8) and 0xFF
                    blue[cell] += pixel and 0xFF
                    count[cell]++
                }
            }

            val cells = ByteArray(GRID * GRID * 3)
            for (cell in 0 until GRID * GRID) {
                // Never zero: both sides are at least GRID long, so every cell holds a pixel.
                val pixelsInCell = count[cell]
                cells[cell * 3] = rounded(red[cell], pixelsInCell)
                cells[cell * 3 + 1] = rounded(green[cell], pixelsInCell)
                cells[cell * 3 + 2] = rounded(blue[cell], pixelsInCell)
            }
            return CoverSignature(cells, width, height)
        }

        private fun rounded(sum: Int, count: Int): Byte = ((sum + count / 2) / count).toByte()
    }
}
