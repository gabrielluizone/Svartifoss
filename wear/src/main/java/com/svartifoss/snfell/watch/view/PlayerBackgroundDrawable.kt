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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.common.AccentFloorStyle
import com.svartifoss.snfell.common.AlbumFillSlot
import com.svartifoss.snfell.common.OverlayBackdropPatterns
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
        private val materialSurfaceSoftened: Boolean,
        /** Only the drawn patterns need it, but they need a *real* one: their dot pitch and line
         *  weights are in dp, and `Resources.getSystem()` reports the default display rather than
         *  the watch's. */
        private val density: Float
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

            PlayerBackgroundStyle.PRISMATIC -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.28f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = LinearGradient(left, top, right, bottom,
                        intArrayOf(ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.48f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.22f)),
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.36f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.72f))),
                        floatArrayOf(0f, .34f, .67f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                stroke.shader = SweepGradient(cx, cy,
                        intArrayOf(tunedPrimary, tunedSecondary, tunedTertiary, tunedPrimary), null)
                stroke.strokeWidth = minDimension * .10f
                stroke.alpha = fixedAlpha(.42f)
                canvas.drawCircle(cx, cy, minDimension * .41f, stroke)
                stroke.shader = null
                stroke.alpha = 255
            }

            PlayerBackgroundStyle.CRESCENT -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.34f))
                canvas.drawRect(left, top, right, bottom, paint)
                stroke.shader = SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT,
                        ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.72f)),
                        ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.28f)),
                        Color.TRANSPARENT), null)
                stroke.style = Paint.Style.STROKE
                stroke.strokeWidth = minDimension * .12f
                stroke.alpha = fixedAlpha(.72f)
                canvas.drawArc(cx - minDimension * .54f, cy - minDimension * .54f,
                        cx + minDimension * .54f, cy + minDimension * .54f, 138f, 196f,
                        false, stroke)
                stroke.style = Paint.Style.FILL
                stroke.shader = null
                stroke.alpha = 255
            }

            PlayerBackgroundStyle.TIDAL -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.20f))
                canvas.drawRect(left, top, right, bottom, paint)
                listOf(Triple(.34f, tunedPrimary, .11f), Triple(.55f, tunedSecondary, .08f),
                        Triple(.76f, tunedTertiary, .06f)).forEachIndexed { index, (y, color, width) ->
                    val wave = Path().apply {
                        moveTo(left, top + height * y)
                        cubicTo(left + width * .24f, top + height * (y - .11f + index * .02f),
                                left + width * .70f, top + height * (y + .10f),
                                right, top + height * (y - .03f))
                    }
                    stroke.color = color
                    stroke.strokeWidth = minDimension * width
                    stroke.alpha = authoredAlpha(.78f)
                    canvas.drawPath(wave, stroke)
                }
                stroke.alpha = 255
            }

            PlayerBackgroundStyle.PAPER -> {
                paint.color = ColorUtils.setAlphaComponent(0xFFFFF3DF.toInt(), authoredAlpha(.18f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = LinearGradient(0f, top, 0f, bottom,
                        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.62f))),
                        floatArrayOf(0f, .64f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                stroke.color = Color.WHITE
                stroke.style = Paint.Style.STROKE
                stroke.strokeWidth = minDimension * .009f
                stroke.alpha = fixedAlpha(.44f)
                canvas.drawRect(left + minDimension * .065f, top + minDimension * .065f,
                        right - minDimension * .065f, bottom - minDimension * .065f, stroke)
                stroke.style = Paint.Style.FILL
                stroke.alpha = 255
            }

            PlayerBackgroundStyle.LANTERN -> {
                paint.shader = LinearGradient(0f, top, 0f, bottom,
                        intArrayOf(ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.55f)),
                                Color.TRANSPARENT, ColorUtils.setAlphaComponent(deep, authoredAlpha(.72f))),
                        floatArrayOf(0f, .46f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(cx, top + height * .82f, minDimension * .41f,
                        intArrayOf(ColorUtils.setAlphaComponent(0xFFFFC857.toInt(), fixedAlpha(.52f)),
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.18f)),
                                Color.TRANSPARENT), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.MIRAGE -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.18f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .08f, top + height * .38f,
                        minDimension * .58f, intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.46f)),
                                Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(right - width * .08f, top + height * .62f,
                        minDimension * .58f, intArrayOf(
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.42f)),
                                Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.GRID -> {
                paint.color = ColorUtils.setAlphaComponent(deep, authoredAlpha(.64f))
                canvas.drawRect(left, top, right, bottom, paint)
                stroke.color = ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.22f))
                stroke.strokeWidth = minDimension * .006f
                stroke.alpha = authoredAlpha(.72f)
                for (step in 1..5) {
                    val x = left + width * step / 6f
                    val y = top + height * step / 6f
                    canvas.drawLine(x, top, x, bottom, stroke)
                    canvas.drawLine(left, y, right, y, stroke)
                }
                stroke.alpha = 255
            }

            PlayerBackgroundStyle.NOCTURNE -> {
                paint.color = ColorUtils.setAlphaComponent(0xFF070B25.toInt(), authoredAlpha(.72f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .68f, top + height * .28f,
                        minDimension * .58f, intArrayOf(
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.36f)),
                                Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = null
                listOf(.16f to .22f, .72f to .18f, .37f to .58f, .82f to .72f).forEach { (x, y) ->
                    paint.color = ColorUtils.setAlphaComponent(Color.WHITE, fixedAlpha(.62f))
                    canvas.drawCircle(left + width * x, top + height * y, minDimension * .009f, paint)
                }
            }

            PlayerBackgroundStyle.CLOUD -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.16f))
                canvas.drawRect(left, top, right, bottom, paint)
                listOf(Triple(.22f, .34f, tunedPrimary), Triple(.74f, .30f, tunedSecondary),
                        Triple(.50f, .78f, tunedTertiary)).forEach { (x, y, color) ->
                    paint.shader = RadialGradient(left + width * x, top + height * y,
                            minDimension * .49f, intArrayOf(
                                    ColorUtils.setAlphaComponent(color, fixedAlpha(.32f)),
                                    Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawRect(left, top, right, bottom, paint)
                }
            }

            PlayerBackgroundStyle.LIQUID -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.30f))
                canvas.drawRect(left, top, right, bottom, paint)
                listOf(Triple(.18f, .72f, tunedPrimary), Triple(.62f, .42f, tunedSecondary),
                        Triple(.86f, .76f, tunedTertiary)).forEach { (x, y, color) ->
                    paint.shader = RadialGradient(left + width * x, top + height * y,
                            minDimension * .38f, intArrayOf(
                                    ColorUtils.setAlphaComponent(color, fixedAlpha(.52f)),
                                    ColorUtils.setAlphaComponent(color, fixedAlpha(.12f)),
                                    Color.TRANSPARENT), floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawRect(left, top, right, bottom, paint)
                }
            }

            PlayerBackgroundStyle.MONOLITH -> {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.58f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = LinearGradient(left, 0f, left + width * .34f, 0f,
                        intArrayOf(ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.58f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.12f)),
                                Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, left + width * .48f, bottom, paint)
                paint.shader = LinearGradient(0f, top, 0f, bottom,
                        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.76f))),
                        floatArrayOf(0f, .64f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.SPLIT_TONE -> {
                paint.shader = LinearGradient(0f, top, 0f, bottom,
                        intArrayOf(ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.36f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.20f)),
                                ColorUtils.setAlphaComponent(deep, authoredAlpha(.78f))),
                        floatArrayOf(0f, .49f, .51f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                stroke.color = ColorUtils.setAlphaComponent(Color.WHITE, fixedAlpha(.48f))
                stroke.strokeWidth = minDimension * .006f
                stroke.alpha = authoredAlpha(.76f)
                canvas.drawLine(left, top + height * .50f, right, top + height * .50f, stroke)
                stroke.alpha = 255
            }

            PlayerBackgroundStyle.GRADIENT -> {
                paint.shader = LinearGradient(left, top, right, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.46f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.30f))),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.DUOTONE -> {
                paint.shader = LinearGradient(left, cy, right, cy,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.40f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.40f))),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.BANDS -> {
                paint.shader = LinearGradient(0f, top, 0f, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.30f)),
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.44f)),
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.18f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.38f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.82f))),
                        floatArrayOf(0f, .25f, .5f, .75f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.VIGNETTE -> {
                paint.shader = null
                paint.color = ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.20f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(cx, cy, minDimension * .68f,
                        intArrayOf(
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.14f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.90f))),
                        floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.GRAPHITE -> {
                paint.shader = null
                paint.color = ColorUtils.setAlphaComponent(0xFF111318.toInt(), authoredAlpha(.86f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = LinearGradient(left, top, right, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(0xFF292D34.toInt(), authoredAlpha(.62f)),
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(0xFF1D2026.toInt(), authoredAlpha(.62f))),
                        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.CINEMA -> {
                paint.shader = LinearGradient(0f, top, 0f, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.94f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.88f)),
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.34f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.24f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.88f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.94f))),
                        floatArrayOf(0f, .2f, .4f, .6f, .8f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.ACRYLIC -> {
                paint.shader = LinearGradient(left, top, right, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.40f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.72f))),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.MESH -> {
                paint.shader = null
                paint.color = ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.14f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .18f, top + height * .22f,
                        minDimension * .72f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.44f)),
                                Color.TRANSPARENT,),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .86f, top + height * .78f,
                        minDimension * .68f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.38f)),
                                Color.TRANSPARENT,),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.NEBULA -> {
                paint.shader = null
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.42f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .16f, top + height * .24f,
                        minDimension * .70f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.42f)),
                                Color.TRANSPARENT,),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .84f, top + height * .32f,
                        minDimension * .64f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.38f)),
                                Color.TRANSPARENT,),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .50f, top + height * .94f,
                        minDimension * .58f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.34f)),
                                Color.TRANSPARENT,),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.BIOLUMINESCENCE -> {
                paint.shader = null
                paint.color = ColorUtils.setAlphaComponent(0xFF041A19.toInt(), authoredAlpha(.66f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .20f, top + height * .74f,
                        minDimension * .62f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.52f)),
                                ColorUtils.setAlphaComponent(0xFF0A6A62.toInt(), fixedAlpha(.26f)),
                                Color.TRANSPARENT,),
                        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .82f, top + height * .24f,
                        minDimension * .54f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.44f)),
                                ColorUtils.setAlphaComponent(0xFF1AB5A2.toInt(), fixedAlpha(.18f)),
                                Color.TRANSPARENT,),
                        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.IRIDESCENT -> {
                paint.shader = LinearGradient(left, top, right, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.36f)),
                                ColorUtils.setAlphaComponent(0xFF4A2F72.toInt(), fixedAlpha(.44f)),
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.42f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.30f)),
                                ColorUtils.setAlphaComponent(0xFF0B101A.toInt(), authoredAlpha(.82f))),
                        floatArrayOf(0f, .25f, .5f, .75f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.ORBIT -> {
                paint.shader = null
                paint.color = ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.13f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .16f, top + height * .30f,
                        minDimension * .64f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.46f)),
                                Color.TRANSPARENT,),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .84f, top + height * .72f,
                        minDimension * .62f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.38f)),
                                Color.TRANSPARENT,),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = RadialGradient(left + width * .50f, top + height * .50f,
                        minDimension * .26f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.34f)),
                                Color.TRANSPARENT,),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.INK_WASH -> {
                paint.shader = null
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.34f))
                canvas.drawRect(left, top, right, bottom, paint)
                paint.shader = LinearGradient(left, top, right, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.44f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.16f)),
                                Color.TRANSPARENT),
                        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.BLOSSOM -> {
                paint.shader = LinearGradient(left, bottom, right, top,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(0xFF160B1D.toInt(), authoredAlpha(.80f)),
                                ColorUtils.setAlphaComponent(0xFF542047.toInt(), fixedAlpha(.52f)),
                                ColorUtils.setAlphaComponent(0xFFB84B74.toInt(), fixedAlpha(.44f)),
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.34f)),
                                ColorUtils.setAlphaComponent(0xFF08050B.toInt(), authoredAlpha(.84f))),
                        floatArrayOf(0f, .25f, .5f, .75f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.FJORD -> {
                paint.shader = LinearGradient(left, top, right, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(0xFF0A2030.toInt(), authoredAlpha(.76f)),
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.34f)),
                                ColorUtils.setAlphaComponent(0xFF0A5960.toInt(), fixedAlpha(.44f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.86f))),
                        floatArrayOf(0f, .33f, .66f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.DOT_MATRIX -> {
                paint.shader = null
                OverlayBackdropPatterns.drawDotMatrix(
                        canvas, RectF(left, top, right, bottom), density,
                        baseColor = Color.TRANSPARENT,
                        dotColor = ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.44f)))
            }

            PlayerBackgroundStyle.SCANLINES -> {
                paint.shader = null
                OverlayBackdropPatterns.drawScanlines(
                        canvas, RectF(left, top, right, bottom), density,
                        baseColor = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.34f)),
                        lineColor = ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.52f)))
            }

            PlayerBackgroundStyle.RADAR -> {
                paint.shader = null
                OverlayBackdropPatterns.drawRadarRings(
                        canvas, RectF(left, top, right, bottom), density,
                        cx = cx, cy = cy, radius = minDimension / 2f,
                        baseColor = ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.12f)),
                        ringColor = ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.50f)),
                        sweepColor = ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.42f)))
            }

            PlayerBackgroundStyle.CONTOUR -> {
                paint.shader = null
                OverlayBackdropPatterns.drawContourLines(
                        canvas, RectF(left, top, right, bottom), density,
                        cx = cx, cy = cy, radius = minDimension / 2f,
                        baseColor = ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.12f)),
                        lineColor = ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.46f)),
                        accent = tunedPrimary)
            }

            PlayerBackgroundStyle.FACETED -> {
                paint.shader = null
                OverlayBackdropPatterns.drawFacetedCrystal(
                        canvas, RectF(left, top, right, bottom), density,
                        primary = ColorUtils.setAlphaComponent(tunedPrimary, fixedAlpha(.42f)),
                        secondary = ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.38f)),
                        tertiary = ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.34f)),
                        accent = tunedPrimary)
            }

            PlayerBackgroundStyle.SOLID_ALBUM,
            PlayerBackgroundStyle.SOLID_SECONDARY,
            PlayerBackgroundStyle.SOLID_TERTIARY -> {
                paint.shader = null
                paint.color = PaletteTransforms.tonalSurface(
                        when (style.flatAlbumFill) {
                            AlbumFillSlot.SECONDARY -> secondary
                            AlbumFillSlot.TERTIARY -> tertiary
                            else -> primary
                        },
                        .24f, PaletteTransforms.FACE_MIN_SAT, PaletteTransforms.FACE_MAX_SAT)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.GLASS -> {
                paint.shader = LinearGradient(0f, top, 0f, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(Color.WHITE, fixedAlpha(.18f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.72f))),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.MIDNIGHT -> {
                paint.shader = LinearGradient(0f, top, 0f, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.30f)),
                                ColorUtils.setAlphaComponent(0xFF070914.toInt(), authoredAlpha(.66f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.86f))),
                        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.SMOKE -> {
                paint.shader = LinearGradient(left, top, right, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(tunedTertiary, fixedAlpha(.24f)),
                                ColorUtils.setAlphaComponent(0xFF323238.toInt(), authoredAlpha(.56f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.82f))),
                        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(left, top, right, bottom, paint)
            }

            PlayerBackgroundStyle.TIDELINE -> {
                paint.shader = LinearGradient(0f, top, 0f, bottom,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(0xFF031423.toInt(), authoredAlpha(.72f)),
                                ColorUtils.setAlphaComponent(0xFF07516A.toInt(), fixedAlpha(.52f)),
                                ColorUtils.setAlphaComponent(tunedSecondary, fixedAlpha(.30f)),
                                ColorUtils.setAlphaComponent(Color.BLACK, authoredAlpha(.84f))),
                        floatArrayOf(0f, .33f, .66f, 1f), Shader.TileMode.CLAMP)
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
