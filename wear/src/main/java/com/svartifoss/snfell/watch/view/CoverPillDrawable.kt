package com.svartifoss.snfell.watch.view

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import kotlin.math.max

/**
 * Background for a full-width pill whose own cover art fills it, with a legibility scrim over the
 * top - the View-side counterpart of the Compose queue/menu cover rows (QueueStyle.COVER).
 *
 * The artwork is center-cropped via a [BitmapShader] matrix rather than by allocating a cropped
 * copy, so re-laying out a row costs no extra bitmap. Both the art and the scrim are drawn as one
 * round rect, which is why this is a Drawable rather than an ImageView behind the row: a View
 * background must paint under the row's own children, and those children (label, artwork slot)
 * are laid out by the existing LinearLayout.
 *
 * The scrim mirrors QueueScreen.coverScrim: horizontal, heaviest on the left where the
 * left-aligned label sits, so the right side of the artwork stays visible.
 */
class CoverPillDrawable(
        private val bitmap: Bitmap,
        private val cornerRadiusPx: Float,
        /** Hue of the scrim. Black for the plain cover; the album accent for the tonal variation,
         *  pulled down to a dark tone first so it darkens rather than merely tinting. */
        private val scrimTint: Int = Color.BLACK,
        /** Blurred backdrops are already low-contrast, so they need much less darkening and can
         *  keep more of the artwork's colour visible. */
        private val softScrim: Boolean = false
) : Drawable() {

    private val shaderMatrix = Matrix()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()

    init {
        bitmapPaint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    override fun onBoundsChange(newBounds: Rect) {
        bounds.set(newBounds)
        if (bitmap.width <= 0 || bitmap.height <= 0) return

        // Center-crop: scale so the art covers both axes, then centre whichever axis overflows.
        val scale = max(
                newBounds.width().toFloat() / bitmap.width,
                newBounds.height().toFloat() / bitmap.height
        )
        shaderMatrix.setScale(scale, scale)
        shaderMatrix.postTranslate(
                newBounds.left + (newBounds.width() - bitmap.width * scale) / 2f,
                newBounds.top + (newBounds.height() - bitmap.height * scale) / 2f
        )
        bitmapPaint.shader?.setLocalMatrix(shaderMatrix)

        val base = darkenForScrim(scrimTint)
        val alphas = if (softScrim) {
            intArrayOf(0x80, 0x5C, 0x47)
        } else {
            intArrayOf(0xBD, 0x75, 0x38)
        }
        scrimPaint.shader = LinearGradient(
                newBounds.left.toFloat(), 0f, newBounds.right.toFloat(), 0f,
                IntArray(alphas.size) { ColorUtils.setAlphaComponent(base, alphas[it]) },
                floatArrayOf(0f, .55f, 1f),
                Shader.TileMode.CLAMP
        )
    }

    /** Mirrors QueueScreen's darkenForScrim: an accent at 70% alpha would tint the artwork
     *  without actually darkening it, so it is taken down to a dark, low-saturation tone first.
     *  Black passes through unchanged (its saturation is already zero). */
    private fun darkenForScrim(color: Int): Int {
        if (color == Color.BLACK) return color
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = (hsl[1] * .8f).coerceIn(0f, .65f)
        hsl[2] = .16f
        return ColorUtils.HSLToColor(hsl)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, bitmapPaint)
        canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, scrimPaint)
    }

    override fun setAlpha(alpha: Int) {
        bitmapPaint.alpha = alpha
        scrimPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bitmapPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
