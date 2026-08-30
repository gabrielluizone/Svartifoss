package com.svartifoss.snfell.common

/**
 * The numbers a face's composition is built from, shared by the Wear renderer and the phone's
 * miniature.
 *
 * These existed twice - once in each face and once in `WatchPreviewView` - because `mobile` cannot
 * depend on `wear`, and the standing rule was to keep the copies in step by hand. That rule was
 * never the only option: `common` is a dependency of *both* modules, so a number both sides need
 * can simply live here and stop being two numbers. What actually cannot be shared is the drawing -
 * Compose on one side, `Canvas` on the other - and none of that is in this file.
 *
 * Everything here is a fraction of the screen, a proportion, or a count. Nothing is a `Dp` or a
 * `Color`: `common` has no Compose dependency, and a raw value keeps each renderer free to apply
 * it in its own units - which is also why the sizes below are plain "in dp" floats rather than
 * typed ones.
 *
 * A face's own private constants stay in that face when only it uses them. This is for the ones
 * the preview has to reproduce.
 */
object FaceGeometry {

    /** The original View-based player. Its typography is still a face contract even though the
     *  layout itself is inflated from XML, so the phone miniature must not invent a second scale. */
    object Classic {
        const val TITLE_MAX_SP = 46f
        const val TITLE_MIN_SP = 25f
        const val ARTIST_MAX_SP = 16f
        const val ARTIST_MIN_SP = 9f
        const val ARTIST_MAX_LINES = 2
        /** Classic's ImageView is 1.1× the artist text size before the user's icon scale. */
        const val SOURCE_ICON_SIZE_ARTIST_FACTOR = 1.1f
        /** Its sibling margin stays relative to the text, not to the scaled icon. */
        const val SOURCE_ICON_END_MARGIN_ARTIST_FACTOR = .28f
        const val CLOCK_SP = 15f
        /** Layout top rather than a Canvas baseline: every awake player clock starts here. */
        const val CLOCK_TOP_PADDING_DP = 5f
        const val TRACK_TIME_SP = 13f

        /** `BoxInsetLayout.FACTOR`: the inset on each boxed edge of a round display. */
        const val ROUND_BOX_INSET_FRACTION = .146447f

        /** `music_screen_text_margin` on a rectangular display (the round resource is zero). */
        const val SQUARE_TEXT_MARGIN_DP = 30f
    }

    /** The full-bleed cover rail. Every text anchor derives from these rather than being tuned
     *  separately, so moving or resizing the cover keeps the artist and title attached to it. */
    object Carousel {
        const val CARD_FRACTION = .52f
        const val RAIL_CENTER = .475f
        const val ARTIST_ROW_FRACTION = .075f
        const val CARD_TOP = RAIL_CENTER - CARD_FRACTION / 2f
        const val CARD_BOTTOM = RAIL_CENTER + CARD_FRACTION / 2f
        const val ARTIST_TOP = CARD_TOP - ARTIST_ROW_FRACTION
        const val TITLE_TOP = CARD_BOTTOM + .014f
        const val NEAR_SHADE = .46f
        const val FAR_SHADE = .68f

        /** The playing app's mark beside the artist line, in dp. */
        const val SOURCE_ICON_SIZE_DP = 12f
    }

    /** The session as a messaging thread. */
    object Chat {
        const val INCOMING_LIGHTNESS = .17f
        const val OUTGOING_LIGHTNESS = .32f

        const val SIDE_PADDING_FRACTION = .09f
        const val TOP_PADDING_FRACTION = .085f
        const val BOTTOM_PADDING_FRACTION = .05f

        const val DAY_CHIP_TEXT_SP = 11f
        const val DAY_CHIP_HORIZONTAL_PADDING_DP = 12f
        const val DAY_CHIP_VERTICAL_PADDING_DP = 4f
        const val DAY_CHIP_ALPHA = .85f
        const val DAY_CHIP_TEXT_ALPHA = .75f
        const val DAY_TO_MESSAGE_GAP_DP = 6f

