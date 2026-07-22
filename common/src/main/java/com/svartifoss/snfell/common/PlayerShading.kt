package com.svartifoss.snfell.common

/**
 * User-selected treatment drawn between the player artwork and its chrome.
 *
 * Persisted values intentionally stay renderer-agnostic: the phone preview, the View-based
 * Classic face and every Compose face decode the same enum, then use their native drawing API.
 */
enum class PlayerShadingStyle(val preferenceValue: String) {
    /** Use the background's authored scrim; plain artwork resolves to the neutral bottom fade. */
    FOLLOW("follow"),
    /** Darken only the circular bezel while leaving the centre comparatively open. */
    EDGE_VIGNETTE("edge_vignette"),
    EDGE_VIGNETTE_STRONG("edge_vignette_strong"),
    EDGE_VIGNETTE_HEAVY("edge_vignette_heavy"),
    /** Diagonal shadow gathering in the lower-right corner, like the Classic composition. */
    BOTTOM_CORNER("bottom_corner"),
    /** A conventional transparent-to-black fade towards the bottom edge. */
    BOTTOM_FADE("bottom_fade"),
    /** Separate shadows at the top and bottom, leaving the middle clear. */
    FLOOR_CEILING("floor_ceiling"),
    /** An even black filter across the complete player background. */
    FULL_FILTER("full_filter"),
    /** A dark monochromatic wash derived from the album's primary colour. */
    ALBUM_TINT("album_tint"),
    /** A diagonal wash made from two real album swatches. */
    DUOTONE("duotone"),
    /** Dark side edges with a clear vertical strip through the centre. */
    SIDE_CURTAINS("side_curtains");

    companion object {
        fun fromPreference(value: String?): PlayerShadingStyle =
                entries.firstOrNull { it.preferenceValue == value } ?: FOLLOW
    }
}

/**
 * Shading strength is a percentage the user sets directly (see `album_art_dim_strength`), read as
 * `percent / 100f`. 100% applies the full authored max-alpha; values above it push toward a
 * near-opaque darkest stop for users who want more contrast, up to [SHADING_MAX_MULTIPLIER].
 */
const val SHADING_MAX_PERCENT = 150

/** Ceiling the shading multiplier (percent / 100) is clamped to across every consumer. */
const val SHADING_MAX_MULTIPLIER = SHADING_MAX_PERCENT / 100f

/** Named strength stops retained only for legacy value migration; the live path is numeric. */
enum class PlayerShadingIntensity(val preferenceValue: String, val multiplier: Float) {
    SOFT("soft", .45f),
    BALANCED("balanced", .80f),
    STRONG("strong", 1f);

    companion object {
        fun fromPreference(value: String?): PlayerShadingIntensity =
                entries.firstOrNull { it.preferenceValue == value } ?: BALANCED

        /** Percentage a legacy named level corresponds to, for one-time migration to numeric. */
        fun percentFor(value: String?): Int =
                ((fromPreference(value).multiplier) * 100f).toInt()
    }
}
