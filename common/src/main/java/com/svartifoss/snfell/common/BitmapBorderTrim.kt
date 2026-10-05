package com.svartifoss.snfell.common

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Crops away outer rows/columns whose pixels are all within a small colour range - a flat
 * letterbox/pillarbox border. A normal cover has varied edges, so nothing is trimmed there.
 *
 * YouTube Music "art track" thumbnails wrap the real square cover in bars (black, or a flat album
 * colour) whichever surface they reach, so a square crop that keeps the bars leaves the cover as a
 * small square floating inside the pill.
 *
 * Lives in `common` because trimming has to happen on **both** sides. The phone applies it to the
 * covers it resolves itself, but a queue thumbnail can also arrive already bordered from a source
 * the phone never re-encodes, and the watch is the only place that sees every cover it draws - so
 * the watch trims again at decode time. Trimming twice is free: an already-trimmed cover is square
 * and comes back unchanged.
 *
 * A non-square picture is trimmed only when the crop brings it closer to square - which is exactly
 * what removing a letterbox or pillarbox around a square cover does. A square picture is normally
 * preserved: only the narrow, two-opposite-bars exception in [shouldCropSquareLetterbox] is
 * allowed through. Without that rule it cut away any flat margin at all, so a minimalist sleeve -
 * a small logo on a plain field, a common design - was cropped down to the logo and shown on the
 * watch as a close-up of it.
 */
object BitmapBorderTrim {
    private const val BORDER_UNIFORMITY_THRESHOLD = 26
    private const val BORDER_SAMPLE_STEP = 4