        const val CURRENT_BUBBLE_TITLE_SP = 15f
        const val CURRENT_BUBBLE_ARTIST_SP = 12f
        const val CURRENT_BUBBLE_MAX_WIDTH_DP = 210f
        const val CURRENT_BUBBLE_HORIZONTAL_PADDING_DP = 11f
        const val CURRENT_BUBBLE_VERTICAL_PADDING_DP = 6f
        const val CURRENT_TO_VOICE_GAP_DP = 4f

        const val BUBBLE_CORNER_DP = 18f
        const val BUBBLE_TAIL_CORNER_DP = 6f
        const val VOICE_BUBBLE_HEIGHT_DP = 58f
        const val VOICE_BUBBLE_HORIZONTAL_PADDING_DP = 8f
        const val AVATAR_SIZE_DP = 34f
        const val AVATAR_TO_WAVE_GAP_DP = 8f
        const val WAVE_TO_GLYPH_GAP_DP = 7f
        const val WAVE_HEIGHT_DP = 18f
        /** The one bar at the playhead breathes while playback is active. */
        const val WAVE_PLAYHEAD_PULSE_MIN_SCALE = .82f
        const val WAVE_PLAYHEAD_PULSE_HALF_CYCLE_MS = 620
        const val PLAY_GLYPH_SIZE_DP = 31f
        const val PLAY_GLYPH_MARK_DP = 13f

        const val TIME_TOP_PADDING_DP = 3f
        const val TIME_END_PADDING_DP = 4f
        const val TIME_TO_TICKS_GAP_DP = 4f
        const val TIME_TEXT_SP = 10f
        const val TICK_WIDTH_DP = 13f
        const val TICK_HEIGHT_DP = 8f
        const val VOICE_TO_ACTION_GAP_DP = 5f

        const val ACTION_DIAMETER_FRACTION = .215f
        const val ACTION_GAP_FRACTION = .05f
        const val ACTION_MIN_DIAMETER_DP = 32f
        const val ACTION_MIN_DESIGNED_DIAMETER_DP = 38f
        const val ACTION_MAX_DESIGNED_DIAMETER_DP = 50f
        const val ACTION_GLYPH_FRACTION = .48f

        /**
         * The voice note's waveform.
         *
         * A fixed shape rather than real amplitudes: the bubble is a *representation* of a voice
         * message, and sampling the audio to draw it would be a decoding job for decoration.
         */
        val WAVE_PATTERN: List<Float> = listOf(
                .30f, .55f, .80f, .45f, 1f, .65f, .35f, .75f, .50f, .90f,
                .40f, .70f, .25f, .60f, .85f, .45f, .30f, .55f
        )
    }

    /** Full-bleed cover with the text block grounded over its lower scrim. */
    object Immersive {
        const val SIDE_PADDING_FRACTION = .10f
        const val BOTTOM_PADDING_FRACTION = .13f

        const val TITLE_SP = 17f
        const val TITLE_LINE_HEIGHT_SP = 19f

        const val ARTIST_TOP_PADDING_DP = 4f
        const val ARTIST_SP = 13f
        const val ARTIST_LINE_HEIGHT_SP = 15f
        const val SOURCE_ICON_SIZE_DP = 15f

        const val TRACK_TIME_TOP_PADDING_DP = 5f
        const val TRACK_TIME_SP = 11f
        const val TRACK_TIME_LINE_HEIGHT_SP = 12f
    }

    /** The notification-card layout: cover above, album-coloured panel below. */
    object Split {
        /** Where the cover stops and the panel starts. Two thirds: the artwork is the subject and
         *  the panel only has to hold two lines of text. */
        const val SEAM_FRACTION = .66f

