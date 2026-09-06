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

    /**
     * The 2017 original, reproduced: matejdro's WearMusicCenter, which this app is a fork of.
     *
     * It is a *View* face rather than a Compose one, and that is the whole reason it can exist at
     * all. Its four quadrant hint icons are the element that made the original screen readable at
     * a glance, and they live in the host's [FourWayTouchLayout] - which `applyScreenThemeNow`
     * forces `GONE` for every Compose face. Rebuilt in Compose this face would have lost the one
     * thing it is here to preserve, so it shares Classic's View presentation and overrides only
     * the geometry of the text block.
     *
     * That geometry is the single compositional difference from [Classic], and it is not
     * expressible as a preference: the original stacked artist over title in two *proportional
     * bands* filling the whole screen, each autosizing to fill its own band, where Classic centres
     * a wrap-content block at fixed sizes. Everything else the original did - white text, no
     * progress, no mini buttons, the evenly darkened cover - is a setting, and ships as this
     * face's per-face defaults instead of being welded in here.
     */
    object Matejdro {
        /** `layout_weight` on the artist band: the upper third of the text area. */
        const val ARTIST_BAND_WEIGHT = 1f

        /** `layout_weight` on the title band: the lower two thirds. The title is the subject. */
        const val TITLE_BAND_WEIGHT = 2f

        /** Fraction of the text area the artist band occupies, derived from the two weights so a
         *  renderer that thinks in fractions cannot disagree with one that thinks in weights. */
        const val ARTIST_BAND_FRACTION =
                ARTIST_BAND_WEIGHT / (ARTIST_BAND_WEIGHT + TITLE_BAND_WEIGHT)

        /** Fraction of the text area the title band occupies. */
        const val TITLE_BAND_FRACTION = 1f - ARTIST_BAND_FRACTION

        /**
         * Upper bound for a title's wrapping search on the band face.
         *
         * This is intentionally a generous ceiling, not a promise that every title gets this
         * many lines. The weighted title band and the autosize floor remain the real constraints;
         * the ceiling only prevents the Classic XML default (`maxLines=2`) from truncating a long
         * Matejdro title before that band can decide whether three, four, or more lines fit.
         */
        const val TITLE_MAX_LINES = 20

        /**
         * The platform's own `autoSizeTextType="uniform"` bounds, which is what the original
         * declared: it set neither `autoSizeMinTextSize` nor `autoSizeMaxTextSize`, so both lines
         * were free to grow to whatever their band could hold. Quoted explicitly rather than left
         * implicit because the phone miniature has no TextView to inherit them from, and a
         * miniature that invents its own ceiling stops being a preview of this face.
         */
        const val AUTOSIZE_MIN_SP = 12f
        const val AUTOSIZE_MAX_SP = 112f
        const val AUTOSIZE_STEP_SP = 1f

        /**
         * The text area is [Classic]'s, and deliberately carries no margin of its own.
         *
         * This was got wrong first time, from reading `values/dimens.xml` alone: the original's
         * `music_screen_text_margin` is 30dp there but **0dp** in `values-round`, so on a round
         * watch it laid its bands out inside the `BoxInsetLayout` inset and nothing else. Forcing
         * 30dp on every display *added* that margin on top of the ~14.6% inset, which is what made
         * every line visibly smaller than the face it reproduces. There is no third value to
         * declare here: the block is the same block, so it uses [Classic.ROUND_BOX_INSET_FRACTION]
         * and [Classic.SQUARE_TEXT_MARGIN_DP] like Classic does, and only the *division* of that
         * area into bands belongs to this face.
         */

        /**
         * The original set `textStyle="bold"` on its title and **nothing** on its artist, so the
         * two lines differ in weight as well as size - which is a large part of what the screen
         * reads like. This app's Classic layout hardcodes bold on both, and its identity weight
         * (400) is defined as "keep what the face designed", so the face has to state its design
         * rather than inherit Classic's. The user's own weight control still overrides it.
         */
        const val ARTIST_DESIGNED_BOLD = false

        /**
         * `android:alpha` on the original's full-screen album art ImageView.
         *
         * Reproduced as a darkening layer rather than by fading the bitmap: this app composites the
         * cover through the background stack, where the equivalent of art at a third brightness is
         * an even black filter at two thirds. [DIM_STRENGTH_PERCENT] carries that conversion.
         */
        const val COVER_ALPHA = .333f

        /**
         * `album_art_dim_strength` reproducing [COVER_ALPHA] through `FULL_FILTER`.
         *
         * That style paints black at `.55 * intensity`, and the alpha which leaves the artwork at
         * [COVER_ALPHA] is `1 - COVER_ALPHA`, so the intensity is `(1 - .333) / .55` ≈ 1.21. Stored
         * as the percent the preference actually holds; a default only, adjustable like any other.
         */
        const val DIM_STRENGTH_PERCENT = 121
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

        /** The uppercase rail text is letterspaced; the ambient variant keeps the same tracking. */
        const val TITLE_TRACKING_SP = 0.12f

        /**
         * The always-on variant: one card, no neighbours, and the text moved below it.
         *
         * Its own numbers rather than the awake ones. With the rail gone the single card is free
         * to sit where one cover reads best instead of where the middle of five did, and the text
         * drops to the foot of the screen it no longer has to share.
         */
        object Ambient {
            const val CARD_FRACTION = .5f
            /** How far above centre the lone card sits, leaving the text the lower third. */
            const val CARD_RISE_FRACTION = .07f
            const val CARD_CORNER_DP = 12f
            const val TEXT_BOTTOM_FRACTION = .16f
            const val TITLE_SP = 13f
            const val TITLE_ALPHA = .82f
            const val ARTIST_SP = 10f
            const val ARTIST_ALPHA = .48f
        }
    }

    /**
     * The metadata blocks of the curated player faces, as the two renderers both need them.
     *
     * These faces have no geometry object of their own - their sizes are literals at each draw
     * site, mirrored by hand in `WatchPreviewView` - and that was tolerable while nothing had to
     * reason about *where a block sits* rather than merely what is in it. The placement controls
     * changed that: keeping a moved block inside a round screen means measuring the chord at the
     * block's real depth, so the depth and the element heights stop being drawing details and
     * become shared inputs. Only the values that feed `blockLineInsets` live here; the fonts,
     * colours and spacings each face composes with stay at their draw sites.
     *
     * `*_TOP_FRACTION` / `*_BOTTOM_FRACTION` are the face's own designed distance from that edge
     * to the near edge of its block. The `*_DP` heights are what each element occupies in the
     * stack, in draw order, with the gap above an element folded into it - a gap and a taller
     * element bind the chord identically (see `RoundScreenText.lineSideInsets`).
     */
    object CuratedText {
        /** Artist row above the title, hung from the top of the screen. */
        const val VINYL_TOP_FRACTION = .14f
        const val VINYL_ARTIST_ROW_DP = 11f
        const val VINYL_TITLE_ROW_DP = 15f

        /** Poster and Studio centre one title-anchored block; both are 72% of the screen wide. */
        const val POSTER_TITLE_LINE_DP = 24f
        const val POSTER_ARTIST_ROW_DP = 16f
        const val STUDIO_TITLE_LINE_DP = 17f
        const val STUDIO_ARTIST_ROW_DP = 15f

        const val HALO_TOP_FRACTION = .145f
        const val HALO_TITLE_LINE_DP = 16f
        /** The tonal pill adds 2dp of padding either side of its 10dp glyph, plus a 3dp gap. */
        const val HALO_ARTIST_ROW_DP = 17f

        const val ECLIPSE_TOP_FRACTION = .15f
        const val ECLIPSE_TITLE_LINE_DP = 17f
        const val ECLIPSE_ARTIST_ROW_DP = 13f

        const val SPECTRUM_TOP_FRACTION = .15f
        const val SPECTRUM_TITLE_LINE_DP = 14f
        const val SPECTRUM_ARTIST_ROW_DP = 13f

        const val MATERIAL_TOP_FRACTION = .17f
        const val MATERIAL_TITLE_LINE_DP = 21f
        const val MATERIAL_ARTIST_ROW_DP = 15f

        /** Depth grounds its block like Immersive, a little higher off the floor. */
        const val DEPTH_BOTTOM_FRACTION = .15f
        const val DEPTH_TITLE_LINE_DP = 18f
        const val DEPTH_ARTIST_ROW_DP = 16f

        /** Aurora leads with the artist, in a block centred inside its card. */
        const val AURORA_ARTIST_ROW_DP = 11f
        const val AURORA_TITLE_LINE_DP = 23f
    }

    /** The session as a messaging thread. */
    object Chat {
        /**
         * Was .62 - the largest center-tap region outside Frame's opaque card - and large enough
         * to reach into the diagonal quadrant taps' apex near the vertical center line, stealing
         * the left/right quadrant gestures that live right beside it. The bubbles aren't clickable
         * so the region still needs to be generous, just not more so than a face with real
         * interactive content (the hosted action row) needs.
         */
        const val CENTER_REGION_FRACTION = .52f

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

        /**
         * The always-on variant: the thread reduced to one outlined bubble.
         *
         * The history bubbles are dropped rather than outlined - on an always-on screen they are
         * stale by definition - so the one that remains is centred and carries its own padding and
         * type sizes instead of the thread's bottom-anchored ones.
         */
        object Ambient {
            const val SIDE_PADDING_FRACTION = .14f
            const val BUBBLE_MAX_WIDTH_DP = 190f
            const val BUBBLE_CORNER_DP = 12f
            const val BUBBLE_TAIL_CORNER_DP = 3f
            const val BUBBLE_BORDER_DP = 1f
            const val BUBBLE_HORIZONTAL_PADDING_DP = 10f
            const val BUBBLE_VERTICAL_PADDING_DP = 6f
            const val TITLE_SP = 13f
            const val ARTIST_SP = 11f
            const val ARTIST_ALPHA = .7f
        }
    }

    /** Full-bleed cover with the text block grounded over its lower scrim. */
    object Immersive {
        const val SIDE_PADDING_FRACTION = .10f

        /**
         * How far the block's last line stops short of the bottom edge.
         *
         * The face is the cover plus one grounded block, so this number *is* the composition: too
         * much of it and the text floats in the middle of the picture with an empty band beneath
         * it, which is what .13 produced. It sits close to the clock's own inset at the top so the
         * two read as a matched pair of margins rather than as text that stopped early.
         *
         * The floor is the round glass, not the rectangle: the block is [SIDE_PADDING_FRACTION]
         * from each side, so 80% of the diameter wide, and the chord only holds that down to about
         * 80% depth. Measured at the glyph band rather than the line box, the artist - the widest
         * line that can reach the full width - still lands inside it here. The track time below it
         * sits deeper than the chord would hold, as it already did, because it is a dozen
         * characters centred in a box it never fills.
         */
        const val BOTTOM_PADDING_FRACTION = .085f

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

    /**
     * The Artist face: the performer's own picture, their name, then the track.
     *
     * Deliberately a near-sibling of [Immersive] - full-bleed artwork with a grounded text block -
     * rather than a new set of proportions, because the difference between the two faces is which
     * line is the subject, not how the screen is divided. What changes here is the hierarchy: the
     * artist runs at title size with the source glyph in front of it, and the track sits below at
     * what would elsewhere be the artist's size.
     */
    object Artist {
        const val SIDE_PADDING_FRACTION = .11f
        const val EDGE_PADDING_FRACTION = .13f

        /** The performer's name, and the largest type on the face. */
        const val NAME_SP = 18f
        const val NAME_LINE_HEIGHT_SP = 20f

        /**
         * The most lines the name may wrap to before the block is simply too tall for the band it
         * sits in.
         *
         * A ceiling rather than the answer: `RoundScreenText.linesThatFit` narrows it further
         * whenever the block has been placed somewhere the chord cannot hold that many lines, which
         * on a round screen the top and bottom bands routinely cannot.
         */
        const val NAME_MAX_LINES = 3

        /**
         * The centred square that takes play/pause taps, as a fraction of the screen.
         *
         * Deliberately a fraction and never the whole screen: this region *consumes* touches, so a
         * full-screen one silently disables the quadrant taps, the configured swipes and the
         * mini-button row that sit under the face. Sized to stop clear of the bottom band the
         * buttons occupy and of the four edges the gestures need.
         */
        const val CENTER_REGION_FRACTION = 0.58f
        const val CENTER_PULSE_FRACTION = 0.38f

        /** The mark in front of the name. Sized to the cap height of [NAME_SP] rather than to the
         *  line box, so it reads as part of the name rather than as a button beside it. */
        const val SOURCE_ICON_SIZE_DP = 14f
        const val SOURCE_ICON_GAP_DP = 5f

        /** The track, one step down and directly beneath the name. */
        const val TRACK_TOP_PADDING_DP = 3f
        const val TRACK_SP = 13f
        const val TRACK_LINE_HEIGHT_SP = 15f

        const val TRACK_TIME_TOP_PADDING_DP = 5f
        const val TRACK_TIME_SP = 11f
        const val TRACK_TIME_LINE_HEIGHT_SP = 12f


        /** The always-on variant keeps the name and the track and drops the picture. */
        object Ambient {
            const val NAME_SP = 17f
            const val TRACK_SP = 12f
            const val NAME_ALPHA = .92f
            const val TRACK_ALPHA = .62f
        }
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

        /**
         * The always-on variant: the two-tone composition reduced to a hairline and the text.
         *
         * A filled half-screen band is the most expensive thing an AOD can draw, so the panel goes
         * and only the seam it created stays. The text then has the whole lower screen rather than
         * a coloured box, which is why these insets are the ambient block's own.
         */
        object Ambient {
            const val SEAM_INSET_FRACTION = .18f
            const val SEAM_ALPHA = .5f
            /** How far below the seam the text block starts. */
            const val TEXT_TOP_GAP_FRACTION = .07f
            const val SIDE_PADDING_FRACTION = .16f
            const val ARTIST_SP = 13f
            const val ARTIST_ALPHA = .7f
            const val TITLE_SP = 18f
            const val TITLE_MAX_LINES = 2
        }
    }

    /** The minimal face: a small cover disc and one centred sentence. */
    object Note {
        const val COVER_FRACTION = .21f

        /**
         * How close to the glass a *moved* Note block is allowed to sit, as a screen fraction.
         *
         * Matches `FaceChrome`'s own edge margin for a raised or grounded block, so the watch and
         * the miniature put it in the same place. Only ever consulted once the user has overridden
         * the vertical placement; the designed composition is centred and keeps no such margin.
         */
        const val MOVED_BLOCK_EDGE_FRACTION = .08f

        /**
         * Was .60 - large enough to reach into the diagonal quadrant taps' apex near the vertical
         * centre line and swallow a left/right quadrant tap aimed near it before
         * `FourWayTouchLayout` ever saw the touch. The disc and sentence leave the centre visually
         * open, but that openness isn't a reason to claim more of it than a face with real
         * interactive content needs.
         */
        const val CENTER_REGION_FRACTION = .52f
        const val CENTER_PULSE_FRACTION = .34f

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

        /** The always-on variant: the same sentence, wider, with the cover dropped - a filled
         *  disc is expensive on an always-on panel and its outline says nothing the text does not. */
        object Ambient {
            const val SIDE_PADDING_FRACTION = .18f
            const val TEXT_SP = 15f
            const val LINE_HEIGHT_SP = 18f
        }
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

        /**
         * Frame has no visible transport button, so its centre handles play/pause. Keep that
         * target comfortably above Wear's 48 dp touch-target guidance without letting it consume
         * the card's side bands, where the shared left/right quadrant actions live.
         *
         * Was .68, then .46 - the card itself spans 77% of the screen width
         * (`1 - CARD_INSET_FRACTION * 2`), so even at .46 a tap meant for the card's own corner,
         * short of its true edge, could still land inside the swallowed square. Tightened again to
         * leave a wider honest margin between the two, still well above the 48 dp floor on any
         * supported screen size.
         */
        const val CENTER_REGION_FRACTION = .40f
        const val CENTER_PULSE_FRACTION = .30f

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
        // The asymmetric band trades a little horizontal space for room below the reel: its lower
        // edge is deeper on a round screen, so the usable chord is roughly 7% narrower. It only
        // bounds the safe text width, though; the content anchor remains the display centre.
        const val BAND_TOP = 0.31f
        const val BAND_BOTTOM = 0.83f

        /**
         * Top of the artist/title running head.
         *
         * Raised to the 14% anchor: a three-row current lyric otherwise puts its preceding line
         * into the same visual band as the track identity.
         */
        const val HEADER_TOP = .14f

        /** The Canvas preview's artist baseline, matching [HEADER_TOP] plus its text ascent. */
        const val HEADER_ARTIST_BASELINE = HEADER_TOP + .06f

        /**
         * The lyric/title-card block is centred on the display.
         *
         * The band still describes the vertical span used to derive a bezel-safe text width, but
         * it must not move the content's anchor. Deriving this from the asymmetric band put both
         * the fallback title card and the lyric reel at 57% of the dial instead of its centre.
         */
        const val BAND_CENTER = .5f

        /**
         * The always-on variant: the running head and the current line alone.
         *
         * The display refreshes about once a minute, so the neighbouring lines would be wrong most
         * of the time and are dropped along with the hairline. The block is shorter than the awake
         * reel, so it asks `RoundScreenText` about its own depth rather than the band's.
         */
        object Ambient {
            const val BAND_TOP = 0.34f
            const val BAND_BOTTOM = 0.66f
            const val TITLE_SP = 10f
            const val TITLE_ALPHA = 0.45f
            const val TITLE_TRACKING_EM = 0.16f
            const val TITLE_TO_LINE_GAP_DP = 10f
            const val LINE_SP = 15f
            const val LINE_HEIGHT_SP = 19f
            const val LINE_ALPHA = 0.85f
            const val LINE_MAX_LINES = 3
        }
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

        /**
         * The always-on variant: the identity and the first couple of rows.
         *
         * A dense table redrawn once a minute is a wall of small text that is mostly stale, and a
         * static grid is exactly what an always-on panel must not burn in. What survives is what
         * answers "what am I listening to" at a glance.
         */
        object Ambient {
            const val SIDE_PADDING_FRACTION = 0.16f
            const val TITLE_SP = 15f
            const val TITLE_LINE_HEIGHT_SP = 19f
            const val TITLE_ALPHA = 0.9f
            const val TITLE_MAX_LINES = 2
            const val ROW_SP = 10f
            const val ROW_ALPHA = 0.6f
            const val ROWS = 2
        }
    }
}
