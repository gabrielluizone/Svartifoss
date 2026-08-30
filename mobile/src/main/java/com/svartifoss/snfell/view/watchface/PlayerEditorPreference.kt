package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.svartifoss.snfell.R

/**
 * One compact, contextual surface that replaces the long visual list on Watch -> Player.
 *
 * The last of the four siblings, with [TypographyEditorPreference], [ColorEditorPreference] and
 * [PanelEditorPreference], and the same contract: the ordinary player preferences remain in the
 * same PreferenceScreen, hidden from this page only. They still own persistence, validation,
 * dependency dispatch, their dialogs and their search metadata; this row is deliberately just
 * another view over those exact Preference objects.
 *
 * It differs from the other three in how a searched control is found. Most of this page's controls
 * are built at bind time - the chips exist only for the faces that can use them - so there is no
 * static id to look up. Every control instead carries its preference key as its view tag, and the
 * pulse searches for that tag. A generated chip and a fixed button are then reached the same way.
 */
class PlayerEditorPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : Preference(context, attrs) {

    internal var bindEditor: ((View) -> Unit)? = null
    private var boundRoot: View? = null

    init {
        layoutResource = R.layout.pref_player_editor
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        boundRoot = holder.itemView
        bindEditor?.invoke(holder.itemView)
    }

    internal fun refresh() = notifyChanged()

    /** Preference objects can outlive their RecyclerView; never retain that destroyed view tree. */
    internal fun releaseBoundView() {
        boundRoot = null
    }

    /** Gives the control holding [key] a restrained pulse, if this face renders one at all. */
    internal fun pulse(key: String, announcement: CharSequence?) {
        val root = boundRoot ?: return
        root.post {
            // A control the current face cannot use is simply not on the page. Search still lands
            // here, which is the useful half; there is nothing to highlight and nothing to fake.
            val control = findByTag(root, key) ?: return@post
            control.animate().cancel()
            control.alpha = 0.55f
            control.animate().alpha(1f).setDuration(420L).start()
            announcement?.takeIf { it.isNotBlank() }?.let(control::announceForAccessibility)
        }
    }

    private fun findByTag(view: View, key: String): View? {
        if (view.tag == key) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findByTag(view.getChildAt(index), key)?.let { return it }
        }
        return null
    }
}
