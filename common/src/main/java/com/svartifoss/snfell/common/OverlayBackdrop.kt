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
    SUNRISE,
    DEEP_OCEAN,

    /** A dark field with several soft album-colour clouds, like a small nebula behind the panel. */
    NEBULA,

    /** A warm, ember-lit gradient that keeps the edges dark for readable panel controls. */
    EMBER,

    /** A cool, layered waterline that moves from deep blue into the album's quieter tone. */
    TIDELINE,

    /** A black-green field with cyan/album blooms, inspired by light suspended in dark water. */
    BIOLUMINESCENCE,

    /** A jewel-toned, pearlescent blend of the album triad with a soft violet middle. */
    IRIDESCENT,

    /** A restrained charcoal surface with a subtle diagonal material grain. */
    GRAPHITE,

    /** A cinematic wash: a narrow, luminous band held between deep letterbox-like edges. */
    CINEMA,

    /** Two offset colour orbits that create depth without turning the panel into a flat gradient. */
    ORBIT,

    /** A calm horizontal glow, useful when the panel should feel spacious rather than loud. */
    HORIZON,

    /** A translucent ink bloom that lets the blurred cover remain part of the surface. */
    INK_WASH,

    /** A deep berry and rose treatment with a small jewel-like centre glow. */
    BLOSSOM,

    /** A cold slate/teal treatment with the quiet, mineral character of a fjord. */
    FJORD,

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
    EXPRESSIVE_NO_BLUR,

    /**
     * A fine hex-offset grid of dots - a technical, schematic texture rather than a wash. See
     * [OverlayBackdropPatterns.drawDotMatrix].
     */
    DOT_MATRIX,

    /** Horizontal interlaced hairlines over a dark wash, like an old CRT display. See
     *  [OverlayBackdropPatterns.drawScanlines]. */
    SCANLINES,

    /** Concentric rings from the centre plus a fixed bright sweep wedge, like a radar screen
     *  caught mid-sweep - never animated. See [OverlayBackdropPatterns.drawRadarRings]. */
    RADAR,

    /** Concentric closed contours nudged off a perfect circle, like elevation lines on a
     *  topographic map - the album's hue decides the exact wobble. See
     *  [OverlayBackdropPatterns.drawContourLines]. */
    CONTOUR,

    /** A low-poly field of triangular facets, each a small deterministic tone step from its
     *  neighbours - a cut-gem surface instead of a smooth gradient. See
     *  [OverlayBackdropPatterns.drawFacetedCrystal]. */
    FACETED,

    /*
     * Treatments carried over from [PlayerBackgroundStyle] so the two catalogues offer the same
     * repertoire, keeping the player's value name, hues and direction.
     *
     * They are not pixel copies and cannot be. A player wash sits *over artwork* and works by
     * alpha; a panel backdrop *is* the surface, with controls on it, so the same hues have to be
     * carried at a lightness a control can be read against. What ports is the composition - where
     * the colour sits and which way it runs - not the opacity.
     */

    /** Only the lower band darkens; the top of the surface stays where it started. */
    DUSK,

    /** Cool cyan across the top falling into a navy floor. */
    ICE,

    /** Muted rose light gathered around the lower right. */
    ROSE,

    /** A warm paper tint with a darkened foot and a fine inset rule. */
    PAPER,

    /** A colour slab down the left edge against black, with a darkened foot. */
    MONOLITH,

    /** A warm lamp low and centred, with the surface falling away above and below it. */
    LANTERN,

    /** One crisp light well slightly above centre on a black field. */
    NOIR,

    /** Near-black plum with a single soft album bloom low and left. */
    VELVET,

    /** Two glows pushed hard to opposite edges, leaving the middle open. */
    MIRAGE,

    /** Three contained glows of the triad, none of them near the centre. */
    BLOOM,

    /** Three wide, soft clouds of the triad over a dark field. */
    CLOUD,

    /** Deep navy with one glow high and right, and four points of light. */
    NOCTURNE,

    /** Three tight, saturated pools of the triad - denser than [CLOUD]. */
    LIQUID,

    /** Three stroked waves crossing the surface. */
    TIDAL,

    /** One wide album glow off-centre on black, the way the Vinyl face lights its disc. */
    VINYL,

    /** Colour only in a soft band hugging the rim; the middle stays clear. */
    CORONA,

    /** A single sweeping arc running off both edges. */
    CRESCENT,

    /** A fine ruled grid over a deep field. */
    GRID,

    /** A pale veil closed by a fine, bright sweeping rim. */
    GLASS_VEIL,

    /** The Material face's own backdrop: one wide tonal container centred on black. */
    MATERIAL,

    /** Poster's darkened head and foot with the sides pulled in. */
    POSTER,

    /** Studio's single diagonal light from the top right. */
    STUDIO,

    /** Spectrum's stacked bands falling from the album surface to black. */
    SPECTRUM;

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
            "sunrise" -> SUNRISE
            "deep_ocean" -> DEEP_OCEAN
            "nebula" -> NEBULA
            "ember" -> EMBER
            "tideline" -> TIDELINE
            "bioluminescence" -> BIOLUMINESCENCE
            "iridescent" -> IRIDESCENT
            "graphite" -> GRAPHITE
            "cinema" -> CINEMA
            "orbit" -> ORBIT
            "horizon" -> HORIZON
            "ink_wash" -> INK_WASH
            "blossom" -> BLOSSOM
            "fjord" -> FJORD
            "liquid_glass" -> LIQUID_GLASS
            "expressive" -> EXPRESSIVE
            "expressive_no_blur" -> EXPRESSIVE_NO_BLUR
            "dot_matrix" -> DOT_MATRIX
            "scanlines" -> SCANLINES
            "radar" -> RADAR
            "contour" -> CONTOUR
            "faceted" -> FACETED
            "dusk" -> DUSK
            "ice" -> ICE
            "rose" -> ROSE
            "paper" -> PAPER
            "monolith" -> MONOLITH
            "lantern" -> LANTERN
            "noir" -> NOIR
            "velvet" -> VELVET
            "mirage" -> MIRAGE
            "bloom" -> BLOOM
            "cloud" -> CLOUD
            "nocturne" -> NOCTURNE
            "liquid" -> LIQUID
            "tidal" -> TIDAL
            "vinyl" -> VINYL
            "corona" -> CORONA
            "crescent" -> CRESCENT
            "grid" -> GRID
            "glass_veil" -> GLASS_VEIL
            "material" -> MATERIAL
            "poster" -> POSTER
            "studio" -> STUDIO
            "spectrum" -> SPECTRUM
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
                this == EXPRESSIVE || this == SMOKE || this == NEBULA ||
                this == BIOLUMINESCENCE || this == INK_WASH
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
