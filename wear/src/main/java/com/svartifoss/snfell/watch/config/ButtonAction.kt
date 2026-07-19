package com.svartifoss.snfell.watch.config

import android.graphics.drawable.Drawable

class ButtonAction(
        val key: String,
        val icon: Drawable?,
        val title: String? = null,
        /** Monochrome glyphs follow the destination surface; artwork keeps its own colours. */
        val iconTintable: Boolean = true,
        /** Optional safe deep link opened on the paired phone through Wear RemoteIntent. */
        val remoteUri: String? = null
) {
    override fun toString(): String {
        return "ButtonAction(key='$key', icon=$icon, title=$title, " +
                "iconTintable=$iconTintable, remoteUri=$remoteUri)"
    }
}