        /** How much of the blurred artwork shows through the panel colour. Composited as artwork
         *  *over* an opaque panel, so the panel's luminance stays anchored to its own colour -
         *  which is the value the text colour is decided from. */
        const val PANEL_ART_ALPHA = .34f
        const val PANEL_LIGHTNESS = .40f

        /** The source mark's footprint on the seam. */
        const val BADGE_FRACTION = .21f
    }

    /** The minimal face: a small cover disc and one centred sentence. */
    object Note {
        const val COVER_FRACTION = .21f

        /**
         * Line ceiling for the `Artist: Title` sentence.
         *
         * The user's Title text behaviour decides how the sentence overflows; this is only how
         * much room the composition has for the answer, so a `wrap5` selection stops here rather
         * than growing down into the track-time readout. Shared because the miniature has to plan
         * the same wrap the wrist does - and because the side inset both sides ask
         * `RoundScreenText` for is measured at exactly this depth.
         */
        const val MAX_LINES = 3
    }

    /**
     * Ribbon: a tall centre cover framed by four vertical queue-art capsules.
     *
     * The outer capsules deliberately extend beyond the screen edge. They read as a continuous
     * cover rail rather than four unrelated pills, while the inner pair brackets the artwork.
     */
    object Ribbon {
        const val COLUMN_WIDTH_FRACTION = .16f
        const val COLUMN_HEIGHT_FRACTION = .44f
        const val COLUMN_CORNER_FRACTION = .50f

        const val CENTER_COVER_WIDTH_FRACTION = .32f
        const val CENTER_COVER_HEIGHT_FRACTION = .44f
        /** Empty space between any two adjacent queue/hero covers. */
        const val COVER_GAP_FRACTION = .02f

        /** All five covers share the dial's vertical centre instead of sitting in a lower band. */
        const val COLUMN_TOP_FRACTION = (1f - COLUMN_HEIGHT_FRACTION) / 2f
        const val CENTER_COVER_TOP_FRACTION = (1f - CENTER_COVER_HEIGHT_FRACTION) / 2f

        /**
         * The two rail positions are derived from the hero cover and the one shared gap. This
         * keeps outer-to-inner and inner-to-hero spacing exactly equal on both sides of the dial.
         */
        const val INNER_COLUMN_CENTER_X = .5f - CENTER_COVER_WIDTH_FRACTION / 2f -
                COVER_GAP_FRACTION - COLUMN_WIDTH_FRACTION / 2f
        const val OUTER_COLUMN_CENTER_X = INNER_COLUMN_CENTER_X - COLUMN_WIDTH_FRACTION -
                COVER_GAP_FRACTION

        /** Softer than a standard rounded rectangle, but not a full capsule. */
        const val CENTER_COVER_CORNER_FRACTION = .34f

        const val ARTIST_TOP_FRACTION = .16f
        const val TITLE_TOP_FRACTION = .77f

        /**
         * The designed line heights of the two text bands, in dp.
         *
         * They exist because the side inset is *derived* rather than tuned: a block this low on a
         * round dial is narrowest at its own bottom edge, so how wide it may be depends on how far
         * down it reaches, which is a line height times a line count (see [RoundScreenText]). The
         * face used to inset both bands by a flat .13 of the dial and the preview by .12/.11 -
         * three hand-picked numbers for one question, and all three too wide for the title's depth,
         * which is where a round screen takes the ends of a line away first.
         */
        const val TITLE_LINE_HEIGHT_DP = 29f
        const val ARTIST_LINE_HEIGHT_DP = 22f

        /** Ceiling handed to [RoundScreenText.linesThatFit]; the glass usually allows fewer. */
        const val TITLE_MAX_LINES = 2

