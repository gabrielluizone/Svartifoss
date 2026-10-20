package com.svartifoss.snfell.common

import kotlin.math.min

/**
 * What one queue style is drawn with when nothing else is asked of it.
 *
 * A style is the row's *finish* - its colours, gradients, strokes and marks, worked out in
 * [QueueRowSkins] - plus the outline, cover cut and rhythm it was designed around. The outline and
 * the cover cut are only the style's *defaults* now: [QueueRowShape] and the queue's cover shape
 * replace them on request, and [QueueAppearance] is where the two are reconciled. The rhythm stays
 * the style's own; see [QueueRowShape] for why.
 *
 * Every number here is the one the watch drew before the outline became a separate choice, so a
 * queue left on "follow" renders exactly as it did - the tests pin them for that reason.
 */
data class QueueStyleSpec(
        /** The value stored in [MiscPreferences.WEAR_QUEUE_STYLE]. */
        val token: String,
        /** The outline the style was drawn with, which [QueueRowShape.FOLLOW] resolves to. */
        val outline: QueueRowOutline,
        /** Padding above and below the row's content, in dp; with the row size it sets the height. */
        val verticalPaddingDp: Float,
        /** The gap between two rows, in dp - tight for flat and railed lists, roomy for cards. */
        val spacingDp: Float,
        /**
         * The cover's corner radius as a fraction of its side: 0 is square, 0.5 a circle.
         *
         * A fraction rather than dp because the cover grows with the row size: a fixed radius
         * that made a 30 dp cover a circle would make a 64 dp one a rounded square, silently
         * changing the style's look with the row size.
         */
        val coverCornerFraction: Float,
        /**
         * Whether the row's outline can be seen at all - a fill or a stroke around it. Minimal draws
         * neither, so there is no outline to change and the shape picker is not offered beside it.
         */
        val drawsOutline: Boolean = true,
        /** Fills the whole row with the entry's own cover under a scrim (the Cover family). */
        val fillsWithCover: Boolean = false,
        /** Keeps the sharp thumbnail as well as the cover fill - only the blurred variant, whose
         *  backdrop is soft enough for the two not to read as the same picture twice. */
        val keepsThumbnailOverCover: Boolean = false,
        /** Strings every entry's cover on one line through the queue: the Route style. */
        val route: Boolean = false
) {
    /** Whether the rows lead with the entry's own thumbnail, which is what a cover shape cuts. */
    val drawsThumbnail: Boolean
        get() = !fillsWithCover || keepsThumbnailOverCover
}

/** The registry of queue styles, by the token each one is stored as. */
object QueueStyles {

    const val DEFAULT_TOKEN = "glass"

    /** The style that carries the queue's route; see [QueueShape.routeSegments]. */
    const val ROUTE_TOKEN = "route"

    private fun rounded(radius: Float) = QueueRowOutline.Rounded.uniform(radius)

    private fun spec(
            token: String,
            outline: QueueRowOutline,
            verticalPadding: Float,
            spacing: Float,
            coverCorner: Float,
            drawsOutline: Boolean = true,
            fillsWithCover: Boolean = false,
            keepsThumbnailOverCover: Boolean = false,
            route: Boolean = false
    ) = QueueStyleSpec(
            token, outline, verticalPadding, spacing, coverCorner,
            drawsOutline, fillsWithCover, keepsThumbnailOverCover, route)

