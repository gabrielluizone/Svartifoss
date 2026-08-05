package com.svartifoss.snfell.common

import android.content.SharedPreferences

/**
 * Resolves the per-element typography preferences into plain values the renderers can apply.
 *
 * Returns primitives rather than Compose or View types on purpose: the same spec is consumed by
 * the Compose faces (`FontWeight`/`TextStyle`), by the classic View face (`Typeface`/`TextView`)
 * and by the phone's `WatchPreviewView` (`Paint` on a Canvas). Sharing the *resolution* while
 * leaving the *application* to each stack is what keeps the preview honest without forcing
 * `common` to depend on any UI toolkit.
 *
 * Every field is a delta over whatever size/colour the face already computed - see the notes on
 * [MiscPreferences.WEAR_TITLE_FONT_SCALE] for why absolute sizes are not offered.
 */
object WatchTypography {

    /**
     * @param weight 1..1000 (OpenType `wght` range) - see [normalizeWeight].
     * @param italic whether to slant the face (synthesized when the family has no italic cut, or
     *   the Flex font's own `slnt` axis when [isFlexFont] - see [flexVariationSettings]).
     * @param scale multiplier over the face's designed text size, e.g. 1.15f.
     * @param alpha 0..1 multiplier over the text colour's own alpha.
     * @param trackingEm extra letter spacing in em; 0 keeps the font's own metrics.
     */
    data class TextSpec(
            val weight: Int,
            val italic: Boolean,
            val scale: Float,
            val alpha: Float,
            val trackingEm: Float
    ) {
        /** True when this spec changes nothing, letting renderers skip their styling path entirely
         *  and keep the exact pre-3.1 drawing code for the (very common) untouched case. */
        val isIdentity: Boolean
            get() = weight == 400 && !italic && scale == 1f && alpha == 1f && trackingEm == 0f

        /** [size] with this spec's scale applied. */
        fun scaled(size: Float): Float = size * scale

        /** [alpha0to255] with this spec's opacity applied, clamped to a valid channel value. */
        fun applyAlpha(alpha0to255: Int): Int =
                (alpha0to255 * alpha).toInt().coerceIn(0, 255)
    }

    /** Size/opacity for the playing-app icon. It has no weight or slant of its own, so it gets a
     *  narrower spec than text rather than an over-general one with unused fields. */
    data class IconSpec(val scale: Float, val alpha: Float) {
        val isIdentity: Boolean get() = scale == 1f && alpha == 1f
    }

    val IDENTITY_TEXT: TextSpec = TextSpec(400, false, 1f, 1f, 0f)
    val IDENTITY_ICON: IconSpec = IconSpec(1f, 1f)

    /** The [MiscPreferences.WEAR_FONT] key for the bundled Google Sans Flex variable font - the
     *  only catalog entry with real variable axes, so it is the only one [flexVariationSettings]
     *  ever needs to run for. */
    const val FLEX_FONT_KEY: String = "google_sans_flex"

    fun isFlexFont(fontKey: String?): Boolean = fontKey == FLEX_FONT_KEY

    /**
     * The four Google Sans Flex axes that are *not* already covered by the per-element
     * weight/italic controls - width, optical size, grade and roundness. These are deliberately
     * global (not per title/artist) rather than doubling every field: unlike weight, a mismatched
     * roundness or width between the title and artist line reads as a rendering glitch rather than
     * a deliberate hierarchy choice, so one shared "character" for the font is what a user actually
     * wants. wght and slnt still come from each element's own [TextSpec] ([TextSpec.weight] and
     * [TextSpec.italic]), so switching to Flex does not add a second, conflicting weight control -
     * see [flexVariationSettings].
     */
    data class FlexAxes(
            val width: Float,
            val opticalSize: Float,
            val grade: Float,
            val roundness: Float
    ) {
        val isIdentity: Boolean
            get() = width == FLEX_WIDTH_DEFAULT && opticalSize == FLEX_OPTICAL_SIZE_DEFAULT &&
                    grade == FLEX_GRADE_DEFAULT && roundness == FLEX_ROUNDNESS_DEFAULT
    }

    /** [FlexAxes] at the font's own defaults, straight from its `fvar` table. */
    val IDENTITY_FLEX_AXES: FlexAxes = FlexAxes(
            FLEX_WIDTH_DEFAULT, FLEX_OPTICAL_SIZE_DEFAULT, FLEX_GRADE_DEFAULT, FLEX_ROUNDNESS_DEFAULT)

    /** Bounds and defaults for every Google Sans Flex axis, read directly from the bundled font's
     *  `fvar` table (`fonttools varLib.instancer` / `ttx` dump) rather than guessed - an axis value
     *  outside these bounds does not error, but Android silently clamps it to the nearest edge the
     *  font actually defines, which would make a slider that goes further than this look broken. */
    const val FLEX_WEIGHT_MIN: Int = 1
    const val FLEX_WEIGHT_MAX: Int = 1000
    const val FLEX_WIDTH_MIN: Float = 25f
    const val FLEX_WIDTH_MAX: Float = 151f
    const val FLEX_WIDTH_DEFAULT: Float = 100f
    const val FLEX_OPTICAL_SIZE_MIN: Float = 6f
    const val FLEX_OPTICAL_SIZE_MAX: Float = 144f
    const val FLEX_OPTICAL_SIZE_DEFAULT: Float = 18f
    const val FLEX_SLANT_UPRIGHT: Float = 0f
    const val FLEX_SLANT_ITALIC: Float = -10f
    const val FLEX_GRADE_MIN: Float = 0f
    const val FLEX_GRADE_MAX: Float = 100f
    const val FLEX_GRADE_DEFAULT: Float = 0f
    const val FLEX_ROUNDNESS_MIN: Float = 0f
    const val FLEX_ROUNDNESS_MAX: Float = 100f
    const val FLEX_ROUNDNESS_DEFAULT: Float = 0f

