package com.svartifoss.snfell.watch.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.common.PlayerBackgroundStyle

/** Native counterpart of Compose's PlayerBackgroundTreatment for the Classic layout. */
class PlayerBackgroundDrawable(
        private val style: PlayerBackgroundStyle,
        private val authoredStrength: Float,
        private val primary: Int,
        private val secondary: Int,
        private val tertiary: Int,
        private val materialSurface: Int,
        private val materialSurfaceSoftened: Boolean
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var drawableAlpha = 255

    private fun fixedAlpha(base: Float): Int =
            (255f * base * drawableAlpha / 255f).toInt().coerceIn(0, 255)

    private fun authoredAlpha(base: Float): Int =
            (255f * base * authoredStrength.coerceIn(0f, 1.25f) * drawableAlpha / 255f)
                    .toInt().coerceIn(0, 255)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val left = b.left.toFloat()
        val top = b.top.toFloat()
        val right = b.right.toFloat()
        val bottom = b.bottom.toFloat()
        val width = b.width().toFloat()
        val height = b.height().toFloat()
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val minDimension = minOf(width, height)
        val maxDimension = maxOf(width, height)
        val tunedPrimary = PaletteTransforms.tunedFaceColor(primary, .62f, .74f)
        val tunedSecondary = PaletteTransforms.tunedFaceColor(secondary, .58f, .70f)
        val tunedTertiary = PaletteTransforms.tunedFaceColor(tertiary, .62f, .72f)
        val deep = PaletteTransforms.tunedFaceColor(primary, .075f, .48f)
        val surface = PaletteTransforms.tunedFaceColor(secondary, .16f, .42f)

        paint.shader = null
        when (style) {
            PlayerBackgroundStyle.COVER,
            PlayerBackgroundStyle.BLUR,
            PlayerBackgroundStyle.BLACK_AND_WHITE,
            PlayerBackgroundStyle.BLURRED_BLACK_AND_WHITE -> Unit

            PlayerBackgroundStyle.EXPRESSIVE -> {
                paint.color = ColorUtils.setAlphaComponent(
                        PaletteTransforms.tonalSurface(primary, .30f, .30f, .90f), fixedAlpha(.45f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.30f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(
                        cx, cy, maxDimension * .68f,
                        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.88f))),
                        floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.MATERIAL -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, fixedAlpha(1f))
                canvas.drawRect(left, top, right, bottom, paint)
                val tint = PaletteTransforms.tonalSurface(
                        materialSurface,
                        if (materialSurfaceSoftened) .36f else .26f,
                        if (materialSurfaceSoftened) 0f else .30f,
                        .80f)
                paint.shader = RadialGradient(
                        cx, cy, minDimension * .85f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tint, fixedAlpha(.72f)),
                                ColorUtils.setAlphaComponent(tint, fixedAlpha(.38f)),
                                ColorUtils.setAlphaComponent(tint, fixedAlpha(.12f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .50f, .80f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.POSTER -> {
                paint.color = ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.12f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = LinearGradient(
                        0f, top, 0f, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.48f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.06f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.25f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.94f))),
                        floatArrayOf(0f, .36f, .68f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = LinearGradient(
                        left, 0f, right, 0f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.36f)),
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.36f))),
                        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.STUDIO -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.48f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = LinearGradient(
                        right, top, left, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.44f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.15f)),
                                Color.TRANSPARENT),
                        null, Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.VINYL -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.68f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(
                        left + width * .64f, top + height * .38f, minDimension * .69f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.32f)),
                                ColorUtils.setAlphaComponent(deep, fixedAlpha(.20f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.HALO -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.68f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(
                        cx, cy, minDimension * .62f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.50f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.18f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.AURORA -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(1f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(
                        left + width * .18f, top + height * .14f, minDimension * .78f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.48f)),
                                ColorUtils.setAlphaComponent(deep, fixedAlpha(.30f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(
                        left + width * .88f, top + height * .72f, minDimension * .72f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.38f)),
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.18f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)

                listOf(
                        Triple(.30f, .52f, tunedPrimary),
                        Triple(.43f, .63f, tunedSecondary),
                        Triple(.56f, .72f, tunedTertiary)
                ).forEachIndexed { index, (startY, endY, color) ->
                    val path = Path().apply {
                        moveTo(left - width * .14f, top + height * startY)
                        cubicTo(
                                left + width * .18f,
                                top + height * (startY - .24f + index * .025f),
                                left + width * .58f,
                                top + height * (endY + .18f - index * .02f),
                                left + width * 1.14f,
                                top + height * endY)
                    }
                    stroke.shader = LinearGradient(
                            left, cy, right, cy,
                            intArrayOf(color, tunedPrimary, tunedSecondary),
                            null, Shader.TileMode.CLAMP)
                    stroke.strokeWidth = minDimension * (.085f - index * .012f)
                    stroke.alpha = fixedAlpha(.32f - index * .045f)
                    canvas.drawPath(path, stroke)
                }
                stroke.shader = null
                stroke.alpha = 255
                paint.shader = LinearGradient(
                        0f, top, 0f, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.06f)),
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.78f))),
                        floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.SPECTRUM -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.58f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = LinearGradient(
                        0f, top, 0f, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(surface, fixedAlpha(.78f)),
                                ColorUtils.setAlphaComponent(deep, fixedAlpha(.90f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.88f))),
                        null, Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.ECLIPSE,
            PlayerBackgroundStyle.HIDDEN -> {
                paint.shader = null
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, fixedAlpha(1f))
                canvas.drawRect(left, top, right, bottom, paint)
            }
        }
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        stroke.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
