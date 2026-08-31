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
import com.svartifoss.snfell.common.SHADING_MAX_MULTIPLIER

/**
 * Canvas renderer for Classic's artwork shading. Compose and the phone preview mirror these
 * stops, while this Drawable lets the existing non-interactive scrim View remain lightweight.
 */
class PlayerShadingDrawable(
        private val style: PlayerShadingStyle,
        private val intensity: Float,
        private val primary: Int,
        private val secondary: Int,
        // Colour of the darkening gradient. Black by default; the user can tint it with the album
        // accent, a desaturated tone, or a custom colour (already resolved to a dark tone).
        private val shadingColor: Int = Color.BLACK
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var drawableAlpha = 255

    private fun scaledAlpha(maxAlpha: Float): Int =
            (255f * maxAlpha * intensity.coerceIn(0f, SHADING_MAX_MULTIPLIER) *
                    (drawableAlpha / 255f))
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
            PlayerShadingStyle.EDGE_VIGNETTE,
            PlayerShadingStyle.EDGE_VIGNETTE_STRONG,
            PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> {
                val baseStop = when (style) {
                    PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> 0.0f
                    PlayerShadingStyle.EDGE_VIGNETTE_STRONG -> 0.15f
                    else -> 0.46f
                }
                val radiusMult = when (style) {
                    PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> 0.475f
                    PlayerShadingStyle.EDGE_VIGNETTE_STRONG -> 0.525f
                    else -> 0.67f
                }
                val outerAlpha = when (style) {
                    PlayerShadingStyle.EDGE_VIGNETTE_HEAVY -> 1.0f
                    PlayerShadingStyle.EDGE_VIGNETTE_STRONG -> 1.0f
                    else -> 0.82f
                }
                RadialGradient(
                        cx, cy, maxOf(b.width(), b.height()) * radiusMult,
                        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(outerAlpha))),
                        floatArrayOf(0f, baseStop, 1f), Shader.TileMode.CLAMP)
            }

            PlayerShadingStyle.BOTTOM_CORNER -> LinearGradient(
                    left, top, right, bottom,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.94f))),
                    floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.FOLLOW,
            PlayerShadingStyle.BOTTOM_FADE -> LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.94f))),
                    floatArrayOf(0f, .34f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.FLOOR_CEILING -> LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.55f)),
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.88f))),
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
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.72f)),
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.72f))),
                    floatArrayOf(0f, .34f, .66f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.TOP_FADE -> LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.94f)),
                            Color.TRANSPARENT, Color.TRANSPARENT),
                    floatArrayOf(0f, .58f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.CENTER_SPOTLIGHT -> RadialGradient(
                    cx, cy, maxOf(b.width(), b.height()) * .64f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.90f))),
                    floatArrayOf(0f, .38f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.DIAGONAL -> LinearGradient(
                    right, top, left, bottom,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.92f))),
                    floatArrayOf(0f, .40f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.LEFT_CURTAIN -> LinearGradient(
                    left, 0f, right, 0f,
                    intArrayOf(ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.90f)),
                            Color.TRANSPARENT, Color.TRANSPARENT),
                    floatArrayOf(0f, .56f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.RIGHT_CURTAIN -> LinearGradient(
                    left, 0f, right, 0f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.90f))),
                    floatArrayOf(0f, .44f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.CENTER_BAND -> LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.84f)),
                            Color.TRANSPARENT, Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.84f))),
                    floatArrayOf(0f, .40f, .60f, 1f), Shader.TileMode.CLAMP)

            PlayerShadingStyle.CROSSFADE -> LinearGradient(
                    left, top, right, bottom,
                    intArrayOf(
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.78f)),
                            Color.TRANSPARENT,
                            ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.78f))),
                    floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        }

        paint.color = when (style) {
            PlayerShadingStyle.FULL_FILTER ->
                ColorUtils.setAlphaComponent(shadingColor, scaledAlpha(.55f))
            PlayerShadingStyle.ALBUM_TINT ->
                ColorUtils.setAlphaComponent(darkAlbumTone(primary), scaledAlpha(.52f))
            else -> Color.WHITE
        }
        canvas.drawRect(left, top, right, bottom, paint)
        paint.shader = null
    }

    private fun darkAlbumTone(color: Int): Int = PaletteTransforms.shadingTone(color)

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