    fun trim(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width < 16 || height < 16) return source
        var top = 0
        var bottom = height - 1
        var left = 0
        var right = width - 1
        while (top < bottom && isUniformRow(source, top, left, right)) top++
        while (bottom > top && isUniformRow(source, bottom, left, right)) bottom--
        while (left < right && isUniformColumn(source, left, top, bottom)) left++
        while (right > left && isUniformColumn(source, right, top, bottom)) right--
        val cropWidth = right - left + 1
        val cropHeight = bottom - top + 1
        return when {
            shouldCrop(width, height, cropWidth, cropHeight) ->
                Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
            // Some YouTube Music art-track thumbnails arrive in a *square* bitmap but contain a
            // landscape picture between two black bars. A square bitmap normally must be kept - a
            // flat margin can be part of an album's design - but two substantial opposite bars
            // and no matching side bars identify this as letterboxing. Remove the bars and take
            // the centre square of the actual picture, rather than retaining a tiny wide picture
            // in a black square.
            shouldCropSquareLetterbox(width, height, top, bottom, left, right) &&
                    letterboxBarsMatch(width, height, top, bottom, left, right) { x, y ->
                        source.getPixel(x, y)
                    } -> {
                val side = minOf(cropWidth, cropHeight)
                val cropLeft = left + (cropWidth - side) / 2
                val cropTop = top + (cropHeight - side) / 2
                Bitmap.createBitmap(source, cropLeft, cropTop, side, side)
            }
            else -> source
        }
    }

    /** How far a picture may be from square and still count as square - see the class doc. */
    private const val SQUARE_TOLERANCE = 0.03f

    /** Whether a [width] x [height] picture is square, within [SQUARE_TOLERANCE]. */
    internal fun isSquare(width: Int, height: Int): Boolean =
            abs(width - height) <= maxOf(width, height) * SQUARE_TOLERANCE

    /**
     * Whether cropping a [width] x [height] picture to [cropWidth] x [cropHeight] is a border
     * worth removing: an actual trim, not a degenerate one (an almost entirely flat image), and one
     * that leaves the picture closer to square than it was.
     */
    internal fun shouldCrop(width: Int, height: Int, cropWidth: Int, cropHeight: Int): Boolean {
        if (cropWidth >= width && cropHeight >= height) return false
        if (cropWidth < 16 || cropHeight < 16) return false
        return squareness(cropWidth, cropHeight) > squareness(width, height)
    }

    /**
     * The narrow exception to the "never trim a square cover" rule: real letterbox bars inside a
     * square canvas. [top], [bottom], [left] and [right] are the bounds left after uniform edges
     * have been scanned. A normal sleeve with a frame on every side is rejected; it remains a
     * square inner picture and its border is part of the artwork.
     */
    internal fun shouldCropSquareLetterbox(
            width: Int,
            height: Int,
            top: Int,
            bottom: Int,
            left: Int,
            right: Int
    ): Boolean {
        if (!isSquare(width, height)) return false
        val minimumBar = (minOf(width, height) * LETTERBOX_BAR_FRACTION).toInt().coerceAtLeast(2)
        val topBar = top
        val bottomBar = height - 1 - bottom
        val leftBar = left
        val rightBar = width - 1 - right
        val hasHorizontalBars = topBar >= minimumBar && bottomBar >= minimumBar &&
                leftBar < minimumBar && rightBar < minimumBar
        val hasVerticalBars = leftBar >= minimumBar && rightBar >= minimumBar &&
                topBar < minimumBar && bottomBar < minimumBar
        return hasHorizontalBars || hasVerticalBars
    }

    /**
     * At least a tenth of the canvas per edge: a 1px compression edge is not a letterbox, and
     * neither is the dark margin a sleeve fades out into. Five percent was the first value and it
     * cropped a real cover - Muse's *Panic Station* is black above and below its design for 6% and
     * 9% of the canvas, and was cut to a 229px square out of 269, so its edges went missing.
     * Letterboxing a picture that is even 5:4 leaves bars of a tenth each, and a nearly square
     * picture has nothing worth removing.
     */
    private const val LETTERBOX_BAR_FRACTION = .10f

    /** How far, per channel, the outermost lines of the two bars may differ and still be one bar. */
    private const val LETTERBOX_COLOUR_MATCH = 24

    /**
     * Whether the two bars [shouldCropSquareLetterbox] found are the same colour.
     *
     * A letterbox is two copies of one bar, so the outermost line of each is the same colour -
     * black, or one flat album colour. A gradient is not: each of its rows is flat, which is all the
     * scan in [trim] asks of a row, so a violet-to-pink sleeve with a glyph in its middle reads as
     * two bars a hundred rows deep, and cropping them zoomed YouTube Music's Liked Music cover to
     * almost twice its size. Its two ends are different colours, which is what gives it away.
     *
     * [pixelAt] is the only way this reads the picture, so a plain JVM test can pin it.
     */
    internal inline fun letterboxBarsMatch(
            width: Int,
            height: Int,
            top: Int,
            bottom: Int,
            left: Int,
            right: Int,
            pixelAt: (x: Int, y: Int) -> Int
    ): Boolean {
        val horizontal = top + (height - 1 - bottom) >= left + (width - 1 - right)
        val first: Int
        val last: Int
        if (horizontal) {
            first = averageColour(0, width - 1) { x -> pixelAt(x, 0) }
            last = averageColour(0, width - 1) { x -> pixelAt(x, height - 1) }
        } else {
            first = averageColour(0, height - 1) { y -> pixelAt(0, y) }
            last = averageColour(0, height - 1) { y -> pixelAt(width - 1, y) }
        }
        return isSameBarColour(first, last)
    }

    /** The mean colour of a line, sampled like [isUniformLine], as packed RGB. */
    internal inline fun averageColour(from: Int, to: Int, pixelAt: (Int) -> Int): Int {
        var r = 0L; var g = 0L; var b = 0L
        var count = 0
        var i = from
        while (i <= to) {
            val pixel = pixelAt(i)
            r += (pixel shr 16) and 0xFF
            g += (pixel shr 8) and 0xFF
            b += pixel and 0xFF
            count++
            i += BORDER_SAMPLE_STEP
        }
        if (count == 0) return 0
        return ((r / count).toInt() shl 16) or ((g / count).toInt() shl 8) or (b / count).toInt()
    }

    /** Whether two packed RGB colours are within [LETTERBOX_COLOUR_MATCH] of each other per channel. */
    internal fun isSameBarColour(a: Int, b: Int): Boolean =
            abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= LETTERBOX_COLOUR_MATCH &&
                    abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= LETTERBOX_COLOUR_MATCH &&
                    abs((a and 0xFF) - (b and 0xFF)) <= LETTERBOX_COLOUR_MATCH

    private fun squareness(width: Int, height: Int): Float =
            minOf(width, height).toFloat() / maxOf(width, height)

    private fun isUniformRow(bitmap: Bitmap, y: Int, x0: Int, x1: Int): Boolean =
            isUniformLine(x0, x1) { x -> bitmap.getPixel(x, y) }

    private fun isUniformColumn(bitmap: Bitmap, x: Int, y0: Int, y1: Int): Boolean =
            isUniformLine(y0, y1) { y -> bitmap.getPixel(x, y) }

    /** No Android dependency - [pixelAt] is the only way this reads a pixel - so this one piece of
     *  actual judgement (what counts as "flat") is pinned by a plain JVM test rather than only
     *  exercised through real Bitmap objects. */
    internal inline fun isUniformLine(from: Int, to: Int, pixelAt: (Int) -> Int): Boolean {
        var minR = 255; var minG = 255; var minB = 255
        var maxR = 0; var maxG = 0; var maxB = 0
        var i = from
        while (i <= to) {
            val pixel = pixelAt(i)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (r < minR) minR = r; if (r > maxR) maxR = r
            if (g < minG) minG = g; if (g > maxG) maxG = g
            if (b < minB) minB = b; if (b > maxB) maxB = b
            if (maxR - minR > BORDER_UNIFORMITY_THRESHOLD ||
                    maxG - minG > BORDER_UNIFORMITY_THRESHOLD ||
                    maxB - minB > BORDER_UNIFORMITY_THRESHOLD) {
                return false
            }
            i += BORDER_SAMPLE_STEP
        }
        return true
    }
}
