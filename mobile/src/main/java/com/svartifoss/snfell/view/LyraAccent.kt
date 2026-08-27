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
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Single source of truth for the accent color outside MainActivity. The dynamic (album-art)
 * accent only exists in MainActivity's memory, so MainActivity persists whatever accent it
 * currently displays under [CURRENT_ACCENT_PREF]; standalone dialog activities (action
 * picker/editor) call [resolve] to match the accent that is actually on screen instead of
 * mixing the static default with the custom preference.
 */
object LyraAccent {
    const val CURRENT_ACCENT_PREF = "current_accent_color"
    private val AFFECTING_PREFERENCES = setOf(
            CURRENT_ACCENT_PREF,
            "custom_accent_color",
            "dynamic_accent_color",
            "desaturated_color")

    fun affectsResolvedColor(key: String?): Boolean = key in AFFECTING_PREFERENCES

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

    /** Keeps an album-derived mark legible on the app background at the non-text 3:1 threshold. */
    fun contrastSafe(context: Context, rawAccent: Int): Int {
        val background = ContextCompat.getColor(context, R.color.lyra_background)
        return contrastSafe(rawAccent, background, minimumContrast = 3.0)
    }

    /**
     * Returns the smallest adjustment of [rawAccent] that reaches [minimumContrast] on an exact
     * background. Small labels must pass their real surface here rather than assuming that every
     * accent is drawn directly on `lyra_background`.
     */
    fun contrastSafe(rawAccent: Int, background: Int, minimumContrast: Double = 4.5): Int {
        return LyraContrast.contrastSafe(rawAccent, background, minimumContrast)
    }

    /** Chooses the black/white foreground with the stronger WCAG contrast on [background]. */
    fun foregroundFor(background: Int): Int = LyraContrast.foregroundFor(background)
}

/**
 * Android-free WCAG color math. Keeping this separate makes the light-accent failure range
 * unit-testable on the JVM instead of depending on the mockable android.jar's Color methods.
 */
internal object LyraContrast {
    const val BLACK: Int = -0x1000000
    const val WHITE: Int = -0x1

    fun contrastSafe(rawAccent: Int, background: Int, minimumContrast: Double): Int {
        require(minimumContrast in 1.0..21.0)
        val accent = opaque(rawAccent)
        val opaqueBackground = opaque(background)
        if (contrastRatio(accent, opaqueBackground) >= minimumContrast) return accent

        val target = foregroundFor(opaqueBackground)
        var low = 0f
        var high = 1f
        repeat(12) {
            val amount = (low + high) / 2f
            if (contrastRatio(blend(accent, target, amount), opaqueBackground) >= minimumContrast) {
                high = amount
            } else {
                low = amount
            }
        }
        return blend(accent, target, high)
    }

    fun foregroundFor(background: Int): Int {
        val opaqueBackground = opaque(background)
        return if (contrastRatio(BLACK, opaqueBackground) >=
                contrastRatio(WHITE, opaqueBackground)) {
            BLACK
        } else {
            WHITE
        }
    }

    fun contrastRatio(foreground: Int, background: Int): Double {
        val first = relativeLuminance(opaque(foreground))
        val second = relativeLuminance(opaque(background))
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun opaque(color: Int): Int = color or BLACK

    private fun relativeLuminance(color: Int): Double =
            0.2126 * linearChannel((color ushr 16) and 0xff) +
                    0.7152 * linearChannel((color ushr 8) and 0xff) +
                    0.0722 * linearChannel(color and 0xff)

    private fun linearChannel(component: Int): Double {
        val normalized = component / 255.0
        return if (normalized <= 0.04045) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        fun component(shift: Int): Int {
            val start = (from ushr shift) and 0xff
            val end = (to ushr shift) and 0xff
            return (start + (end - start) * amount).roundToInt().coerceIn(0, 255)
        }
        return BLACK or
                (component(16) shl 16) or
                (component(8) shl 8) or
                component(0)
    }
}
