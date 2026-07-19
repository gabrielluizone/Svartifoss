package com.svartifoss.snfell.common

/**
 * Background treatment shared by the volume, seek and quick-actions overlays.
 *
 * This is deliberately separate from the content style: changing the volume arc or the quick
 * action buttons must not silently change what fills the screen behind them. [FOLLOW_STYLE] is
 * retained for users who prefer the old coupled behaviour.
 */
enum class OverlayBackdrop {
    FOLLOW_STYLE,
    ACRYLIC,
    SOLID_BLACK,
    SOLID_ALBUM,
    GLASS,
    GRADIENT,
    DUOTONE,
    PRISM;

    companion object {
        fun fromPreference(value: String?): OverlayBackdrop = when (value) {
            "acrylic", "blur" -> ACRYLIC
            "black" -> SOLID_BLACK
            "album" -> SOLID_ALBUM
            "glass" -> GLASS
            "gradient" -> GRADIENT
            "duotone" -> DUOTONE
            "prism" -> PRISM
            else -> FOLLOW_STYLE
        }
    }

    /** Whether the cached blurred album bitmap is part of this background composition. */
    val usesAlbumBlur: Boolean
        get() = this == ACRYLIC || this == GLASS || this == PRISM
}

/** Resolves the compatibility option without leaking renderer-specific Android classes here. */
object OverlayBackdropResolver {
    fun resolve(preference: String?, contentStyle: String?): OverlayBackdrop {
        val requested = OverlayBackdrop.fromPreference(preference)
        if (requested != OverlayBackdrop.FOLLOW_STYLE) return requested

        return when (contentStyle) {
            "glass", "frost", "pill" -> OverlayBackdrop.GLASS
            "tonal", "ink", "expressive" -> OverlayBackdrop.SOLID_ALBUM
            "gradient", "aurora" -> OverlayBackdrop.GRADIENT
            "duotone" -> OverlayBackdrop.DUOTONE
            "prism" -> OverlayBackdrop.PRISM
            else -> OverlayBackdrop.SOLID_BLACK
        }
    }

    /**
     * Maps the seek readout style to the "follow" content style for its overlay backdrop. Shared by
     * the watch and the phone preview. The universal control-style theme
     * ([MiscPreferences.WEAR_SCREEN_THEME]) deliberately never feeds in here: the backdrop is owned
     * by the seek/overlay style prefs, and letting the control theme change it made the seek surface
     * look different from the volume/quick surfaces for no configured reason.
     */
    fun seekContentStyle(seekOverlayStyle: String?): String = when (seekOverlayStyle) {
        "expressive" -> "tonal"
        "material" -> "material"
        "white" -> "light"
        else -> "glass"
    }
}
