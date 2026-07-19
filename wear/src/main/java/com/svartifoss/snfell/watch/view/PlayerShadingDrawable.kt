package com.svartifoss.snfell.watch.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.common.PlayerShadingStyle

/**
 * Canvas renderer for Classic's artwork shading. Compose and the phone preview mirror these
 * stops, while this Drawable lets the existing non-interactive scrim View remain lightweight.
 */
class PlayerShadingDrawable(
        private val style: PlayerShadingStyle,
        private val intensity: Float,
        private val primary: Int,
        private val secondary: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var drawableAlpha = 255

    private fun scaledAlpha(maxAlpha: Float): Int =
            (255f * maxAlpha * intensity.coerceIn(0f, 1f) * (drawableAlpha / 255f))
                    .toInt().coerceIn(0, 255)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty || intensity <= 0f) return
        val left = b.left.toFloat()
        val top = b.top.toFloat()
        val right = b.right.toFloat()
        val bottom = b.bottom.toFloat()
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()

        paint.shader = when (style) {
            PlayerShadingStyle.EDGE_VIGNETTE -> RadialGradient(
                    cx, cy, maxOf(b.width(), b.height()) * .67f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(Color.BLACK, scaledAlpha(.82f))),
                    floatArrayOf(0f, .46f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.BOTTOM_CORNER -> LinearGradient(
                    left, top, right, bottom,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(Color.BLACK, scaledAlpha(.94f))),
                    floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.FOLLOW,
            PlayerShadingStyle.BOTTOM_FADE -> LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(Color.BLACK, scaledAlpha(.94f))),
                    floatArrayOf(0f, .34f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.FLOOR_CEILING -> LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(
                            ColorUtils.setAlphaComponent(Color.BLACK, scaledAlpha(.55f)),
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(Color.BLACK, scaledAlpha(.88f))),
                    floatArrayOf(0f, .30f, .60f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.FULL_FILTER -> null

            PlayerShadingStyle.ALBUM_TINT -> null

            PlayerShadingStyle.DUOTONE -> LinearGradient(
                    left, top, right, bottom,
                    ColorUtils.setAlphaComponent(darkAlbumTone(primary), scaledAlpha(.58f)),
                    ColorUtils.setAlphaComponent(darkAlbumTone(secondary), scaledAlpha(.58f)),
                    Shader.TileMode.CLAMP)

            PlayerShadingStyle.SIDE_CURTAINS -> LinearGradient(
                    left, 0f, right, 0f,
                    intArrayOf(
                            ColorUtils.setAlphaComponent(Color.BLACK, scaledAlpha(.72f)),
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(Color.BLACK, scaledAlpha(.72f))),
                    floatArrayOf(0f, .34f, .66f, 1f), Shader.TileMode.CLAMP)
        }

        paint.color = when (style) {
            PlayerShadingStyle.FULL_FILTER ->
                ColorUtils.setAlphaComponent(Color.BLACK, scaledAlpha(.55f))
            PlayerShadingStyle.ALBUM_TINT ->
                ColorUtils.setAlphaComponent(darkAlbumTone(primary), scaledAlpha(.52f))
            else -> Color.WHITE
        }
        canvas.drawRect(left, top, right, bottom, paint)
        paint.shader = null
    }

    private fun darkAlbumTone(color: Int): Int = PaletteTransforms.tonalSurface(
            color, lightness = .13f, minSat = .25f, maxSat = .78f)

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
