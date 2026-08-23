package com.svartifoss.snfell.common

/**
 * How the Split face fills the panel below its seam.
 *
 * Split is the one face that paints its own opaque backdrop, so the ~20 Album background styles
 * have no effect on it by construction - the two-tone composition *is* the treatment. That is
 * exactly why the panel needs a control of its own: without one, the only way to change how the
 * lower half looks is to leave the face.
 *
 * A face-scoped preference rather than a hardcoded change, because [SOLID] is what the face
 * shipped with and someone chose it. [BLUR] is the default: the same artwork continuing behind the
 * text says more about the record than a flat swatch of one colour taken from it, and it is the
 * treatment the rest of the app already uses for exactly this job (see `PlayerBackgroundStyle`'s
 * Expressive).
 *
 * Pure and free of `android.*` so the fallback is pinned by a JVM test - which is the whole point
 * of the object, since an unknown value has to resolve to *something* and the wrong something is a
 * face that silently ignores the setting.
 */
enum class SplitPanelStyle(val preferenceValue: String) {

    /**
     * The album's artwork, blurred, under a wash of its own colour.
     *
     * The artwork is laid out across the **whole screen** and then clipped to the panel, not scaled
     * into it: the point is that the cover above the seam and the blur below are one continuous
     * image, sharp in the band and soft in the panel. Scaling a second copy into the lower box
     * would put a different crop there and the seam would read as two unrelated pictures.
     */
    BLUR("blur"),

    /** A flat tone derived from the album accent - the notification-card look the face shipped with. */
    SOLID("solid");

    companion object {

        val DEFAULT = BLUR

        /**
         * Unknown values resolve to [DEFAULT] rather than to [SOLID].
         *
         * A value can arrive from an imported backup or from a newer build on the other device, and
         * the honest answer to "I do not recognise this" is the face's normal appearance, not the
         * other named option - picking that would make an unreadable value look like a deliberate
         * choice of the thing it is not.
         */
        fun fromPref(value: String?): SplitPanelStyle =
                entries.firstOrNull { it.preferenceValue == value?.trim() } ?: DEFAULT
    }
}
