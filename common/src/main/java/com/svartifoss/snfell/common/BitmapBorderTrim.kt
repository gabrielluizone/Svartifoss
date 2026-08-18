package com.svartifoss.snfell.common

import android.graphics.Bitmap

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
 * the watch trims again at decode time. Trimming twice is free: an already-trimmed cover has varied
 * edges and comes back unchanged.
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
        // Ignore a degenerate result (e.g. an almost entirely flat image) and require an actual trim.
        return if ((cropWidth < width || cropHeight < height) && cropWidth >= 16 && cropHeight >= 16) {
            Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
        } else {
            source
        }
    }

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