    val ALL: List<QueueStyleSpec> = listOf(
            spec("glass", rounded(26f), 12f, 6f, .5f),
            // No fill and no stroke: the keyline is all there is, so no outline to reshape.
            spec("minimal", rounded(0f), 10f, 2f, .067f, drawsOutline = false),
            spec("material", rounded(12f), 14f, 8f, .2f),
            spec("tonal", rounded(28f), 16f, 8f, .5f),
            spec("neon", rounded(18f), 12f, 6f, .3f),
            spec("light", rounded(20f), 13f, 8f, .333f),
            spec("gradient", rounded(22f), 14f, 8f, .367f),
            spec("mono", rounded(14f), 13f, 6f, .233f),
            spec("outline", rounded(16f), 12f, 6f, .267f),
            spec("duotone", rounded(22f), 14f, 8f, .367f),
            spec("contrast", rounded(8f), 13f, 6f, .133f),
            spec("terminal", rounded(0f), 11f, 2f, 0f),
            spec("prism", rounded(22f), 14f, 8f, .267f),
            spec("frost", rounded(24f), 13f, 8f, .4f),
            spec("soft", rounded(30f), 15f, 8f, .45f),
            spec("slab", rounded(10f), 12f, 5f, .167f),
            spec("ink", rounded(24f), 11f, 4f, .3f),
            spec("rail", rounded(6f), 13f, 3f, .067f),
            spec("sunset", rounded(20f), 15f, 9f, .4f),
            spec("bubble", QueueRowOutline.speechBubble(), 16f, 10f, .5f),
            spec("chrome", rounded(12f), 13f, 6f, .2f),
            spec("holo", rounded(26f), 14f, 7f, .367f),
            spec("cover", rounded(26f), 12f, 6f, .5f, fillsWithCover = true),
            spec("cover_blur", rounded(26f), 12f, 6f, .5f,
                    fillsWithCover = true, keepsThumbnailOverCover = true),
            spec("cover_tonal", rounded(26f), 12f, 6f, .5f, fillsWithCover = true),
            // The two legacy size presets. They predate the row-size preference and are no longer
            // offered; an already-synced value still renders at the size its name promised.
            spec("cover_compact", rounded(26f), 8f, 6f, .5f, fillsWithCover = true),
            spec("cover_tall", rounded(26f), 14f, 6f, .5f, fillsWithCover = true),
            // Archived from the picker: it is Cover with tight corners, which the pill shape now
            // says on its own. Kept so a configuration that holds it keeps rendering as it did.
            spec("cover_square", rounded(10f), 12f, 6f, .2f, fillsWithCover = true),
            spec("basalt", QueueRowOutline.Hexagon(QueueRowOutline.BASALT_POINT_DP), 12f, 4f, .5f),
            spec("strata", rounded(6f), 12f, 3f, .2f),
            // Only the playing row has an outline to draw - the soft capsule around "you are
            // here" - and the covers are stations, so they default to circles on the line.
            spec(ROUTE_TOKEN, rounded(QueueRowOutline.STADIUM), 10f, 2f, .5f, route = true))

    private val BY_TOKEN: Map<String, QueueStyleSpec> = ALL.associateBy { it.token }.also {
        check(it.size == ALL.size) { "Queue style tokens must be unique" }
    }

    /** The values that fill the row with the entry's cover - the phone checks these to decide
     *  whether to send larger covers, and the preview to draw them. */
    val COVER_FILL_TOKENS: Set<String> = ALL.filter { it.fillsWithCover }.map { it.token }.toSet()

    /** The style stored as [token], or Glass for an unknown or missing one - the historical
     *  default, and what every renderer has always fallen back to. */
    fun spec(token: String?): QueueStyleSpec = BY_TOKEN[token] ?: BY_TOKEN.getValue(DEFAULT_TOKEN)

    fun isKnown(token: String?): Boolean = token in BY_TOKEN
}

/**
 * A queue style together with the outline and cover cut the user chose for it, reconciled.
 *
 * Each choice is honoured only where it can be seen, and the reconciliation lives here so that
 * the watch, the phone's miniature and the settings screen cannot disagree about it:
 *  - the pill shape is ignored for a style that draws no outline ([QueueStyleSpec.drawsOutline]),
 *    whose rows would look the same whatever it said, and the picker is hidden beside it;
 *  - the cover shape is ignored for a style that fills the row with the cover instead of leading
 *    with a thumbnail, which leaves nothing for it to cut.
 * A choice that does not apply is left stored, not cleared, so going back to a style it suits
 * brings it back.
 */
data class QueueRowAppearance(
        val style: QueueStyleSpec,
        /** The outline the rows are drawn with. */
        val outline: QueueRowOutline,
        /** How the thumbnail is cut. */
        val coverCorners: CoverCorners,
        /** Whether the pill-shape choice has anything to act on with this style. */
        val rowShapeApplies: Boolean,
        /** Whether the cover-shape choice has anything to act on with this style. */
        val coverShapeApplies: Boolean
) {
    val verticalPaddingDp: Float get() = style.verticalPaddingDp
    val spacingDp: Float get() = style.spacingDp
}

