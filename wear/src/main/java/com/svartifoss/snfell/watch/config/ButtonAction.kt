package com.svartifoss.snfell.watch.config

import android.graphics.drawable.Drawable

class ButtonAction(
        val key: String,
        val icon: Drawable?,
        val title: String? = null,
        /** Monochrome glyphs follow the destination surface; artwork keeps its own colours. */
        val iconTintable: Boolean = true,
        /** Optional safe deep link opened on the paired phone through Wear RemoteIntent. */
        val remoteUri: String? = null,
        /** True only for genuine fetched cover art (e.g. a streaming shortcut's cached
         *  thumbnail) - never a generic app-launcher icon - eligible to fill a whole
         *  quick-panel pill's background. */
        val isCoverArt: Boolean = false,
        /** Signed milliseconds this action moves playback by, when the phone said - the
         *  skip/reverse-by-seconds actions. Lets the press be drawn at once; see
         *  `MusicViewModel.applyOptimisticSeek`. Null for everything else, and for any action
         *  from a phone build that predates it. */
        val seekOffsetMs: Long? = null,
        /** For an entry that starts one streaming destination (a saved shortcut, the account's
         *  liked songs): its service and kind, shown on that destination's own screen. Its
         *  presence is what lets a pick from the actions menu open that screen - see
         *  `ShortcutScreenPolicy`. Null for everything else and for older phone builds. */
        val shortcutSubtitle: String? = null,
        /** Alongside [shortcutSubtitle]: whether that screen offers Shuffle beside Play. */
        val shortcutShuffleable: Boolean = false
) {
    override fun toString(): String {
        return "ButtonAction(key='$key', icon=$icon, title=$title, " +
                "iconTintable=$iconTintable, remoteUri=$remoteUri, isCoverArt=$isCoverArt, " +
                "seekOffsetMs=$seekOffsetMs, shortcutSubtitle=$shortcutSubtitle, " +
                "shortcutShuffleable=$shortcutShuffleable)"
    }
}
