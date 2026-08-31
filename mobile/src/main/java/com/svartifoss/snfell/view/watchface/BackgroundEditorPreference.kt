package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.svartifoss.snfell.R

/**
 * The contextual Background editor: the artwork underneath, and the stack of treatments over it.
 *
 * The fifth sibling of [TypographyEditorPreference], [ColorEditorPreference], [PanelEditorPreference]
 * and [PlayerEditorPreference], with the same contract - the ordinary Background preferences stay
 * in this PreferenceScreen, hidden from this page only, still owning persistence, validation,
 * dependency dispatch and their search metadata.
 *
 * It differs from its siblings in one way that shapes everything else here: the other four present
 * a fixed set of controls, one per key, and this one presents a *list* the user builds. So the
 * layer rows are inflated by the fragment rather than declared in the layout, and their values
 * live in one encoded preference instead of one key each - see
 * [com.svartifoss.snfell.common.BackgroundLayerStack] for why that is the storage rather than a
 * fixed number of numbered slots.
 */
class BackgroundEditorPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : Preference(context, attrs) {

    internal var bindEditor: ((View) -> Unit)? = null
    private var boundRoot: View? = null

    init {
        layoutResource = R.layout.pref_background_editor
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

    /** Gives the searched control a restrained pulse once the surface is on screen. */
    internal fun pulse(controlId: Int, announcement: CharSequence?) {
        val root = boundRoot ?: return
        root.post {
            val control = root.findViewById<View>(controlId) ?: root
            control.animate().cancel()
            control.alpha = 0.55f
            control.animate().alpha(1f).setDuration(420L).start()
            announcement?.takeIf { it.isNotBlank() }?.let(control::announceForAccessibility)
        }
    }
}
