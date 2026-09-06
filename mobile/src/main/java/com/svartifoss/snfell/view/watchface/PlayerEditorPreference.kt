package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.util.AttributeSet
import com.svartifoss.snfell.R

/**
 * One compact, contextual surface that replaces the long visual list on Watch -> Player.
 *
 * A sibling of [TypographyEditorPreference], [ColorEditorPreference], [PanelEditorPreference],
 * [AodEditorPreference] and [MiniButtonEditorPreference], with the same contract: the ordinary
 * player preferences remain in the same PreferenceScreen, hidden from this page only. They still
 * own persistence, validation, dependency dispatch, their dialogs and their search metadata; this
 * row is deliberately just another view over those exact Preference objects.
 *
 * It differs from the rail-based three in how a searched control is found - see
 * [TaggedEditorPreference], which owns that lookup for every page whose controls are built at bind
 * time.
 */
class PlayerEditorPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : TaggedEditorPreference(context, attrs) {

    init {
        layoutResource = R.layout.pref_player_editor
    }
}
