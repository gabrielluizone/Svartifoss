package com.svartifoss.snfell.common

/**
 * The range every numeric appearance preference may hold.
 *
 * These numbers already existed - in the watch's `coerceIn` calls, in the phone sliders' bounds,
 * and in the community-theme contract - but nowhere that the *input* could see. So the phone's
 * numeric fields accepted anything at all: the watch clamped what it read, the screen therefore
 * looked exactly as the user intended, and the value only turned out to be a problem much later,
 * at submission, as "The gallery can't accept this theme's Overlay blur setting" - naming a
 * setting that had never objected to the number when it was typed. Every one of the fifty numeric
 * settings could do that; `overlay_blur_radius` was simply the one somebody reached first.
 *
 * This is the authority now. `CommunityThemeConstraints` still reads its own copy out of the
 * shipped asset, because that file is the public boundary and has to be data rather than code -
 * but `AppearanceNumericRangeParityTest` compares the two in both directions, so the contract
 * cannot quietly disagree with what the app lets somebody build.
 *
 * Kept in `common` because all three sides need it: the phone clamps on input, the theme
 * repository clamps a profile saved before a bound existed, and the watch clamps what it reads.
 */
object AppearanceNumericRanges {

    /**
     * Key -> the values it accepts, inclusive.
     *
     * A key absent from this map is not "unbounded", it is a key nobody has thought about; the
     * parity test refuses one that the contract declares and this does not.
     */
    val RANGES: Map<String, IntRange> = mapOf(
            // Not appearance settings and never part of a theme, but the watch already clamps
            // both, so the field that writes them may as well say the same thing. Relocated from
            // those `coerceIn` calls rather than invented here.
            "rotary_deadzone" to 0..30,
            "volume_overlay_timeout" to 300..5000,

            "album_art_blur_radius" to 5..120,
            "album_art_dim_strength" to 0..150,
            "ambient_album_art_opacity" to 20..100,
            "overlay_blur_radius" to 5..120,
            "screen_buttons_bottom_offset" to 42..42,
            "screen_buttons_opacity" to 0..100,
            "wear_aod_intensity" to 20..100,
            "wear_artist_font_flex_grade" to 0..100,
            "wear_artist_font_flex_optical_size" to 6..144,
            "wear_artist_font_flex_roundness" to 0..100,
            "wear_artist_font_flex_width" to 25..151,
            "wear_artist_font_opacity" to 20..100,
            "wear_artist_font_scale" to 70..140,
            "wear_artist_font_tracking" to -5..20,
            "wear_artist_font_weight" to 1..1000,
            "wear_artist_text_bg_opacity" to
                    TextBackdropSpec.MIN_OPACITY_PERCENT..TextBackdropSpec.MAX_OPACITY_PERCENT,
            "wear_artist_shadow_strength" to
                    TextShadowSpec.MIN_STRENGTH_PERCENT..TextShadowSpec.MAX_STRENGTH_PERCENT,
            "wear_clock_font_flex_grade" to 0..100,
            "wear_clock_font_flex_optical_size" to 6..144,
            "wear_clock_font_flex_roundness" to 0..100,
            "wear_clock_font_flex_width" to 25..151,
            "wear_clock_font_scale" to 70..140,
            "wear_clock_font_tracking" to -5..20,
            "wear_clock_font_weight" to 1..1000,
            "wear_clock_opacity" to 10..100,
            "wear_color_hue_shift" to 0..359,
            "wear_font_flex_grade" to 0..100,
            "wear_font_flex_optical_size" to 6..144,
            "wear_font_flex_roundness" to 0..100,
            "wear_font_flex_width" to 25..151,
            "wear_lyrics_font_flex_grade" to 0..100,
            "wear_lyrics_font_flex_optical_size" to 6..144,
            "wear_lyrics_font_flex_roundness" to 0..100,
            "wear_lyrics_font_flex_width" to 25..151,
            "wear_source_icon_opacity" to 20..100,
            "wear_source_icon_scale" to 70..140,
            "wear_title_font_flex_grade" to 0..100,
            "wear_title_font_flex_optical_size" to 6..144,
            "wear_title_font_flex_roundness" to 0..100,
            "wear_title_font_flex_width" to 25..151,
            "wear_title_font_opacity" to 20..100,
            "wear_title_font_scale" to 70..140,
            "wear_title_font_tracking" to -5..20,
            "wear_title_font_weight" to 1..1000,
            "wear_title_text_bg_opacity" to
                    TextBackdropSpec.MIN_OPACITY_PERCENT..TextBackdropSpec.MAX_OPACITY_PERCENT,
            "wear_title_shadow_strength" to
                    TextShadowSpec.MIN_STRENGTH_PERCENT..TextShadowSpec.MAX_STRENGTH_PERCENT,
            "wear_track_time_font_flex_grade" to 0..100,
            "wear_track_time_font_flex_optical_size" to 6..144,
            "wear_track_time_font_flex_roundness" to 0..100,
            "wear_track_time_font_flex_width" to 25..151,
            "wear_track_time_font_opacity" to 20..100,
            "wear_track_time_font_scale" to 70..140,
            "wear_track_time_font_tracking" to -5..20,
            "wear_track_time_font_weight" to 1..1000,    )

    /** [value] brought inside [key]'s range, or unchanged when the key declares none. */
    fun clamp(key: String, value: Int): Int =
            RANGES[key]?.let { value.coerceIn(it.first, it.last) } ?: value

    /** True when [value] is one [key] accepts. A key with no declared range accepts anything. */
    fun accepts(key: String, value: Int): Boolean =
            RANGES[key]?.contains(value) ?: true

    fun rangeFor(key: String): IntRange? = RANGES[key]
}
