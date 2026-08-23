package com.svartifoss.snfell.view.settings

import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

/** Scrolls a search destination into view and gives the resolved row one restrained visual pulse. */
internal fun PreferenceFragmentCompat.scrollToAndPulsePreference(
    preference: Preference
) {
    val key = preference.key ?: return
    scrollToPreference(key)
    listView?.postDelayed({
        val list = listView ?: return@postDelayed
        // PreferenceGroupAdapter's key-to-position API is library-private. After the public
        // scrollToPreference call has brought the target on screen, identify its bound row by the
        // same title the Preference framework placed in android.R.id.title.
        val row = (0 until list.childCount)
            .map(list::getChildAt)
            .firstOrNull { child ->
                child.findViewById<TextView>(android.R.id.title)?.text == preference.title
            } ?: return@postDelayed
        row.animate().cancel()
        row.alpha = 0.55f
        row.animate().alpha(1f).setDuration(420L).start()
        row.announceForAccessibility(preference.title)
    }, 180L)
}
