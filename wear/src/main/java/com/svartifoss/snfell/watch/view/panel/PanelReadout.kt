package com.svartifoss.snfell.watch.view.panel

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.PaletteTransforms
import kotlin.math.roundToInt

/**
 * The centred readout of a panel surface - the seek time and the volume percentage - styled by
 * `MiscPreferences.WEAR_SEEK_STYLE`.
 *
 * Extracted out of `MainActivity` for the same reason [OverlayBackdropDrawables] was: the
 * dedicated volume and progress screens are separate Activities and could not reach a private
 * method there, so they shipped with a plain white number while the transient overlay of the very
 * same panel honoured all twenty-odd styles. One table, both surfaces.
 *
 * Everything here is a `TextView` restyled in place, which is why a Compose screen hosts it through
 * an `AndroidView` rather than reimplementing the styles: a second implementation of a twenty-branch
 * visual table is exactly the drift this project keeps paying for elsewhere.
 */
object PanelReadout {

    private const val PILL_ON_LIGHT = 0xFF202124.toInt()
    private val LIGHT_PANEL_ON = 0xFF111111.toInt()
    private val MONO_PANEL_SURFACE = 0xFF262626.toInt()
    private val TERMINAL_GREEN = 0xFF33FF66.toInt()

    /**
     * Black or white by whichever gives the higher WCAG contrast ratio against [backgroundColor]
     * (crossover ~0.179 luminance), not a naive 0.5 split - the latter left white icons on
     * medium-light pills (e.g. the expressive face's tonal surfaces, ~0.45 luminance but visually
     * light) where dark ink is clearly more legible.
     */
    fun contrastingIconColor(backgroundColor: Int): Int {
        val lum = ColorUtils.calculateLuminance(backgroundColor)
        val contrastWithWhite = 1.05 / (lum + 0.05)
        val contrastWithBlack = (lum + 0.05) / 0.05
        return if (contrastWithBlack >= contrastWithWhite) Color.BLACK else Color.WHITE
    }

