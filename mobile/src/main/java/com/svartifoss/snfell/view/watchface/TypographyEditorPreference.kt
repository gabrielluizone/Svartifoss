package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.HorizontalScrollView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.svartifoss.snfell.R

/**
 * One compact, contextual surface that replaces the long visual list on Watch -> Text.
 *
 * The ordinary typography preferences remain in the same PreferenceScreen, hidden from this
 * page only. They still own persistence, validation, dependency dispatch and search metadata;
 * this row is deliberately just another view over those exact Preference objects.
 */
class TypographyEditorPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : Preference(context, attrs) {

    internal var bindEditor: ((View) -> Unit)? = null
    private var boundRoot: View? = null

    init {
        layoutResource = R.layout.pref_typography_editor
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

    /** Reveals both horizontal destinations, then gives the searched control a restrained pulse. */
    internal fun revealAndPulse(
            targetButtonId: Int,
            controlId: Int,
            announcement: CharSequence?
    ) {
        val root = boundRoot ?: return
        root.post {
            val targetButton = root.findViewById<View>(targetButtonId)
            revealIn(root.findViewById(R.id.typography_target_scroll), targetButton)

            val control = root.findViewById<View>(controlId) ?: root
            revealIn(root.findViewById(R.id.typography_toolbar_scroll), control)
            control.animate().cancel()
            control.alpha = 0.55f
            control.animate().alpha(1f).setDuration(420L).start()
            announcement?.takeIf { it.isNotBlank() }?.let(control::announceForAccessibility)
        }
    }

    private fun revealIn(scrollView: HorizontalScrollView?, child: View?) {
        if (scrollView == null || child == null || !scrollView.isShown || !child.isShown) return
        var ancestor = child.parent
        while (ancestor is View && ancestor !== scrollView) ancestor = ancestor.parent
        if (ancestor !== scrollView) return
        val bounds = Rect()
        child.getDrawingRect(bounds)
        scrollView.offsetDescendantRectToMyCoords(child, bounds)
        val delta = bounds.centerX() - scrollView.scrollX - scrollView.width / 2
        if (delta != 0) scrollView.smoothScrollBy(delta, 0)
    }
}
