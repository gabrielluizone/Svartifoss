package com.svartifoss.snfell.common

/** How album artwork is rendered behind the low-power always-on presentation. */
enum class AodArtTreatment(val preferenceValue: String) {
    /** Coloured, softly blurred artwork. This preserves the historical AOD appearance. */
    BLUR("blur"),
    /** Coloured artwork without the ambient-only blur. */
    CLEAR("clear"),
    /** Greyscale artwork with the ambient-only blur. */
    MONOCHROME_BLUR("monochrome_blur"),
    /** Reuse the interactive player's cover/blur/black-and-white/hidden choice. */
    FOLLOW("follow");

    companion object {
        fun fromPreference(value: String?): AodArtTreatment = when (value) {
            CLEAR.preferenceValue -> CLEAR
            MONOCHROME_BLUR.preferenceValue -> MONOCHROME_BLUR
            FOLLOW.preferenceValue -> FOLLOW
            else -> BLUR
        }
    }
}

/** Fully resolved artwork work for one AOD frame. A hidden result must not decode or blur art. */
data class AodArtworkSpec(
        val visible: Boolean,
        val blurred: Boolean = false,
        val monochrome: Boolean = false,
        val photoFilter: AlbumArtFilter = AlbumArtFilter.NONE
)

/**
 * Resolves the independent AOD artwork controls against the effective ambient presentation.
 * Chrono and Eclipse are guaranteed art-free here, rather than rendering/blur-processing an
 * image that their black canvases immediately cover.
 */
fun resolveAodArtwork(
        showArtwork: Boolean,
        effectiveAodStyle: String,
        treatment: AodArtTreatment,
        playerArtworkStyle: String
): AodArtworkSpec {
    if (!showArtwork || effectiveAodStyle == "chrono" || effectiveAodStyle == "eclipse") {
        return AodArtworkSpec(visible = false)
    }

    return when (treatment) {
        AodArtTreatment.BLUR -> AodArtworkSpec(visible = true, blurred = true)
        AodArtTreatment.CLEAR -> AodArtworkSpec(visible = true)
        AodArtTreatment.MONOCHROME_BLUR ->
            AodArtworkSpec(visible = true, blurred = true, monochrome = true)
        AodArtTreatment.FOLLOW -> PlayerBackgroundStyle.fromPreference(playerArtworkStyle).let {
            AodArtworkSpec(
                    visible = !it.hidesArtwork,
                    blurred = it.blurredArtwork,
                    monochrome = it.grayscaleArtwork,
                    photoFilter = it.photoFilter)
        }
    }
}