        /**
         * The playback hairline, centred in the gap the composition already leaves between the
         * cover rail and the title. Derived from its two neighbours so it stays in that gap if
         * either moves, and sized to the hero cover: it belongs to the track in the middle, not to
         * the screen.
         */
        const val PROGRESS_CENTER_FRACTION =
                (COLUMN_TOP_FRACTION + COLUMN_HEIGHT_FRACTION + TITLE_TOP_FRACTION) / 2f
        const val PROGRESS_WIDTH_FRACTION = CENTER_COVER_WIDTH_FRACTION
        const val PROGRESS_THICKNESS_DP = 3f

        /** Canvas baselines, expressed against the full square watch canvas. */
        const val ARTIST_BASELINE_FRACTION = .25f
        const val TITLE_BASELINE_FRACTION = .86f
        const val CLOCK_BASELINE_FRACTION = .12f
    }

    /**
     * Frame: a rounded tonal card, artist chip and wide cover inset inside the dial.
     *
     * Coordinates describe the card and its content as fractions of the square watch canvas so
     * the Canvas preview and the Compose implementation retain the same silhouette.
     */
    object Frame {
        /**
         * The card is balanced around the dial centre, not dropped toward the lower bezel.
         *
         * At a .10 inset the card's *unrounded* corners land exactly on the circle, which leaves
         * the drawn shape reaching 92% of the radius - inside the glass, but with no room for a
         * device whose bezel eats the last few percent. The extra 1.5% buys that margin back
         * without visibly narrowing the card.
         */
        const val CARD_INSET_FRACTION = .115f
        const val CARD_TOP_FRACTION = .205f
        const val CARD_BOTTOM_FRACTION = .795f
        const val CARD_CORNER_FRACTION = .17f
        const val CONTENT_INSET_FRACTION = .04f

        /** The chip keeps the same margin from the card's top edge that it keeps from its side. */
        const val ART_TOP_FRACTION = CARD_TOP_FRACTION + CONTENT_INSET_FRACTION
        const val TITLE_TOP_FRACTION = .36f
        const val ARTWORK_TOP_FRACTION = .50f

        /**
         * The cover's bottom edge, one [CONTENT_INSET_FRACTION] clear of the card - the same
         * margin it keeps on the sides.
         *
         * It used to stop .02 short of the card, half of its own side inset, which put its bottom
         * corners *inside the card's corner radius*: the Compose face clips its content to the
         * card, so the cover came out with its corners shaved off, while the Canvas preview does
         * not clip and drew it poking out over the card's edge. Neither is the composition, and
         * the two disagreed about which wrong thing to show. [cardCornerClearance] is what pins
         * the real invariant rather than the number.
         */
        const val ARTWORK_BOTTOM_FRACTION = CARD_BOTTOM_FRACTION - CONTENT_INSET_FRACTION
        /**
         * The cover's corner radius, as a fraction of the dial - **not** of the cover.
         *
         * Concentric with the card: an inner box inset by [CONTENT_INSET_FRACTION] inside a corner
         * of radius `r` looks parallel to it at radius `r - inset`, and anything smaller reads as a
         * rectangle sitting in a rounded box rather than as part of it. The cover used to take .14
         * of its own shorter side, which on a wide crop is barely a third of that - a visibly
         * squarer corner a few dp from the card's.
         *
         * Clamped to half the cover's height so a shorter crop degrades to a capsule end rather
         * than to an inverted corner.
         */
        fun artworkCornerFraction(): Float {
            val cardWidth = 1f - CARD_INSET_FRACTION * 2f
            val cardHeight = CARD_BOTTOM_FRACTION - CARD_TOP_FRACTION
            val cardCorner = minOf(cardWidth, cardHeight) * CARD_CORNER_FRACTION
            val artHeight = ARTWORK_BOTTOM_FRACTION - ARTWORK_TOP_FRACTION
            return (cardCorner - CONTENT_INSET_FRACTION).coerceIn(0f, artHeight / 2f)
        }

