package com.svartifoss.snfell.common

/**
 * Color-treatment vocabulary shared by the phone preview and the watch renderer.
 *
 * Component-specific controls can inherit the face-wide treatment or override it. The parser
 * deliberately understands the previous Colors-page values so a watch can render a setting sent
 * by an older phone before that phone has had a chance to run its migration.
 */
enum class SurfaceColorTreatment {
    FOLLOW,
    NORMAL,
    DESATURATED,
    EXPRESSIVE;

    fun resolveAgainst(global: SurfaceColorTreatment): SurfaceColorTreatment =
            if (this == FOLLOW) global.takeUnless { it == FOLLOW } ?: EXPRESSIVE else this

    companion object {
        fun fromPreference(
                value: String?,
                legacyDesaturated: Boolean = false,
                default: SurfaceColorTreatment = FOLLOW
        ): SurfaceColorTreatment = when (value) {
            "follow" -> FOLLOW
            "normal", "neutral", "custom" -> NORMAL
            "desaturated" -> DESATURATED
            "expressive" -> EXPRESSIVE
            "album" -> if (legacyDesaturated) DESATURATED else EXPRESSIVE
            else -> default
        }
    }
}
