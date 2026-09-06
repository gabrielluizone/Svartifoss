package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * The shared body of a contextual Watch-tab editor whose controls are found by preference key.
 *
 * Every one of these pages is one custom view over the real Preference objects, which stay
 * inflated (but hidden) in the same PreferenceScreen and go on owning persistence, validation,
 * dialogs and search metadata. Only the layout and the controls differ between subclasses, so the
 * lifecycle and the search pulse live here once.
 *
 * The pulse searches by **view tag** rather than by id, because most of these controls are built
 * at bind time - a chip exists only for the faces or ambient styles that can use it - so there is
 * no static id to look up. Every control carries its preference key as its tag, and a generated
 * chip and a declared button are then reached the same way. [PanelEditorPreference],
 * [TypographyEditorPreference] and [ColorEditorPreference] keep their own id-plus-rail pulse
 * instead: those three have a target rail to reveal first, which a tag alone cannot express.
 */
abstract class TaggedEditorPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : Preference(context, attrs) {

    internal var bindEditor: ((View) -> Unit)? = null
    private var boundRoot: View? = null

    init {
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

    /** Gives the control holding [key] a restrained pulse, if this page renders one at all. */
    internal fun pulse(key: String, announcement: CharSequence?) {
        val root = boundRoot ?: return
        root.post {
            // A control the current face or ambient style cannot use is simply not on the page.
            // Search still lands here, which is the useful half; there is nothing to highlight and
            // nothing to fake.
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
