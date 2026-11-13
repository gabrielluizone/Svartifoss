package com.svartifoss.snfell.view.watchface.home

import androidx.annotation.StringRes
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.watchface.WatchPreviewView.PreviewSurface

/**
 * The words under the Watch tab's preview: the theme being edited, and which screen the preview is
 * showing when that is not the player.
 *
 * The preview follows the control being edited - touching a volume setting shows the volume
 * screen - which is useful and used to be unlabelled, so a person could not tell why the watch in
 * front of them had changed. Every label is a string every locale already carries.
 */
internal object PreviewCaption {

    /** The name of [surface], as the rest of the Watch tab calls that screen. */
    @StringRes
    fun labelRes(surface: PreviewSurface): Int = when (surface) {
        PreviewSurface.PLAYER -> R.string.watch_section_style
        PreviewSurface.AOD -> R.string.watch_section_aod
        PreviewSurface.VOLUME -> R.string.watch_element_volume
        PreviewSurface.SEEK -> R.string.watch_element_progress
        PreviewSurface.QUICK_PANEL -> R.string.watch_element_quick_panel
        PreviewSurface.QUEUE -> R.string.watch_element_queue
        PreviewSurface.LYRICS -> R.string.watch_element_lyrics
        PreviewSurface.MINI_BUTTONS -> R.string.watch_section_mini_buttons
    }

    /**
     * What the caption adds after the theme's name, or null on the player.
     *
     * The player is what the preview shows unless something says otherwise, so naming it would put
     * a word under the watch at all times that only means "nothing special".
     */
    @StringRes
    fun suffixRes(surface: PreviewSurface): Int? =
            if (surface == PreviewSurface.PLAYER) null else labelRes(surface)
}