object QueueAppearance {

    /** The stored value meaning "the cut the style was drawn with". */
    const val FOLLOW = "follow"

    fun resolve(
            styleToken: String?,
            rowShapeToken: String?,
            coverShapeToken: String?
    ): QueueRowAppearance {
        val style = QueueStyles.spec(styleToken)
        val rowShapeApplies = style.drawsOutline
        val coverShapeApplies = style.drawsThumbnail
        val outline = QueueRowShape.fromPreference(rowShapeToken).outline
                ?.takeIf { rowShapeApplies }
                ?: style.outline
        val corners = coverShape(coverShapeToken)
                ?.takeIf { coverShapeApplies }
                ?.let(CoverCorners::of)
                ?: CoverCorners.uniform(style.coverCornerFraction)
        return QueueRowAppearance(style, outline, corners, rowShapeApplies, coverShapeApplies)
    }

    /**
     * The cover shape stored as [value], or null for "follow the style".
     *
     * Deliberately not [CoverShape.fromPreference], which falls back to a shape of the caller's
     * choosing: here an unknown value has to fall back to the style's own cut, the one thing no
     * single [CoverShape] can stand for.
     */
    fun coverShape(value: String?): CoverShape? =
            CoverShape.entries.firstOrNull { it.preferenceValue == value }

    /** Whether the pill-shape picker has anything to act on beside [styleToken]. */
    fun rowShapeApplies(styleToken: String?): Boolean = QueueStyles.spec(styleToken).drawsOutline

    /** Whether the cover-shape picker has anything to act on beside [styleToken]. */
    fun coverShapeApplies(styleToken: String?): Boolean =
            QueueStyles.spec(styleToken).drawsThumbnail
}

/**
 * The measurements of one queue row in dp, worked out once for the watch and its miniature on the
 * phone: its height, its cover's side, where the cover starts and the side padding of its text.
 *
 * Where the cover starts comes from the outline rather than a constant, because the outline is
 * chosen apart from the cover's cut now - see [QueueRowOutline.coverStart]. Every combination a
 * style was drawn with keeps the [ARTWORK_INSET_DP] it always had.
 */
data class QueueRowLayout(
        val heightDp: Float,
        val artworkDp: Float,
        val coverStartDp: Float,
        val textInsetDp: Float
) {
    /** Where the Route style's line passes through the row: the middle of its cover. */
    val stationCenterDp: Float get() = coverStartDp + artworkDp / 2f

    companion object {
        /** Air between the cover and the row's own edge, top, bottom and left. */
        const val ARTWORK_INSET_DP = 5f

        /** Ceiling on the cover, so a tall row does not crowd its text off a 192 dp screen. */
        const val ARTWORK_MAX_DP = 64f

        /** The content height each row size wraps; the style adds its padding round it. Unknown
         *  values are the normal size, what every install drew before the size was a choice. */
        fun contentHeightDp(rowSize: String?): Float = when (rowSize) {
            "compact" -> 22f
            "tall" -> 52f
            "xtall" -> 78f
            else -> 30f
        }

        /** A list row's cover: the row's height less the inset at top and bottom, capped. */
        fun artworkDp(rowHeightDp: Float): Float =
                min(rowHeightDp - ARTWORK_INSET_DP * 2, ARTWORK_MAX_DP)

        fun of(appearance: QueueRowAppearance, contentHeightDp: Float): QueueRowLayout {
            val height = contentHeightDp + appearance.verticalPaddingDp * 2
            val artwork = artworkDp(height)
            // The width only reaches the outline through its clamps (a corner is held to half the
            // shorter side, a hexagon's point to a third of the width), and a list row is always
            // several times wider than it is tall, so a nominal width answers for every real one.
            val coverStart = appearance.outline.coverStart(
                    width = height * NOMINAL_ROW_ASPECT,
                    height = height,
                    size = artwork,
                    corners = appearance.coverCorners,
                    inset = ARTWORK_INSET_DP,
                    clearance = QueueRowOutline.COVER_CLEARANCE_DP,
                    dp = 1f)
            return QueueRowLayout(height, artwork, coverStart, appearance.outline.textInsetDp)
        }

        private const val NOMINAL_ROW_ASPECT = 4f
    }
}
