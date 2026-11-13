package com.svartifoss.snfell.view.watchface.home

import androidx.annotation.StringRes
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.watchface.PlayerEditorModel

/**
 * One of the on/off chips on Home's quick adjustments: an element of the player, by the short name
 * the editor pages already use for it. The preference's own full title is what a screen reader
 * hears, the arrangement `AodSettingSpec.labelRes` documents.
 */
internal enum class HomeChip(val key: String, @StringRes val labelRes: Int) {
    TITLE("wear_show_track_title", R.string.category_wf_typography_title),
    ARTIST("wear_show_track_artist", R.string.category_wf_typography_artist),
    APP_ICON("wear_show_source_icon", R.string.category_wf_typography_icon),
    CLOCK("always_show_time", R.string.category_wf_clock),
    PROGRESS("wear_edge_progress_visible", R.string.watch_element_progress),
    CONTROLS("wear_classic_icons_visible", R.string.controls_header)
}

/**
 * Home's quick adjustments: the handful of settings most people change, one level above the pages
 * that hold them.
 *
 * Every control here is a view over a Preference that lives on one of the seven pages and keeps
 * its place there - nothing moves in storage or in search. The rows open that preference's own
 * picker (the grids of pictures for the styles), and the chips flip that preference.
 *
 * Mini buttons are deliberately absent: their visibility is a four-way choice, and the row draws
 * nothing until actions are assigned on the Controls tab, so on a screen meant to be obvious it
 * would be a control with no visible effect.
 *
 * Pure, so which keys appear and on which layouts is pinned by a JVM test, and the per-layout rule
 * is the owning page's, never a second copy of a face list.
 */
internal object WatchHomeModel {

    /** The pickers, in the order they are listed. */
    val ROWS: List<String> = listOf(
            "wear_color_treatment",
            "wear_font",
            "album_art_style",
            "wear_aod_style")

    /** The colour a Normal treatment uses, offered right under the treatment as the Colors page does. */
    const val NORMAL_COLOR_KEY = "wear_normal_color"

    /** The treatment value that reveals [NORMAL_COLOR_KEY]. */
    private const val NORMAL_TREATMENT = "normal"

    val CHIPS: List<HomeChip> = HomeChip.entries

    /** Every key Home shows; a change to any of them redraws the card. */
    val keys: Set<String> = buildSet {
        addAll(ROWS)
        add(NORMAL_COLOR_KEY)
        CHIPS.forEach { add(it.key) }
    }

    fun showsNormalColor(treatment: String?): Boolean = treatment == NORMAL_TREATMENT

    /**
     * Whether [face] draws what [chip] switches. Asked of the page that owns the key: the Player
     * page's rule where it has one, and otherwise every face - the title and the artist line,
     * which the Text page owns, are drawn by all of them.
     */
    fun appliesToFace(chip: HomeChip, face: String): Boolean {
        val spec = PlayerEditorModel.specFor(chip.key) ?: return true
        return PlayerEditorModel.appliesToFace(spec.control, face)
    }
}
