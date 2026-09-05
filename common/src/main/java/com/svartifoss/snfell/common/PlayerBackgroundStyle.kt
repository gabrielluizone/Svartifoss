package com.svartifoss.snfell.common

/**
 * Artwork/background treatment selected independently from the structural player layout.
 *
 * The persisted values are shared by the phone preview and Wear renderer. Legacy artwork-only
 * choices remain valid, while the named treatments expose the authored backdrops that used to be
 * locked to one specific layout.
 */
/**
 * Which slot of the album triad a flat-fill background paints.
 *
 * Only the three solid fills carried over from the panel catalogue have one. It exists so a
 * renderer never has to be asked "which colour is this style?" through a `when` of its own, and
 * so [AdaptiveTextContrast] can be told the real luminance of a field that hides the artwork
 * without being black.
 */
enum class AlbumFillSlot { PRIMARY, SECONDARY, TERTIARY }

enum class PlayerBackgroundStyle(
        val preferenceValue: String,
        val blurredArtwork: Boolean = false,
        val grayscaleArtwork: Boolean = false,
        val hidesArtwork: Boolean = false,
        val frostedEdges: Boolean = false,
        /** Filter carried by a pre-layer value; new settings use the independent Filter key. */
        val legacyFilter: AlbumArtFilter = AlbumArtFilter.NONE,
        /** Set only by the flat album fills - see [AlbumFillSlot]. */
        val flatAlbumFill: AlbumFillSlot? = null
) {
    COVER("cover"),
    BLUR("blur", blurredArtwork = true),
    /**
     * Sharp in the middle, frosted glass towards the rim - see [FrostedEdges].
     *
     * [frostedEdges] rather than [blurredArtwork]: the two are mutually exclusive treatments of
     * the same bitmap (blurring the whole image and then frosting its edge would just be a blur),
     * but frosting still wants the "Blur radius" preference to govern its strength, which
     * [usesBlurRadius] grants separately.
     */
    FROSTED("frosted", frostedEdges = true),
    BLACK_AND_WHITE("bw", grayscaleArtwork = true),
    BLURRED_BLACK_AND_WHITE(
            "blur_bw",
            blurredArtwork = true,
            grayscaleArtwork = true
    ),
    /** Fits the (typically square) cover uncropped inside the round screen instead of
     *  center-cropping it to fill the circle, with the corners around it always filled by a
     *  blurred copy of the same artwork so there is never a black void. [blurredArtwork] = true
     *  is deliberate here too: it is the corner backdrop's blur, not the whole image's, but it
     *  still means the existing "Blur radius" preference governs its strength. Three corner
     *  styles - see [squareCornerRadiusFraction] - share this same rendering, differing only in
     *  how rounded the inset's corners are. */
    SQUARE_SHARP("square_sharp", blurredArtwork = true),
    SQUARE_SOFT("square_soft", blurredArtwork = true),
    SQUARE("square", blurredArtwork = true),
    EXPRESSIVE("expressive", blurredArtwork = true),
    EXPRESSIVE_NO_BLUR("expressive_no_blur", blurredArtwork = false),
    MATERIAL("material"),
    POSTER("poster"),
    STUDIO("studio"),
    VINYL("vinyl"),
    HALO("halo"),
    AURORA("aurora"),
    SPECTRUM("spectrum"),
    /** A soft color ring hugging the rim - the conic sweep (tertiary -> primary -> secondary ->
     *  tertiary) drawn as a wide stroke near the edge, not a full-bleed fill, so the cover's
     *  center stays fully legible and only the border picks up the accent hues. */
    CORONA("corona"),
    /** A single smooth vertical fade that darkens only toward the bottom - the top of the cover
     *  stays essentially untouched. Simpler and moodier than Poster/Spectrum's layered gradients:
     *  no side vignette, no bright color, just a deepening shadow. */
    DUSK("dusk"),
    /** Three small, soft, contained glows of the album's accent colors, tucked off-center - more
     *  minimal than Aurora's darker ribbon-and-glow composition, and leaves most of the cover
     *  untouched between them. */
    BLOOM("bloom"),
    /** The lightest-touch authored treatment in the set: only the lower band of the screen
     *  darkens, just enough for control legibility, leaving the rest of the cover untouched. */
    HORIZON("horizon"),
    /** A single small, warm glow tucked into one corner - no base scrim at all, so the cover is
     *  otherwise completely untouched. */
    EMBER("ember"),
    /** A cool blue/teal wash rising from the bottom over the sharp artwork. */
    OCEAN("ocean"),
    /** Warm coral and amber light pooled across the lower half of the artwork. */
    SUNSET("sunset"),
    /** A restrained central light well surrounded by a deep circular vignette. */
    SPOTLIGHT("spotlight"),
    /** Blurred artwork behind a pale translucent veil and a fine bright rim. */
    GLASS_VEIL("glass_veil", blurredArtwork = true),
    /** Near-black plum treatment with a small, soft album-colour bloom. */
    VELVET("velvet"),
    /** Black field, desaturated artwork impression and one crisp white light well. */
    NOIR("noir", grayscaleArtwork = true),
    /** Cool cyan light across the upper half, fading into a navy floor. */
    ICE("ice"),
    /** Muted rose light gathered around the lower-right controls. */
    ROSE("rose"),
    /** Authored backdrops that remain distinct from the bitmap's independent Filter layer. */
    PRISMATIC("prismatic"),
    CRESCENT("crescent"),
    TIDAL("tidal"),
    PAPER("paper"),
    LANTERN("lantern"),
    MIRAGE("mirage"),
    GRID("grid"),
    NOCTURNE("nocturne"),
    CLOUD("cloud"),
    LIQUID("liquid"),
    MONOLITH("monolith"),
    SPLIT_TONE("split_tone"),
    /*
     * Carried over from [OverlayBackdrop] so the two catalogues offer the same repertoire.
     *
     * The adaptation runs the opposite way to a player-to-panel port: a panel backdrop *is* an
     * opaque surface, and here the same composition has to let the cover through, so what arrives
     * is the geometry and the hues at the alpha an artwork treatment works by.
     */
    /** Diagonal wash from the album's primary into its secondary. */
    GRADIENT("gradient"),
    /** Two tones meeting across the middle. */
    DUOTONE("duotone"),
    /** Stacked horizontal bands of the whole triad. */
    BANDS("bands"),
    /** An album tone open at the centre and closing hard at the rim. */
    VIGNETTE("vignette"),
    /** A charcoal surface with a diagonal material grain. */
    GRAPHITE("graphite"),
    /** A luminous band held between deep letterbox edges. */
    CINEMA("cinema"),
    /** A frosted diagonal pane, album-tinted at the top corner. */
    ACRYLIC("acrylic"),
    /** Two wide colour fields meeting across an album-toned ground. */
    MESH("mesh"),
    /** Soft album clouds scattered over a dark field. */
    NEBULA("nebula"),
    /** A green-black field lit by two cyan blooms. */
    BIOLUMINESCENCE("bioluminescence"),
    /** A pearlescent run through the triad with violet at its middle. */
    IRIDESCENT("iridescent"),
    /** Two offset colour orbits with a small core. */
    ORBIT("orbit"),
    /** A translucent ink bloom running off one corner. */
    INK_WASH("ink_wash"),
    /** Berry and rose with a jewel-like centre. */
    BLOSSOM("blossom"),
    /** Cold slate and teal with the mineral character of a fjord. */
    FJORD("fjord"),
    /*
     * The five drawn patterns. Unlike every other treatment here these are not gradients, so all
     * three renderers call the *same* `OverlayBackdropPatterns` function - Compose reaches it
     * through `drawIntoCanvas { it.nativeCanvas }`, which is what keeps a hand-drawn pattern from
     * being written twice and drifting.
     */
    /** A fine hex-offset grid of dots. */
    DOT_MATRIX("dot_matrix"),
    /** Interlaced hairlines, like an old CRT. */
    SCANLINES("scanlines"),
    /** Concentric rings and one fixed sweep wedge. */
    RADAR("radar"),
    /** Elevation-style contours nudged off a perfect circle. */
    CONTOUR("contour"),
    /** A low-poly field of triangular facets. */
    FACETED("faceted"),
    /*
     * The flat album fills. They set [hidesArtwork] because they genuinely do cover the cover -
     * but unlike HIDDEN and ECLIPSE the field they paint is *not* black, which is why they also
     * carry a [flatAlbumFill] for `AdaptiveTextContrast.backdropLuminance` to measure. Without
     * that, adaptive contrast would assume a black ground under a possibly light album tone and
     * darken text that needed lifting.
     */
    /** A flat field in the album's own accent. */
    SOLID_ALBUM("album", hidesArtwork = true, flatAlbumFill = AlbumFillSlot.PRIMARY),
    /** A flat field in the palette's secondary. */
    SOLID_SECONDARY("secondary", hidesArtwork = true, flatAlbumFill = AlbumFillSlot.SECONDARY),
    /** A flat field in the palette's tertiary. */
    SOLID_TERTIARY("tertiary", hidesArtwork = true, flatAlbumFill = AlbumFillSlot.TERTIARY),
    /** A pale wash falling into black - the panel's Glass, over the player's artwork. */
    GLASS("glass"),
    /** Deep navy falling to black. */
    MIDNIGHT("midnight"),
    /** A grey diagonal haze. */
    SMOKE("smoke"),
    /** A layered waterline from deep blue into the album's quieter tone. */
    TIDELINE("tideline"),
    // Deprecated persisted aliases. They remain readable while the picker no longer mixes them
    // into Album art style; resolveAlbumArtFilter() turns them into the independent filter.
    FILTER_WARM("filter_warm", legacyFilter = AlbumArtFilter.WARM),
    FILTER_COOL("filter_cool", legacyFilter = AlbumArtFilter.COOL),
    FILTER_GOLDEN("filter_golden", legacyFilter = AlbumArtFilter.GOLDEN),
    FILTER_ROSE("filter_rose", legacyFilter = AlbumArtFilter.ROSE),
    FILTER_VINTAGE("filter_vintage", legacyFilter = AlbumArtFilter.VINTAGE),
    FILTER_FADED("filter_faded", legacyFilter = AlbumArtFilter.FADED),
    FILTER_MATTE("filter_matte", legacyFilter = AlbumArtFilter.MATTE),
    FILTER_VIVID("filter_vivid", legacyFilter = AlbumArtFilter.VIVID),
    FILTER_PUNCH("filter_punch", legacyFilter = AlbumArtFilter.PUNCH),
    FILTER_PASTEL("filter_pastel", legacyFilter = AlbumArtFilter.PASTEL),
    FILTER_SEPIA("filter_sepia", legacyFilter = AlbumArtFilter.SEPIA),
    FILTER_CYANOTYPE("filter_cyanotype", legacyFilter = AlbumArtFilter.CYANOTYPE),
    FILTER_TEAL_ORANGE("filter_teal_orange", legacyFilter = AlbumArtFilter.TEAL_ORANGE),
    FILTER_HIGH_CONTRAST("filter_high_contrast", legacyFilter = AlbumArtFilter.HIGH_CONTRAST),
    FILTER_SOFT_LIGHT("filter_soft_light", legacyFilter = AlbumArtFilter.SOFT_LIGHT),
    FILTER_NIGHT("filter_night", legacyFilter = AlbumArtFilter.NIGHT),
    ECLIPSE("eclipse", hidesArtwork = true),
    HIDDEN("hidden", hidesArtwork = true);

    val usesBlurRadius: Boolean
        get() = blurredArtwork || frostedEdges

    val artworkFilter: AlbumArtFilter
        get() = if (grayscaleArtwork) AlbumArtFilter.MONOCHROME else legacyFilter

    /** Styles that only transform the host image and therefore need the shared default fade. */
    val isPlainArtworkTreatment: Boolean
        get() = this == COVER || this == BLUR || this == BLACK_AND_WHITE ||
                this == BLURRED_BLACK_AND_WHITE || this == FROSTED ||
                this == SQUARE_SHARP || this == SQUARE_SOFT || this == SQUARE ||
                legacyFilter != AlbumArtFilter.NONE

    /** Fraction of the inset square's own side used as its corner radius, or null for a style
     *  that isn't a Square variant at all. */
    val squareCornerRadiusFraction: Float?
        get() = when (this) {
            SQUARE_SHARP -> 0f
            SQUARE_SOFT -> 0.04f
            SQUARE -> 0.10f
            else -> null
        }

    companion object {
        private val LEGACY_OVERRIDE_VALUES = setOf(
                "blur", "bw", "blur_bw", "hidden",
                "filter_warm", "filter_cool", "filter_golden", "filter_rose",
                "filter_vintage", "filter_faded", "filter_matte", "filter_vivid",
                "filter_punch", "filter_pastel", "filter_sepia", "filter_cyanotype",
                "filter_teal_orange", "filter_high_contrast", "filter_soft_light", "filter_night")

        fun fromPreference(value: String?): PlayerBackgroundStyle =
                entries.firstOrNull { it.preferenceValue == value } ?: COVER

        /** Values a pre-preset global preference could represent as real non-default intent. */
        fun isLegacyOverrideValue(value: String?): Boolean = value in LEGACY_OVERRIDE_VALUES

        /** Preserves each built-in layout's shipped visual identity until the user chooses a
         * different treatment for that layout. */
        fun defaultForFace(face: String): PlayerBackgroundStyle = when (face) {
            "expressive" -> EXPRESSIVE
            "material" -> MATERIAL
            "poster" -> POSTER
            "studio" -> STUDIO
            "vinyl" -> VINYL
            "halo" -> HALO
            "aurora" -> AURORA
            "spectrum" -> SPECTRUM
            "eclipse" -> ECLIPSE
            // A rail of sharp covers needs a quiet backdrop; the full-bleed sharp cover the
            // default would give competes with every card on screen.
            "carousel", "chat", "split" -> EXPRESSIVE
            // Text on emptiness is the whole idea - see NoteFace.
            "note" -> HIDDEN
            // Verse paints its own black-and-accent floor and reads three lines of type across the
            // middle; artwork behind that is the one thing guaranteed to make it unreadable.
            "verse" -> HIDDEN
            // Small text over a full-bleed cover is unreadable, and this face is nothing but
            // small text. A default only - the background picker still reaches it like any other.
            "metadata" -> HIDDEN
            // Ribbon and Frame author their own vivid rails/card on an OLED-black field. Keeping
            // the artwork out by default makes that composition legible, while the background
            // picker can still put a cover treatment behind either face when the user wants one.
            "ribbon", "frame" -> HIDDEN
            else -> COVER
        }
    }
}
