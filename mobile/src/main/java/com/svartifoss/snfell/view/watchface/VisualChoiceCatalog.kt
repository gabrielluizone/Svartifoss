package com.svartifoss.snfell.view.watchface

/**
 * The style pickers that open as a grid of pictures instead of a list of names.
 *
 * Each of these chooses how something looks - a layout, a background, an always-on screen, a panel
 * - from up to seventy entries whose names (Velvet, Fjord, Ink wash) say nothing about the result,
 * so a list could only be judged by picking, closing and opening it again. In the grid every cell
 * is the user's own watch with that one value changed, drawn by the real preview.
 *
 * Only plain `ListPreference` rows belong here, pinned by `VisualChoiceCatalogTest`. Pickers that
 * already show what they offer keep their own dialogs: `DescribedListPreference` (a sentence per
 * entry), `ColorTreatmentPreference` (swatches), `TransportGlyphPreference` (the glyphs),
 * `AppIconStylePreference` (the icons), and the cover-shape pickers, whose dialog starts with
 * "Hidden".
 */
internal object VisualChoiceCatalog {

    val KEYS: Set<String> = linkedSetOf(
            "wear_screen_face",
            "album_art_style",
            "wear_aod_style",
            "wear_progress_style",
            "wear_volume_style",
            "wear_volume_layout",
            "wear_seek_style",
            "wear_seek_layout",
            "wear_quick_panel_style",
            "wear_queue_style",
            "screen_buttons_shape",
            "screen_buttons_bg_style")

    fun isVisual(key: String?): Boolean = key != null && key in KEYS
}
