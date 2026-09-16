package com.svartifoss.snfell.common

/** Dimensions in dp, shared by the centered transport faces and their phone previews. */
object PlayerControlGeometry {
    const val METADATA_GAP = 6f

    fun transportDiameter(face: String, screen: Float): Float? = when (face) {
        "expressive" -> if (screen >= 225f) 78f else 62f
        "material" -> screen * .30f
        else -> null
    }

    const val MIN_MINI_SCALE = .78f

    /**
     * The tap target that stands in for a transport the user has hidden, as a fraction of the
     * screen, with the smaller fraction its confirmation ring expands to.
     *
     * Hiding the controls removes the glyph, not the gesture: the centre of the player still
     * toggles playback, still opens the quick panel on a double tap and still opens the face picker
     * on a long press. Every face with no permanent control of its own already keeps a region like
     * this; these two only needed one once their transport could actually go away.
     */
    const val HIDDEN_TRANSPORT_REGION_FRACTION = .52f
    const val HIDDEN_TRANSPORT_PULSE_FRACTION = .34f

    data class TransportLayout(
            val metadataTop: Float,
            val metadataHeight: Float,
            val centerY: Float,
            val diameter: Float,
            val timeCenterY: Float,
            val timeHeight: Float
    )

    /**
     * The metadata band a face gets when its transport is not on screen at all.
     *
     * Larger than the 36f the transport leaves, because there is no longer a ring for the text to
     * stop above: a title that had to shrink or wrap can use the room the control gave back. It is
     * still a ceiling rather than the whole area - the band positions the text, and handing it the
     * entire screen would place two lines at the very top of it.
     */
    const val METADATA_MAX_WITHOUT_TRANSPORT = 72f

    /** The playback-time readout and the gap above it, when there is room for both. */
    const val TIME_HEIGHT = 14f
    const val TIME_GAP = 4f

    /** What the metadata takes when everything fits: a title line, an artist row, and no more. */
    const val METADATA_MAX = 36f
    const val METADATA_SHARE = .45f

    /**
     * One title line, and the point below which the text stops yielding.
     *
     * Matches `EXPRESSIVE_TITLE_LINE_DP`: a 16sp line with its leading. A band narrower than this
     * cannot show a whole line at any size, so shrinking past it buys nothing.
     */
    const val METADATA_MIN = 21f

    /**
     * The smallest the centred transport is allowed to become, in dp.
     *
     * A control is a touch target before it is a decoration, and below the platform minimum it
     * stops being one. The old allocation had no floor at all: the metadata took its 45% share and
     * the transport took whatever remained, so a crowded screen produced a ring a few dp across -
     * still tappable only because the whole centre of the face happens to be.
     */
    const val TRANSPORT_MIN_DIAMETER = 48f

    /** What the allocation decided: the bands, and whether the time survived it. */
    data class TransportBands(
            val metadata: Float,
            val diameter: Float,
            val timeHeight: Float,
            val timeGap: Float
    )

    /**
     * The order things yield in when the band cannot hold everything.
     *
     * One order, written once, instead of a clamp at each draw site. Each face used to solve this
     * for itself and they disagreed, which is why the same settings produced a legible screen on
     * one face and a ring a few dp across on the next.
     *
     *  1. The **chrome** never yields - that is [PlayerChromeLayout]'s promise, and it holds before
     *     this function is reached.
     *  2. The **track time** goes first. It is a readout, not a control, and the bezel progress
     *     ring and the dedicated progress screen both still carry the position.
     *  3. The **metadata** gives up its designed share rather than let the control fall below
     *     [TRANSPORT_MIN_DIAMETER].
     *  4. The **transport** takes what is left, down to that minimum.
     *  5. Below all of it the text wins the remainder, because a screen you cannot read the track
     *     on is worse than a small control on a face whose whole centre is a tap target anyway -
     *     and the title's own shrink/wrap cascade absorbs the rest.
     */
    fun allocateTransportBands(
            area: Float,
            preferredDiameter: Float,
            wantsTime: Boolean
    ): TransportBands {
        val usableArea = (area - METADATA_GAP).coerceAtLeast(0f)
        val keepTime = wantsTime &&
                usableArea - TIME_HEIGHT - TIME_GAP >= METADATA_MIN + TRANSPORT_MIN_DIAMETER
        val timeHeight = if (keepTime) TIME_HEIGHT else 0f
        val timeGap = if (keepTime) TIME_GAP else 0f
        val usable = (usableArea - timeHeight - timeGap).coerceAtLeast(0f)
        val metadata = minOf(METADATA_MAX, usable * METADATA_SHARE)
                .coerceAtMost((usable - TRANSPORT_MIN_DIAMETER).coerceAtLeast(0f))
                .coerceAtLeast(minOf(METADATA_MIN, usable))
        val diameter = minOf(preferredDiameter, (usable - metadata).coerceAtLeast(0f))
        return TransportBands(metadata, diameter, timeHeight, timeGap)
    }

