package com.svartifoss.snfell.music

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader

/**
 * The next track's cover as the persistent notification draws it: a plain disc, [sizePx] across,
 * centre-cropped from [source]. The corners are transparent, so the shade shows a circle wherever
 * it puts the large icon.
 *
 * A new, small bitmap of its own: the notification is parcelled to System UI on every post, and a
 * full-size cover would cross that boundary each time.
 */
internal object NotificationCover {
    fun circular(source: Bitmap, sizePx: Int): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val side = minOf(source.width, source.height).coerceAtLeast(1)
        val scale = size.toFloat() / side
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                    (size - source.width * scale) / 2f,
                    (size - source.height * scale) / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    .apply { setLocalMatrix(matrix) }
        }
        val radius = size / 2f
        Canvas(output).drawCircle(radius, radius, radius, paint)
        return output
    }
}