    fun apply(
            text: TextView,
            style: String,
            content: String,
            accentColor: Int,
            /** The surface's companion colour - only "split_tone" reads it. */
            secondaryColor: Int,
            /** `R.color.theme_accent`, the app's own accent - the fixed colour "solid_theme"
             *  paints regardless of the album. */
            themeAccentColor: Int,
            screenFace: String,
            density: Float,
            /** The watch's UI font. Null keeps whatever the view already had - the readout used to
             *  render in the platform default while every surface around it followed the chosen
             *  font, which was most obvious on the dedicated screens, where a Compose title in the
             *  theme's font sat directly above a percentage in Roboto. */
            typeface: Typeface? = null
    ) {
        // One TextView is restyled in place every time the style changes, so any property only some
        // branches set has to be cleared here or it leaks into the next style. Background, colour,
        // size and padding are set by every branch; these two are not.
        // Bold regardless of family: this is a large glanceable number over a busy backdrop, and
        // it is the weight the transient overlay has always drawn it at.
        typeface?.let { text.setTypeface(it, Typeface.BOLD) }
        text.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        text.letterSpacing = 0f
        text.minWidth = 0
        text.minHeight = 0
        text.gravity = Gravity.CENTER
        text.setLineSpacing(0f, 1f)

        when (style) {
            "pill" -> {
                text.textSize = 26f
                text.setTextColor(Color.WHITE)
                text.setBackgroundResource(R.drawable.glass_pill_background)
                val padH = (18 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "glass_white" -> {
                text.textSize = 26f
                text.setTextColor(Color.WHITE)
                text.background = capsule(Color.argb(0x1A, 0xFF, 0xFF, 0xFF))
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "translucent_album" -> {
                text.textSize = 26f
                val accent = accentColor
                val tintColor = if (screenFace == "expressive") {
                    PaletteTransforms.tonalSurface(accent, 0.74f, 0.40f, 0.92f)
                } else {
                    accent
                }
                text.setTextColor(Color.WHITE)
                text.background = capsule(ColorUtils.setAlphaComponent(tintColor, 0x4D))
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "glow_album" -> {
                text.textSize = 26f
                val accent = accentColor
                val tintColor = if (screenFace == "expressive") {
                    PaletteTransforms.tonalSurface(accent, 0.74f, 0.40f, 0.92f)
                } else {
                    accent
                }
                val glowColor = liftedAccent(tintColor)
                text.setTextColor(glowColor)
                text.background = capsule(
                        Color.TRANSPARENT,
                        (2f * density).roundToInt(),
                        ColorUtils.setAlphaComponent(glowColor, 0xE0)
                )
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "outline" -> {
                text.textSize = 26f
                val accent = accentColor
                text.setTextColor(accent)
                text.background = capsule(
                        Color.TRANSPARENT,
                        (1.5f * density).roundToInt(),
                        ColorUtils.setAlphaComponent(accent, 0xE0)
                )
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "solid_theme" -> {
                text.textSize = 26f
                val accent = themeAccentColor
                val tintColor = if (screenFace == "expressive") {
                    PaletteTransforms.tonalSurface(accent, 0.74f, 0.40f, 0.92f)
                } else {
                    accent
                }
                text.setTextColor(contrastingIconColor(tintColor))
                text.background = capsule(tintColor)
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "solid_album" -> {
                text.textSize = 26f
                val accent = accentColor
                val tintColor = if (screenFace == "expressive") {
                    PaletteTransforms.tonalSurface(accent, 0.74f, 0.40f, 0.92f)
                } else {
                    accent
                }
                text.setTextColor(contrastingIconColor(tintColor))
                text.background = capsule(tintColor)
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "expressive" -> {
                text.textSize = 26f
                text.setTextColor(PILL_ON_LIGHT)
                text.background = capsule(expressivePillFillColor(accentColor))
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "material" -> {
                text.textSize = 26f
                text.setTextColor(PILL_ON_LIGHT)
                text.background = capsule(tonalSurface(accentColor, lightness = 0.92f))
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "white" -> {
                text.textSize = 26f
                text.setTextColor(PILL_ON_LIGHT)
                text.background = capsule(Color.WHITE)
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "giant" -> {
                text.textSize = 52f
                text.setTextColor(Color.WHITE)
                text.background = null
                text.setPadding(0, 0, 0, 0)
                text.text = content
            }
            // --- Minimal readouts: no filled container, the numerals themselves carry the style.
            "micro" -> {
                // Deliberately the smallest readout offered: on a scrub the ring already shows the
                // position, so the numerals only need to confirm it, not announce it.
                text.textSize = 15f
                text.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 0xB3))
                text.letterSpacing = 0.08f
                text.background = null
                text.setPadding(0, 0, 0, 0)
                text.text = content
            }
            "shadow" -> {
                // Chromeless but readable over bright artwork: the drop shadow does the work a
                // capsule would, without covering the art.
                text.textSize = 30f
                text.setTextColor(Color.WHITE)
                text.setShadowLayer(
                        6f * density, 0f,
                        1.5f * density,
                        ColorUtils.setAlphaComponent(Color.BLACK, 0xCC))
                text.background = null
                text.setPadding(0, 0, 0, 0)
                text.text = content
            }
            "underline" -> {
                val accent = liftedAccent(accentColor)
                text.textSize = 28f
                text.setTextColor(Color.WHITE)
                text.background = underlineDrawable(
                        accent, (2f * density).roundToInt())
                val padH = (4 * density).toInt()
                val padV = (6 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "hairline" -> {
                text.textSize = 22f
                text.setTextColor(Color.WHITE)
                text.background = capsule(
                        Color.TRANSPARENT,
                        (1f * density).roundToInt(),
                        ColorUtils.setAlphaComponent(Color.WHITE, 0x66))
                val padH = (20 * density).toInt()
                val padV = (7 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "giant_album" -> {
                text.textSize = 52f
                text.setTextColor(liftedAccent(accentColor))
                text.background = null
                text.setPadding(0, 0, 0, 0)
                text.text = content
            }
            // --- Filled counterparts that stay off the album accent entirely, or invert it.
            "mono" -> {
                text.textSize = 26f
                text.setTextColor(Color.WHITE)
                text.background = capsule(MONO_PANEL_SURFACE)
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "tonal_dark" -> {
                // The inverse of "material": a dark tonal container with light accent numerals,
                // for users who find the light pill too bright at night.
                val container = tonalSurface(accentColor, lightness = 0.22f)
                text.textSize = 26f
                text.setTextColor(liftedAccent(accentColor))
                text.background = capsule(container)
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "terminal" -> {
                // Sharp corners and forced green, matching the "terminal" vocabulary the arc and
                // quick panel already speak - the album accent is ignored on purpose.
                text.textSize = 24f
                text.setTextColor(TERMINAL_GREEN)
                text.letterSpacing = 0.12f
                text.background = capsule(
                        ColorUtils.setAlphaComponent(Color.BLACK, 0xCC),
                        (1f * density).roundToInt(),
                        ColorUtils.setAlphaComponent(TERMINAL_GREEN, 0x99),
                        radiusPx = 0f)
                val padH = (16 * density).toInt()
                val padV = (7 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "compact_pill" -> {
                text.textSize = 20f
                text.setTextColor(Color.WHITE)
                text.background = capsule(ColorUtils.setAlphaComponent(
                        tonalSurface(accentColor, .20f), 0xE6))
                val padH = (12 * density).toInt()
                val padV = (5 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "square_album" -> {
                val fill = tonalSurface(accentColor, .44f)
                text.textSize = 26f
                text.setTextColor(contrastingIconColor(fill))
                text.background = capsule(
                        fill, radiusPx = 8f * density)
                val pad = (12 * density).toInt()
                text.setPadding(pad, pad, pad, pad)
                text.text = content
            }
            "stacked_pill" -> {
                text.textSize = 22f
                text.setTextColor(Color.WHITE)
                text.background = capsule(ColorUtils.setAlphaComponent(
                        tonalSurface(accentColor, .22f), 0xE8))
                text.setLineSpacing(0f, .86f)
                val padH = (20 * density).toInt()
                val padV = (8 * density).toInt()
                text.setPadding(padH, padV, padH, padV)
                text.text = content
            }
            "badge" -> {
                val d = density
                text.textSize = 18f
                text.setTextColor(contrastingIconColor(accentColor))
                text.background = capsule(accentColor)
                text.minWidth = (58f * d).roundToInt()
                text.minHeight = (46f * d).roundToInt()
                text.setPadding((10f * d).roundToInt(), 0, (10f * d).roundToInt(), 0)
                text.text = content
            }
            "glass_bar" -> {
                val d = density
                text.textSize = 24f
                text.setTextColor(Color.WHITE)
                text.background = capsule(
                        0x24FFFFFF,
                        (1f * d).roundToInt(),
                        0x70FFFFFF,
                        radiusPx = 12f * d)
                text.setPadding((32f * d).roundToInt(), (7f * d).roundToInt(),
                        (32f * d).roundToInt(), (7f * d).roundToInt())
                text.text = content
            }
            "lcd" -> {
                val d = density
                text.textSize = 24f
                text.setTextColor(TERMINAL_GREEN)
                text.letterSpacing = .14f
                text.background = capsule(
                        0xF0000802.toInt(),
                        (2f * d).roundToInt(),
                        0x8833FF66.toInt(),
                        radiusPx = 3f * d)
                text.setPadding((16f * d).roundToInt(), (8f * d).roundToInt(),
                        (16f * d).roundToInt(), (8f * d).roundToInt())
                text.text = content
            }
            "outline_square" -> {
                val d = density
                val lifted = liftedAccent(accentColor)
                text.textSize = 25f
                text.setTextColor(lifted)
                text.background = capsule(
                        Color.TRANSPARENT,
                        (2f * d).roundToInt(),
                        lifted,
                        radiusPx = 6f * d)
                text.setPadding((17f * d).roundToInt(), (8f * d).roundToInt(),
                        (17f * d).roundToInt(), (8f * d).roundToInt())
                text.text = content
            }
            "ribbon" -> {
                val d = density
                val fill = tonalSurface(accentColor, .34f)
                text.textSize = 24f
                text.setTextColor(contrastingIconColor(fill))
                text.background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(
                                tonalSurface(accentColor, .18f),
                                tonalSurface(accentColor, .56f),
                                tonalSurface(accentColor, .18f))).apply {
                    cornerRadius = 4f * d
                }
                text.setPadding((30f * d).roundToInt(), (7f * d).roundToInt(),
                        (30f * d).roundToInt(), (7f * d).roundToInt())
                text.text = content
            }
            "bubble_time" -> {
                val d = density
                val fill = tonalSurface(accentColor, .48f)
                text.textSize = 22f
                text.setTextColor(contrastingIconColor(fill))
                text.background = capsule(fill, (1f * d).roundToInt(),
                        ColorUtils.setAlphaComponent(Color.WHITE, 0x70))
                text.minWidth = (72f * d).roundToInt()
                text.minHeight = (72f * d).roundToInt()
                text.setPadding((10f * d).roundToInt(), 0, (10f * d).roundToInt(), 0)
                text.text = content
            }
            "split_tone" -> {
                val d = density
                val left = tonalSurface(accentColor, .24f)
                val right = tonalSurface(secondaryColor, .52f)
                text.textSize = 25f
                text.setTextColor(Color.WHITE)
                text.background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(left, right)).apply { cornerRadius = 18f * d }
                text.setPadding((22f * d).roundToInt(), (8f * d).roundToInt(),
                        (22f * d).roundToInt(), (8f * d).roundToInt())
                text.text = content
            }
            // Position over total length, the second line smaller and dimmed. Seek-only: there is
            // no equivalent second line for a plain volume percentage, so a volume readout asking
            // for it falls through to the bare centred number below.
            "split" -> {
                text.textSize = 30f
                text.setTextColor(Color.WHITE)
                text.background = null
                text.setPadding(0, 0, 0, 0)
                val breakAt = content.indexOf('\n')
                if (breakAt < 0) {
                    text.text = content
                } else {
                    val stacked = SpannableString(content)
                    stacked.setSpan(RelativeSizeSpan(0.55f), breakAt + 1, stacked.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    stacked.setSpan(ForegroundColorSpan(0xB3FFFFFF.toInt()), breakAt + 1,
                            stacked.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text.text = stacked
                }
            }
            else -> {
                text.textSize = 30f
                text.setTextColor(Color.WHITE)
                text.background = null
                text.setPadding(0, 0, 0, 0)
                text.text = content
            }
        }
    }

    /**
     * Chromeless readouts draw white numerals straight onto the backdrop, which the LIGHT arc style
     * makes pale; they get flipped to dark text. Styles carrying their own filled container already
     * guarantee their own contrast and are left alone.
     *
     * Shared so the transient volume overlay and the dedicated volume screen make the same call -
     * the screen would otherwise show white-on-pale for exactly the arc style the rule exists for.
     */
    fun applyLightArcContrast(text: TextView, readoutStyle: String, volumeStyleIsLight: Boolean) {
        if (volumeStyleIsLight && readoutStyle !in STYLES_WITH_OWN_CONTAINER) {
            text.setTextColor(LIGHT_PANEL_ON)
        }
    }

    private val STYLES_WITH_OWN_CONTAINER = setOf(
            "pill", "expressive", "material", "white", "glass_white", "translucent_album",
            "glow_album", "outline", "solid_theme", "solid_album", "mono", "tonal_dark",
            "terminal", "compact_pill", "square_album", "stacked_pill", "badge", "glass_bar",
            "lcd", "outline_square", "ribbon")

    /** Light, saturated tint of the album accent - the "expressive" pill style's fill. Lightness
     *  is high enough that dark text stays legible regardless of the accent's own hue. */
    fun expressivePillFillColor(accentColor: Int): Int =
            tonalSurface(accentColor, lightness = 0.82f)

    /**
     * The album accent raised to a lightness that stays readable when it is used as *text* or a
     * hairline rather than as a fill. Dark album art routinely yields an accent near-black, which
     * is invisible on the overlay backdrop; anything already light is passed through untouched.
     */
    fun liftedAccent(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        if (hsl[2] >= 0.4f) return color
        hsl[2] = 0.45f
        return ColorUtils.HSLToColor(hsl)
    }

    fun tonalSurface(accent: Int, lightness: Float = 0.28f): Int =
            PaletteTransforms.tonalSurface(accent, lightness)

    fun capsule(fill: Int, strokePx: Int = 0, strokeColor: Int = 0, radiusPx: Float = 999f) =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(fill)
                if (strokePx > 0) setStroke(strokePx, strokeColor)
            }

    fun gradientCapsule(topColor: Int, bottomColor: Int, radiusPx: Float = 999f) =
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(topColor, bottomColor)).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
            }

    /**
     * A rule drawn along the bottom edge of whatever it backs, used by the "underline" readout.
     * A [LayerDrawable] with a bottom-gravity fixed-height layer rather than an inset drawable,
     * because the inset would have to be computed from a view height that isn't known when the
     * background is assigned.
     */
    fun underlineDrawable(color: Int, thicknessPx: Int): LayerDrawable =
            LayerDrawable(arrayOf<android.graphics.drawable.Drawable>(
                    ColorDrawable(Color.TRANSPARENT),
                    GradientDrawable().apply { setColor(color); cornerRadius = thicknessPx / 2f }
            )).apply {
                setLayerGravity(1, Gravity.BOTTOM)
                setLayerHeight(1, thicknessPx)
            }
}
