package com.svartifoss.snfell.common

/**
 * The silhouette a face cuts its album artwork to, shared so the watch and the phone preview cut
 * the same one.
 *
 * It began as Carousel's card outline and is now a small registry four faces read - Carousel's
 * cover rail, Note's cover disc, Chat's avatar and Metadata's thumbnail - which is the rule this
 * project applies to any treatment worth having on more than one composition: it lives somewhere
 * shared so any face can wear it and a saved theme can carry it, rather than being welded into the
 * layout that happened to want it first.
 *
 * The corner radius is expressed as a *fraction of the cover size* rather than in dp. Carousel's
 * hero card and its two smaller neighbours must read as the same shape, which a fixed radius would
 * break by making the smaller cards look rounder; a single cover elsewhere is one size, but the same
 * fraction keeps every consumer honestly the same shape at very different sizes.
 */
enum class CoverShape(val preferenceValue: String, val cornerFraction: Float) {
    /** A generously rounded square - the Carousel rail's reference look. */
    ROUNDED("rounded", 0.08f),

    /** Hard corners - the most "album sleeve" of the four. */
    SQUARE("square", 0f),

    /** Softer than [ROUNDED] without becoming a circle. */
    SQUIRCLE("squircle", 0.18f),

    /** Rounder still than [SQUIRCLE], short of a circle - the continuous-curvature look of a modern
     *  app-icon mask (One UI, iOS). Approximated the same way every other entry here is, with a
     *  larger corner fraction rather than a true superellipse path, so it stays the one shared,
     *  cheap primitive [toComposeShape] already knows how to draw. */
    SAMSUNG("samsung", 0.30f),

    /** Full circle. Crops the most artwork, so on Carousel it is a deliberate choice; on Note it is
     *  the default, because there the cover reads as a contact photo beside a message. */
    CIRCLE("circle", 0.5f);

    companion object {
        /** [default] is the caller's, not a property of the vocabulary: Carousel rests on a card
         *  and Note on a disc, and an unknown or missing value must land on whatever that face has
         *  always drawn rather than on a shape it never had. */
        fun fromPreference(value: String?, default: CoverShape = ROUNDED): CoverShape =
                entries.firstOrNull { it.preferenceValue == value } ?: default
    }
}
