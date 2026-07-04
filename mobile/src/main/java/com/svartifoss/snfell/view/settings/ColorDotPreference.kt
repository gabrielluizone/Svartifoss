package com.svartifoss.snfell.view.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.mainactivity.MainActivity

/**
 * Plain preference with a small circle on the right showing the accent color currently in
 * effect - the host activity's *live* accent when available (so a dynamic album-art color
 * shows correctly), falling back to [LyraAccent.resolve]. Used by the "Accent color" setting.
 */
class ColorDotPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : Preference(context, attrs) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val dot = holder.findViewById(R.id.color_dot) as? ImageView
        dot?.imageTintList = ColorStateList.valueOf(liveAccent())
    }

    private fun liveAccent(): Int {
        var candidate: Context = context
        while (candidate is ContextWrapper) {
            if (candidate is MainActivity) {
                return candidate.currentAccentColor()
            }
            candidate = candidate.baseContext
        }
        return LyraAccent.resolve(context)
    }

    /** Re-binds the row so the dot picks up a just-changed accent. */
    fun refreshDot() = notifyChanged()
}
