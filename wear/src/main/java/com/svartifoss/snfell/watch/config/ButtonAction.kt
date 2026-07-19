package com.svartifoss.snfell.watch.config

import android.graphics.drawable.Drawable

class ButtonAction(
        val key: String,
        val icon: Drawable?,
        val title: String? = null,
        /** Monochrome glyphs follow the destination surface; artwork keeps its own colours. */
        val iconTintable: Boolean = true
) {
    override fun toString(): String {
        return "ButtonAction(key='$key', icon=$icon, title=$title, iconTintable=$iconTintable)"
    }
}
