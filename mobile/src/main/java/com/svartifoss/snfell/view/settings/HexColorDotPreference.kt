package com.svartifoss.snfell.view.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.svartifoss.snfell.R

/**
 * Preference whose right-side dot previews the hex color (#RRGGBB) stored under its own key -
 * unlike [ColorDotPreference], which mirrors the app's live accent. A translucent gray dot
 * stands in while no color has been picked yet.
 */
class HexColorDotPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : Preference(context, attrs) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val dot = holder.findViewById(R.id.color_dot) as? ImageView
        // parseColor throws StringIndexOutOfBounds (not IllegalArgument) on an empty string,
        // so guard blank input separately and catch broadly. Reads through the PreferenceDataStore
        // when one is installed (the Watch tab scopes custom colors per face); sharedPreferences
        // is null in that case.
        val hex = preferenceDataStore?.getString(key, null) ?: sharedPreferences?.getString(key, null)
        val color = if (hex.isNullOrBlank()) {
            0x40808080
        } else {
            try {
                Color.parseColor(hex)
            } catch (ignored: Exception) {
                0x40808080
            }
        }
        dot?.imageTintList = ColorStateList.valueOf(color)
    }

    /** Re-binds the row so the dot picks up a just-changed color. */
    fun refreshDot() = notifyChanged()
}
