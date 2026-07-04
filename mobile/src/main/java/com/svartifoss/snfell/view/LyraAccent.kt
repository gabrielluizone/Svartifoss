package com.svartifoss.snfell.view

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.R

/**
 * Single source of truth for the accent color outside MainActivity. The dynamic (album-art)
 * accent only exists in MainActivity's memory, so MainActivity persists whatever accent it
 * currently displays under [CURRENT_ACCENT_PREF]; standalone dialog activities (action
 * picker/editor) call [resolve] to match the accent that is actually on screen instead of
 * mixing the static default with the custom preference.
 */
object LyraAccent {
    const val CURRENT_ACCENT_PREF = "current_accent_color"

    fun resolve(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        // 0 (transparent black) is never a real accent - accents are always opaque colors.
        val current = prefs.getInt(CURRENT_ACCENT_PREF, 0)
        if (current != 0) {
            return current
        }

        val customHex = prefs.getString("custom_accent_color", null)
        if (customHex != null) {
            try {
                return Color.parseColor(customHex)
            } catch (ignored: IllegalArgumentException) {
            }
        }

        return ContextCompat.getColor(context, R.color.lyra_accent)
    }
}
