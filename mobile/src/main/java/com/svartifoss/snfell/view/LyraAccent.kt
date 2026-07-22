package com.svartifoss.snfell.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.widget.EditText
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

    /**
     * Tints an [EditText]'s selection UI - text-cursor, teardrop handles, selection highlight and
     * the focused underline - to [color], defaulting to the current runtime accent.
     *
     * These all come from the theme's `colorControlActivated`, which is the static Lyra sage green
     * resolved once at inflation and can never follow a runtime accent - so an EditText that
     * MainActivity's own accent traversal doesn't reach (standalone dialog activities, the theme
     * name box, etc.) keeps showing sage-green handles under any custom accent. Call this for those.
     */
    fun applyToEditText(editText: EditText, color: Int = resolve(editText.context)) {
        editText.highlightColor = ColorUtils.setAlphaComponent(color, 0x55)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.textCursorDrawable = editText.textCursorDrawable?.mutate()?.apply { setTint(color) }
            editText.textSelectHandle?.mutate()?.apply { setTint(color) }
                    ?.let { editText.setTextSelectHandle(it) }
            editText.textSelectHandleLeft?.mutate()?.apply { setTint(color) }
                    ?.let { editText.setTextSelectHandleLeft(it) }
            editText.textSelectHandleRight?.mutate()?.apply { setTint(color) }
                    ?.let { editText.setTextSelectHandleRight(it) }
        }
        editText.backgroundTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(color, ContextCompat.getColor(editText.context, R.color.lyra_divider)))
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
