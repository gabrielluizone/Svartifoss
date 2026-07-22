package com.svartifoss.snfell.common

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * The app's blur for artwork bitmaps, shared by the watch (player background, cover pills) and the
 * phone preview so a given radius looks the same everywhere.
 *
 * Repeated bilinear down/up sampling rather than a single hard downscale: one pass leaves visible
 * pixel blocks, while several cheap passes converge on something close to a Gaussian. That matters
 * because [android.graphics.RenderEffect]'s real GPU blur is API 31+ and cannot be applied to a
 * plain Bitmap without a RenderNode - this runs on every supported watch and on any bitmap.
 */
object BitmapBlur {

    /** Never sample below this on either axis; past it the passes stop reading as blur and start
     *  reading as pixelation. */
    private const val MIN_SAMPLE_PX = 96

    fun blur(source: Bitmap, radiusPx: Float): Bitmap {
        if (source.width <= 0 || source.height <= 0 || radiusPx <= 0f) return source

        val scale = PaletteTransforms.legacyBlurScale(radiusPx)
        val targetWidth = (source.width * scale).roundToInt()
                .coerceAtLeast(MIN_SAMPLE_PX)
                .coerceAtMost(source.width)
        val targetHeight = (source.height * scale).roundToInt()
                .coerceAtLeast(MIN_SAMPLE_PX)
                .coerceAtMost(source.height)
        val passes = (2 + radiusPx.roundToInt() / 18).coerceIn(2, 8)

        var current = source
        repeat(passes) {
            val down = Bitmap.createScaledBitmap(current, targetWidth, targetHeight, true)
            val up = Bitmap.createScaledBitmap(down, source.width, source.height, true)
            if (current !== source) current.recycle()
            if (down !== up) down.recycle()
            current = up
        }
        return current
    }
}
