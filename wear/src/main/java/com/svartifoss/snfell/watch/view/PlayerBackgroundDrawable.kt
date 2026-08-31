package com.svartifoss.snfell.watch.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.common.AccentFloorStyle
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.ResolvedBackgroundLayer
import com.svartifoss.snfell.common.SHADING_MAX_MULTIPLIER

/**
 * Native counterpart of Compose's PlayerBackgroundTreatment for the Classic layout.
 *
 * It draws the whole ordered stack rather than one treatment, for the same reason the Compose side
 * does it in a single Canvas: the order of these passes is a user choice now, and two sibling
 * Views can only express the one order the layout file happens to declare. So Classic's shading
 * scrim is drawn here too, rather than on the separate View that used to sit above this one.
 */
class PlayerBackgroundDrawable(
        private val layers: List<ResolvedBackgroundLayer>,
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

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        layers.forEach { layer ->
            when (layer) {
                is ResolvedBackgroundLayer.Wash -> drawWash(canvas, layer.style, layer.strength)
                // The shading vocabulary already has exactly one implementation, and a layer is
                // not a reason to grow a second copy of it that can drift.
                is ResolvedBackgroundLayer.Shade -> PlayerShadingDrawable(
                        style = layer.style,
                        intensity = layer.strength,
                        primary = primary,
                        secondary = secondary,
                        shadingColor = layer.color).apply {
                    bounds = this@PlayerBackgroundDrawable.bounds
                    setAlpha(drawableAlpha)
                }.draw(canvas)

                is ResolvedBackgroundLayer.Floor ->
                    drawAccentFloor(canvas, layer.style, layer.color, layer.strength)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun drawWash(
            canvas: Canvas,
            style: PlayerBackgroundStyle,
            authoredStrength: Float
    ) {
        fun authoredAlpha(base: Float): Int =
                (255f * base * authoredStrength.coerceIn(0f, AUTHORED_STRENGTH_CEILING) *
                        drawableAlpha / 255f)
                        .toInt().coerceIn(0, 255)

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
            // Frosting lives in the bitmap itself (FrostedEdges), so like every other plain
            // artwork treatment this layer draws nothing on top of it.
            PlayerBackgroundStyle.FROSTED,
            PlayerBackgroundStyle.BLACK_AND_WHITE,
            PlayerBackgroundStyle.BLURRED_BLACK_AND_WHITE,
            PlayerBackgroundStyle.SQUARE_SHARP,
            PlayerBackgroundStyle.SQUARE_SOFT,
            PlayerBackgroundStyle.SQUARE,
            PlayerBackgroundStyle.FILTER_WARM,
            PlayerBackgroundStyle.FILTER_COOL,
            PlayerBackgroundStyle.FILTER_GOLDEN,
            PlayerBackgroundStyle.FILTER_ROSE,
            PlayerBackgroundStyle.FILTER_VINTAGE,
            PlayerBackgroundStyle.FILTER_FADED,
            PlayerBackgroundStyle.FILTER_MATTE,
            PlayerBackgroundStyle.FILTER_VIVID,
            PlayerBackgroundStyle.FILTER_PUNCH,
            PlayerBackgroundStyle.FILTER_PASTEL,
            PlayerBackgroundStyle.FILTER_SEPIA,
            PlayerBackgroundStyle.FILTER_CYANOTYPE,
            PlayerBackgroundStyle.FILTER_TEAL_ORANGE,
            PlayerBackgroundStyle.FILTER_HIGH_CONTRAST,
            PlayerBackgroundStyle.FILTER_SOFT_LIGHT,
            PlayerBackgroundStyle.FILTER_NIGHT -> Unit

            PlayerBackgroundStyle.EXPRESSIVE,
            PlayerBackgroundStyle.EXPRESSIVE_NO_BLUR -> {
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

            PlayerBackgroundStyle.CORONA -> {
                // Color lives only in a soft ring hugging the rim - a wide stroked circle, not a
                // full-bleed fill - so the cover stays fully legible through its center and only
                // the border picks up the sweep's hues.
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.16f))
                canvas.drawRect(left, top, right, bottom, paint)
                stroke.shader = SweepGradient(
                        cx, cy,
                        intArrayOf(tunedTertiary, tunedPrimary, tunedSecondary, tunedTertiary),
                        null)
                stroke.strokeWidth = minDimension * .24f
                stroke.alpha = fixedAlpha(.58f)
                canvas.drawCircle(cx, cy, maxDimension * .44f, stroke)
                stroke.shader = null
                stroke.alpha = 255
            }

            PlayerBackgroundStyle.DUSK -> {
                // No base fill at all - the fade itself is the only treatment, so the top of the
                // cover stays untouched and only the lower band darkens toward black.
                paint.shader = LinearGradient(
                        0f, top, 0f, bottom,
                        intArrayOf(
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(deep, fixedAlpha(.38f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.70f))),
                        floatArrayOf(0f, .60f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.BLOOM -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.16f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(
                        left + width * .22f, top + height * .26f, minDimension * .52f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.38f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .85f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(
                        left + width * .80f, top + height * .22f, minDimension * .46f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.32f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .85f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(
                        left + width * .50f, top + height * .88f, minDimension * .48f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.28f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .85f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.HORIZON -> {
                paint.shader = LinearGradient(
                        0f, top, 0f, bottom,
                        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.62f))),
                        floatArrayOf(0f, .72f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.EMBER -> {
                paint.shader = RadialGradient(
                        left + width * .82f, top + height * .84f, minDimension * .46f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.40f)),
                                ColorUtils.setAlphaComponent(deep, fixedAlpha(.22f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.OCEAN -> {
                paint.shader = LinearGradient(0f, top, 0f, bottom,
                        intArrayOf(Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.22f)),
                                ColorUtils.setAlphaComponent(0xFF063C4C.toInt(), authoredAlpha(.78f))),
                        floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.SUNSET -> {
                paint.shader = LinearGradient(left, top, right, bottom,
                        intArrayOf(Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(0xFFFF6F61.toInt(), fixedAlpha(.28f)),
                                ColorUtils.setAlphaComponent(0xFFFFB347.toInt(), fixedAlpha(.34f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.62f))),
                        floatArrayOf(0f, .46f, .72f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.SPOTLIGHT -> {
                paint.shader = RadialGradient(cx, cy, minDimension * .72f,
                        intArrayOf(Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(deep, fixedAlpha(.16f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.90f))),
                        floatArrayOf(0f, .44f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.GLASS_VEIL -> {
                paint.color = ColorUtils.setAlphaComponent(Color.WHITE, fixedAlpha(.12f))
                canvas.drawRect(left, top, right, bottom, paint)
                stroke.shader = SweepGradient(cx, cy,
                        intArrayOf(Color.WHITE, tunedPrimary, Color.WHITE, tunedSecondary, Color.WHITE),
                        null)
                stroke.strokeWidth = minDimension * .018f
                stroke.alpha = fixedAlpha(.72f)
                canvas.drawCircle(cx, cy, minDimension * .485f, stroke)
                stroke.shader = null
                stroke.alpha = 255
            }

            PlayerBackgroundStyle.VELVET -> {
                paint.color = ColorUtils.setAlphaComponent(0xFF120B16.toInt(), authoredAlpha(.74f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .34f, top + height * .76f,
                        minDimension * .62f,
                        intArrayOf(ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.34f)),
                                Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.NOIR -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.58f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(cx, cy - height * .08f, minDimension * .58f,
                        intArrayOf(ColorUtils.setAlphaComponent(Color.WHITE, fixedAlpha(.10f)),
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.82f))),
                        floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.ICE -> {
                paint.shader = LinearGradient(0f, top, 0f, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(0xFFB9F3FF.toInt(), fixedAlpha(.34f)),
                                ColorUtils.setAlphaComponent(0xFF3B82C4.toInt(), fixedAlpha(.22f)),
                                ColorUtils.setAlphaComponent(0xFF061426.toInt(), authoredAlpha(.76f))),
                        floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.ROSE -> {
                paint.shader = RadialGradient(left + width * .72f, top + height * .74f,
                        minDimension * .72f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(0xFFFF8CAB.toInt(), fixedAlpha(.42f)),
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.18f)),
                                ColorUtils.setAlphaComponent(0xFF1B0810.toInt(), authoredAlpha(.68f))),
                        floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
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

    /** Native twin of Compose's accentFloorGlow for the View-based Classic face. */
    private fun drawAccentFloor(
            canvas: Canvas,
            style: AccentFloorStyle,
            accent: Int,
            alphaScale: Float
    ) {
        if (!style.isVisible) return
        val peak = (style.maxAlpha * alphaScale).coerceIn(0f, 1f)
        if (peak <= 0f) return

        val b = bounds
        val width = b.width().toFloat()
        val height = b.height().toFloat()
        val radius = minOf(width, height) / 2f
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val saved = canvas.saveLayer(
                b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), null)

        paint.xfermode = null
        paint.shader = RadialGradient(
                cx,
                cy,
                radius,
                intArrayOf(
                        Color.TRANSPARENT,
                        ColorUtils.setAlphaComponent(accent, (peak * 255).toInt())
                ),
                floatArrayOf(style.innerStop, 1f),
                Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)

        paint.shader = LinearGradient(
                0f,
                b.top + height * style.maskStart,
                0f,
                b.top + height * AccentFloorStyle.MASK_END,
                Color.TRANSPARENT,
                Color.BLACK,
                Shader.TileMode.CLAMP)
        paint.xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(b, paint)

        paint.xfermode = null
        paint.shader = null
        canvas.restoreToCount(saved)
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

    companion object {
        // Callers pass authoredStrength = shadingMultiplier / 0.8 (the reference balanced anchor,
        // so 80% == 1.0). The safety clamp must admit the maximum multiplier through that same
        // mapping, otherwise stronger-than-100% shading would be silently capped here.
        private val AUTHORED_STRENGTH_CEILING = SHADING_MAX_MULTIPLIER / 0.8f
    }
}
