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
    SOLID_SECONDARY,
    SOLID_TERTIARY,
    GLASS,
    GRADIENT,
    DUOTONE,
    PRISM,
    MESH,
    AURORA,
    SPOTLIGHT,
    VIGNETTE,
    SPLIT,
    BANDS,
    MIDNIGHT,
    HALO,
    SMOKE,

    /**
     * Heavily blurred artwork read through a bright, thin-edged pane - the treatment Apple calls
     * liquid glass.
     *
     * Distinct from [GLASS], which is a flat white-to-black wash: this one keeps far more of the
     * blurred cover visible, tints it with the album rather than with grey, and carries a bright
     * rim so the pane reads as having an edge and a thickness. It is the most transparent backdrop
     * in the set by design, which is also why it is the one most dependent on the album blur
     * actually being available underneath.
     */
    LIQUID_GLASS,

    /**
     * The Expressive face's authored backdrop, reused as an overlay surface: an album-tinted wash
     * over the artwork, a black knock-back, and a vignette that closes in hard at the rim.
     *
     * Deliberately the same two values [PlayerBackgroundStyle] uses for the player background
     * (`expressive` / `expressive_no_blur`, labelled "Expressive blur" / "Expressive"), because
     * they are the same treatment applied to a different surface - a user who picked Expressive for
     * their face and then finds a differently-named near-match in the panel list has to guess
     * whether the two are related.
     */
    EXPRESSIVE,

    /** [EXPRESSIVE] over the *sharp* cover. See [PlayerBackgroundStyle.EXPRESSIVE_NO_BLUR]. */
    EXPRESSIVE_NO_BLUR;

    companion object {
        fun fromPreference(value: String?): OverlayBackdrop = when (value) {
            "acrylic", "blur" -> ACRYLIC
            "black" -> SOLID_BLACK
            "album" -> SOLID_ALBUM
            "secondary" -> SOLID_SECONDARY
            "tertiary" -> SOLID_TERTIARY
            "glass" -> GLASS
            "gradient" -> GRADIENT
            "duotone" -> DUOTONE
            "prism" -> PRISM
            "mesh" -> MESH
            "aurora" -> AURORA
            "spotlight" -> SPOTLIGHT
            "vignette" -> VIGNETTE
            "split" -> SPLIT
            "bands" -> BANDS
            "midnight" -> MIDNIGHT
            "halo" -> HALO
            "smoke" -> SMOKE
            "liquid_glass" -> LIQUID_GLASS
            "expressive" -> EXPRESSIVE
            "expressive_no_blur" -> EXPRESSIVE_NO_BLUR
            else -> FOLLOW_STYLE
        }
    }

    /**
     * Whether the cached blurred album bitmap is part of this background composition.
     *
     * [EXPRESSIVE_NO_BLUR] is absent on purpose rather than by omission: it is the one backdrop that
     * wants the *sharp* artwork behind it, which it gets for free because the player's own album-art
     * View sits below the overlay group - so the honest answer here is "not the blurred copy".
     */
    val usesAlbumBlur: Boolean
        get() = this == ACRYLIC || this == GLASS || this == PRISM || this == LIQUID_GLASS ||
                this == EXPRESSIVE || this == SMOKE
}

/** Resolves the compatibility option without leaking renderer-specific Android classes here. */
object OverlayBackdropResolver {
    fun resolve(preference: String?, contentStyle: String?): OverlayBackdrop {
        val requested = OverlayBackdrop.fromPreference(preference)
        if (requested != OverlayBackdrop.FOLLOW_STYLE) return requested

        return when (contentStyle) {
            "glass", "glass_white", "glass_tonal", "outline_glass_white", "frost", "mist",
            "pill", "chrome" ->
                OverlayBackdrop.GLASS
            "tonal", "ink", "expressive", "soft", "bubble", "pulse", "rail" ->
                OverlayBackdrop.SOLID_ALBUM
            "gradient", "aurora", "sunset" -> OverlayBackdrop.GRADIENT
            "duotone", "dual" -> OverlayBackdrop.DUOTONE
            "prism", "holo", "spectrum" -> OverlayBackdrop.PRISM
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
        "square_album", "stacked_pill" -> "tonal"
        "ribbon" -> "gradient"
        "lcd" -> "terminal"
        "compact_pill", "badge", "glass_bar", "outline_square" -> "glass"
        else -> "glass"
    }
}
