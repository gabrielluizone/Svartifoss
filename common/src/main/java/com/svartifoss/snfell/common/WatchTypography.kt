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
     * @param case text-case transform - see [TextCase]. Only Title and Artist ever populate this
     *   from a real preference ([titleSpec]/[artistSpec]); every other element's spec carries the
     *   [TextCase.NORMAL] default, which is a no-op, so bundling it here rather than as a sixth
     *   sibling field on [NowPlayingFaceState] costs those elements nothing and reaches every
     *   existing call site - font family, weight, scale, opacity and tracking already all thread
     *   through the one `typography` parameter faces pass, and case is no different in kind.
     */
    data class TextSpec(
            val weight: Int,
            val italic: Boolean,
            val scale: Float,
            val alpha: Float,
            val trackingEm: Float,
            val case: TextCase = TextCase.NORMAL
    ) {
        /** True when this spec changes nothing, letting renderers skip their styling path entirely
         *  and keep the exact pre-3.1 drawing code for the (very common) untouched case. */
        val isIdentity: Boolean
            get() = weight == 400 && !italic && scale == 1f && alpha == 1f && trackingEm == 0f &&
                    case == TextCase.NORMAL

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

    /** [MiscPreferences.WEAR_TITLE_FONT] value meaning "use the global track family". */
    const val TITLE_FONT_FOLLOW: String = "follow"

    /**
     * The font key the track title should render in.
     *
     * The title used to be inseparable from [MiscPreferences.WEAR_FONT].
     * [TITLE_FONT_FOLLOW] preserves that exact behavior by default, while an explicit catalog
     * choice can give the title its own visual role. Blank imported values are treated as follow
     * for the same backwards-compatible reason.
     */
    fun titleFontKey(titlePreference: String?, globalFontKey: String?): String? =
            fontOverrideKey(titlePreference, TITLE_FONT_FOLLOW, globalFontKey)

    /** [MiscPreferences.WEAR_ARTIST_FONT] value meaning "use the global track family". */
    const val ARTIST_FONT_FOLLOW: String = "follow"

    /**
     * The font key the artist line should render in. See [titleFontKey] for the fallback contract:
     * an unset or [ARTIST_FONT_FOLLOW] value retains the global family, while an explicit catalog
     * key wins unchanged for the catalog to resolve.
     */
    fun artistFontKey(artistPreference: String?, globalFontKey: String?): String? =
            fontOverrideKey(artistPreference, ARTIST_FONT_FOLLOW, globalFontKey)

    /** [MiscPreferences.WEAR_CLOCK_FONT] value meaning "use whatever the track text uses". */
    const val CLOCK_FONT_FOLLOW: String = "follow"

    /**
     * The font key the on-screen clock should render in.
     *
     * The clock used to be locked to [MiscPreferences.WEAR_FONT] with no way to separate the two,
     * which is the wrong coupling for a chrome element: a display face chosen to make a track title
     * striking (Bebas Neue, Orbitron, Caveat) is frequently the wrong choice for a small time
     * readout that has to stay glanceable. [CLOCK_FONT_FOLLOW] keeps the old coupling and is the
     * default, so nothing changes for anyone who does not touch the new control.
     *
     * An empty or unknown value also resolves to following the track font rather than to a
     * hardcoded family: the value can arrive from an imported backup or a newer build, and silently
     * falling back to the user's chosen face is far less surprising than reverting to Google Sans.
     */
    fun clockFontKey(clockPreference: String?, trackFontKey: String?): String? =
            fontOverrideKey(clockPreference, CLOCK_FONT_FOLLOW, trackFontKey)

    /** [MiscPreferences.WEAR_LYRICS_FONT] value meaning "use whatever the track text uses". */
    const val LYRICS_FONT_FOLLOW: String = "follow"

    /**
     * The font key song lyrics should render in, on whichever surface is asking.
     *
     * Lyrics were the one body of text in the app with no font control at all, and they were
     * coupled two different ways at once: the lyrics screen followed
     * [MiscPreferences.WEAR_FONT_ALL_SCREENS], a switch meant for menu and queue chrome, while the
     * Verse face had a serif welded into its composition.
     *
     * [LYRICS_FONT_FOLLOW] therefore resolves to [trackFontKey] - the theme's own typeface - and
     * **not** to whatever each surface used to hardcode. That distinction is the whole point rather
     * than a detail: a first pass at this kept each surface's designed font as the default, which
     * preserved every existing theme exactly and in doing so preserved the actual complaint. Change
     * the theme's typeface and the words of the song were the one thing that did not follow, which
     * is indistinguishable from the control not working at all.
     *
     * The serif the Verse face shipped with is still one tap away; it is simply a choice now
     * (Marcellus) rather than a fixture. Same shape, same reasoning, and the same wording in the
     * picker as [clockFontKey] - so "follow" means one thing across the app instead of two.
     *
     * An empty or unknown value also resolves to the track font rather than to a fixed family: the
     * value can arrive from an imported backup or a newer build, and falling back to the user's own
     * chosen face is far less surprising than reverting to something they never picked.
     */
    fun lyricsFontKey(lyricsPreference: String?, trackFontKey: String?): String? =
            fontOverrideKey(lyricsPreference, LYRICS_FONT_FOLLOW, trackFontKey)

    /** [MiscPreferences.WEAR_TRACK_TIME_FONT] value meaning "keep this face's authored readout". */
    const val TRACK_TIME_FONT_FOLLOW: String = "follow"

    /**
     * Resolves the playback-time font override.
     *
     * Unlike the title, artist and clock, the elapsed/total readout was intentionally authored
     * with a different family by some faces. Its identity choice therefore returns null instead
     * of [trackFontKey]: renderers retain their own designed typeface until the user explicitly
     * selects one. A blank imported value gets the same safe behavior.
     */
    fun trackTimeFontKey(trackTimePreference: String?): String? =
            trackTimePreference?.takeUnless {
                it.isBlank() || it == TRACK_TIME_FONT_FOLLOW
            }

    /**
     * The four Google Sans Flex axes that are *not* already covered by the per-element
     * weight/italic controls: width, optical size, grade and roundness. A title or artist that
     * follows the global family uses the global set; every explicit Flex selection owns its own
     * set. wght and slnt still come from each element's own [TextSpec] ([TextSpec.weight] and
     * [TextSpec.italic]), so switching to Flex does not add a second, conflicting weight control
     * - see [flexVariationSettings].
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

    /** Which font family owns a Google Sans Flex axis set. */
    enum class FlexAxesTarget {
        /** The [MiscPreferences.WEAR_FONT] family, used by elements that follow the global font. */
        GLOBAL,
        /** An explicit [MiscPreferences.WEAR_TITLE_FONT] Flex override. */
        TITLE,
        /** An explicit [MiscPreferences.WEAR_ARTIST_FONT] Flex override. */
        ARTIST,
        /** An explicit [MiscPreferences.WEAR_CLOCK_FONT] Flex override. */
        CLOCK,
        /** An explicit [MiscPreferences.WEAR_LYRICS_FONT] Flex override. */
        LYRICS,
        /** An explicit [MiscPreferences.WEAR_TRACK_TIME_FONT] Flex override. */
        TRACK_TIME
    }

    /**
     * Resolves one independent Google Sans Flex axis set.
     *
     * An element following [FlexAxesTarget.GLOBAL] keeps the global axes. Title, artist, clock,
     * lyrics and playback time can select Flex independently, so their width, optical size, grade
     * and roundness must be independent as well.
     */
    fun flexAxes(
            prefs: SharedPreferences,
            context: AppearanceContext,
            target: FlexAxesTarget = FlexAxesTarget.GLOBAL
    ): FlexAxes {
        val definitions = when (target) {
            FlexAxesTarget.GLOBAL -> FlexAxisDefinitions(
                    MiscPreferences.WEAR_FONT_FLEX_WIDTH,
                    MiscPreferences.WEAR_FONT_FLEX_OPTICAL_SIZE,
                    MiscPreferences.WEAR_FONT_FLEX_GRADE,
                    MiscPreferences.WEAR_FONT_FLEX_ROUNDNESS)
            FlexAxesTarget.TITLE -> FlexAxisDefinitions(
                    MiscPreferences.WEAR_TITLE_FONT_FLEX_WIDTH,
                    MiscPreferences.WEAR_TITLE_FONT_FLEX_OPTICAL_SIZE,
                    MiscPreferences.WEAR_TITLE_FONT_FLEX_GRADE,
                    MiscPreferences.WEAR_TITLE_FONT_FLEX_ROUNDNESS)
            FlexAxesTarget.ARTIST -> FlexAxisDefinitions(
                    MiscPreferences.WEAR_ARTIST_FONT_FLEX_WIDTH,
                    MiscPreferences.WEAR_ARTIST_FONT_FLEX_OPTICAL_SIZE,
                    MiscPreferences.WEAR_ARTIST_FONT_FLEX_GRADE,
                    MiscPreferences.WEAR_ARTIST_FONT_FLEX_ROUNDNESS)
            FlexAxesTarget.CLOCK -> FlexAxisDefinitions(
                    MiscPreferences.WEAR_CLOCK_FONT_FLEX_WIDTH,
                    MiscPreferences.WEAR_CLOCK_FONT_FLEX_OPTICAL_SIZE,
                    MiscPreferences.WEAR_CLOCK_FONT_FLEX_GRADE,
                    MiscPreferences.WEAR_CLOCK_FONT_FLEX_ROUNDNESS)
            FlexAxesTarget.LYRICS -> FlexAxisDefinitions(
                    MiscPreferences.WEAR_LYRICS_FONT_FLEX_WIDTH,
                    MiscPreferences.WEAR_LYRICS_FONT_FLEX_OPTICAL_SIZE,
                    MiscPreferences.WEAR_LYRICS_FONT_FLEX_GRADE,
                    MiscPreferences.WEAR_LYRICS_FONT_FLEX_ROUNDNESS)
            FlexAxesTarget.TRACK_TIME -> FlexAxisDefinitions(
                    MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_WIDTH,
                    MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_OPTICAL_SIZE,
                    MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_GRADE,
                    MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_ROUNDNESS)
        }
        return FlexAxes(
                width = normalizeFlexAxis(
                        FaceScopedPreferences.getInt(prefs, definitions.width, context),
                        FLEX_WIDTH_MIN, FLEX_WIDTH_MAX),
                opticalSize = normalizeFlexAxis(
                        FaceScopedPreferences.getInt(prefs, definitions.opticalSize, context),
                        FLEX_OPTICAL_SIZE_MIN, FLEX_OPTICAL_SIZE_MAX),
                grade = normalizeFlexAxis(
                        FaceScopedPreferences.getInt(prefs, definitions.grade, context),
                        FLEX_GRADE_MIN, FLEX_GRADE_MAX),
                roundness = normalizeFlexAxis(
                        FaceScopedPreferences.getInt(prefs, definitions.roundness, context),
                        FLEX_ROUNDNESS_MIN, FLEX_ROUNDNESS_MAX)
        )
    }

    private data class FlexAxisDefinitions(
            val width: com.matejdro.wearutils.preferences.definition.PreferenceDefinition<Int>,
            val opticalSize: com.matejdro.wearutils.preferences.definition.PreferenceDefinition<Int>,
            val grade: com.matejdro.wearutils.preferences.definition.PreferenceDefinition<Int>,
            val roundness: com.matejdro.wearutils.preferences.definition.PreferenceDefinition<Int>
    )

    private fun normalizeFlexAxis(raw: Int, min: Float, max: Float): Float =
            raw.toFloat().coerceIn(min, max)

    private fun fontOverrideKey(
            preference: String?,
            followValue: String,
            globalFontKey: String?
    ): String? = if (preference.isNullOrBlank() || preference == followValue) {
        globalFontKey
    } else {
        preference
    }

    /**
     * The `android.graphics.Typeface.Builder#setFontVariationSettings` string for one text element
     * of the Flex font: its own weight/slant from [spec], plus its selected [axes]. Both the watch's
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
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_TITLE_FONT_TRACKING, context)),
            case = TextCase.fromPreference(
                    FaceScopedPreferences.getString(prefs, MiscPreferences.WEAR_TITLE_TEXT_CASE, context))
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
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_ARTIST_FONT_TRACKING, context)),
            case = TextCase.fromPreference(
                    FaceScopedPreferences.getString(prefs, MiscPreferences.WEAR_ARTIST_TEXT_CASE, context))
    )

    /** Typography deltas for the elapsed/total playback readout. */
    fun trackTimeSpec(prefs: SharedPreferences, context: AppearanceContext): TextSpec = TextSpec(
            weight = normalizeWeight(
                    FaceScopedPreferences.getInt(
                            prefs, MiscPreferences.WEAR_TRACK_TIME_FONT_WEIGHT, context)),
            italic = FaceScopedPreferences.getBoolean(
                    prefs, MiscPreferences.WEAR_TRACK_TIME_FONT_ITALIC, context),
            scale = normalizeScale(
                    FaceScopedPreferences.getInt(
                            prefs, MiscPreferences.WEAR_TRACK_TIME_FONT_SCALE, context)),
            alpha = normalizeOpacity(
                    FaceScopedPreferences.getInt(
                            prefs, MiscPreferences.WEAR_TRACK_TIME_FONT_OPACITY, context)),
            trackingEm = normalizeTracking(
                    FaceScopedPreferences.getInt(
                            prefs, MiscPreferences.WEAR_TRACK_TIME_FONT_TRACKING, context))
    )

    /**
     * The clock's own weight/italic/scale/tracking.
     *
     * [TextSpec.alpha] is fixed at 1 rather than reading a preference: the clock's opacity already
     * lives in [MiscPreferences.WEAR_CLOCK_OPACITY], which is baked into the resolved colour, and a
     * second multiplier here would silently square it.
     */
    fun clockSpec(prefs: SharedPreferences, context: AppearanceContext): TextSpec = TextSpec(
            weight = normalizeWeight(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_CLOCK_FONT_WEIGHT, context)),
            italic = FaceScopedPreferences.getBoolean(
                    prefs, MiscPreferences.WEAR_CLOCK_FONT_ITALIC, context),
            scale = normalizeScale(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_CLOCK_FONT_SCALE, context)),
            alpha = 1f,
            trackingEm = normalizeTracking(
                    FaceScopedPreferences.getInt(prefs, MiscPreferences.WEAR_CLOCK_FONT_TRACKING, context))
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
