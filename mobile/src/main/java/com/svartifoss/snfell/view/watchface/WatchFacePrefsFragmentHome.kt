package com.svartifoss.snfell.view.watchface

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.preference.Preference
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.svartifoss.snfell.R

/*
 * The Watch tab's Home, drawn by the preference page created with SECTION_HOME.
 *
 * Kept out of WatchFacePrefsFragment itself, which already holds the seven areas' editors, and
 * written as extensions so Home reaches the same building blocks those editors use - the palette,
 * the group card, the settings row - instead of copies of them.
 */

/** The preference whose own confirmation the Home reset row runs, as the Player page's does. */
private const val RESET_APPEARANCE_KEY = "reset_appearance"

/**
 * The theme strip's row: its heading, the way to the themes library, and the list of themes, which
 * [WatchFaceFragment] fills because it owns the pictures and the live cover they are drawn on.
 */
internal fun WatchFacePrefsFragment.bindHomeThemes(root: View) {
    root.setTag(R.id.tag_handles_accent_locally, true)
    val palette = editorPalette()
    val host = parentFragment as? WatchFaceFragment

    root.findViewById<TextView>(R.id.home_themes_title).setTextColor(palette.textAccent)
    root.findViewById<MaterialButton>(R.id.home_themes_manage).apply {
        setTextColor(palette.textAccent)
        rippleColor = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.lyra_ripple))
        setOnClickListener { host?.openThemeLibrary() }
    }
    host?.showLooksIn(
            root.findViewById<RecyclerView>(R.id.home_themes_list),
            accent = palette.textAccent,
            surface = palette.surface,
            background = ContextCompat.getColor(requireContext(), R.color.lyra_background))
}

/**
 * The seven areas, each with its icon and the sentence that says what is inside, and below them
 * the reset of the current layout.
 *
 * Built at every bind, like the editors' cards, so the accent and the text never disagree with
 * what is stored. An area row carries its section key as its tag, the convention the editors'
 * search pulse uses.
 */
internal fun WatchFacePrefsFragment.bindHomeAreas(root: View) {
    root.setTag(R.id.tag_handles_accent_locally, true)
    val palette = editorPalette()
    val host = parentFragment as? WatchFaceFragment

    val container = root.findViewById<LinearLayout>(R.id.home_areas_groups)
    container.removeAllViews()

    val rows = inflateEditorGroupCard(container, R.string.watch_home_areas_title, palette)
    val inflater = LayoutInflater.from(rows.context)
    WatchFaceFragment.SECTIONS.forEach { area ->
        val row = inflater.inflate(R.layout.item_watch_area, rows, false)
        val title = getString(area.title)
        val description = getString(area.description)
        row.findViewById<ImageView>(R.id.watch_area_icon).apply {
            setImageResource(area.icon)
            imageTintList = ColorStateList.valueOf(palette.accent)
        }
        row.findViewById<TextView>(R.id.watch_area_title).apply {
            text = title
            setTextColor(palette.onSurface)
        }
        row.findViewById<TextView>(R.id.watch_area_description).apply {
            text = description
            setTextColor(palette.secondary)
        }
        row.findViewById<ImageView>(R.id.watch_area_chevron).imageTintList =
                ColorStateList.valueOf(palette.secondary)
        row.tag = area.key
        row.contentDescription = "$title. $description"
        ViewCompat.setAccessibilityDelegate(row, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        })
        row.setOnClickListener { host?.openArea(area.key) }
        rows.addView(row)
    }

    // The reset names itself, so its card has no heading - the arrangement the Mini buttons page
    // uses for its gestures row. It runs the preference's own listener, confirmation included.
    val reset = findPreference<Preference>(RESET_APPEARANCE_KEY) ?: return
    val resetRows = inflateEditorGroupCard(container, null, palette)
    val resetRow = EditorSettingRow.inflate(resetRows, editorRowColors(palette)).bindLink(
            key = RESET_APPEARANCE_KEY,
            titleText = reset.title ?: "",
            descriptionText = reset.summary,
            onClick = { reset.onPreferenceClickListener?.onPreferenceClick(reset) })
    resetRows.addView(resetRow.view)
}
