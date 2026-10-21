package com.svartifoss.snfell.watch.view.shortcut

import com.svartifoss.snfell.common.AdaptiveTextContrast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The shortcut screen's colours, all derived from one colour taken from the cover. Pure, so the
 * cases that go wrong - a black, white or grey cover, a ground light enough to flip the text - are
 * pinned by a JVM test rather than found on a watch.
 *
 * One colour, not one per element: the cover's colour is extracted once (`ShortcutGlow`) and every
 * element that carries it takes a fixed tone of *that* colour - a light one and a dark one, the
 * same hue and saturation - so the service line and the Play button can never disagree.
 */
internal object ShortcutPalette {

    /** The HSL saturation below which a colour counts as grey: its hue is noise, and a tone of it
     *  would be a tinted grey that reads as dirt rather than as the cover's colour. */
    const val MIN_SATURATION = .20f

    /** The chroma (the spread between the strongest and weakest channel, 0..1) below which a
     *  colour counts as having none. HSL saturation alone is not enough: near black or near white
     *  it reaches 1 for a colour whose channels differ by a few levels. */
    const val MIN_CHROMA = .12f

    /** The lightness of the light tone - the Play button and a service line on a dark ground. */
    const val LIGHT_TONE = .80f

    /** The lightness of the dark tone - the Play glyph and a service line on a light ground. */
    const val DARK_TONE = .18f

    /** The saturation band the tones are held in: enough to read as the colour, never neon. */
    private const val MIN_TONE_SATURATION = .30f
    private const val MAX_TONE_SATURATION = .85f

    /** The service line when the cover gives no colour: the neutral the screen had before. */
    const val NEUTRAL_LIGHT_TEXT = 0xB8FFFFFF.toInt()
    const val NEUTRAL_DARK_TEXT = 0xFF1E1E20.toInt()

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    /** The colours the screen draws with. */
    data class Colors(
            /** The service line ("YouTube Music • Playlist"). */
            val subtitle: Int,
            /** The Play button's fill and its glyph. */
            val playContainer: Int,
            val playContent: Int
    )

    /**
     * The screen's colours for [accent] (the cover's colour, already through [usableAccent]; null
     * when the cover has none or there is no cover), with the service line read against
     * [subtitleGround] - the colour of the backdrop where that line sits, or null while it is not
     * known yet, which counts as dark: the ground is black until the glow lands.
     *
     * [neutralPlay] keeps the Play button white whatever the cover - YouTube Music's Liked Music,
     * whose own page draws its primary button white over the same cover.
     */
    fun resolve(accent: Int?, subtitleGround: Int?, neutralPlay: Boolean): Colors {
        val groundIsLight = subtitleGround != null &&
                AdaptiveTextContrast.prefersDarkText(subtitleGround)
        if (accent == null) {
            return Colors(
                    subtitle = if (groundIsLight) NEUTRAL_DARK_TEXT else NEUTRAL_LIGHT_TEXT,
                    playContainer = WHITE,
                    playContent = BLACK)
        }
        val light = lightTone(accent)
        val dark = darkTone(accent)
        return Colors(
                subtitle = if (groundIsLight) dark else light,
                playContainer = if (neutralPlay) WHITE else light,
                playContent = if (neutralPlay) BLACK else dark)
    }

    /**
     * [argb] when it is a colour worth carrying, null when it is black, white or grey - see
     * [MIN_SATURATION] and [MIN_CHROMA]. The caller then falls back to the neutral palette.
     */
    fun usableAccent(argb: Int?): Int? {
        if (argb == null) return null
        val red = ((argb shr 16) and 0xFF) / 255f
        val green = ((argb shr 8) and 0xFF) / 255f
        val blue = (argb and 0xFF) / 255f
        val chroma = max(red, max(green, blue)) - min(red, min(green, blue))
        if (chroma < MIN_CHROMA) return null
        return argb.takeIf { hsl(it)[1] >= MIN_SATURATION }
    }

    fun lightTone(accent: Int): Int = tone(accent, LIGHT_TONE)

    fun darkTone(accent: Int): Int = tone(accent, DARK_TONE)

    private fun tone(accent: Int, lightness: Float): Int {
        val (hue, saturation) = hsl(accent)
        return fromHsl(hue, saturation.coerceIn(MIN_TONE_SATURATION, MAX_TONE_SATURATION), lightness)
    }

    /** Hue (0..360), saturation and lightness (0..1) of an opaque colour. */
    internal fun hsl(argb: Int): FloatArray {
        val red = ((argb shr 16) and 0xFF) / 255f
        val green = ((argb shr 8) and 0xFF) / 255f
        val blue = (argb and 0xFF) / 255f
        val maximum = max(red, max(green, blue))
        val minimum = min(red, min(green, blue))
        val lightness = (maximum + minimum) / 2f
        val delta = maximum - minimum
        if (delta == 0f) return floatArrayOf(0f, 0f, lightness)
        val saturation = delta / (1f - abs(2f * lightness - 1f))
        val hue = when (maximum) {
            red -> 60f * (((green - blue) / delta) % 6f)
            green -> 60f * (((blue - red) / delta) + 2f)
            else -> 60f * (((red - green) / delta) + 4f)
        }
        return floatArrayOf((hue + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness)
    }

    internal fun fromHsl(hue: Float, saturation: Float, lightness: Float): Int {
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val section = ((hue % 360f) + 360f) % 360f / 60f
        val second = chroma * (1f - abs(section % 2f - 1f))
        val (red, green, blue) = when (section.toInt()) {
            0 -> Triple(chroma, second, 0f)
            1 -> Triple(second, chroma, 0f)
            2 -> Triple(0f, chroma, second)
            3 -> Triple(0f, second, chroma)
            4 -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }
        val offset = lightness - chroma / 2f
        fun channel(value: Float) = ((value + offset) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    }
}
