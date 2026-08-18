package com.svartifoss.snfell.common

/** A quantized artwork colour and how many pixels of the cover it covers. */
data class SwatchInfo(
        val rgb: Int,
        val population: Int
)

/**
 * Which album swatch becomes the accent.
 *
 * The two answers are both defensible and neither is right for every cover, which is why this is a
 * user choice rather than a constant. Palette's *vibrant* swatch is the most colourful thing in the
 * artwork; its *dominant* swatch is the colour that covers the most pixels. On a cover whose colour
 * lives in a small area - a beige jacket against a grey studio wall - those two disagree completely.
 *
 * The watch has always used [BALANCED] and the phone's preview has always used [VIBRANT], each with
 * the comment claiming it matched the other. That is the divergence this enum turns into a setting:
 * both sides now resolve through [selectPrimaryAccent] with the user's choice, so the miniature and
 * the wrist finally agree, whichever answer is picked.
 */
enum class AlbumAccentSource {
    /**
     * Vibrant, but only when it covers a meaningful share of the cover; the dominant colour
     * otherwise. Keeps a tiny bright detail - a logo, a reflection, a lens flare - from turning a
     * mostly-blue cover's UI red while the blurred background behind it stays blue.
     */
    BALANCED,

    /**
     * The vibrant swatch, whatever its size. Picks up the colour a person would name when asked
     * what colour a cover is, at the cost of occasionally latching onto a small bright detail -
     * which is exactly the trade [BALANCED] refuses to make.
     */
    VIBRANT;

    companion object {
        const val BALANCED_VALUE = "balanced"
        const val VIBRANT_VALUE = "vibrant"

        /** Unknown values resolve to [BALANCED]: it is the behaviour every existing install already
         *  renders, so a value from a newer phone, or a corrupted one, cannot silently repaint a
         *  watch that never opted into anything. */
        fun fromPreference(value: String?): AlbumAccentSource =
                if (value?.trim()?.lowercase() == VIBRANT_VALUE) VIBRANT else BALANCED
    }
}

/**
 * Chooses the accent colour from a cover's quantized swatches.
 *
 * Returns null only when there is no colour information at all, which the caller reports as "keep
 * whatever accent is already showing" rather than as a colour.
 *
 * @param minShare the fraction of the dominant swatch's pixel count that the vibrant swatch must
 *   reach for [AlbumAccentSource.BALANCED] to trust it. Ignored entirely by
 *   [AlbumAccentSource.VIBRANT], which is the point of that option.
 */
fun selectPrimaryAccent(
        vibrant: SwatchInfo?,
        swatches: List<SwatchInfo>,
        source: AlbumAccentSource = AlbumAccentSource.BALANCED,
        minShare: Float = 0.10f
): Int? {
    val dominant = swatches.maxByOrNull { it.population }
    if (vibrant == null) return dominant?.rgb
    if (source == AlbumAccentSource.VIBRANT) return vibrant.rgb
    if (dominant == null) return vibrant.rgb
    return if (vibrant.population >= dominant.population * minShare) vibrant.rgb else dominant.rgb
}
