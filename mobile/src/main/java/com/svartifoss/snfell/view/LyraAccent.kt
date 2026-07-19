package com.svartifoss.snfell.view

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
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

    /** Keeps an album-derived accent legible as text/marks on the Lyra surface: if the raw accent
     *  is too low-contrast against the background it is blended toward black/white until it clears
     *  a 3:1 ratio. */
    fun contrastSafe(context: Context, rawAccent: Int): Int {
        val background = ContextCompat.getColor(context, R.color.lyra_background)
        val accent = ColorUtils.setAlphaComponent(rawAccent, 255)
        if (ColorUtils.calculateContrast(accent, background) >= 3.0) return accent

        val target = if (ColorUtils.calculateLuminance(background) > 0.5) Color.BLACK else Color.WHITE
        var low = 0f
        var high = 1f
        repeat(10) {
            val amount = (low + high) / 2f
            if (ColorUtils.calculateContrast(ColorUtils.blendARGB(accent, target, amount), background) >= 3.0) {
                high = amount
            } else {
                low = amount
            }
        }
        return ColorUtils.blendARGB(accent, target, high)
    }
}