    fun flexAxes(prefs: SharedPreferences, context: AppearanceContext): FlexAxes = FlexAxes(
            width = normalizeFlexAxis(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_FONT_FLEX_WIDTH, context),
                    FLEX_WIDTH_MIN, FLEX_WIDTH_MAX),
            opticalSize = normalizeFlexAxis(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_FONT_FLEX_OPTICAL_SIZE, context),
                    FLEX_OPTICAL_SIZE_MIN, FLEX_OPTICAL_SIZE_MAX),
            grade = normalizeFlexAxis(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_FONT_FLEX_GRADE, context),
                    FLEX_GRADE_MIN, FLEX_GRADE_MAX),
            roundness = normalizeFlexAxis(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_FONT_FLEX_ROUNDNESS, context),
                    FLEX_ROUNDNESS_MIN, FLEX_ROUNDNESS_MAX)
    )

    private fun normalizeFlexAxis(raw: Int, min: Float, max: Float): Float =
            raw.toFloat().coerceIn(min, max)

    /**
     * The `android.graphics.Typeface.Builder#setFontVariationSettings` string for one text element
     * of the Flex font: its own weight/slant from [spec], plus the shared [axes]. Both the watch's
     * `Typeface.Builder(context, R.font.google_sans_flex)` path and the phone preview's identical
     * `Paint` typeface use this exact string, so neither can render a combination the other
     * disagrees on.
     */
    fun flexVariationSettings(spec: TextSpec, axes: FlexAxes): String {
        val weight = spec.weight.coerceIn(FLEX_WEIGHT_MIN, FLEX_WEIGHT_MAX)
        val slant = if (spec.italic) FLEX_SLANT_ITALIC else FLEX_SLANT_UPRIGHT
        return "'wght' $weight, 'wdth' ${axes.width}, 'opsz' ${axes.opticalSize}, " +
                "'slnt' $slant, 'GRAD' ${axes.grade}, 'ROND' ${axes.roundness}"
    }

    fun titleSpec(prefs: SharedPreferences, context: AppearanceContext): TextSpec = TextSpec(
            weight = normalizeWeight(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_TITLE_FONT_WEIGHT, context)),
            italic = FaceScopedPreferences.getBoolean(
                    prefs, MiscPreferences.WEAR_TITLE_FONT_ITALIC, context),
            scale = normalizeScale(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_TITLE_FONT_SCALE, context)),
            alpha = normalizeOpacity(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_TITLE_FONT_OPACITY, context)),
            trackingEm = normalizeTracking(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_TITLE_FONT_TRACKING, context))
    )

    fun artistSpec(prefs: SharedPreferences, context: AppearanceContext): TextSpec = TextSpec(
            weight = normalizeWeight(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_ARTIST_FONT_WEIGHT, context)),
            italic = FaceScopedPreferences.getBoolean(
                    prefs, MiscPreferences.WEAR_ARTIST_FONT_ITALIC, context),
            scale = normalizeScale(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_ARTIST_FONT_SCALE, context)),
            alpha = normalizeOpacity(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_ARTIST_FONT_OPACITY, context)),
            trackingEm = normalizeTracking(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_ARTIST_FONT_TRACKING, context))
    )

    fun sourceIconSpec(prefs: SharedPreferences, context: AppearanceContext): IconSpec = IconSpec(
            scale = normalizeScale(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_SOURCE_ICON_SCALE, context)),
            alpha = normalizeOpacity(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_SOURCE_ICON_OPACITY, context))
    )

    /**
     * Clamps to the full 1..1000 range `Typeface.create(family, weight, italic)` and Google Sans
     * Flex's own `wght` axis both accept - continuous, not stepped to the nine CSS keyword weights,
     * so the control is exactly as configurable as the platform actually allows. A static (non-Flex)
     * font has no real master at every weight; the platform synthesizes toward whichever weight is
     * requested against its nearest real master, same as it always has for the old 100-step control -
     * this only widens how finely that request can be aimed.
     */
    fun normalizeWeight(raw: Int): Int = raw.coerceIn(FLEX_WEIGHT_MIN, FLEX_WEIGHT_MAX)

    fun normalizeScale(percent: Int): Float =
            percent.coerceIn(MiscPreferences.TYPOGRAPHY_MIN_SCALE, MiscPreferences.TYPOGRAPHY_MAX_SCALE) / 100f

    fun normalizeOpacity(percent: Int): Float =
            percent.coerceIn(MiscPreferences.TYPOGRAPHY_MIN_OPACITY, 100) / 100f

    fun normalizeTracking(hundredthsEm: Int): Float =
            hundredthsEm.coerceIn(
                    MiscPreferences.TYPOGRAPHY_MIN_TRACKING,
                    MiscPreferences.TYPOGRAPHY_MAX_TRACKING) / 100f
}
