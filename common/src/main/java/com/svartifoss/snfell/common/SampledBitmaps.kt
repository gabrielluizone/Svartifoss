package com.svartifoss.snfell.common

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Decodes a picture at no more than the size it is for: the bounds are read first and
 * `inSampleSize` set from them, so an oversized input costs a fraction of its full-resolution
 * memory instead of being decoded whole and scaled down afterwards.
 *
 * Nearly everything both apps decode is bounded at its source - the phone sizes every asset it
 * sends to the watch, the artwork stores re-encode what they cache, and the services the network
 * lookups ask publish fixed sizes - and for those this changes nothing, because nothing up to twice
 * the target is sampled at all. What it removes is the one decode that would allocate whatever an
 * unexpected input happened to measure. Play Console also flags any decode made without it
 * ("BitmapFactory without downsampling"), so a new decode belongs here too.
 */
object SampledBitmaps {

    /**
     * The `inSampleSize` that shrinks a [width] x [height] image the most while keeping both sides
     * at or above [targetPx] - the largest power of two that does not undershoot, which is the
     * contract `inSampleSize` documents.
     *
     * Never going below the target means whatever scales the result afterwards still has real
     * pixels to work from rather than a blur of an already-shrunken decode.
     */
    fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) {
            sample *= 2
        }
        return sample
    }

    /** The longer side of this device's display: nothing drawn on it needs more pixels than that. */
    fun screenPx(resources: Resources): Int =
            resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }

    /** [bytes] decoded at [sampleSizeFor] [targetPx], or null when there are none or they are not
     *  an image. */
    fun decode(bytes: ByteArray?, targetPx: Int): Bitmap? {
        if (bytes == null) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sampledFor(bounds, targetPx))
    }

    /** [file] decoded at [sampleSizeFor] [targetPx], or null when it is not an image. */
    fun decodeFile(file: File, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(file.path, sampledFor(bounds, targetPx))
    }

    /** Drawable resource [id] decoded at [sampleSizeFor] [targetPx] (after any density scaling
     *  the resource's folder implies), or null when it is not a bitmap. */
    fun decodeResource(resources: Resources, id: Int, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, id, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeResource(resources, id, sampledFor(bounds, targetPx))
    }

    private fun sampledFor(bounds: BitmapFactory.Options, targetPx: Int) =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
            }
}