    /**
     * The lowest a foreground row may reach before the centred transport above it stops fitting.
     *
     * Derived from the same minimums as [allocateTransportBands] rather than from the *preferred*
     * transport size, which is what the three copies of this expression used to quote - the row was
     * being scaled back to protect a control that had room to shrink first.
     */
    fun minimumContentBottom(screen: Float): Float =
            screen * .17f + TRANSPORT_MIN_DIAMETER + METADATA_MIN + METADATA_GAP

    /** Allocate disjoint bands; large text is fitted as a whole inside the metadata band. */
    fun transportLayout(face: String, screen: Float, lowerTop: Float, showTime: Boolean,
            metadataPosition: TextBlockPosition = TextBlockPosition.FOLLOW,
            showTransport: Boolean = true): TransportLayout {
        val preferred = transportDiameter(face, screen) ?: 62f
        val top = screen * .17f
        val bottom = minOf(screen - 6f, lowerTop - 6f).coerceAtLeast(top)
        val timeHeight = if (showTime) TIME_HEIGHT else 0f
        val timeGap = if (showTime) TIME_GAP else 0f
        if (!showTransport) {
            // Hiding the controls gives the band back rather than leaving a hole where they were.
            // The metadata keeps its own size and the group centres in what is now free, which is
            // the faithful continuation of a composition whose text was placed above a ring that
            // no longer exists - `follow` cannot mean "stay above nothing".
            val metadata = minOf(METADATA_MAX_WITHOUT_TRANSPORT,
                    (bottom - top - timeHeight - timeGap).coerceAtLeast(0f))
            val group = metadata + timeGap + timeHeight
            val metadataTop = when (metadataPosition) {
                TextBlockPosition.TOP -> top
                TextBlockPosition.BOTTOM -> bottom - group
                else -> top + ((bottom - top) - group) / 2f
            }.coerceIn(top, maxOf(top, bottom - group))
            return TransportLayout(metadataTop, metadata, metadataTop + group / 2f, 0f,
                    metadataTop + metadata + timeGap + timeHeight / 2f, timeHeight)
        }
        val bands = allocateTransportBands(bottom - top, preferred, showTime)
        val metadata = bands.metadata
        val diameter = bands.diameter
        val resolvedTimeHeight = bands.timeHeight
        val resolvedTimeGap = bands.timeGap
        if (metadataPosition == TextBlockPosition.BOTTOM) {
            val metadataTop = bottom - metadata
            val ringBottom = metadataTop - METADATA_GAP - resolvedTimeHeight - resolvedTimeGap
            return TransportLayout(metadataTop, metadata, ringBottom - diameter / 2f, diameter,
                    ringBottom + resolvedTimeGap + resolvedTimeHeight / 2f, resolvedTimeHeight)
        }
        val preferredCenter = screen * .5f
        val center = preferredCenter.coerceIn(
                top + metadata + METADATA_GAP + diameter / 2f,
                maxOf(top + metadata + METADATA_GAP + diameter / 2f,
                        bottom - resolvedTimeHeight - resolvedTimeGap - diameter / 2f))
        val metadataTop = if (metadataPosition == TextBlockPosition.MIDDLE)
            minOf(screen * .5f - metadata / 2f, center - diameter / 2f - METADATA_GAP - metadata)
                    .coerceAtLeast(top) else top
        return TransportLayout(metadataTop, metadata, center, diameter,
                center + diameter / 2f + resolvedTimeGap + resolvedTimeHeight / 2f,
                resolvedTimeHeight)
    }

    data class CoverRailLayout(val artTop: Float, val artHeight: Float,
            val titleTop: Float, val titleHeight: Float)

    /** Cover rails give the footer its own band, reducing artwork before clipping the title. */
    fun coverRailLayout(screen: Float, lowerTop: Float, artTopFraction: Float,
            artBottomFraction: Float, titleTopFraction: Float, titleHeight: Float): CoverRailLayout {
        val artTop = screen * artTopFraction
        val bottom = minOf(screen * .96f, lowerTop - 6f)
        val gap = screen * (titleTopFraction - artBottomFraction)
        val height = minOf(titleHeight, ((bottom - artTop - gap) * .45f).coerceAtLeast(0f))
        val titleTop = minOf(screen * titleTopFraction, bottom - height)
        return CoverRailLayout(artTop, (titleTop - gap - artTop).coerceAtLeast(0f), titleTop, height)
    }

    /** Readability is a hard floor. The foreground adapts to the resulting row, never vice versa. */
    fun miniRowScale(
            face: String,
            screen: Float,
            rowBottom: Float,
            rowHeight: Float,
            bottomAtScale: (Float) -> Float = { rowBottom }
    ): Float {
        if (transportDiameter(face, screen) == null || rowHeight <= 0f) return 1f
        val minimumContentBottom = minimumContentBottom(screen)
        var low = MIN_MINI_SCALE
        var high = 1f
        repeat(20) {
            val scale = (low + high) / 2f
            if (bottomAtScale(scale) - rowHeight * scale >= minimumContentBottom) low = scale else high = scale
        }
        return low
    }
}
