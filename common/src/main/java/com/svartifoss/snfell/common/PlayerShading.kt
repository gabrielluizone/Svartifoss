package com.svartifoss.snfell.common

/**
 * User-selected treatment drawn between the player artwork and its chrome.
 *
 * Persisted values intentionally stay renderer-agnostic: the phone preview, the View-based
 * Classic face and every Compose face decode the same enum, then use their native drawing API.
 */
enum class PlayerShadingStyle(val preferenceValue: String) {
    /** Preserve the face's authored treatment (Classic uses its historical bottom fade). */
    FOLLOW("follow"),
    /** Darken only the circular bezel while leaving the centre comparatively open. */
    EDGE_VIGNETTE("edge_vignette"),
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

/** Three deliberately legible strength stops used by every explicit shading treatment. */
enum class PlayerShadingIntensity(val preferenceValue: String, val multiplier: Float) {
    SOFT("soft", .45f),
    BALANCED("balanced", .80f),
    STRONG("strong", 1f);

    companion object {
        fun fromPreference(value: String?): PlayerShadingIntensity =
                entries.firstOrNull { it.preferenceValue == value } ?: BALANCED

        /** Maps the former free-form percentage when a profile has not selected a level yet. */
        fun fromLegacyPercent(percent: Int): PlayerShadingIntensity = when {
            percent <= 55 -> SOFT
            percent >= 90 -> STRONG
            else -> BALANCED
        }
    }
}
