package com.svartifoss.snfell.common

/**
 * Artwork/background treatment selected independently from the structural player layout.
 *
 * The persisted values are shared by the phone preview and Wear renderer. Legacy artwork-only
 * choices remain valid, while the named treatments expose the authored backdrops that used to be
 * locked to one specific layout.
 */
enum class PlayerBackgroundStyle(
        val preferenceValue: String,
        val blurredArtwork: Boolean = false,
        val grayscaleArtwork: Boolean = false,
        val hidesArtwork: Boolean = false,
        val frostedEdges: Boolean = false
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
    ECLIPSE("eclipse", hidesArtwork = true),
    HIDDEN("hidden", hidesArtwork = true);

    val usesBlurRadius: Boolean
        get() = blurredArtwork || frostedEdges

    /** Styles that only transform the host image and therefore need the shared default fade. */
    val isPlainArtworkTreatment: Boolean
        get() = this == COVER || this == BLUR || this == BLACK_AND_WHITE ||
                this == BLURRED_BLACK_AND_WHITE || this == FROSTED ||
                this == SQUARE_SHARP || this == SQUARE_SOFT || this == SQUARE

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
        private val LEGACY_OVERRIDE_VALUES = setOf("blur", "bw", "blur_bw", "hidden")

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
