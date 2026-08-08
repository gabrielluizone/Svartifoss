package com.svartifoss.snfell.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max

/**
 * Composites artwork that stays sharp in the middle and dissolves into frosted glass at the rim.
 *
 * This exists because the shading styles cannot do it. [PlayerShadingStyle] renders *over* the
 * artwork and never receives the bitmap, so every "edge" treatment it offers can only darken -
 * hence a vignette but never a blur. Frosting is therefore an artwork treatment
 * (`MiscPreferences.ALBUM_ART_STYLE`), composed once where the bitmap is known and consumed by the
 * Classic face, every Compose face and the phone preview alike.
 *
 * Blurring goes through [BitmapBlur] rather than `RenderEffect` for the reason that class
 * documents: the GPU path is API 31+ and needs a RenderNode, and this has to work on every
 * supported watch *and* inside the phone's Canvas preview.
 */
object FrostedEdges {

    /** Where the sharp centre ends and the frost starts ramping in, as a fraction of the radius. */
    const val DEFAULT_INNER_STOP = 0.30f

    /**
     * Where the frost reaches full strength, short of the mask radius on purpose.
     *
     * The mask radius is half the bitmap's longest side, but the watch centre-crops that bitmap into
     * a *round* screen - so the pixels at exactly `radius` are the ones clipped away, and a ramp
     * that only peaked there produced a frosted band nobody could see. Peaking early leaves a real
     * band of fully frosted artwork inside the visible circle.
     */
    private const val FULL_FROST_STOP = 0.82f

    /**
     * Frosting multiplies the user's blur radius, because only a band of the image shows it.
     * At the shared radius the rim read as slightly soft rather than as glass - the same value that
     * is clearly a blur when applied to the whole cover is nearly invisible on an edge.
     */
    private const val FROST_BLUR_MULTIPLIER = 2.5f

    /**
     * Returns a new bitmap with [source] sharp at the centre and blurred towards the edges.
     *
     * Returns [source] itself when there is nothing to do, so callers must not assume a fresh
     * bitmap - and equally must not recycle the result without checking identity.
     *
     * [innerStop] is the radius fraction where frost begins; [rimAlpha] how completely the blurred
     * copy takes over at the very edge. A rimAlpha below 1 leaves the artwork faintly readable
     * through the frost, which is what makes it read as translucent glass rather than as a
     * blurred border.
     */
    fun compose(
            source: Bitmap,
            blurRadiusPx: Float,
            innerStop: Float = DEFAULT_INNER_STOP,
            rimAlpha: Float = 1f
    ): Bitmap {
        if (source.width <= 0 || source.height <= 0 || blurRadiusPx <= 0f) return source

        val blurred = BitmapBlur.blur(source, blurRadiusPx * FROST_BLUR_MULTIPLIER)
        // BitmapBlur returns the source unchanged for a no-op radius; compositing a bitmap onto
        // itself would just waste an allocation to produce the original.
        if (blurred === source) return source

        val out = source.copy(Bitmap.Config.ARGB_8888, true)
                // copy() returns null when the source is in a config that cannot be copied
                // (hardware bitmaps, which arrive from some decoders). Falling back to the plain
                // blur keeps the face rendering rather than dropping the artwork entirely.
                ?: return blurred

        val canvas = Canvas(out)
        val bounds = Rect(0, 0, out.width, out.height)
        val boundsF = RectF(bounds)

        // The frosted rim is built in its own layer: the mask below has to erase the blurred copy
        // only, never the sharp base underneath it.
        val layer = canvas.saveLayer(boundsF, null)
        canvas.drawBitmap(blurred, bounds, boundsF, null)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        // DST_IN keeps the layer (the blur) wherever the mask has alpha. The ramp runs transparent
        // at the centre to opaque at the rim, so the blur survives only around the edge.
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        val radius = max(out.width, out.height) / 2f
        val rim = Color.argb((255 * rimAlpha.coerceIn(0f, 1f)).toInt(), 255, 255, 255)
        val inner = innerStop.coerceIn(0f, FULL_FROST_STOP - 0.05f)
        maskPaint.shader = RadialGradient(
                out.width / 2f, out.height / 2f, radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, rim, rim),
                floatArrayOf(0f, inner, FULL_FROST_STOP, 1f),
                Shader.TileMode.CLAMP)
        canvas.drawRect(boundsF, maskPaint)
        canvas.restoreToCount(layer)

        if (!blurred.isRecycled) blurred.recycle()
        return out
    }
}
