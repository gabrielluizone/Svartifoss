package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.util.AttributeSet
import com.svartifoss.snfell.R

/**
 * One compact, contextual surface that replaces the Mini buttons page's row list.
 *
 * A sibling of [PlayerEditorPreference] and the rest, with the same contract: the ordinary
 * mini-button and gesture preferences remain in the same PreferenceScreen, hidden from this page
 * only. They still own persistence, validation, their dialogs and their search metadata; this row
 * is deliberately just another view over those exact Preference objects.
 *
 * Its picker rows are built at bind time - a face that hosts the row inside its own composition
 * places and shapes the buttons itself - so a searched control is found by tag. See
 * [TaggedEditorPreference].
 */
class MiniButtonEditorPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : TaggedEditorPreference(context, attrs) {

    init {
        layoutResource = R.layout.pref_mini_button_editor
    }
}
