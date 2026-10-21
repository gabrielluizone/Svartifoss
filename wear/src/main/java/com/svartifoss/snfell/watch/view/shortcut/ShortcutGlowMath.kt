package com.svartifoss.snfell.watch.view.shortcut

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The arithmetic behind the shortcut screen's backdrop, kept free of `android.*` so a plain JVM
 * test pins it. [ShortcutGlow] is the part that touches bitmaps.
 *
 * The backdrop is the cover itself, centred on the screen and softened: reduced to a thumbnail,
 * blurred a little (a Gaussian about a thirtieth of the picture wide, so its shapes stay shapes),
 * given a richer tone with its highlights held down, and drawn under two masks that close it to
 * black ([ShortcutDetailGeometry.RADIAL_MASK], [ShortcutDetailGeometry.VERTICAL_MASK]). That is why
 * it is done here and not with `BitmapBlur`, which never samples below ~96 px and is built for a
 * different job.
 */
internal object ShortcutGlowMath {

    /** The side the cover is reduced to before blurring: small enough that the blur is a handful of
     *  taps, large enough that a thumbs-up or a face survives as a shape. */
    const val SAMPLE_PX = 64

    /** The blur's standard deviation in [SAMPLE_PX] pixels - about a thirtieth of the picture: the
     *  cover softens into its own colours and its shapes are still the cover's. */
    const val SIGMA_PX = 2.2f

    /** More colour than the blur leaves. Averaging pulls a cover towards grey, and the backdrop is
     *  the one place the page carries the cover's colour at all. */
    const val SATURATION = 1.30f

    /** A curve over the levels (1 is none): above 1 it takes the middle of the range down while
     *  leaving black and white where they are, so the cover's haze goes deep. */
    const val GAMMA = 1.35f

    /**
     * Highlights: a pixel brighter than [HIGHLIGHT_KNEE] (linear luminance) is brought down along a
     * curve that never passes [HIGHLIGHT_CEILING], its colour kept. A white cover, or the white
     * thumbs-up of Liked Music, then reads as a light grey of the cover's own tint, under which the
     * white name and the light Play button stand out - instead of the whole backdrop being dimmed
     * to make room for its brightest part, which took the colour out of every cover with any white
     * in it.
     */
    const val HIGHLIGHT_KNEE = .20f
    const val HIGHLIGHT_CEILING = .32f

    /** How opaque the cover is drawn under the masks. */
    const val GLOW_ALPHA = .95f

    // The weights Android's ColorMatrix.setToSaturation uses, so this matches what a colour filter
    // would have done to the same pixels.
    private const val LUMA_R = .213f
    private const val LUMA_G = .715f
    private const val LUMA_B = .072f

    /** A normalised Gaussian kernel, `2 * ceil(3 * sigma) + 1` taps wide. A non-positive [sigma] is
     *  the identity kernel. */
    fun kernel(sigma: Float): FloatArray {
        if (sigma <= 0f) return floatArrayOf(1f)
        val radius = ceil(3f * sigma).toInt()
        val weights = FloatArray(2 * radius + 1) { index ->
            val distance = (index - radius).toFloat()
            exp(-(distance * distance) / (2f * sigma * sigma))
        }
        val sum = weights.sum()
        for (i in weights.indices) weights[i] /= sum
        return weights
    }

    /**
     * [pixels] (ARGB, row-major, [width] x [height]) blurred by a separable Gaussian of [sigma],
     * clamped at the edges, as opaque ARGB.
     *
     * Translucent pixels are composited onto black first - that is what is behind them on the
     * screen, and blurring straight channels would otherwise bleed the colour of whatever sits under
     * a transparent pixel into its neighbours.
     */
    fun blur(pixels: IntArray, width: Int, height: Int, sigma: Float): IntArray {
        require(width > 0 && height > 0) { "empty image" }
        require(pixels.size == width * height) { "pixels do not match ${width}x$height" }
        val count = pixels.size
        val weights = kernel(sigma)
        val channels = Array(3) { FloatArray(count) }
        for (i in 0 until count) {
            val pixel = pixels[i]
            val opacity = ((pixel ushr 24) and 0xFF) / 255f
            channels[0][i] = ((pixel shr 16) and 0xFF) * opacity
            channels[1][i] = ((pixel shr 8) and 0xFF) * opacity
            channels[2][i] = (pixel and 0xFF) * opacity
        }
        val scratch = FloatArray(count)
        for (channel in channels) {
            convolve(channel, scratch, width, height, weights, horizontal = true)
            convolve(scratch, channel, width, height, weights, horizontal = false)
        }
        return IntArray(count) { i ->
            (0xFF shl 24) or
                    (channels[0][i].roundToInt().coerceIn(0, 255) shl 16) or
                    (channels[1][i].roundToInt().coerceIn(0, 255) shl 8) or
                    channels[2][i].roundToInt().coerceIn(0, 255)
        }
    }

