package com.svartifoss.snfell.view.watchface.home

import android.content.Context
import android.util.AttributeSet
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.watchface.TaggedEditorPreference

/*
 * The rows of the Watch tab's Home, which is one more section of the same preference screen the
 * seven areas use (`cat_wf_home_editor` in watch_face_settings.xml).
 *
 * Home is built that way, rather than as a screen of its own, because its controls are views over
 * the real Preference objects - the reset row runs `reset_appearance`'s own confirmation, and a
 * later phase's quick adjustments open the real pickers with their archived-option filters,
 * imported fonts and validation - and those exist only inside this screen.
 *
 * Separate rows rather than one, because an editor row rebuilds its whole view on refresh: one
 * surface would rebind the theme strip, and lose its scroll position, on every unrelated change.
 */

/** The theme strip: every theme drawn by the real preview. Stores nothing. */
class HomeThemesPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : TaggedEditorPreference(context, attrs) {

    init {
        layoutResource = R.layout.pref_home_themes
    }
}

/** The quick adjustments card: four pickers and the on-screen chips. Stores nothing. */
class HomeQuickPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : TaggedEditorPreference(context, attrs) {

    init {
        layoutResource = R.layout.pref_home_quick
    }
}

/** The list of the seven areas and the reset of the current layout. Stores nothing. */
class HomeAreasPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : TaggedEditorPreference(context, attrs) {

    init {
        layoutResource = R.layout.pref_home_areas
    }
}
