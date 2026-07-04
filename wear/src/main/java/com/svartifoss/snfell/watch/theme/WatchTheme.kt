package com.svartifoss.snfell.watch.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.R

/**
 * Single source of truth for the watch UI's design constants, shared by all three UI stacks in
 * this module: legacy Views, Compose (queue) and ProtoLayout (Tiles). XML resources can't
 * reference these values, so colors.xml mirrors [ACCENT_DEFAULT] (theme_accent) and
 * [SURFACE_DARK] (queue_pill_fill) - keep them in sync when changing either side.
 */
object WatchTheme {
    /** Default accent (sage) used when no album art palette is available. */
    const val ACCENT_DEFAULT = 0xFF87A89F.toInt()

    const val BACKGROUND_BLACK = 0xFF000000.toInt()

    /** Lifted near-black for idle pills/cards/buttons on the OLED-black background. */
    const val SURFACE_DARK = 0xFF1E1E20.toInt()

    /** Primary text/icon color on dark surfaces. */
    const val ON_SURFACE = 0xFFF0F0F0.toInt()

    /** Secondary/subtitle text color on dark surfaces. */
    const val TEXT_SECONDARY = 0xFFB0B0B0.toInt()

    private const val TEXT_ACCENT_MIN_LIGHTNESS = 0.62f
    private const val SURFACE_ACCENT_MIN_SATURATION = 0.45f
    private const val SURFACE_ACCENT_MIN_LIGHTNESS = 0.62f
    private const val SURFACE_ACCENT_MAX_LIGHTNESS = 0.82f

    /**
     * Adapts a raw palette [color] for use as *text* on the dark background: raises lightness so
     * it reads as a soft accent instead of vanishing into black.
     */
    fun accentForText(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceAtLeast(TEXT_ACCENT_MIN_LIGHTNESS)
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * Adapts a raw palette [color] for use as a *filled surface carrying black text* (queue
     * now-playing row, drawer highlight pill): clamps saturation up and lightness into a band
     * where the fill reads clearly against pure black and black text always reads on the fill -
     * the same way a light theme's accent gets a brighter dark-theme variant instead of being
     * reused as-is.
     */
    fun accentForSurface(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceAtLeast(SURFACE_ACCENT_MIN_SATURATION)
        hsl[2] = hsl[2].coerceIn(SURFACE_ACCENT_MIN_LIGHTNESS, SURFACE_ACCENT_MAX_LIGHTNESS)
        return ColorUtils.HSLToColor(hsl)
    }
}

/** The app-wide Google Sans typeface for Compose screens (View layouts set it via @font/google_sans). */
val GoogleSansFamily = FontFamily(
        Font(R.font.google_sans_regular, FontWeight.Normal),
        Font(R.font.google_sans_bold, FontWeight.Bold)
)
