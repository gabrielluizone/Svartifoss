package com.svartifoss.snfell.common

import androidx.annotation.DrawableRes

/**
 * The five drawings the play and pause marks can be set in (`icons/icons_4/PLAY-A..E.svg` and
 * `PAUSE-A..E.svg`). A letter names the same *style* for both marks - A filled and rounded, B and
 * E rounded outlines (E the heavier), C a sharp outline, D filled and sharp - but the two marks are
 * chosen separately ([MiscPreferences.WEAR_PLAY_ICON], [MiscPreferences.WEAR_PAUSE_ICON]), so a
 * pair is just the same letter twice and a mixed one is allowed.
 */
enum class TransportGlyph(val preferenceValue: String) {
    A("a"),
    B("b"),
    C("c"),
    D("d"),
    E("e")
}

/** Which of the two marks is being decided. */
enum class TransportMark { PLAY, PAUSE }

/**
 * The one place that decides which glyph a face draws for play and for pause, and the drawable that
 * draws it - shared by the watch's faces and the phone's miniature of them, so the two cannot
 * disagree about what a stored value looks like.
 *
 * `"follow"` (the default, and what every unknown or missing value resolves to) means *the glyph
 * this face was designed with*. That is not one glyph for everybody: the faces were authored with
 * the filled sharp pair (D), except Expressive's pause, which has always been the sharp outline
 * (C). Resolving through [designed] rather than hard-coding D keeps every existing install, saved
 * theme and published theme looking exactly as it did, which is what makes it safe for the key to
 * exist on every face.
 *
 * Only the awake faces use this. AOD draws its own outline glyphs and is styled by `WEAR_AOD_*`.
 */
object TransportGlyphs {

    const val FOLLOW = "follow"

    /** The picker's values, in picker order: the face's own first, then each style. */
    val CHOICES: List<String> = listOf(FOLLOW) + TransportGlyph.entries.map { it.preferenceValue }

    /**
     * The faces that draw a play/pause mark of their own while awake, and so have something for
     * the two preferences to change: the curated transport faces, Expressive and Material, and
     * Frame and Ribbon, whose tap confirmation shows the mark that the tap produced.
     *
     * Classic and Matejdro are absent on purpose: their centre control is the *action's* icon,
     * chosen in the button configuration, which is not a play or pause mark that follows the
     * player's state. Every other face draws no transport mark at all - offering the rows there
     * would be offering pickers that provably change nothing.
     */
    val FACES: Set<String> = setOf(
            "expressive", "material", "vinyl", "poster", "studio", "halo", "aurora", "eclipse",
            "spectrum", "frame", "ribbon")

    /** What [face] draws for [mark] when nothing has been chosen. */
    fun designed(mark: TransportMark, face: String): TransportGlyph =
            if (mark == TransportMark.PAUSE && face == "expressive") TransportGlyph.C
            else TransportGlyph.D

    /** The glyph [face] draws for [mark] given the stored [choice]. */
    fun resolve(mark: TransportMark, choice: String?, face: String): TransportGlyph =
            TransportGlyph.entries.firstOrNull { it.preferenceValue == choice }
                    ?: designed(mark, face)

    @DrawableRes
    fun drawable(mark: TransportMark, glyph: TransportGlyph): Int = when (mark) {
        TransportMark.PLAY -> when (glyph) {
            TransportGlyph.A -> R.drawable.glyph_play_a
            TransportGlyph.B -> R.drawable.glyph_play_b
            TransportGlyph.C -> R.drawable.glyph_play_c
            TransportGlyph.D -> R.drawable.glyph_play_d
            TransportGlyph.E -> R.drawable.glyph_play_e
        }
        TransportMark.PAUSE -> when (glyph) {
            TransportGlyph.A -> R.drawable.glyph_pause_a
            TransportGlyph.B -> R.drawable.glyph_pause_b
            TransportGlyph.C -> R.drawable.glyph_pause_c
            TransportGlyph.D -> R.drawable.glyph_pause_d
            TransportGlyph.E -> R.drawable.glyph_pause_e
        }
    }

    /** [resolve] and [drawable] in one step, for the callers that only want the picture. */
    @DrawableRes
    fun drawableFor(mark: TransportMark, choice: String?, face: String): Int =
            drawable(mark, resolve(mark, choice, face))
}
