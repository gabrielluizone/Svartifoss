package com.svartifoss.snfell.common

/**
 * A wash of the album accent pooled along the bottom edge of the screen, concentric with the case.
 *
 * A *piece*, not part of any one face. It began inside the lyrics screen and the Verse face, which
 * is exactly the mistake this enum exists to undo: a distinctive treatment welded into one
 * composition cannot be put on another face or saved into a custom theme, so the only way to have
 * it was to accept everything else that face decided too.
 *
 * Rendered by the shared background layer (`PlayerBackgroundTreatment`), so it sits above the
 * backdrop and below the face's own content - which is the only stacking that works. Drawn over the
 * whole face it would tint the text; drawn under the backdrop an opaque one would hide it.
 *
 * [OFF] is the default for every face but Verse. This is a strong, opinionated treatment, and
 * turning it on everywhere would redesign fifteen faces nobody asked to have redesigned.
 */
enum class AccentFloorStyle(
        val preferenceValue: String,
        /** Peak alpha of the accent at the very bottom of the screen. */
        val maxAlpha: Float,
        /** Where the ring starts, as a fraction of the screen radius - lower reaches further in. */
        val innerStop: Float,
        /** Where the vertical mask begins, as a fraction of screen height - lower reaches higher. */
        val maskStart: Float,
) {
    OFF("off", 0f, 1f, 1f),

    /** Barely there: a hint of colour at the rim, for faces with content near the bottom. */
    SOFT("soft", 0.26f, 0.62f, 0.70f),

    /** What the lyrics screen and the Verse face ship with. */
    STANDARD("standard", 0.44f, 0.55f, 0.62f),

    /** Reaches further up the screen and holds more colour. */
    BOLD("bold", 0.62f, 0.45f, 0.52f);

    val isVisible: Boolean get() = this != OFF

    companion object {
        val DEFAULT = OFF

        /** Where the mask always ends - the very bottom of the screen, for every style. */
        const val MASK_END = 0.99f

        /**
         * Unknown values resolve to [DEFAULT] rather than to something visible: a value can arrive
         * from an imported backup or a newer build on the other device, and inventing a strong
         * treatment on a face the user never chose it for is the worse failure.
         */
        fun fromPreference(value: String?): AccentFloorStyle =
                entries.firstOrNull { it.preferenceValue == value?.trim()?.lowercase() } ?: DEFAULT
    }
}