    private fun convolve(
            source: FloatArray,
            target: FloatArray,
            width: Int,
            height: Int,
            weights: FloatArray,
            horizontal: Boolean
    ) {
        val radius = weights.size / 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                for (tap in weights.indices) {
                    val offset = tap - radius
                    val sx = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                    val sy = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                    sum += weights[tap] * source[sy * width + sx]
                }
                target[y * width + x] = sum
            }
        }
    }

    /**
     * [pixels] given [saturation] (1 leaves the colour as it is, 0 is grey), then [gamma] (see
     * [GAMMA]), then their highlights held under [ceiling] from [knee] up (see [HIGHLIGHT_CEILING];
     * a ceiling of 1 or more holds nothing), as opaque ARGB. Greys stay grey throughout, and black
     * stays black.
     */
    fun tone(
            pixels: IntArray,
            saturation: Float = SATURATION,
            gamma: Float = GAMMA,
            knee: Float = HIGHLIGHT_KNEE,
            ceiling: Float = HIGHLIGHT_CEILING
    ): IntArray = IntArray(pixels.size) { i ->
        val pixel = pixels[i]
        val red = ((pixel shr 16) and 0xFF).toFloat()
        val green = ((pixel shr 8) and 0xFF).toFloat()
        val blue = (pixel and 0xFF).toFloat()
        val grey = LUMA_R * red + LUMA_G * green + LUMA_B * blue
        // Saturated and curved, in linear light from here on, so the highlight hold below is a
        // change of brightness and not of colour.
        val linearRed = linear(curved(grey + (red - grey) * saturation, gamma))
        val linearGreen = linear(curved(grey + (green - grey) * saturation, gamma))
        val linearBlue = linear(curved(grey + (blue - grey) * saturation, gamma))
        val luminance = .2126f * linearRed + .7152f * linearGreen + .0722f * linearBlue
        val scale = if (luminance > knee && ceiling < 1f && luminance > 0f) {
            heldLuminance(luminance, knee, ceiling) / luminance
        } else {
            1f
        }
        (0xFF shl 24) or
                (encoded(linearRed * scale) shl 16) or
                (encoded(linearGreen * scale) shl 8) or
                encoded(linearBlue * scale)
    }

    /** [luminance] above [knee] brought towards [ceiling] and never past it, rising as it did at
     *  the knee so the hold has no visible edge. */
    internal fun heldLuminance(luminance: Float, knee: Float, ceiling: Float): Float {
        if (luminance <= knee) return luminance
        val span = ceiling - knee
        return knee + span * (1f - exp(-(luminance - knee) / span))
    }

    private fun curved(value: Float, gamma: Float): Float =
            (value.coerceIn(0f, 255f) / 255f).pow(gamma)

    private fun linear(encoded: Float): Float =
            if (encoded <= .04045f) encoded / 12.92f else ((encoded + .055f) / 1.055f).pow(2.4f)

    private fun encoded(linear: Float): Int {
        val value = linear.coerceIn(0f, 1f)
        val srgb = if (value <= .0031308f) value * 12.92f else 1.055f * value.pow(1f / 2.4f) - .055f
        return (srgb * 255f).roundToInt().coerceIn(0, 255)
    }

    /**
     * The mean colour the backdrop shows over the rectangle [left]..[right] x [top]..[bottom]
     * (fractions of the screen), worked out from the glow's own [pixels] ([side] x [side], drawn
     * over the whole screen) and the masks the screen lays over it - the colour the service line
     * is read against. Opaque ARGB; black for an empty rectangle.
     */
    fun groundColor(
            pixels: IntArray,
            side: Int,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            alpha: Float = GLOW_ALPHA
    ): Int {
        require(pixels.size == side * side) { "pixels do not match ${side}x$side" }
        if (right <= left || bottom <= top) return 0xFF000000.toInt()
        var red = 0f
        var green = 0f
        var blue = 0f
        var count = 0
        for (row in 0 until GROUND_SAMPLES) {
            val y = top + (bottom - top) * (row + .5f) / GROUND_SAMPLES
            for (column in 0 until GROUND_SAMPLES) {
                val x = left + (right - left) * (column + .5f) / GROUND_SAMPLES
                val pixel = pixels[pixelIndex(y, side) * side + pixelIndex(x, side)]
                val radial = ShortcutDetailGeometry.maskAt(ShortcutDetailGeometry.RADIAL_MASK,
                        distanceFromCentre(x, y) / ShortcutDetailGeometry.RADIAL_MASK_RADIUS)
                val vertical = ShortcutDetailGeometry.maskAt(ShortcutDetailGeometry.VERTICAL_MASK, y)
                val shown = alpha * (1f - radial) * (1f - vertical)
                red += ((pixel shr 16) and 0xFF) * shown
                green += ((pixel shr 8) and 0xFF) * shown
                blue += (pixel and 0xFF) * shown
                count++
            }
        }
        fun mean(sum: Float) = (sum / count).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mean(red) shl 16) or (mean(green) shl 8) or mean(blue)
    }

    private const val GROUND_SAMPLES = 12

    private fun pixelIndex(fraction: Float, side: Int): Int =
            (fraction * side).toInt().coerceIn(0, side - 1)

    private fun distanceFromCentre(x: Float, y: Float): Float {
        val dx = x - .5f
        val dy = y - .5f
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
