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
 * Every entry is one primitive - a rectangle with its own radius at each corner - which is what
 * both renderers already draw cheaply (`AbsoluteRoundedCornerShape` in Compose, `addRoundRect`
 * with eight radii on a Canvas). The asymmetric entries are the mini-button row's leaf, drop,
 * arch, pebble and shield silhouettes carried over to artwork, so the app has one shape language
 * rather than two. The radii are expressed as a *fraction of the cover size* rather than in dp.
 * Carousel's hero card and its two smaller neighbours must read as the same shape, which a fixed
 * radius would break by making the smaller cards look rounder; a single cover elsewhere is one
 * size, but the same fraction keeps every consumer honestly the same shape at very different sizes.
 *
 * Corners are absolute (left/right), not start/end: the preview draws on a Canvas that has no
 * layout direction, and a leaf that mirrored in a right-to-left locale on the watch alone would be
 * the preview disagreeing with the wrist.
 *
 * One entry is not a rectangle at all: [SUPERELLIPSE] is a curve that bends the whole way round
 * (see [Superellipse]), which no radius at any corner can draw. It still carries a corner fraction
 * so a consumer that has not learned about it draws something close rather than nothing, but every
 * renderer here checks [isSuperellipse] first and draws the real outline.
 */
enum class CoverShape(
        val preferenceValue: String,
        val topLeft: Float,
        val topRight: Float,
        val bottomRight: Float,
        val bottomLeft: Float,
        /** Drawn as a [Superellipse] instead of a rounded rectangle; the corners are then only the
         *  nearest rounded-rectangle stand-in. */
        val isSuperellipse: Boolean = false
) {
    /** A generously rounded square - the Carousel rail's reference look. */
    ROUNDED("rounded", 0.08f),

    /** Hard corners - the most "album sleeve" of them. */
    SQUARE("square", 0f),

    /** Softer than [ROUNDED] without becoming a circle. */
    SQUIRCLE("squircle", 0.18f),

    /** The continuous-curvature outline of a modern app icon: rounder than [SQUIRCLE] and not a
     *  rectangle with big corners, because it bends along the whole side - see [Superellipse]. */
    SUPERELLIPSE("superellipse", 0.30f, 0.30f, 0.30f, 0.30f, isSuperellipse = true),

    /** Full circle. Crops the most artwork, so on Carousel it is a deliberate choice; on Note it is
     *  the default, because there the cover reads as a contact photo beside a message. */
    CIRCLE("circle", 0.5f),

    /** Two opposite corners swept round, the other two nearly square. */
    LEAF("leaf", 0.42f, 0.06f, 0.42f, 0.06f),

    /** A teardrop: round everywhere but the top-left corner, which stays nearly sharp. */
    DROP("drop", 0.06f, 0.5f, 0.5f, 0.5f),

    /** A round-topped window: a full semicircle over a square base. */
    ARCH("arch", 0.5f, 0.5f, 0.06f, 0.06f),

    /** Four different corners, so it reads as a worn stone rather than as a geometric figure. */
    PEBBLE("pebble", 0.42f, 0.24f, 0.36f, 0.14f),

    /** The arch turned over: a square top on a fully rounded base. */
    SHIELD("shield", 0.06f, 0.06f, 0.5f, 0.5f);

    constructor(preferenceValue: String, corner: Float) :
            this(preferenceValue, corner, corner, corner, corner)

    /** Whether all four corners share one radius - such a shape can be drawn by the simplest
     *  primitive each renderer has, and a circle by its dedicated one. Never true of a
     *  [Superellipse], whose uniform stand-in corners are not what is drawn. */
    val isUniform: Boolean
        get() = !isSuperellipse && topLeft == topRight && topRight == bottomRight &&
                bottomRight == bottomLeft

    /**
     * The eight Canvas radii for a cover [size] pixels across, in `Path.addRoundRect` order:
     * x then y for top-left, top-right, bottom-right and bottom-left. Every corner is circular.
     */
    fun radii(size: Float): FloatArray = floatArrayOf(
            topLeft * size, topLeft * size,
            topRight * size, topRight * size,
            bottomRight * size, bottomRight * size,
            bottomLeft * size, bottomLeft * size)

    companion object {
        /**
         * What the superellipse was stored as before it had a name of its own: it shipped in 4.0
         * under a trademarked label, so installs, backups and saved themes still carry the old
         * token. Read as [SUPERELLIPSE] everywhere ([parse]) and rewritten where it is stored
         * ([migrate]); nothing new is ever written under it.
         */
        const val RETIRED_VALUE = "samsung"

        /** The shape stored as [value], the retired token included, or null when it names none.
         *  Not [fromPreference]: the queue needs "unknown" to mean "follow the style". */
        fun parse(value: String?): CoverShape? {
            val token = value?.trim()
            if (token == RETIRED_VALUE) return SUPERELLIPSE
            return entries.firstOrNull { it.preferenceValue == token }
        }

        /** The value a stored [value] should be rewritten to, or null when it needs nothing - pure
         *  and null for the ordinary case so a sweep writes only what changed. */
        fun migrate(value: String?): String? =
                if (value?.trim() == RETIRED_VALUE) SUPERELLIPSE.preferenceValue else null

        /** [default] is the caller's, not a property of the vocabulary: Carousel rests on a card
         *  and Note on a disc, and an unknown or missing value must land on whatever that face has
         *  always drawn rather than on a shape it never had. */
        fun fromPreference(value: String?, default: CoverShape = ROUNDED): CoverShape =
                parse(value) ?: default
    }
}