        /**
         * How far the cover's nearest corner sits inside the card's rounded corner, as a fraction
         * of the dial. Positive means clear; zero means touching.
         *
         * A rounded rectangle's corner curves *away* from its own bounding box, so an inset that
         * looks generous against the card's flat edges can still be outside the arc. This is the
         * only place that arithmetic exists, and both renderers inherit it by reading the
         * fractions above.
         */
        fun cardCornerClearance(): Float {
            val cardWidth = 1f - CARD_INSET_FRACTION * 2f
            val cardHeight = CARD_BOTTOM_FRACTION - CARD_TOP_FRACTION
            val corner = minOf(cardWidth, cardHeight) * CARD_CORNER_FRACTION
            // The card's bottom-left corner arc, and the cover's bottom-left corner against it.
            val arcCenterX = CARD_INSET_FRACTION + corner
            val arcCenterY = CARD_BOTTOM_FRACTION - corner
            val coverX = CARD_INSET_FRACTION + CONTENT_INSET_FRACTION
            val coverY = ARTWORK_BOTTOM_FRACTION
            if (coverY <= arcCenterY) return coverX - CARD_INSET_FRACTION
            val dy = coverY - arcCenterY
            if (dy >= corner) return -1f
            val arcX = arcCenterX - kotlin.math.sqrt(corner * corner - dy * dy)
            return coverX - arcX
        }

        /**
         * The title band holds exactly one line, and the face says so instead of clipping.
         *
         * The card has room for a chip, a title and a cover, and the arithmetic does not leave a
         * second title line anywhere to go: the band is [TITLE_TOP_FRACTION]..[ARTWORK_TOP_FRACTION]
         * of a 192dp dial, which is one line at the design size and still short of two at
         * [TITLE_MIN_TEXT_SIZE_SP]. Passed to the shared title renderer as a ceiling so a "wrap3"
         * or a two-line "smart" title ellipsizes on its line rather than growing down into the
         * cover - the artwork below it is positioned absolutely and cannot move out of the way.
         */
        const val TITLE_MAX_LINES = 1

        /** Awake metadata proportions, shared by the Compose face and Canvas preview. */
        const val ARTIST_TEXT_SIZE_SP = 11f
        const val ARTIST_ICON_SIZE_DP = 14f
        const val ARTIST_HORIZONTAL_PADDING_DP = 8f
        const val ARTIST_VERTICAL_PADDING_DP = 4f
        const val TITLE_TEXT_SIZE_SP = 20f
        const val TITLE_MIN_TEXT_SIZE_SP = 12f

        /** The elapsed/total readout shares the chip's row, right-aligned - the one band inside
         *  the card with space to spare, and where a notification puts its timestamp. */
        const val TRACK_TIME_TEXT_SIZE_SP = 10f

        /** The played fraction, along the cover's own bottom edge. A card has no spare row for a
         *  progress bar, and the cover is the one element wide enough to carry one. */
        const val PROGRESS_THICKNESS_DP = 3f
    }

    /** The lyric face: previous, current and next line. */
    object Verse {
        const val BAND_TOP = 0.28f
        const val BAND_BOTTOM = 0.80f

        /** The block is centred within the band, not on the screen - the band reaches into the
         *  strip the composition leaves empty below the words. */
        const val BAND_CENTER = (BAND_TOP + BAND_BOTTOM) / 2f
    }

    /** The Material 3 Expressive controls: the cookie play/pause button and its contour ring. */
    object Expressive {
        const val COOKIE_LOBES = 10
        const val COOKIE_SOFTNESS = 0.55f
        const val COOKIE_MODULATION = 0.05f
        const val RING_MODULATION = 0.03f
        const val RING_GAP_DEGREES = 7f
    }

    /** The detail table: how much of the screen it may take, and what a line costs it. */
    object Metadata {
        const val TABLE_HEIGHT_FRACTION = 0.42f
        const val ROW_HEIGHT_DP = 13f
        const val MIN_ROWS = 4
        const val MAX_ROWS = 12
    }
}
