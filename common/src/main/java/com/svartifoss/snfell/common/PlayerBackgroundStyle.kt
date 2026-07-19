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
        val hidesArtwork: Boolean = false
) {
    COVER("cover"),
    BLUR("blur", blurredArtwork = true),
    BLACK_AND_WHITE("bw", grayscaleArtwork = true),
    BLURRED_BLACK_AND_WHITE(
            "blur_bw",
            blurredArtwork = true,
            grayscaleArtwork = true
    ),
    EXPRESSIVE("expressive", blurredArtwork = true),
    MATERIAL("material"),
    POSTER("poster"),
    STUDIO("studio"),
    VINYL("vinyl"),
    HALO("halo"),
    AURORA("aurora"),
    SPECTRUM("spectrum"),
    ECLIPSE("eclipse", hidesArtwork = true),
    HIDDEN("hidden", hidesArtwork = true);

    val usesBlurRadius: Boolean
        get() = blurredArtwork

    /** Styles that only transform the host image and therefore need the shared default fade. */
    val isPlainArtworkTreatment: Boolean
        get() = this == COVER || this == BLUR || this == BLACK_AND_WHITE ||
                this == BLURRED_BLACK_AND_WHITE

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
            else -> COVER
        }
    }
}
