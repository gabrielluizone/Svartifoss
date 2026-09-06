package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.util.AttributeSet
import com.svartifoss.snfell.R

/**
 * One compact, contextual surface that replaces the eleven-row list on Watch -> Always-on.
 *
 * A sibling of [PlayerEditorPreference] and the rest, with the same contract: the ordinary ambient
 * preferences remain in the same PreferenceScreen, hidden from this page only. They still own
 * persistence, validation, dependency dispatch, their dialogs and their search metadata; this row
 * is deliberately just another view over those exact Preference objects.
 *
 * Its element chips are built at bind time - a chip exists only for the ambient styles that can
 * draw it - so a searched control is found by tag. See [TaggedEditorPreference].
 */
class AodEditorPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : TaggedEditorPreference(context, attrs) {

    init {
        layoutResource = R.layout.pref_aod_editor
    }
}
