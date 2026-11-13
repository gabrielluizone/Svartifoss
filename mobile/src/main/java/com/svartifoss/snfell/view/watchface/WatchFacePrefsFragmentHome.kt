package com.svartifoss.snfell.view.watchface

import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.view.settings.FaceScopedPreferenceDataStore
import com.svartifoss.snfell.view.settings.parseHexOrDefault
import com.svartifoss.snfell.view.watchface.home.WatchHomeModel
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import kotlin.math.roundToInt

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
 * The quick adjustments: under the heading, the theme every change lands in; then the four pickers
 * most people change, each opening the same picker as its page; then the on-screen chips the
 * current layout can honour.
 *
 * Every control is a view over the real Preference: a row opens its dialog (the grid of pictures
 * for the styles) and a chip commits through its change listener, so the preview, validation and
 * scoped storage behind each one run as on its own page.
 */
internal fun WatchFacePrefsFragment.bindHomeQuick(root: View) {
    root.setTag(R.id.tag_handles_accent_locally, true)
    val context = requireContext()
    val palette = editorPalette()
    val rowColors = editorRowColors(palette)
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val appearance = ThemeAppearance.resolve(prefs)

    val container = root.findViewById<LinearLayout>(R.id.home_quick_groups)
    container.removeAllViews()
    val rows = inflateEditorGroupCard(container, R.string.watch_home_quick_title, palette)

    // Appearance is stored per theme, and nothing on screen used to say so: a font chosen on
    // Studio is not on Vinyl. The line sits between the heading and the rows it is about.
    (rows.parent as? LinearLayout)?.addView(
            TextView(context).apply {
                text = getString(R.string.watch_home_scope_note, activeThemeName(appearance))
                setTextColor(palette.secondary)
                textSize = 13f
                typeface = ResourcesCompat.getFont(context, R.font.google_sans)
                includeFontPadding = false
                setPadding(dp(16), dp(6), dp(16), dp(2))
            },
            1)

    WatchHomeModel.ROWS.forEach { key ->
        val picker = findPreference<ListPreference>(key) ?: return@forEach
        rows.addView(EditorSettingRow.inflate(rows, rowColors).bindChoice(
                key = key,
                titleText = picker.title ?: key,
                valueText = picker.entry ?: "",
                descriptionText = null,
                onClick = { openPreferenceDialog(key) }).view)
        if (key == WatchHomeModel.ROWS.first() && WatchHomeModel.showsNormalColor(picker.value)) {
            bindNormalColorRow(rows, rowColors, prefs)
        }
    }

    val chips = WatchHomeModel.CHIPS.filter { WatchHomeModel.appliesToFace(it, appearance.baseFace) }
    if (chips.isEmpty()) return
    val block = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }
    block.addView(TextView(context).apply {
        setText(R.string.player_section_on_screen)
        setTextColor(palette.secondary)
        textSize = 12f
        isAllCaps = true
        typeface = ResourcesCompat.getFont(context, R.font.google_sans)
        includeFontPadding = false
    })
    val group = ChipGroup(context).apply {
        chipSpacingHorizontal = dp(8)
        chipSpacingVertical = dp(4)
        layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
    }
    chips.forEach { chip ->
        val toggle = findPreference<TwoStatePreference>(chip.key) ?: return@forEach
        group.addView(newEditorChip(
                key = chip.key,
                label = getString(chip.labelRes),
                description = toggle.title,
                checked = toggle.isChecked,
                onToggle = { checked -> commitHomeChip(chip.key, checked) }))
    }
    tintEditorChipGroup(group, palette)
    block.addView(group)
    rows.addView(block)
}

/** The Normal treatment's colour, right under the treatment, opening the shared colour picker. */
private fun WatchFacePrefsFragment.bindNormalColorRow(
        rows: LinearLayout,
        rowColors: EditorRowColors,
        prefs: android.content.SharedPreferences
) {
    val key = WatchHomeModel.NORMAL_COLOR_KEY
    val colorPreference = findPreference<Preference>(key) ?: return
    val color = parseHexOrDefault(FaceScopedPreferenceDataStore(prefs).getString(key, null))
    val swatch = SpannableString("●").apply {
        setSpan(ForegroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    rows.addView(EditorSettingRow.inflate(rows, rowColors).bindChoice(
            key = key,
            titleText = colorPreference.title ?: key,
            valueText = swatch,
            descriptionText = null,
            onClick = { openColorPicker(key) }).view)
}

/** A chip commits as its page's switch would, then redraws only this card. */
private fun WatchFacePrefsFragment.commitHomeChip(key: String, checked: Boolean) {
    val toggle = findPreference<TwoStatePreference>(key) ?: return
    if (toggle.isChecked != checked && toggle.callChangeListener(checked)) toggle.isChecked = checked
    refreshHomeQuick()
}

/** The applied theme's name, as the caption under the preview gives it. */
private fun WatchFacePrefsFragment.activeThemeName(appearance: AppearanceContext): String =
        when (appearance) {
            is AppearanceContext.Custom ->
                WatchThemeRepository(requireContext())
                        .activeProfile(PreferenceManager.getDefaultSharedPreferences(requireContext()))
                        ?.name ?: getString(R.string.watch_theme_unknown_name)
            is AppearanceContext.BuiltIn ->
                WatchThemeRepository.displayNameForFace(requireContext(), appearance.baseFace)
        }

private fun WatchFacePrefsFragment.dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

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
