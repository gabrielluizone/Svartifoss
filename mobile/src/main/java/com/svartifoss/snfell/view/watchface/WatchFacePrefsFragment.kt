package com.svartifoss.snfell.view.watchface

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiniButtonPlacement
import com.svartifoss.snfell.common.ColorModifier
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.SurfaceColorTreatment
import com.svartifoss.snfell.common.SurfacePaletteResolver
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.TrackMetadataFields
import com.svartifoss.snfell.common.WatchTypography
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.applyLyraDialogStyling
import com.svartifoss.snfell.view.settings.ColorTreatmentPreference
import com.svartifoss.snfell.view.settings.FaceScopedPreferenceDataStore
import com.svartifoss.snfell.view.settings.HexColorDotPreference
import com.svartifoss.snfell.view.settings.WatchFontCatalog
import com.svartifoss.snfell.view.settings.lyraRuntimeAccent
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import com.svartifoss.snfell.view.settings.PlaylistShortcutsActivity
import com.svartifoss.snfell.view.settings.SettingsCatalog
import com.svartifoss.snfell.view.settings.parseHexOrDefault
import com.svartifoss.snfell.view.settings.showLyraColorPickerDialog
import com.svartifoss.snfell.view.settings.scrollToAndPulsePreference
import com.svartifoss.snfell.view.settings.tintOpenLyraPreferenceDialog
import com.matejdro.wearutils.preferences.compat.PreferenceFragmentCompatEx

/**
 * The preference-list half of the Watch tab (see [WatchFaceFragment]), filtered into focused
 * appearance sections. [WatchPreviewView] follows the focused row, switching between the player,
 * ambient screen, overlays, queue and mini-button examples. Behavior settings (gestures, crown,
 * automation, ...) stay in Settings.
 */
class WatchFacePrefsFragment : PreferenceFragmentCompatEx() {

    companion object {
        const val SECTION_STYLE = "style"
        const val SECTION_BACKGROUND = "background"
        const val SECTION_COLORS = "colors"
        const val SECTION_TYPOGRAPHY = "typography"
        const val SECTION_AOD = "aod"
        const val SECTION_PANELS = "panels"
        const val SECTION_MINI_BUTTONS = "miniButtons"
        private const val ARG_SECTION = "watchAppearanceSection"
        private const val ARG_HIGHLIGHT_KEY = "watchAppearanceHighlightKey"
        private const val STATE_TYPOGRAPHY_TARGET = "watchTypographyTarget"
        private const val STATE_COLOR_TARGET = "watchColorTarget"
        private const val STATE_PANEL_TARGET = "watchPanelTarget"
        private const val DEFAULT_SWATCH_COLOR = 0xFF86A69D.toInt()
        private const val TYPOGRAPHY_EDITOR_CATEGORY = "cat_wf_typography_editor"
        private const val TYPOGRAPHY_EDITOR_KEY = "typography_editor_surface"
        private const val TYPOGRAPHY_SIZE_STEP = 5
        private const val COLOR_EDITOR_CATEGORY = "cat_wf_colors_editor"
        private const val COLOR_EDITOR_KEY = "color_editor_surface"
        private const val PANEL_EDITOR_CATEGORY = "cat_wf_panels_editor"
        private const val PANEL_EDITOR_KEY = "panel_editor_surface"
        private const val PLAYER_EDITOR_CATEGORY = "cat_wf_player_editor"
        private const val PLAYER_EDITOR_KEY = "player_editor_surface"
        /** Stands in wherever a value names no fixed colour, matching HexColorDotPreference. */
        private const val UNSET_SWATCH_COLOR = 0x40808080

        /** [highlightKey] scrolls the page to that preference once laid out - set only by the
         *  settings search, so a result lands on the row itself rather than at the top of a page
         *  the user then has to scan. */
        fun newInstance(section: String, highlightKey: String? = null) = WatchFacePrefsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SECTION, section)
                putString(ARG_HIGHLIGHT_KEY, highlightKey)
            }
        }

        /** Restores every built-in face to its factory look - the recovery action for "someone
         *  changed the built-in themes and can't get back". Deactivates any active custom theme
         *  first (so the reset is actually visible and can't rewrite a saved profile), then clears
         *  every scoped appearance value for all faces plus the custom snapshot scope and the
         *  legacy global keys, so each face falls back purely to its per-face definition default.
         *  The chosen face, saved custom themes and behaviour prefs are left untouched. Removing
         *  the flat scoped keys triggers the normal phone -> watch sync.
         *
         *  [FaceResetMigrationPrompt] is now its only caller: the Player page no longer offers a
         *  "reset all faces" row. The one-shot upgrade prompt is where a whole-library reset is
         *  actually warranted, and it explains why it is asking; the same button sitting
         *  permanently beside "reset this layout" was one mistap away from discarding every face
         *  the user had tuned. Keep the logic here rather than moving it into the prompt - it is
         *  the counterpart to [resetCurrentFaceAppearance] and belongs beside it. */
        fun resetAllFaces(context: android.content.Context, prefs: SharedPreferences) {
            val currentFace = ThemeAppearance.resolve(prefs).baseFace
            WatchThemeRepository(context).applyBuiltIn(prefs, currentFace)

            val editor = prefs.edit()
            for (baseKey in FaceScopedPreferences.SCOPED_KEYS) {
                for (face in ThemeAppearance.ALLOWED_BASE_FACES) {
                    editor.remove(FaceScopedPreferences.scopedKey(baseKey, face))
                }
                editor.remove(FaceScopedPreferences.scopedKey(baseKey, ThemeAppearance.CUSTOM_SCOPE))
                editor.remove(baseKey)
            }
            editor.apply()
        }
    }

    private var section = SECTION_STYLE

    /** The real backing store; appearance keys are scoped per face through [store]. */
    private lateinit var rawPrefs: SharedPreferences
    private lateinit var store: FaceScopedPreferenceDataStore
    private var typographyTarget = TypographyTarget.TITLE
    private var typographyEditor: TypographyEditorPreference? = null
    private var colorTarget = ColorTarget.TITLE
    private var colorEditor: ColorEditorPreference? = null
    private var panelTarget = PanelTarget.VOLUME
    private var panelEditor: PanelEditorPreference? = null
    private var playerEditor: PlayerEditorPreference? = null

    /** Re-reads scoped values after a face change and refreshes archived lists when their
     *  developer switch changes, even if this fragment remained alive beside Settings. */
    private val faceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val baseKey = key?.substringBefore(FaceScopedPreferences.SCOPE_SEPARATOR)
        when (baseKey) {
            MiscPreferences.WEAR_SCREEN_FACE.key,
            MiscPreferences.WEAR_ACTIVE_CUSTOM_THEME_ID.key,
            MiscPreferences.WEAR_CUSTOM_THEME_SCHEMA.key,
            MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.key -> {
                migrateLegacyColorSettings()
                rebindScopedValues()
                refreshColorTargetSummaries()
                refreshConditionalPreferences()
                refreshTypographyEditor()
                refreshColorEditor()
                refreshPanelEditor()
                refreshPlayerEditor()
            }
            "dev_show_archived" -> {
                applyArchivedOptionFilters()
                refreshTypographyEditor()
                // The overlay backdrop list hides an archived value, so its label can change here.
                refreshPanelEditor()
                // The face picker hides archived faces the same way.
                refreshPlayerEditor()
            }
            in TypographyEditorModel.keys,
            MiscPreferences.WEAR_SHOW_SOURCE_ICON.key -> {
                rebindScopedValues()
                refreshTypographyEditor()
                // The title/artist visibility switches live on Text but disable colour rows here.
                refreshColorEditor()
            }
            in ColorEditorModel.keys -> {
                rebindScopedValues()
                refreshColorEditor()
            }
            in PanelEditorModel.keys,
            // Not panel rows themselves, but the two edge-progress switches on Player decide
            // whether the Seek tab's ring controls apply at all.
            MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE.key,
            MiscPreferences.WEAR_EDGE_SEEK_ENABLED.key -> {
                rebindScopedValues()
                refreshPanelEditor()
                refreshPlayerEditor()
            }
            in PlayerEditorModel.keys -> {
                rebindScopedValues()
                refreshPlayerEditor()
            }
            else -> if (LyraAccent.affectsResolvedColor(baseKey)) {
                refreshTypographyEditor()
                refreshColorEditor()
                refreshPanelEditor()
                refreshPlayerEditor()
            }
        }
    }

    /** Values hidden from their normal pickers because they are archived. They come back when the
     *  developer-mode "Show archived options" switch is on. A value currently selected always
     *  stays listed so an existing configuration can be understood and changed without migration. */
    private val archivedFaces = com.svartifoss.snfell.view.watchface.theme.ArchivedFaces.KEYS
    private val archivedFonts = setOf("typewriter")
    /** "liquid_glass" shipped and did not work in practice - archived rather than removed. */
    private val archivedOverlayBackdrops = setOf("liquid_glass")
    private val archivedMiniButtonBackgrounds = setOf("solid_theme")
    private val archivedMiniButtonShapes = setOf(
            "pill_wide_large", "pill_wide_xlarge", "rounded_rect_medium", "rounded_rect_large")

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        section = arguments?.getString(ARG_SECTION) ?: SECTION_STYLE
        typographyTarget = savedInstanceState?.getString(STATE_TYPOGRAPHY_TARGET)
                ?.let { saved -> runCatching { TypographyTarget.valueOf(saved) }.getOrNull() }
                ?: TypographyTarget.TITLE
        colorTarget = savedInstanceState?.getString(STATE_COLOR_TARGET)
                ?.let { saved -> runCatching { ColorTarget.valueOf(saved) }.getOrNull() }
                ?: ColorTarget.TITLE
        panelTarget = savedInstanceState?.getString(STATE_PANEL_TARGET)
                ?.let { saved -> runCatching { PanelTarget.valueOf(saved) }.getOrNull() }
                ?: PanelTarget.VOLUME
        // Scope every appearance preference to the selected face BEFORE inflating, so each control
        // reads/writes "<key>@<face>". Must be set before addPreferencesFromResource.
        rawPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        store = FaceScopedPreferenceDataStore(rawPrefs)
        migrateLegacyColorSettings()
        preferenceManager.preferenceDataStore = store
        addPreferencesFromResource(R.xml.watch_face_settings)
        wirePreferences()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TYPOGRAPHY_TARGET, typographyTarget.name)
        outState.putString(STATE_COLOR_TARGET, colorTarget.name)
        outState.putString(STATE_PANEL_TARGET, panelTarget.name)
        super.onSaveInstanceState(outState)
    }

    /** Everything that turns the inflated preference tree into a working screen. Extracted so
     *  [resetCurrentFaceAppearance] can rebuild the screen from the (now cleared) values. */
    private fun wirePreferences() {
        applyArchivedOptionFilters()
        initListSummaries()
        initFaceDependencies()
        initBlurRadiusDependency()
        initTypographyDependencies()
        initMiniButtonOpacityValidation()
        initAodPercentageValidation()
        initTypographyValidation()
        initUnifiedColorTreatment()
        initAccentColorTarget(
                modeKey = "wear_title_color_mode",
                customColorKey = "wear_title_custom_color",
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_title_custom_color
        )
        initAccentColorTarget(
                modeKey = "wear_artist_color_mode",
                customColorKey = "wear_artist_custom_color",
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_artist_custom_color_description
        )
        initAccentColorTarget(
                modeKey = "wear_progress_color_mode",
                customColorKey = "wear_progress_custom_color",
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_progress_custom_color_description
        )
        initAccentColorTarget(
                modeKey = "wear_volume_color_mode",
                customColorKey = "wear_volume_custom_color",
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_volume_custom_color_description
        )
        initAccentColorTarget(
                modeKey = "wear_quick_panel_color_mode",
                customColorKey = "wear_quick_panel_custom_color",
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_quick_panel_custom_color_description
        )
        // Shading color modes are black/album/desaturated/custom; only "custom" reveals the color
        // row, which the shared dependency logic already produces since there is no "normal" here.
        initAccentColorTarget(
                modeKey = "wear_shading_color_mode",
                customColorKey = "wear_shading_custom_color",
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_shading_custom_color_description
        )
        initAppearanceResetActions()
        initAccentColorTarget(
                modeKey = "wear_aod_color_mode",
                customColorKey = "wear_aod_custom_color",
                // No desaturate toggle for the AOD tint - the watch already lifts/clamps its
                // lightness for legibility on the pure-black ambient background.
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_aod_custom_color_description
        )
        // Clock colour modes are white/dynamic/album/custom; only "custom" reveals the colour row.
        initAccentColorTarget(
                modeKey = "wear_clock_color_mode",
                customColorKey = "wear_clock_custom_color",
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_clock_custom_color_description
        )
        findPreference<Preference>("screen_buttons_hint")?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    (activity as? com.svartifoss.snfell.view.mainactivity.MainActivity)?.openControls()
                    true
                }
        initStreamingShortcutsGuide()
        initTypographyEditor()
        initColorEditor()
        initPanelEditor()
        initPlayerEditor()
        applySectionVisibility()
        wirePreviewInteractions()
    }

    private fun initListSummaries() {
        listOf(
            "wear_screen_face",
            "wear_expressive_seek_mode",
            "wear_title_text_mode",
            "wear_track_time_mode",
            "wear_aod_style",
            "wear_aod_art_treatment",
            "wear_aod_color_mode",
            "wear_overlay_backdrop_style",
            "wear_volume_style",
            "wear_volume_layout",
            "wear_seek_style",
            "wear_seek_layout",
            "wear_quick_panel_style",
            "wear_quick_panel_layout",
            "wear_quick_panel_source",
            "wear_queue_style",
            "album_art_style",
            "wear_player_shading_style",
            "wear_color_treatment",
            "wear_artist_color_mode",
            "wear_progress_color_mode",
            "wear_volume_color_mode",
            "wear_quick_panel_color_mode",
            "wear_shading_color_mode",
            "wear_progress_style",
            "screen_buttons_bg_style",
            "screen_buttons_shape"
        ).forEach { key ->
            findPreference<ListPreference>(key)?.summaryProvider =
                ListPreference.SimpleSummaryProvider.getInstance()
        }
    }

    /**
     * Replaces the visual wall of Typography preferences with one contextual editor while keeping
     * those original Preference objects as the authority for storage, validation and dialogs.
     */
    private fun initTypographyEditor() {
        val editor = findPreference<TypographyEditorPreference>(TYPOGRAPHY_EDITOR_KEY) ?: return
        typographyEditor = editor
        editor.bindEditor = ::bindTypographyEditor
        editor.refresh()
    }

    private fun refreshTypographyEditor() {
        typographyEditor?.refresh()
    }

    private fun bindTypographyEditor(root: View) {
        root.setTag(R.id.tag_handles_accent_locally, true)
        root.disableScrollbarsInSubtree()
        val targetGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.typography_target_group)
        targetGroup.clearOnButtonCheckedListeners()
        targetGroup.check(buttonIdFor(typographyTarget))
        targetGroup.addOnButtonCheckedListener { _, checkedId, checked ->
            if (!checked) return@addOnButtonCheckedListener
            typographyTarget = targetForButtonId(checkedId) ?: return@addOnButtonCheckedListener
            renderTypographyEditor(root)
            resetTypographyToolbarScroll(root)
            focusTypographyTarget(typographyTarget)
        }

        root.findViewById<MaterialButton>(R.id.typography_font_button).setOnClickListener {
            openPreferenceDialog(MiscPreferences.WEAR_FONT.key)
        }
        root.findViewById<MaterialButton>(R.id.typography_flex_button).setOnClickListener {
            showFlexAxesDialog()
        }
        root.findViewById<MaterialButton>(R.id.typography_element_font_button).setOnClickListener {
            elementFontKey(typographyTarget)?.let(::openPreferenceDialog)
        }
        root.findViewById<MaterialButton>(R.id.typography_element_flex_button).setOnClickListener {
            showFlexAxesDialog(typographyTarget)
        }
        root.findViewById<MaterialButton>(R.id.typography_weight_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.WEIGHT)?.let(::showTypographySlider)
        }
        root.findViewById<MaterialButton>(R.id.typography_italic_button).setOnClickListener { view ->
            val key = settingKey(typographyTarget, TypographyControl.ITALIC) ?: return@setOnClickListener
            commitTypographyBoolean(key, (view as MaterialButton).isChecked)
        }
        root.findViewById<MaterialButton>(R.id.typography_size_decrease_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.SIZE)?.let {
                adjustTypographyNumber(it, -TYPOGRAPHY_SIZE_STEP)
            }
        }
        root.findViewById<MaterialButton>(R.id.typography_size_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.SIZE)?.let(::showTypographySlider)
        }
        root.findViewById<MaterialButton>(R.id.typography_size_increase_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.SIZE)?.let {
                adjustTypographyNumber(it, TYPOGRAPHY_SIZE_STEP)
            }
        }
        root.findViewById<MaterialButton>(R.id.typography_opacity_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.OPACITY)?.let(::showTypographySlider)
        }
        root.findViewById<MaterialButton>(R.id.typography_tracking_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.TRACKING)?.let(::showTypographySlider)
        }
        root.findViewById<MaterialButton>(R.id.typography_case_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.CASE)?.let(::openPreferenceDialog)
        }
        root.findViewById<MaterialButton>(R.id.typography_behavior_button).setOnClickListener {
            openPreferenceDialog(MiscPreferences.WEAR_TITLE_TEXT_MODE.key)
        }

        renderTypographyEditor(root)
    }

    @SuppressLint("SetTextI18n") // B, percentages and +/- are locale-independent editor notation.
    private fun renderTypographyEditor(root: View) {
        val fontValue = readStringPreference(
                MiscPreferences.WEAR_FONT.key,
                MiscPreferences.WEAR_FONT.defaultValue)
        bindFontButton(
                root.findViewById(R.id.typography_font_button),
                MiscPreferences.WEAR_FONT.key,
                MiscPreferences.WEAR_FONT.defaultValue,
                fontValue)

        bindTypographySwitch(
                root.findViewById(R.id.typography_all_screens_switch),
                MiscPreferences.WEAR_FONT_ALL_SCREENS.key,
                MiscPreferences.WEAR_FONT_ALL_SCREENS.defaultValue)
        // The global axes belong to the global title/artist family. An element that explicitly
        // selects Flex gets its own axes button below its font picker instead of editing this set.
        root.findViewById<View>(R.id.typography_flex_button).isVisible =
                WatchTypography.isFlexFont(fontValue)

        val targetGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.typography_target_group)
        if (targetGroup.checkedButtonId != buttonIdFor(typographyTarget)) {
            targetGroup.clearOnButtonCheckedListeners()
            targetGroup.check(buttonIdFor(typographyTarget))
            targetGroup.addOnButtonCheckedListener { _, checkedId, checked ->
                if (checked) {
                    typographyTarget = targetForButtonId(checkedId) ?: typographyTarget
                    renderTypographyEditor(root)
                    resetTypographyToolbarScroll(root)
                    focusTypographyTarget(typographyTarget)
                }
            }
        }

        val visibilityKey = visibilityKey(typographyTarget)
        val visibilitySwitch = root.findViewById<SwitchMaterial>(R.id.typography_visibility_switch)
        visibilitySwitch.isVisible = visibilityKey != null
        val elementVisible = visibilityKey?.let { key ->
            val defaultValue = if (key == MiscPreferences.WEAR_SHOW_SOURCE_ICON.key) {
                MiscPreferences.WEAR_SHOW_SOURCE_ICON.defaultValue
            } else {
                (TypographyEditorModel.specFor(key)?.value as? TypographyValueSpec.Toggle)
                        ?.defaultValue ?: true
            }
            bindTypographySwitch(visibilitySwitch, key, defaultValue)
            store.getBoolean(key, defaultValue)
        } ?: true

        val elementFontKey = elementFontKey(typographyTarget)
        val elementFontButton = root.findViewById<MaterialButton>(R.id.typography_element_font_button)
        elementFontButton.isVisible = elementFontKey != null
        if (elementFontKey != null) {
            bindFontButton(
                    elementFontButton,
                    elementFontKey,
                    choiceDefault(elementFontKey),
                    effectiveFontFor(typographyTarget, fontValue) ?: fontValue)
        }
        val elementFlexButton = root.findViewById<MaterialButton>(
                R.id.typography_element_flex_button)
        elementFlexButton.isVisible = isExplicitElementFlexFont(typographyTarget)
        if (elementFlexButton.isVisible) {
            val label = getString(R.string.category_wf_typography_flex)
            elementFlexButton.text = label
            elementFlexButton.contentDescription = "${typographyTargetLabel(typographyTarget)}. $label"
        }

        val toolbar = root.findViewById<View>(R.id.typography_toolbar_scroll)
        toolbar.isVisible = typographyTarget != TypographyTarget.LYRICS

        val weightKey = settingKey(typographyTarget, TypographyControl.WEIGHT)
        val weightButton = root.findViewById<MaterialButton>(R.id.typography_weight_button)
        weightButton.isVisible = weightKey != null
        weightKey?.let { key ->
            val value = readTypographyNumber(key)
            weightButton.text = "B $value"
            describeNumericButton(weightButton, key, value)
        }

        val italicKey = settingKey(typographyTarget, TypographyControl.ITALIC)
        val italicButton = root.findViewById<MaterialButton>(R.id.typography_italic_button)
        italicButton.isVisible = italicKey != null
        if (italicKey != null) {
            val defaultValue = (TypographyEditorModel.specFor(italicKey)?.value
                    as? TypographyValueSpec.Toggle)?.defaultValue ?: false
            italicButton.isChecked = store.getBoolean(italicKey, defaultValue)
            italicButton.contentDescription = findPreference<Preference>(italicKey)?.title
        }

        val sizeKey = settingKey(typographyTarget, TypographyControl.SIZE)
        val sizeControls = listOf(
                root.findViewById<MaterialButton>(R.id.typography_size_decrease_button),
                root.findViewById<MaterialButton>(R.id.typography_size_button),
                root.findViewById<MaterialButton>(R.id.typography_size_increase_button))
        sizeControls.forEach { it.isVisible = sizeKey != null }
        sizeKey?.let { key ->
            val value = readTypographyNumber(key)
            sizeControls[0].contentDescription = buildPreferenceDescription(key, "−")
            sizeControls[1].text = "$value%"
            describeNumericButton(sizeControls[1], key, value)
            sizeControls[2].contentDescription = buildPreferenceDescription(key, "+")
        }

        val opacityKey = settingKey(typographyTarget, TypographyControl.OPACITY)
        val opacityButton = root.findViewById<MaterialButton>(R.id.typography_opacity_button)
        opacityButton.isVisible = opacityKey != null
        opacityKey?.let { key ->
            val value = readTypographyNumber(key)
            opacityButton.text = "$value%"
            describeNumericButton(opacityButton, key, value)
        }

        val trackingKey = settingKey(typographyTarget, TypographyControl.TRACKING)
        val trackingButton = root.findViewById<MaterialButton>(R.id.typography_tracking_button)
        trackingButton.isVisible = trackingKey != null
        trackingKey?.let { key ->
            val value = readTypographyNumber(key)
            trackingButton.text = if (value > 0) "+$value" else value.toString()
            describeNumericButton(trackingButton, key, value)
        }

        // Icon-only: the icon itself is the value, so it swaps to match the current choice
        // instead of carrying a static glyph the way every numeric neighbour does.
        val caseKey = settingKey(typographyTarget, TypographyControl.CASE)
        val caseButton = root.findViewById<MaterialButton>(R.id.typography_case_button)
        caseButton.isVisible = caseKey != null
        caseKey?.let { key ->
            val value = readStringPreference(key, choiceDefault(key))
            caseButton.icon = ContextCompat.getDrawable(requireContext(), when (
                    com.svartifoss.snfell.common.TextCase.fromPreference(value)) {
                com.svartifoss.snfell.common.TextCase.UPPERCASE -> R.drawable.ic_uppercase
                com.svartifoss.snfell.common.TextCase.LOWERCASE -> R.drawable.ic_lowercase
                com.svartifoss.snfell.common.TextCase.NORMAL -> R.drawable.ic_match_case
            })
            caseButton.contentDescription =
                    buildPreferenceDescription(key, choiceLabel(key, value))
        }

        val behaviorButton = root.findViewById<MaterialButton>(R.id.typography_behavior_button)
        behaviorButton.isVisible = typographyTarget == TypographyTarget.TITLE
        if (behaviorButton.isVisible) {
            val value = readStringPreference(
                    MiscPreferences.WEAR_TITLE_TEXT_MODE.key,
                    MiscPreferences.WEAR_TITLE_TEXT_MODE.defaultValue)
            behaviorButton.text = choiceLabel(MiscPreferences.WEAR_TITLE_TEXT_MODE.key, value)
            behaviorButton.contentDescription = buildPreferenceDescription(
                    MiscPreferences.WEAR_TITLE_TEXT_MODE.key, behaviorButton.text)
        }

        listOfNotNull(
                elementFontButton.takeIf { it.isVisible },
                elementFlexButton.takeIf { it.isVisible },
                weightButton.takeIf { it.isVisible },
                italicButton.takeIf { it.isVisible },
                *sizeControls.filter { it.isVisible }.toTypedArray(),
                opacityButton.takeIf { it.isVisible },
                trackingButton.takeIf { it.isVisible },
                caseButton.takeIf { it.isVisible },
                behaviorButton.takeIf { it.isVisible }
        ).forEach { it.isEnabled = elementVisible }

        tintTypographyEditor(root)
    }

    private fun bindTypographySwitch(
            switch: SwitchMaterial,
            key: String,
            defaultValue: Boolean
    ) {
        switch.setOnCheckedChangeListener(null)
        switch.text = findPreference<Preference>(key)?.title ?: switch.text
        switch.isChecked = store.getBoolean(key, defaultValue)
        // SwitchMaterial already announces its checked state in the active locale.
        switch.contentDescription = switch.text
        switch.setOnCheckedChangeListener { _, checked ->
            commitTypographyBoolean(key, checked)
        }
    }

    private fun bindFontButton(
            button: MaterialButton,
            key: String,
            defaultValue: String,
            previewFontValue: String
    ) {
        val selected = readStringPreference(key, defaultValue)
        val label = choiceLabel(key, selected)
        button.text = label
        button.typeface = WatchFontCatalog.previewTypefaceFor(requireContext(), previewFontValue)
        button.contentDescription = buildPreferenceDescription(key, label)
    }

    private fun tintTypographyEditor(root: View) {
        val surface = ContextCompat.getColor(requireContext(), R.color.lyra_surface)
        val rawAccent = lyraRuntimeAccent()
        val accent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 3.0)
        val textAccent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 4.5)
        val onAccent = LyraAccent.foregroundFor(accent)
        val onSurface = ContextCompat.getColor(requireContext(), R.color.lyra_on_surface)
        val secondary = ContextCompat.getColor(requireContext(), R.color.lyra_text_secondary)
        val divider = ContextCompat.getColor(requireContext(), R.color.lyra_divider)
        val checkedStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf())
        val fills = ColorStateList(
                checkedStates,
                intArrayOf(Color.TRANSPARENT, accent, Color.TRANSPARENT))
        val foregrounds = ColorStateList(checkedStates, intArrayOf(secondary, onAccent, onSurface))
        val strokes = ColorStateList(checkedStates, intArrayOf(divider, accent, divider))

        root.findViewById<TextView>(R.id.typography_font_heading).setTextColor(textAccent)

        listOf(
                R.id.typography_target_title,
                R.id.typography_target_artist,
                R.id.typography_target_track_time,
                R.id.typography_target_clock,
                R.id.typography_target_icon,
                R.id.typography_target_lyrics,
                R.id.typography_italic_button
        ).forEach { id ->
            root.findViewById<MaterialButton>(id)?.apply {
                backgroundTintList = fills
                setTextColor(foregrounds)
                strokeColor = strokes
            }
        }

        val neutralStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf())
        val neutralForegrounds = ColorStateList(neutralStates, intArrayOf(secondary, onSurface))
        val neutralFills = ColorStateList(
                neutralStates,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT))
        val neutralStrokes = ColorStateList(neutralStates, intArrayOf(divider, divider))
        listOf(
                R.id.typography_weight_button,
                R.id.typography_size_decrease_button,
                R.id.typography_size_button,
                R.id.typography_size_increase_button,
                R.id.typography_opacity_button,
                R.id.typography_tracking_button,
                R.id.typography_case_button
        ).forEach { id ->
            root.findViewById<MaterialButton>(id)?.apply {
                backgroundTintList = neutralFills
                setTextColor(neutralForegrounds)
                iconTint = neutralForegrounds
                strokeColor = neutralStrokes
            }
        }

        val switchStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf())
        val disabled = divider
        val thumb = ColorStateList(
                switchStates,
                intArrayOf(disabled, accent, ContextCompat.getColor(requireContext(), R.color.lyra_stone)))
        val track = ColorStateList(
                switchStates,
                intArrayOf(
                        ColorUtils.setAlphaComponent(disabled, 0x60),
                        ColorUtils.setAlphaComponent(accent, 0x80),
                        divider))
        listOf(
                root.findViewById<SwitchMaterial>(R.id.typography_all_screens_switch),
                root.findViewById<SwitchMaterial>(R.id.typography_visibility_switch)
        ).forEach {
            it.thumbTintList = thumb
            it.trackTintList = track
            it.jumpDrawablesToCurrentState()
        }
    }

    /**
     * Clears scrollbars on every view of the editor, not just the two horizontal rails.
     *
     * The rails were the obvious suspects and they were innocent - their `scrollbars="none"` was
     * always compiled in. The bar was drawn by the *buttons*: a MaterialButton carrying an icon
     * ends up with content taller than the space its insets leave, so the button reports a
     * vertical scroll range and paints a thumb at the right edge of its own content box. That is
     * why only the icon-bearing buttons showed one, and why the thumb moved when their padding
     * changed. Nothing in this editor is meant to scroll visibly, so the flag comes off the whole
     * subtree rather than off the one widget class that happens to expose it today.
     */
    private fun View.disableScrollbarsInSubtree() {
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
        if (this is ViewGroup) {
            for (index in 0 until childCount) getChildAt(index).disableScrollbarsInSubtree()
        }
    }

    private fun resetTypographyToolbarScroll(root: View) {
        root.findViewById<HorizontalScrollView>(R.id.typography_toolbar_scroll)
                .smoothScrollTo(0, 0)
    }

    /** Opens the real Preference's own dialog, so both compact editors stay a view over it. */
    private fun openPreferenceDialog(key: String) {
        val preference = findPreference<Preference>(key) ?: return
        notifyPreviewInteraction(key, null)
        onDisplayPreferenceDialog(preference)
    }

    /**
     * Runs the colour row's existing click listener rather than reimplementing the picker.
     *
     * [initAccentColorTarget] and [initUnifiedColorTreatment] already wire every hex row to the
     * shared Lyra picker with that row's own reset/apply/preview behaviour, so going through the
     * listener keeps one implementation instead of a second that would drift on the part which is
     * easy to forget: reset persists "" rather than removing the key, because preference sync does
     * not transmit removals and the watch would keep rendering the old colour forever.
     */
    private fun openColorPicker(key: String) {
        val preference = findPreference<Preference>(key) ?: return
        notifyPreviewInteraction(key, null)
        preference.onPreferenceClickListener?.onPreferenceClick(preference)
    }

    private fun showTypographySlider(key: String) {
        val number = (TypographyEditorModel.specFor(key)?.value as? TypographyValueSpec.Number)
                ?: return
        val control = TypographyEditorModel.specFor(key)?.control ?: return
        showNumericSlider(
                key,
                number.range,
                number.defaultValue,
                format = { formatTypographyValue(control, it) },
                onCommit = { value -> commitTypographyNumber(key, value) })
    }

    private fun showColorSlider(key: String) {
        val number = (ColorEditorModel.specFor(key)?.value as? ColorValueSpec.Number) ?: return
        val control = ColorEditorModel.specFor(key)?.control ?: return
        showNumericSlider(
                key,
                number.range,
                number.defaultValue,
                format = { formatColorValue(control, it) },
                onCommit = { value -> commitColorNumber(key, value) })
    }

    /**
     * The one numeric-slider dialog both compact editors use.
     *
     * Shared rather than copied because the parts that are easy to get subtly different are the
     * ones a user notices: Cancel and dismiss must both restore the preview to the value the
     * dialog opened on, and Reset must move the slider *and* re-preview rather than silently
     * writing a default the screen never showed.
     */
    private fun showNumericSlider(
            key: String,
            range: IntRange,
            defaultValue: Int,
            format: (Int) -> String,
            onCommit: (Int) -> Unit
    ) {
        val preference = findPreference<EditTextPreference>(key) ?: return
        val content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_typography_slider, null)
        val slider = content.findViewById<Slider>(R.id.typography_slider)
        val valueLabel = content.findViewById<TextView>(R.id.typography_slider_value)
        val minLabel = content.findViewById<TextView>(R.id.typography_slider_min)
        val maxLabel = content.findViewById<TextView>(R.id.typography_slider_max)
        val initial = store.getInt(key, defaultValue).coerceIn(range)
        var selected = initial

        slider.valueFrom = range.first.toFloat()
        slider.valueTo = range.last.toFloat()
        slider.stepSize = 1f
        slider.value = selected.toFloat()
        minLabel.text = format(range.first)
        maxLabel.text = format(range.last)
        fun renderValue(value: Int) {
            selected = value.coerceIn(range)
            valueLabel.text = format(selected)
            valueLabel.contentDescription = buildPreferenceDescription(key, valueLabel.text)
        }
        renderValue(selected)
        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            renderValue(value.toInt())
            notifyPreviewInteraction(key, selected.toString())
        }
        val dialog = AlertDialog.Builder(requireContext())
                .setTitle(preference.title)
                .setView(content)
                .setNeutralButton(R.string.pref_reset_default, null)
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    notifyPreviewInteraction(key, initial.toString())
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onCommit(selected)
                }
                .create()
        dialog.setOnCancelListener {
            notifyPreviewInteraction(key, initial.toString())
        }
        dialog.setOnShowListener {
            val accent = lyraRuntimeAccent()
            val surface = ContextCompat.getColor(requireContext(), R.color.lyra_surface)
            val controlAccent = LyraAccent.contrastSafe(accent, surface, 3.0)
            val textAccent = LyraAccent.contrastSafe(accent, surface, 4.5)
            slider.thumbTintList = ColorStateList.valueOf(controlAccent)
            slider.trackActiveTintList = ColorStateList.valueOf(controlAccent)
            slider.haloTintList = ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(controlAccent, 0x33))
            dialog.applyLyraDialogStyling(accent = controlAccent, positiveColor = textAccent)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                slider.value = defaultValue.toFloat()
                renderValue(defaultValue)
                notifyPreviewInteraction(key, defaultValue.toString())
            }
        }
        dialog.show()
    }

    /** Opens the four axes owned by the global family, or by one explicit element override. */
    private fun showFlexAxesDialog(target: TypographyTarget? = null) {
        val keys = flexAxesKeys(target) ?: return
        val labels = keys.map { key ->
            val title = findPreference<Preference>(key)?.title ?: key
            "$title · ${readTypographyNumber(key)}"
        }.toTypedArray()
        // No setMessage here. AlertController swaps the item list into the content panel only on
        // the branch where there is no message, so this dialog used to show the hint and *none*
        // of the four axes - the Flex button opened onto nothing to adjust.
        val dialog = AlertDialog.Builder(requireContext())
                .setTitle(
                        if (target == null) {
                            getString(R.string.category_wf_typography_flex)
                        } else {
                            "${typographyTargetLabel(target)} · " +
                                    getString(R.string.category_wf_typography_flex)
                        })
                .setItems(labels) { _, index -> showTypographySlider(keys[index]) }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.setOnShowListener {
            val surface = ContextCompat.getColor(requireContext(), R.color.lyra_surface)
            val accent = LyraAccent.contrastSafe(lyraRuntimeAccent(), surface, 4.5)
            dialog.applyLyraDialogStyling(accent = accent)
        }
        dialog.show()
    }

    private fun commitTypographyBoolean(key: String, value: Boolean) {
        val preference = findPreference<TwoStatePreference>(key) ?: return
        if (preference.callChangeListener(value)) {
            preference.isChecked = value
            refreshTypographyEditor()
        } else {
            refreshTypographyEditor()
        }
    }

    private fun commitTypographyNumber(key: String, value: Int) {
        val number = TypographyEditorModel.specFor(key)?.value as? TypographyValueSpec.Number
                ?: return
        val candidate = value.coerceIn(number.range).toString()
        val preference = findPreference<EditTextPreference>(key) ?: return
        if (preference.callChangeListener(candidate)) {
            preference.text = candidate
            refreshTypographyEditor()
        }
    }

    private fun adjustTypographyNumber(key: String, delta: Int) {
        val number = TypographyEditorModel.specFor(key)?.value as? TypographyValueSpec.Number
                ?: return
        commitTypographyNumber(key, (readTypographyNumber(key) + delta).coerceIn(number.range))
    }

    private fun readTypographyNumber(key: String): Int {
        val number = TypographyEditorModel.specFor(key)?.value as? TypographyValueSpec.Number
                ?: return 0
        return store.getInt(key, number.defaultValue).coerceIn(number.range)
    }

    private fun settingKey(target: TypographyTarget, control: TypographyControl): String? =
            TypographyEditorModel.specsFor(target)
                    .firstOrNull { it.control == control && it.persisted }
                    ?.key

    /**
     * The typeface [target] actually renders in: its own override, or the track font it follows.
     *
     * Every individual font override may name Flex while the track font is an entirely different
     * family - which is the case the Flex button has to notice. Track time's `follow` is special:
     * it intentionally preserves each face's own authored family, represented by null here.
     */
    private fun effectiveFontFor(target: TypographyTarget, globalFont: String): String? {
        val key = elementFontKey(target) ?: return globalFont
        val selected = readStringPreference(key, choiceDefault(key))
        return when (target) {
            TypographyTarget.TITLE -> WatchTypography.titleFontKey(selected, globalFont)
            TypographyTarget.ARTIST -> WatchTypography.artistFontKey(selected, globalFont)
            TypographyTarget.CLOCK -> WatchTypography.clockFontKey(selected, globalFont)
            TypographyTarget.LYRICS -> WatchTypography.lyricsFontKey(selected, globalFont)
            TypographyTarget.TRACK_TIME -> WatchTypography.trackTimeFontKey(selected)
            else -> globalFont
        }
    }

    private fun elementFontKey(target: TypographyTarget): String? = when (target) {
        TypographyTarget.TITLE -> MiscPreferences.WEAR_TITLE_FONT.key
        TypographyTarget.ARTIST -> MiscPreferences.WEAR_ARTIST_FONT.key
        TypographyTarget.TRACK_TIME -> MiscPreferences.WEAR_TRACK_TIME_FONT.key
        TypographyTarget.CLOCK -> MiscPreferences.WEAR_CLOCK_FONT.key
        TypographyTarget.LYRICS -> MiscPreferences.WEAR_LYRICS_FONT.key
        else -> null
    }

    /** True only when this element picked Flex itself, rather than following a global Flex font. */
    private fun isExplicitElementFlexFont(target: TypographyTarget): Boolean {
        val key = elementFontKey(target) ?: return false
        return readStringPreference(key, choiceDefault(key)) == WatchTypography.FLEX_FONT_KEY
    }

    /** The four persisted axis rows belonging to [target], or the global set for null. */
    private fun flexAxesKeys(target: TypographyTarget?): List<String>? = when (target) {
        null -> listOf(
                MiscPreferences.WEAR_FONT_FLEX_WIDTH.key,
                MiscPreferences.WEAR_FONT_FLEX_OPTICAL_SIZE.key,
                MiscPreferences.WEAR_FONT_FLEX_GRADE.key,
                MiscPreferences.WEAR_FONT_FLEX_ROUNDNESS.key)
        TypographyTarget.TITLE -> listOf(
                MiscPreferences.WEAR_TITLE_FONT_FLEX_WIDTH.key,
                MiscPreferences.WEAR_TITLE_FONT_FLEX_OPTICAL_SIZE.key,
                MiscPreferences.WEAR_TITLE_FONT_FLEX_GRADE.key,
                MiscPreferences.WEAR_TITLE_FONT_FLEX_ROUNDNESS.key)
        TypographyTarget.ARTIST -> listOf(
                MiscPreferences.WEAR_ARTIST_FONT_FLEX_WIDTH.key,
                MiscPreferences.WEAR_ARTIST_FONT_FLEX_OPTICAL_SIZE.key,
                MiscPreferences.WEAR_ARTIST_FONT_FLEX_GRADE.key,
                MiscPreferences.WEAR_ARTIST_FONT_FLEX_ROUNDNESS.key)
        TypographyTarget.CLOCK -> listOf(
                MiscPreferences.WEAR_CLOCK_FONT_FLEX_WIDTH.key,
                MiscPreferences.WEAR_CLOCK_FONT_FLEX_OPTICAL_SIZE.key,
                MiscPreferences.WEAR_CLOCK_FONT_FLEX_GRADE.key,
                MiscPreferences.WEAR_CLOCK_FONT_FLEX_ROUNDNESS.key)
        TypographyTarget.LYRICS -> listOf(
                MiscPreferences.WEAR_LYRICS_FONT_FLEX_WIDTH.key,
                MiscPreferences.WEAR_LYRICS_FONT_FLEX_OPTICAL_SIZE.key,
                MiscPreferences.WEAR_LYRICS_FONT_FLEX_GRADE.key,
                MiscPreferences.WEAR_LYRICS_FONT_FLEX_ROUNDNESS.key)
        TypographyTarget.TRACK_TIME -> listOf(
                MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_WIDTH.key,
                MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_OPTICAL_SIZE.key,
                MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_GRADE.key,
                MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_ROUNDNESS.key)
        else -> null
    }

    private fun typographyTargetLabel(target: TypographyTarget): String = getString(when (target) {
        TypographyTarget.TITLE -> R.string.category_wf_typography_title
        TypographyTarget.ARTIST -> R.string.category_wf_typography_artist
        TypographyTarget.TRACK_TIME -> R.string.category_wf_typography_track_time
        TypographyTarget.CLOCK -> R.string.category_wf_clock
        TypographyTarget.ICON -> R.string.category_wf_typography_icon
        TypographyTarget.LYRICS -> R.string.action_open_lyrics
    })

    private fun visibilityKey(target: TypographyTarget): String? = when (target) {
        TypographyTarget.TITLE -> MiscPreferences.WEAR_SHOW_TRACK_TITLE.key
        TypographyTarget.ARTIST -> MiscPreferences.WEAR_SHOW_TRACK_ARTIST.key
        TypographyTarget.ICON -> MiscPreferences.WEAR_SHOW_SOURCE_ICON.key
        else -> null
    }

    private fun choiceDefault(key: String): String =
            (TypographyEditorModel.specFor(key)?.value as? TypographyValueSpec.Choice)
                    ?.defaultValue ?: ""

    private fun choiceLabel(key: String, value: String): CharSequence {
        val preference = findPreference<ListPreference>(key) ?: return value
        val index = preference.findIndexOfValue(value)
        return preference.entries?.getOrNull(index) ?: value
    }

    private fun describeNumericButton(button: MaterialButton, key: String, value: Int) {
        button.contentDescription = buildPreferenceDescription(
                key,
                formatTypographyValue(
                        TypographyEditorModel.specFor(key)?.control ?: TypographyControl.WEIGHT,
                        value))
    }

    private fun buildPreferenceDescription(key: String, value: Any?): CharSequence {
        val title = findPreference<Preference>(key)?.title ?: key
        return "$title. $value"
    }

    private fun formatTypographyValue(control: TypographyControl, value: Int): String = when (control) {
        TypographyControl.SIZE,
        TypographyControl.OPACITY -> "$value%"
        TypographyControl.TRACKING -> String.format(java.util.Locale.getDefault(), "%.2f em", value / 100f)
        else -> value.toString()
    }

    private fun focusTypographyTarget(target: TypographyTarget) {
        val key = when (target) {
            TypographyTarget.TITLE -> MiscPreferences.WEAR_TITLE_FONT_WEIGHT.key
            TypographyTarget.ARTIST -> MiscPreferences.WEAR_ARTIST_FONT_WEIGHT.key
            TypographyTarget.TRACK_TIME -> MiscPreferences.WEAR_TRACK_TIME_FONT_WEIGHT.key
            TypographyTarget.CLOCK -> MiscPreferences.WEAR_CLOCK_FONT_WEIGHT.key
            TypographyTarget.ICON -> MiscPreferences.WEAR_SOURCE_ICON_SCALE.key
            TypographyTarget.LYRICS -> MiscPreferences.WEAR_LYRICS_FONT.key
        }
        notifyPreviewInteraction(key, null)
    }

    private fun buttonIdFor(target: TypographyTarget): Int = when (target) {
        TypographyTarget.TITLE -> R.id.typography_target_title
        TypographyTarget.ARTIST -> R.id.typography_target_artist
        TypographyTarget.TRACK_TIME -> R.id.typography_target_track_time
        TypographyTarget.CLOCK -> R.id.typography_target_clock
        TypographyTarget.ICON -> R.id.typography_target_icon
        TypographyTarget.LYRICS -> R.id.typography_target_lyrics
    }

    private fun targetForButtonId(id: Int): TypographyTarget? = when (id) {
        R.id.typography_target_title -> TypographyTarget.TITLE
        R.id.typography_target_artist -> TypographyTarget.ARTIST
        R.id.typography_target_track_time -> TypographyTarget.TRACK_TIME
        R.id.typography_target_clock -> TypographyTarget.CLOCK
        R.id.typography_target_icon -> TypographyTarget.ICON
        R.id.typography_target_lyrics -> TypographyTarget.LYRICS
        else -> null
    }

    private fun controlIdFor(target: TypographySearchTarget): Int = when (target.control) {
        TypographyControl.FONT -> R.id.typography_font_button
        TypographyControl.FONT_SCOPE -> R.id.typography_all_screens_switch
        TypographyControl.ELEMENT_FONT -> R.id.typography_element_font_button
        TypographyControl.VISIBILITY -> R.id.typography_visibility_switch
        TypographyControl.TEXT_BEHAVIOR -> R.id.typography_behavior_button
        TypographyControl.WEIGHT -> R.id.typography_weight_button
        TypographyControl.ITALIC -> R.id.typography_italic_button
        TypographyControl.SIZE -> R.id.typography_size_button
        TypographyControl.OPACITY -> R.id.typography_opacity_button
        TypographyControl.TRACKING -> R.id.typography_tracking_button
        TypographyControl.CASE -> R.id.typography_case_button
        TypographyControl.GLOBAL_FLEX -> R.id.typography_flex_button
        TypographyControl.FLEX -> R.id.typography_element_flex_button
    }


    /**
     * Replaces the visual wall of Colors preferences with one contextual editor, exactly as
     * [initTypographyEditor] does for Text, and on the same terms: those original Preference
     * objects stay the authority for storage, validation and dialogs.
     */
    private fun initColorEditor() {
        val editor = findPreference<ColorEditorPreference>(COLOR_EDITOR_KEY) ?: return
        colorEditor = editor
        editor.bindEditor = ::bindColorEditor
        editor.refresh()
    }

    private fun refreshColorEditor() {
        colorEditor?.refresh()
    }

    private fun bindColorEditor(root: View) {
        root.setTag(R.id.tag_handles_accent_locally, true)
        root.disableScrollbarsInSubtree()
        val targetGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.color_editor_target_group)
        targetGroup.clearOnButtonCheckedListeners()
        targetGroup.check(colorButtonIdFor(colorTarget))
        targetGroup.addOnButtonCheckedListener { _, checkedId, checked ->
            if (!checked) return@addOnButtonCheckedListener
            colorTarget = colorTargetForButtonId(checkedId) ?: return@addOnButtonCheckedListener
            renderColorEditor(root)
            focusColorTarget(colorTarget)
        }

        root.findViewById<MaterialButton>(R.id.color_editor_treatment_button).setOnClickListener {
            openPreferenceDialog(MiscPreferences.WEAR_COLOR_TREATMENT.key)
        }
        root.findViewById<MaterialButton>(R.id.color_editor_accent_source_button)
                .setOnClickListener {
                    openPreferenceDialog(MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE.key)
                }
        root.findViewById<MaterialButton>(R.id.color_editor_modifier_button).setOnClickListener {
            openPreferenceDialog(MiscPreferences.WEAR_COLOR_MODIFIER.key)
        }
        root.findViewById<MaterialButton>(R.id.color_editor_hue_shift_button).setOnClickListener {
            showColorSlider(MiscPreferences.WEAR_COLOR_HUE_SHIFT.key)
        }
        root.findViewById<MaterialButton>(R.id.color_editor_normal_color_button)
                .setOnClickListener { openColorPicker(MiscPreferences.WEAR_NORMAL_COLOR.key) }

        root.findViewById<MaterialButton>(R.id.color_editor_mode_button).setOnClickListener {
            ColorEditorModel.keyFor(colorTarget, ColorControl.MODE)?.let(::openPreferenceDialog)
        }
        root.findViewById<MaterialButton>(R.id.color_editor_custom_color_button)
                .setOnClickListener {
                    ColorEditorModel.keyFor(colorTarget, ColorControl.CUSTOM_COLOR)
                            ?.let(::openColorPicker)
                }
        root.findViewById<MaterialButton>(R.id.color_editor_opacity_button).setOnClickListener {
            ColorEditorModel.keyFor(colorTarget, ColorControl.OPACITY)?.let(::showColorSlider)
        }

        renderColorEditor(root)
    }

    @SuppressLint("SetTextI18n") // Degrees and percentages are locale-independent editor notation.
    private fun renderColorEditor(root: View) {
        val treatment = readStringPreference(
                MiscPreferences.WEAR_COLOR_TREATMENT.key,
                MiscPreferences.WEAR_COLOR_TREATMENT.defaultValue)

        bindColorChoiceButton(
                root.findViewById(R.id.color_editor_treatment_button),
                MiscPreferences.WEAR_COLOR_TREATMENT.key)
        bindColorChoiceButton(
                root.findViewById(R.id.color_editor_accent_source_button),
                MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE.key)
        bindColorChoiceButton(
                root.findViewById(R.id.color_editor_modifier_button),
                MiscPreferences.WEAR_COLOR_MODIFIER.key)

        val hueShift = store.getInt(
                MiscPreferences.WEAR_COLOR_HUE_SHIFT.key,
                MiscPreferences.WEAR_COLOR_HUE_SHIFT.defaultValue)
                .coerceIn(ColorEditorModel.HUE_SHIFT_RANGE)
        val hueButton = root.findViewById<MaterialButton>(R.id.color_editor_hue_shift_button)
        hueButton.text = formatColorValue(ColorControl.HUE_SHIFT, hueShift)
        hueButton.contentDescription = buildPreferenceDescription(
                MiscPreferences.WEAR_COLOR_HUE_SHIFT.key, hueButton.text)

        // The palette the page works from, resolved through the same `common` resolver the watch
        // and the preview use - so these three dots cannot promise colours the watch renders
        // differently, and a treatment added later shows up here untouched.
        val globalTriad = resolveEditorTriad(treatment, customColorKey = null)
        listOf(
                R.id.color_editor_swatch_primary to globalTriad.first,
                R.id.color_editor_swatch_secondary to globalTriad.second,
                R.id.color_editor_swatch_tertiary to globalTriad.third
        ).forEach { (id, color) ->
            root.findViewById<ImageView>(id).imageTintList = ColorStateList.valueOf(color)
        }

        // The picker belongs to Normal alone: every other treatment derives its colour from the
        // artwork and has nothing for a picker to set. Mirrors updateUnifiedColorTreatmentVisibility.
        val normalColorButton = root.findViewById<MaterialButton>(
                R.id.color_editor_normal_color_button)
        normalColorButton.isVisible = treatment == "normal"
        if (normalColorButton.isVisible) {
            bindColorSwatchButton(
                    normalColorButton,
                    MiscPreferences.WEAR_NORMAL_COLOR.key,
                    R.string.setting_wear_normal_color)
        }

        bindColorSwitch(
                root.findViewById(R.id.color_editor_palette_switch),
                MiscPreferences.WEAR_NORMAL_COLOR_MULTI.key,
                MiscPreferences.WEAR_NORMAL_COLOR_MULTI.defaultValue)

        val targetGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.color_editor_target_group)
        if (targetGroup.checkedButtonId != colorButtonIdFor(colorTarget)) {
            targetGroup.clearOnButtonCheckedListeners()
            targetGroup.check(colorButtonIdFor(colorTarget))
            targetGroup.addOnButtonCheckedListener { _, checkedId, checked ->
                if (checked) {
                    colorTarget = colorTargetForButtonId(checkedId) ?: colorTarget
                    renderColorEditor(root)
                    focusColorTarget(colorTarget)
                }
            }
        }

        val modeKey = ColorEditorModel.keyFor(colorTarget, ColorControl.MODE)
        val modeButton = root.findViewById<MaterialButton>(R.id.color_editor_mode_button)
        modeButton.isVisible = modeKey != null
        val mode = modeKey?.let { readStringPreference(it, colorChoiceDefault(it)) }
        if (modeKey != null && mode != null) {
            bindColorChoiceButton(modeButton, modeKey)
            tintColorButtonIcon(modeButton, resolveTargetSwatch(colorTarget, mode))
        }

        val customKey = ColorEditorModel.keyFor(colorTarget, ColorControl.CUSTOM_COLOR)
        val customButton = root.findViewById<MaterialButton>(
                R.id.color_editor_custom_color_button)
        customButton.isVisible = customKey != null && mode != null && customColorApplies(mode)
        if (customButton.isVisible && customKey != null) {
            bindColorSwatchButton(
                    customButton, customKey, findPreference<Preference>(customKey)?.title)
        }

        val opacityKey = ColorEditorModel.keyFor(colorTarget, ColorControl.OPACITY)
        val opacityButton = root.findViewById<MaterialButton>(R.id.color_editor_opacity_button)
        opacityButton.isVisible = opacityKey != null
        opacityKey?.let { key ->
            val range = (ColorEditorModel.specFor(key)?.value as? ColorValueSpec.Number)?.range
                    ?: ColorEditorModel.CLOCK_OPACITY_RANGE
            val value = store.getInt(key, MiscPreferences.WEAR_CLOCK_OPACITY.defaultValue)
                    .coerceIn(range)
            val label = formatColorValue(ColorControl.OPACITY, value)
            opacityButton.text = "${findPreference<Preference>(key)?.title ?: key} · $label"
            opacityButton.contentDescription = buildPreferenceDescription(key, label)
        }

        val contrastKey = ColorEditorModel.keyFor(colorTarget, ColorControl.ADAPTIVE_CONTRAST)
        val contrastSwitch = root.findViewById<SwitchMaterial>(R.id.color_editor_contrast_switch)
        // The correction measures the line against the artwork, which says nothing while the title
        // keeps the face's own designed colour - that colour is not derived from the art. Same rule
        // as updateTitleAdaptiveContrastVisibility.
        contrastSwitch.isVisible = contrastKey != null &&
                (colorTarget != ColorTarget.TITLE || mode != MiscPreferences.TITLE_COLOR_FACE_DEFAULT)
        if (contrastSwitch.isVisible && contrastKey != null) {
            bindColorSwitch(
                    contrastSwitch,
                    contrastKey,
                    (ColorEditorModel.specFor(contrastKey)?.value as? ColorValueSpec.Toggle)
                            ?.defaultValue ?: false)
        }

        // Title and artist colours are dependencies of their Text-page visibility switches. The
        // legacy rows express that with android:dependency; the compact editor has to say it too,
        // or it offers live controls for a line the watch is not drawing.
        val elementShown = when (colorTarget) {
            ColorTarget.TITLE -> store.getBoolean(
                    MiscPreferences.WEAR_SHOW_TRACK_TITLE.key,
                    MiscPreferences.WEAR_SHOW_TRACK_TITLE.defaultValue)
            ColorTarget.ARTIST -> store.getBoolean(
                    MiscPreferences.WEAR_SHOW_TRACK_ARTIST.key,
                    MiscPreferences.WEAR_SHOW_TRACK_ARTIST.defaultValue)
            else -> true
        }
        listOf(modeButton, customButton, opacityButton).forEach { it.isEnabled = elementShown }
        contrastSwitch.isEnabled = elementShown

        tintColorEditor(root)
    }

    /** Mirrors [updateAccentColorTargetDependencies]: only these two modes have a colour to pick. */
    private fun customColorApplies(mode: String): Boolean = mode == "normal" || mode == "custom"

    private fun colorChoiceDefault(key: String): String =
            when (val value = ColorEditorModel.specFor(key)?.value) {
                is ColorValueSpec.Choice -> value.defaultValue
                is ColorValueSpec.Hex -> value.defaultValue
                else -> ""
            }

    private fun bindColorChoiceButton(button: MaterialButton, key: String) {
        val value = readStringPreference(key, colorChoiceDefault(key))
        val label = choiceLabel(key, value)
        button.text = label
        button.contentDescription = buildPreferenceDescription(key, label)
    }

    /** A hex row: its own stored colour on the dot, its title as the label. */
    private fun bindColorSwatchButton(
            button: MaterialButton,
            key: String,
            title: CharSequence?
    ) {
        val hex = store.getString(key, null)?.takeUnless { it.isBlank() }
        button.text = title ?: key
        button.contentDescription = buildPreferenceDescription(
                key, hex ?: getString(R.string.setting_wear_normal_color_description))
        tintColorButtonIcon(button, hex?.let(::parseHexOrDefault))
    }

    private fun bindColorSwatchButton(button: MaterialButton, key: String, titleRes: Int) =
            bindColorSwatchButton(button, key, getString(titleRes))

    /** Null paints the neutral placeholder - the honest answer when nothing names a colour. */
    private fun tintColorButtonIcon(button: MaterialButton, color: Int?) {
        button.iconTint = ColorStateList.valueOf(color ?: UNSET_SWATCH_COLOR)
    }

    private fun bindColorSwitch(switch: SwitchMaterial, key: String, defaultValue: Boolean) {
        switch.setOnCheckedChangeListener(null)
        switch.text = findPreference<Preference>(key)?.title ?: switch.text
        switch.isChecked = store.getBoolean(key, defaultValue)
        // SwitchMaterial already announces its checked state in the active locale.
        switch.contentDescription = switch.text
        switch.setOnCheckedChangeListener { _, checked ->
            val preference = findPreference<TwoStatePreference>(key)
            if (preference != null && preference.callChangeListener(checked)) {
                preference.isChecked = checked
            }
            refreshColorEditor()
        }
    }

    private fun commitColorNumber(key: String, value: Int) {
        val number = ColorEditorModel.specFor(key)?.value as? ColorValueSpec.Number ?: return
        val candidate = value.coerceIn(number.range).toString()
        val preference = findPreference<EditTextPreference>(key) ?: return
        if (preference.callChangeListener(candidate)) {
            preference.text = candidate
            refreshColorEditor()
        }
    }

    private fun formatColorValue(control: ColorControl, value: Int): String = when (control) {
        ColorControl.HUE_SHIFT -> "$value°"
        ColorControl.OPACITY -> "$value%"
        else -> value.toString()
    }

    /**
     * The palette [treatment] resolves to, through the same resolver the watch and preview use.
     *
     * [customColorKey] is the target's own hex when it has one, because a per-element Normal
     * override is drawn from that colour rather than from the global picker - the substitution
     * [refreshColorTreatmentSwatches] already makes for the treatment dialog.
     */
    private fun resolveEditorTriad(
            treatment: String,
            customColorKey: String?
    ): Triple<Int, Int, Int> {
        val (rawPrimary, rawSecondary, rawTertiary) =
                (parentFragment as? WatchFaceFragment)?.currentAlbumAccents()
                        ?: Triple(DEFAULT_SWATCH_COLOR, DEFAULT_SWATCH_COLOR, DEFAULT_SWATCH_COLOR)
        val global = SurfaceColorTreatment.fromPreference(
                readStringPreference(
                        MiscPreferences.WEAR_COLOR_TREATMENT.key,
                        MiscPreferences.WEAR_COLOR_TREATMENT.defaultValue),
                default = SurfaceColorTreatment.EXPRESSIVE)
        val customHex = customColorKey?.let { store.getString(it, null) }?.takeUnless { it.isBlank() }
        val triad = SurfacePaletteResolver.derive(
                SurfaceColorTreatment.fromPreference(treatment).resolveAgainst(global),
                ColorModifier.fromPreference(
                        readStringPreference(
                                MiscPreferences.WEAR_COLOR_MODIFIER.key,
                                MiscPreferences.WEAR_COLOR_MODIFIER.defaultValue)),
                rawPrimary,
                rawSecondary,
                rawTertiary,
                parseHexOrDefault(customHex ?: store.getString(
                        MiscPreferences.WEAR_NORMAL_COLOR.key, null)),
                store.getInt(
                        MiscPreferences.WEAR_COLOR_HUE_SHIFT.key,
                        MiscPreferences.WEAR_COLOR_HUE_SHIFT.defaultValue).toFloat(),
                store.getBoolean(
                        MiscPreferences.WEAR_NORMAL_COLOR_MULTI.key,
                        MiscPreferences.WEAR_NORMAL_COLOR_MULTI.defaultValue))
        // Verbatim. The resolver promotes a colourless accent before deriving, so these dots are
        // the colours the watch resolves - there is no second correction to apply here, and a
        // swatch rule sitting at this end could only ever match one of the surfaces.
        return Triple(triad.primary, triad.secondary, triad.tertiary)
    }

    /**
     * The colour one element's dot should show, or null where the stored value names no colour.
     *
     * Null is a real answer here rather than a failure, and the two cases that produce it are the
     * reason this is not just [resolveEditorTriad]: the title's "this face's own colour" is decided
     * by the face and is not derived from the artwork, and the clock's "dynamic" flips black or
     * white against whatever is behind it. Painting either as a definite swatch would state a
     * colour the watch never committed to.
     */
    private fun resolveTargetSwatch(target: ColorTarget, mode: String): Int? {
        val customKey = ColorEditorModel.keyFor(target, ColorControl.CUSTOM_COLOR)
        val customHex = customKey?.let { store.getString(it, null) }?.takeUnless { it.isBlank() }
        if (target == ColorTarget.TITLE && mode == MiscPreferences.TITLE_COLOR_FACE_DEFAULT) {
            return null
        }
        // The clock's modes are white/dynamic/album/custom - not the shared treatment vocabulary,
        // so they must not be run through SurfaceColorTreatment, which resolves both unknown values
        // to the same default and would report one colour for two different settings.
        if (target == ColorTarget.CLOCK) {
            return when (mode) {
                "white" -> Color.WHITE
                "custom" -> customHex?.let(::parseHexOrDefault)
                "album" -> resolveEditorTriad("expressive", customColorKey = null).first
                else -> null
            }
        }
        if (mode == "custom" || mode == "normal") {
            return customHex?.let(::parseHexOrDefault)
                    ?: resolveEditorTriad(mode, customColorKey = customKey).first
        }
        return resolveEditorTriad(mode, customColorKey = customKey).first
    }

    private fun tintColorEditor(root: View) {
        val surface = ContextCompat.getColor(requireContext(), R.color.lyra_surface)
        val rawAccent = lyraRuntimeAccent()
        val accent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 3.0)
        val textAccent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 4.5)
        val onAccent = LyraAccent.foregroundFor(accent)
        val onSurface = ContextCompat.getColor(requireContext(), R.color.lyra_on_surface)
        val secondary = ContextCompat.getColor(requireContext(), R.color.lyra_text_secondary)
        val divider = ContextCompat.getColor(requireContext(), R.color.lyra_divider)

        root.findViewById<TextView>(R.id.color_editor_global_heading).setTextColor(textAccent)

        val checkedStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf())
        val fills = ColorStateList(
                checkedStates,
                intArrayOf(Color.TRANSPARENT, accent, Color.TRANSPARENT))
        val foregrounds = ColorStateList(checkedStates, intArrayOf(secondary, onAccent, onSurface))
        val strokes = ColorStateList(checkedStates, intArrayOf(divider, accent, divider))
        listOf(
                R.id.color_editor_target_title,
                R.id.color_editor_target_artist,
                R.id.color_editor_target_clock,
                R.id.color_editor_target_progress,
                R.id.color_editor_target_volume,
                R.id.color_editor_target_quick_panel
        ).forEach { id ->
            root.findViewById<MaterialButton>(id)?.apply {
                backgroundTintList = fills
                setTextColor(foregrounds)
                strokeColor = strokes
            }
        }

        val neutralStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf())
        val neutralForegrounds = ColorStateList(neutralStates, intArrayOf(secondary, onSurface))
        val neutralFills = ColorStateList(
                neutralStates,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT))
        val neutralStrokes = ColorStateList(neutralStates, intArrayOf(divider, divider))
        listOf(
                R.id.color_editor_accent_source_button,
                R.id.color_editor_modifier_button,
                R.id.color_editor_hue_shift_button
        ).forEach { id ->
            root.findViewById<MaterialButton>(id)?.apply {
                backgroundTintList = neutralFills
                setTextColor(neutralForegrounds)
                iconTint = neutralForegrounds
                strokeColor = neutralStrokes
            }
        }

        val switchStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf())
        val thumb = ColorStateList(
                switchStates,
                intArrayOf(
                        divider,
                        accent,
                        ContextCompat.getColor(requireContext(), R.color.lyra_stone)))
        val track = ColorStateList(
                switchStates,
                intArrayOf(
                        ColorUtils.setAlphaComponent(divider, 0x60),
                        ColorUtils.setAlphaComponent(accent, 0x80),
                        divider))
        listOf(
                root.findViewById<SwitchMaterial>(R.id.color_editor_palette_switch),
                root.findViewById<SwitchMaterial>(R.id.color_editor_contrast_switch)
        ).forEach {
            it.thumbTintList = thumb
            it.trackTintList = track
            it.jumpDrawablesToCurrentState()
        }
    }

    /** Points the contextual preview at the surface this element actually appears on. */
    private fun focusColorTarget(target: ColorTarget) {
        ColorEditorModel.keyFor(target, ColorControl.MODE)
                ?.let { notifyPreviewInteraction(it, null) }
    }

    private fun colorButtonIdFor(target: ColorTarget): Int = when (target) {
        ColorTarget.TITLE -> R.id.color_editor_target_title
        ColorTarget.ARTIST -> R.id.color_editor_target_artist
        ColorTarget.CLOCK -> R.id.color_editor_target_clock
        ColorTarget.PROGRESS -> R.id.color_editor_target_progress
        ColorTarget.VOLUME -> R.id.color_editor_target_volume
        ColorTarget.QUICK_PANEL -> R.id.color_editor_target_quick_panel
    }

    private fun colorTargetForButtonId(id: Int): ColorTarget? = when (id) {
        R.id.color_editor_target_title -> ColorTarget.TITLE
        R.id.color_editor_target_artist -> ColorTarget.ARTIST
        R.id.color_editor_target_clock -> ColorTarget.CLOCK
        R.id.color_editor_target_progress -> ColorTarget.PROGRESS
        R.id.color_editor_target_volume -> ColorTarget.VOLUME
        R.id.color_editor_target_quick_panel -> ColorTarget.QUICK_PANEL
        else -> null
    }

    private fun colorControlIdFor(target: ColorSearchTarget): Int = when (target.control) {
        ColorControl.ACCENT_SOURCE -> R.id.color_editor_accent_source_button
        ColorControl.TREATMENT -> R.id.color_editor_treatment_button
        ColorControl.MODIFIER -> R.id.color_editor_modifier_button
        ColorControl.HUE_SHIFT -> R.id.color_editor_hue_shift_button
        ColorControl.GLOBAL_COLOR -> R.id.color_editor_normal_color_button
        ColorControl.PALETTE -> R.id.color_editor_palette_switch
        ColorControl.MODE -> R.id.color_editor_mode_button
        ColorControl.CUSTOM_COLOR -> R.id.color_editor_custom_color_button
        ColorControl.OPACITY -> R.id.color_editor_opacity_button
        ColorControl.ADAPTIVE_CONTRAST -> R.id.color_editor_contrast_switch
    }


    /**
     * Replaces the visual wall of Panel preferences with one contextual editor, on the same terms
     * as [initTypographyEditor] and [initColorEditor].
     */
    private fun initPanelEditor() {
        val editor = findPreference<PanelEditorPreference>(PANEL_EDITOR_KEY) ?: return
        panelEditor = editor
        editor.bindEditor = ::bindPanelEditor
        editor.refresh()
    }

    private fun refreshPanelEditor() {
        panelEditor?.refresh()
    }

    private fun bindPanelEditor(root: View) {
        root.setTag(R.id.tag_handles_accent_locally, true)
        root.disableScrollbarsInSubtree()
        val targetGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.panel_editor_target_group)
        targetGroup.clearOnButtonCheckedListeners()
        targetGroup.check(panelButtonIdFor(panelTarget))
        targetGroup.addOnButtonCheckedListener { _, checkedId, checked ->
            if (!checked) return@addOnButtonCheckedListener
            panelTarget = panelTargetForButtonId(checkedId) ?: return@addOnButtonCheckedListener
            renderPanelEditor(root)
            focusPanelTarget(panelTarget)
        }

        root.findViewById<MaterialButton>(R.id.panel_editor_backdrop_button).setOnClickListener {
            openPreferenceDialog(MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE.key)
        }
        root.findViewById<MaterialButton>(R.id.panel_editor_blur_button).setOnClickListener {
            openPreferenceDialog(MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS.key)
        }

        // Every per-surface control opens the real Preference's own dialog, so the editor adds no
        // second copy of a picker, its validation or its archived-option filtering.
        listOf(
                R.id.panel_editor_ring_style_button to PanelControl.RING_STYLE,
                R.id.panel_editor_ring_layout_button to PanelControl.RING_LAYOUT,
                R.id.panel_editor_style_button to PanelControl.STYLE,
                R.id.panel_editor_layout_button to PanelControl.LAYOUT,
                R.id.panel_editor_row_size_button to PanelControl.ROW_SIZE,
                R.id.panel_editor_up_next_style_button to PanelControl.UP_NEXT_STYLE,
                R.id.panel_editor_source_button to PanelControl.SOURCE
        ).forEach { (id, control) ->
            root.findViewById<MaterialButton>(id).setOnClickListener {
                PanelEditorModel.keyFor(panelTarget, control)?.let(::openPreferenceDialog)
            }
        }

        // The shortcuts row is an action rather than a value: it opens the library screen the
        // quick panel's shortcut slots are filled from.
        root.findViewById<MaterialButton>(R.id.panel_editor_shortcuts_button).setOnClickListener {
            findPreference<Preference>("watch_streaming_shortcuts")?.let { preference ->
                preference.onPreferenceClickListener?.onPreferenceClick(preference)
            }
        }

        renderPanelEditor(root)
    }

    @SuppressLint("SetTextI18n") // "Title · value" is the editor's own notation, not prose.
    private fun renderPanelEditor(root: View) {
        bindPanelChoiceButton(
                root.findViewById(R.id.panel_editor_backdrop_button),
                MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE.key)

        val blurKey = MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS.key
        val blurButton = root.findViewById<MaterialButton>(R.id.panel_editor_blur_button)
        val blurValue = store.getInt(
                blurKey, MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS.defaultValue)
        blurButton.text = "${getString(R.string.category_wf_panel_effects)} · $blurValue"
        blurButton.contentDescription = buildPreferenceDescription(blurKey, blurValue)

        val targetGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.panel_editor_target_group)
        if (targetGroup.checkedButtonId != panelButtonIdFor(panelTarget)) {
            targetGroup.clearOnButtonCheckedListeners()
            targetGroup.check(panelButtonIdFor(panelTarget))
            targetGroup.addOnButtonCheckedListener { _, checkedId, checked ->
                if (checked) {
                    panelTarget = panelTargetForButtonId(checkedId) ?: panelTarget
                    renderPanelEditor(root)
                    focusPanelTarget(panelTarget)
                }
            }
        }

        listOf(
                R.id.panel_editor_ring_style_button to PanelControl.RING_STYLE,
                R.id.panel_editor_ring_layout_button to PanelControl.RING_LAYOUT,
                R.id.panel_editor_style_button to PanelControl.STYLE,
                R.id.panel_editor_layout_button to PanelControl.LAYOUT,
                R.id.panel_editor_row_size_button to PanelControl.ROW_SIZE,
                R.id.panel_editor_up_next_style_button to PanelControl.UP_NEXT_STYLE,
                R.id.panel_editor_source_button to PanelControl.SOURCE
        ).forEach { (id, control) ->
            val key = PanelEditorModel.keyFor(panelTarget, control)
            val button = root.findViewById<MaterialButton>(id)
            button.isVisible = key != null && panelControlApplies(control, key)
            if (button.isVisible && key != null) bindPanelChoiceButton(button, key)
        }

        listOf(
                R.id.panel_editor_ring_gradient_switch to PanelControl.RING_GRADIENT,
                R.id.panel_editor_up_next_switch to PanelControl.UP_NEXT,
                R.id.panel_editor_shortcut_cover_switch to PanelControl.SHORTCUT_COVER,
                R.id.panel_editor_remote_artwork_switch to PanelControl.REMOTE_ARTWORK
        ).forEach { (id, control) ->
            val key = PanelEditorModel.keyFor(panelTarget, control)
            val switch = root.findViewById<SwitchMaterial>(id)
            switch.isVisible = key != null && panelControlApplies(control, key)
            if (switch.isVisible && key != null) {
                bindPanelSwitch(
                        switch,
                        key,
                        (PanelEditorModel.specFor(key)?.value as? PanelValueSpec.Toggle)
                                ?.defaultValue ?: false)
            }
        }

        val shortcutsButton = root.findViewById<MaterialButton>(R.id.panel_editor_shortcuts_button)
        shortcutsButton.isVisible =
                PanelEditorModel.keyFor(panelTarget, PanelControl.SHORTCUTS) != null
        if (shortcutsButton.isVisible) {
            // The row carries a live count in its summary, so the label reads as the library it
            // opens rather than as a static link - refreshStreamingShortcutsGuide keeps it current.
            val preference = findPreference<Preference>("watch_streaming_shortcuts")
            shortcutsButton.text = preference?.title
            shortcutsButton.contentDescription = "${preference?.title}. ${preference?.summary}"
        }

        val noteKey = PanelEditorModel.keyFor(panelTarget, PanelControl.OPEN_NOTE)
        val note = root.findViewById<TextView>(R.id.panel_editor_note)
        note.isVisible = noteKey != null
        if (noteKey != null) note.text = findPreference<Preference>(noteKey)?.summary

        tintPanelEditor(root)
    }

    /**
     * The value-dependent half of a control's visibility, mirroring the legacy rows exactly.
     *
     * Only the two rules the preference screen already applies are repeated here. Nothing else is
     * invented: the Up next *style* picker stays visible while the pill itself is off, because that
     * is what the legacy row does, and adding a rule in one place only would leave the editor and
     * [WatchSearchTargetResolver] disagreeing about whether a search result is reachable.
     */
    private fun panelControlApplies(control: PanelControl, key: String): Boolean = when (control) {
        // See updatePlayerCapabilityVisibility: an active drag reveals the ring even with the
        // resting ring off, so the picker survives as long as either route can show it.
        PanelControl.RING_STYLE,
        PanelControl.RING_LAYOUT ->
            store.getBoolean("wear_edge_progress_visible", true) ||
                    store.getBoolean("wear_edge_seek_enabled", true)
        // See updateProgressGradientVisibility: only the Solid ring blends the companion colours.
        PanelControl.RING_GRADIENT ->
            panelControlApplies(PanelControl.RING_STYLE, key) &&
                    readStringPreference(MiscPreferences.WEAR_PROGRESS_STYLE.key, "solid") == "solid"
        else -> true
    }

    private fun bindPanelChoiceButton(button: MaterialButton, key: String) {
        val default = (PanelEditorModel.specFor(key)?.value as? PanelValueSpec.Choice)
                ?.defaultValue ?: ""
        val label = choiceLabel(key, readStringPreference(key, default))
        button.text = label
        button.contentDescription = buildPreferenceDescription(key, label)
    }

    private fun bindPanelSwitch(switch: SwitchMaterial, key: String, defaultValue: Boolean) {
        switch.setOnCheckedChangeListener(null)
        switch.text = findPreference<Preference>(key)?.title ?: switch.text
        switch.isChecked = store.getBoolean(key, defaultValue)
        // SwitchMaterial already announces its checked state in the active locale.
        switch.contentDescription = switch.text
        switch.setOnCheckedChangeListener { _, checked ->
            val preference = findPreference<TwoStatePreference>(key)
            if (preference != null && preference.callChangeListener(checked)) {
                preference.isChecked = checked
            }
            refreshPanelEditor()
        }
    }

    private fun tintPanelEditor(root: View) {
        val surface = ContextCompat.getColor(requireContext(), R.color.lyra_surface)
        val rawAccent = lyraRuntimeAccent()
        val accent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 3.0)
        val textAccent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 4.5)
        val onAccent = LyraAccent.foregroundFor(accent)
        val onSurface = ContextCompat.getColor(requireContext(), R.color.lyra_on_surface)
        val secondary = ContextCompat.getColor(requireContext(), R.color.lyra_text_secondary)
        val divider = ContextCompat.getColor(requireContext(), R.color.lyra_divider)

        root.findViewById<TextView>(R.id.panel_editor_global_heading).setTextColor(textAccent)

        val checkedStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf())
        val fills = ColorStateList(
                checkedStates,
                intArrayOf(Color.TRANSPARENT, accent, Color.TRANSPARENT))
        val foregrounds = ColorStateList(checkedStates, intArrayOf(secondary, onAccent, onSurface))
        val strokes = ColorStateList(checkedStates, intArrayOf(divider, accent, divider))
        listOf(
                R.id.panel_editor_target_volume,
                R.id.panel_editor_target_seek,
                R.id.panel_editor_target_quick,
                R.id.panel_editor_target_queue
        ).forEach { id ->
            root.findViewById<MaterialButton>(id)?.apply {
                backgroundTintList = fills
                setTextColor(foregrounds)
                strokeColor = strokes
            }
        }

        val neutralStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf())
        val neutralForegrounds = ColorStateList(neutralStates, intArrayOf(secondary, onSurface))
        val neutralFills = ColorStateList(
                neutralStates,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT))
        val neutralStrokes = ColorStateList(neutralStates, intArrayOf(divider, divider))
        root.findViewById<MaterialButton>(R.id.panel_editor_blur_button)?.apply {
            backgroundTintList = neutralFills
            setTextColor(neutralForegrounds)
            iconTint = neutralForegrounds
            strokeColor = neutralStrokes
        }

        val switchStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf())
        val thumb = ColorStateList(
                switchStates,
                intArrayOf(
                        divider,
                        accent,
                        ContextCompat.getColor(requireContext(), R.color.lyra_stone)))
        val track = ColorStateList(
                switchStates,
                intArrayOf(
                        ColorUtils.setAlphaComponent(divider, 0x60),
                        ColorUtils.setAlphaComponent(accent, 0x80),
                        divider))
        listOf(
                R.id.panel_editor_ring_gradient_switch,
                R.id.panel_editor_up_next_switch,
                R.id.panel_editor_shortcut_cover_switch,
                R.id.panel_editor_remote_artwork_switch
        ).forEach { id ->
            root.findViewById<SwitchMaterial>(id)?.apply {
                thumbTintList = thumb
                trackTintList = track
                jumpDrawablesToCurrentState()
            }
        }
    }

    /** Points the contextual preview at the surface this tab actually styles. */
    private fun focusPanelTarget(target: PanelTarget) {
        PanelEditorModel.keyFor(target, PanelControl.STYLE)
                ?.let { notifyPreviewInteraction(it, null) }
    }

    private fun panelButtonIdFor(target: PanelTarget): Int = when (target) {
        PanelTarget.VOLUME -> R.id.panel_editor_target_volume
        PanelTarget.SEEK -> R.id.panel_editor_target_seek
        PanelTarget.QUICK_PANEL -> R.id.panel_editor_target_quick
        PanelTarget.QUEUE -> R.id.panel_editor_target_queue
    }

    private fun panelTargetForButtonId(id: Int): PanelTarget? = when (id) {
        R.id.panel_editor_target_volume -> PanelTarget.VOLUME
        R.id.panel_editor_target_seek -> PanelTarget.SEEK
        R.id.panel_editor_target_quick -> PanelTarget.QUICK_PANEL
        R.id.panel_editor_target_queue -> PanelTarget.QUEUE
        else -> null
    }

    private fun panelControlIdFor(target: PanelSearchTarget): Int = when (target.control) {
        PanelControl.BACKDROP -> R.id.panel_editor_backdrop_button
        PanelControl.BLUR -> R.id.panel_editor_blur_button
        PanelControl.RING_STYLE -> R.id.panel_editor_ring_style_button
        PanelControl.RING_LAYOUT -> R.id.panel_editor_ring_layout_button
        PanelControl.RING_GRADIENT -> R.id.panel_editor_ring_gradient_switch
        PanelControl.STYLE -> R.id.panel_editor_style_button
        PanelControl.LAYOUT -> R.id.panel_editor_layout_button
        PanelControl.ROW_SIZE -> R.id.panel_editor_row_size_button
        PanelControl.UP_NEXT -> R.id.panel_editor_up_next_switch
        PanelControl.UP_NEXT_STYLE -> R.id.panel_editor_up_next_style_button
        PanelControl.SOURCE -> R.id.panel_editor_source_button
        PanelControl.SHORTCUT_COVER -> R.id.panel_editor_shortcut_cover_switch
        PanelControl.REMOTE_ARTWORK -> R.id.panel_editor_remote_artwork_switch
        PanelControl.OPEN_NOTE -> R.id.panel_editor_note
        PanelControl.SHORTCUTS -> R.id.panel_editor_shortcuts_button
    }


    /**
     * Replaces the visual wall of Player preferences with one contextual editor.
     *
     * The same contract as the Text, Color and Panel editors, but not the same shape - see
     * [PlayerSlot] for why this page has no target rail. Almost every control is built here rather
     * than declared in the layout, so a face only ever renders what it can actually consume.
     */
    private fun initPlayerEditor() {
        val editor = findPreference<PlayerEditorPreference>(PLAYER_EDITOR_KEY) ?: return
        playerEditor = editor
        editor.bindEditor = ::bindPlayerEditor
        editor.refresh()
    }

    private fun refreshPlayerEditor() {
        playerEditor?.refresh()
    }

    private fun bindPlayerEditor(root: View) {
        root.setTag(R.id.tag_handles_accent_locally, true)
        root.disableScrollbarsInSubtree()

        // The two identity rows and the reset action are the only fixed controls; everything else
        // is generated in renderPlayerEditor. Each opens the real Preference's own dialog, so the
        // editor adds no second copy of a picker, its validation or its confirmation prompt.
        listOf(
                R.id.player_editor_face_button to PlayerControl.FACE,
                R.id.player_editor_screen_theme_button to PlayerControl.SCREEN_THEME
        ).forEach { (id, control) ->
            root.findViewById<MaterialButton>(id).setOnClickListener {
                PlayerEditorModel.keyFor(control)?.let(::openPreferenceDialog)
            }
        }
        root.findViewById<MaterialButton>(R.id.player_editor_reset_button).setOnClickListener {
            val key = PlayerEditorModel.keyFor(PlayerControl.RESET_FACE)
                    ?: return@setOnClickListener
            findPreference<Preference>(key)?.let { preference ->
                preference.onPreferenceClickListener?.onPreferenceClick(preference)
            }
        }

        renderPlayerEditor(root)
    }

    private fun renderPlayerEditor(root: View) {
        val face = readStringPreference(
                MiscPreferences.WEAR_SCREEN_FACE.key,
                MiscPreferences.WEAR_SCREEN_FACE.defaultValue)

        bindPlayerChoiceButton(
                root.findViewById(R.id.player_editor_face_button),
                MiscPreferences.WEAR_SCREEN_FACE.key)
        // Control style only restyles a face's own icon glyphs (see
        // PlayerEditorModel.CONTROL_STYLE_FACES) - offering it on a face with none reads as a
        // broken picker rather than an inapplicable one, the same rule Carousel's card shape and
        // Split's panel already follow below.
        val screenThemeButton = root.findViewById<MaterialButton>(
                R.id.player_editor_screen_theme_button)
        screenThemeButton.isVisible =
                PlayerEditorModel.appliesToFace(PlayerControl.SCREEN_THEME, face)
        if (screenThemeButton.isVisible) {
            bindPlayerChoiceButton(screenThemeButton, MiscPreferences.WEAR_SCREEN_THEME.key)
        }

        renderPlayerChips(
                root.findViewById(R.id.player_editor_element_chips),
                PlayerEditorModel.visibleIn(PlayerSlot.ELEMENT, face))
        renderPlayerChoiceRows(
                root.findViewById(R.id.player_editor_choice_rows),
                PlayerEditorModel.visibleIn(PlayerSlot.CHOICE, face))

        val details = PlayerEditorModel.visibleIn(PlayerSlot.DETAIL, face)
        root.findViewById<View>(R.id.player_editor_details_card).isVisible = details.isNotEmpty()
        renderPlayerChips(root.findViewById(R.id.player_editor_detail_chips), details)

        val keepScreenOn = root.findViewById<SwitchMaterial>(
                R.id.player_editor_keep_screen_on_switch)
        bindPlayerSwitch(
                keepScreenOn,
                MiscPreferences.WEAR_KEEP_SCREEN_ON.key,
                MiscPreferences.WEAR_KEEP_SCREEN_ON.defaultValue)

        PlayerEditorModel.keyFor(PlayerControl.RESET_FACE)?.let { key ->
            val button = root.findViewById<MaterialButton>(R.id.player_editor_reset_button)
            button.text = findPreference<Preference>(key)?.title
            button.contentDescription = findPreference<Preference>(key)?.summary
            button.tag = key
        }

        tintPlayerEditor(root)
        // Must run after the chips and rows are built, not only in bindPlayerEditor: those views
        // are created here on every refresh, so a sweep done before this point never reaches them.
        root.disableScrollbarsInSubtree()
    }

    /**
     * Rebuilds a chip field from [specs].
     *
     * Cleared first: this runs on every rebind, and appending without clearing would stack another
     * full set of chips each time the editor refreshed - which, since toggling a chip refreshes it,
     * would be every tap.
     */
    private fun renderPlayerChips(group: ChipGroup, specs: List<PlayerSettingSpec>) {
        group.removeAllViews()
        group.isVisible = specs.isNotEmpty()
        specs.forEach { spec ->
            val toggle = spec.value as? PlayerValueSpec.Toggle ?: return@forEach
            val title = findPreference<Preference>(spec.key)?.title
            val chip = Chip(requireContext()).apply {
                setChipDrawable(
                        ChipDrawable.createFromAttributes(
                                requireContext(), null, 0, R.style.LyraCommunityGalleryChip))
                // The short noun where one exists; the sentence otherwise. See PlayerSettingSpec.
                text = spec.chipLabelRes?.let(::getString) ?: title
                isCheckable = true
                isChecked = store.getBoolean(spec.key, toggle.defaultValue)
                // The chip shows a noun, so the full row title is what a screen reader needs; the
                // checked state is announced by the widget itself.
                contentDescription = title
                tag = spec.key
                setOnClickListener {
                    commitPlayerBoolean(spec.key, isChecked)
                }
            }
            group.addView(chip)
        }
    }

    /** Rebuilds the multi-way pickers. Cleared first, for the reason [renderPlayerChips] gives. */
    private fun renderPlayerChoiceRows(container: LinearLayout, specs: List<PlayerSettingSpec>) {
        container.removeAllViews()
        container.isVisible = specs.isNotEmpty()
        specs.forEach { spec ->
            // The attribute belongs to the Material library's R, not the app's - the app's R.attr
            // has no such entry and resolving it against the wrong one is a compile error, not a
            // silently unstyled button.
            val button = MaterialButton(
                    requireContext(),
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(R.dimen.editor_row_height)
                ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.editor_row_gap) }
                isAllCaps = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = ResourcesCompat.getFont(requireContext(), R.font.google_sans)
                // 12sp, and the insets zeroed to match LyraGestureButton. A MaterialButton keeps
                // 6dp of inset top and bottom by default, which leaves a 48dp row only 36dp of
                // content box; a 13sp line plus its font padding overran that, and an overrun is
                // exactly what makes a button report a scroll range and paint a thumb down its own
                // edge - the same cause the editor's icon buttons hit. Nothing here scrolls.
                textSize = 12f
                insetTop = 0
                insetBottom = 0
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                tag = spec.key
                setOnClickListener { openPreferenceDialog(spec.key) }
            }
            bindPlayerChoiceRow(button, spec.key)
            container.addView(button)
        }
    }

    /** A picker row reads "<what it is> · <what it is set to>": the label alone would not say. */
    private fun bindPlayerChoiceRow(button: MaterialButton, key: String) {
        val default = (PlayerEditorModel.specFor(key)?.value as? PlayerValueSpec.Choice)
                ?.defaultValue ?: ""
        val title = findPreference<Preference>(key)?.title ?: key
        val label = choiceLabel(key, readStringPreference(key, default))
        button.text = "$title · $label"
        button.contentDescription = buildPreferenceDescription(key, label)
    }

    private fun bindPlayerChoiceButton(button: MaterialButton, key: String) {
        val default = (PlayerEditorModel.specFor(key)?.value as? PlayerValueSpec.Choice)
                ?.defaultValue ?: ""
        val label = choiceLabel(key, readStringPreference(key, default))
        button.text = label
        button.contentDescription = buildPreferenceDescription(key, label)
        button.tag = key
    }

    private fun bindPlayerSwitch(switch: SwitchMaterial, key: String, defaultValue: Boolean) {
        switch.setOnCheckedChangeListener(null)
        switch.text = findPreference<Preference>(key)?.title ?: switch.text
        switch.isChecked = store.getBoolean(key, defaultValue)
        // SwitchMaterial already announces its checked state in the active locale.
        switch.contentDescription = switch.text
        switch.tag = key
        switch.setOnCheckedChangeListener { _, checked -> commitPlayerBoolean(key, checked) }
    }

    private fun commitPlayerBoolean(key: String, value: Boolean) {
        val preference = findPreference<TwoStatePreference>(key)
        if (preference != null && preference.callChangeListener(value)) {
            preference.isChecked = value
        }
        // Refresh either way: a rejected change has to snap the control back to the stored value
        // rather than leave it showing one the watch will never receive.
        refreshPlayerEditor()
    }

    private fun tintPlayerEditor(root: View) {
        val surface = ContextCompat.getColor(requireContext(), R.color.lyra_surface)
        val rawAccent = lyraRuntimeAccent()
        val accent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 3.0)
        val textAccent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 4.5)
        val onSurface = ContextCompat.getColor(requireContext(), R.color.lyra_on_surface)
        val secondary = ContextCompat.getColor(requireContext(), R.color.lyra_text_secondary)
        val divider = ContextCompat.getColor(requireContext(), R.color.lyra_divider)

        listOf(
                R.id.player_editor_face_heading,
                R.id.player_editor_elements_heading,
                R.id.player_editor_details_heading
        ).forEach { root.findViewById<TextView>(it)?.setTextColor(textAccent) }

        // A checked chip is filled with a blend rather than the raw accent, and its content is
        // contrast-corrected against that blend - the treatment the Community gallery's filters
        // already use, so a selected chip means the same thing in both places.
        val selectedContainer = ColorUtils.blendARGB(surface, accent, 0.16f)
        val selectedContent = LyraAccent.contrastSafe(
                accent, selectedContainer, minimumContrast = 4.5)
        listOf(R.id.player_editor_element_chips, R.id.player_editor_detail_chips).forEach { id ->
            val group = root.findViewById<ChipGroup>(id) ?: return@forEach
            for (index in 0 until group.childCount) {
                val chip = group.getChildAt(index) as? Chip ?: continue
                val selected = chip.isChecked
                chip.chipBackgroundColor = ColorStateList.valueOf(
                        if (selected) selectedContainer else surface)
                chip.chipStrokeColor = ColorStateList.valueOf(
                        if (selected) selectedContent else divider)
                chip.setTextColor(if (selected) selectedContent else onSurface)
                chip.rippleColor = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.lyra_ripple))
            }
        }

        val neutralStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf())
        val neutralForegrounds = ColorStateList(neutralStates, intArrayOf(secondary, onSurface))
        val neutralFills = ColorStateList(
                neutralStates,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT))
        val neutralStrokes = ColorStateList(neutralStates, intArrayOf(divider, divider))
        val choiceRows = root.findViewById<LinearLayout>(R.id.player_editor_choice_rows)
        (0 until choiceRows.childCount)
                .mapNotNull { choiceRows.getChildAt(it) as? MaterialButton }
                .forEach {
                    it.backgroundTintList = neutralFills
                    it.setTextColor(neutralForegrounds)
                    it.iconTint = neutralForegrounds
                    it.strokeColor = neutralStrokes
                }

        val switchStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf())
        root.findViewById<SwitchMaterial>(R.id.player_editor_keep_screen_on_switch)?.apply {
            thumbTintList = ColorStateList(
                    switchStates,
                    intArrayOf(
                            divider,
                            accent,
                            ContextCompat.getColor(requireContext(), R.color.lyra_stone)))
            trackTintList = ColorStateList(
                    switchStates,
                    intArrayOf(
                            ColorUtils.setAlphaComponent(divider, 0x60),
                            ColorUtils.setAlphaComponent(accent, 0x80),
                            divider))
            jumpDrawablesToCurrentState()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // AndroidX's preference_recyclerview declares android:scrollbars="vertical", so touching
        // any control on these pages flashes a scrollbar down the edge - most visibly on Text,
        // whose editor is a row of controls the finger travels along rather than a list to scroll.
        // Every other scrolling surface in the app already opts out of the bar.
        listView?.isVerticalScrollBarEnabled = false
        consumeHighlightKey()
    }

    /**
     * Scrolls to the preference a search result pointed at, then clears the argument.
     *
     * Clearing matters: the argument would otherwise survive into every later recreation of this
     * page, so rotating the phone after scrolling elsewhere would yank the list back to a row the
     * user had already moved on from. Posted to the list because the categories are only made
     * visible in onCreatePreferences, and scrolling to a row that is still GONE does nothing.
     */
    private fun consumeHighlightKey() {
        val key = arguments?.getString(ARG_HIGHLIGHT_KEY) ?: return
        arguments?.remove(ARG_HIGHLIGHT_KEY)
        listView?.post {
            if (!isAdded) return@post
            if (section == SECTION_TYPOGRAPHY) {
                TypographyEditorModel.searchTargetFor(key)?.let { target ->
                    typographyTarget = target.target
                    refreshTypographyEditor()
                    listView?.scrollToPosition(0)
                    listView?.post {
                        val title = findPreference<Preference>(key)?.title
                        typographyEditor?.revealAndPulse(
                                buttonIdFor(target.target),
                                controlIdFor(target),
                                title)
                    }
                    return@post
                }
            }
            if (section == SECTION_STYLE && key in PlayerEditorModel.keys) {
                // No tabs to select: the whole page is one surface, so the result only has to be
                // scrolled to the top and the control holding this key pulsed. A control the
                // current face cannot use is genuinely absent, and pulse() does nothing rather
                // than inventing a highlight for it.
                refreshPlayerEditor()
                listView?.scrollToPosition(0)
                listView?.post {
                    playerEditor?.pulse(key, findPreference<Preference>(key)?.title)
                }
                return@post
            }
            if (section == SECTION_PANELS) {
                PanelEditorModel.searchTargetFor(key)?.let { target ->
                    panelTarget = target.target
                    refreshPanelEditor()
                    listView?.scrollToPosition(0)
                    listView?.post {
                        val title = findPreference<Preference>(key)?.title
                        panelEditor?.revealAndPulse(
                                panelButtonIdFor(target.target),
                                panelControlIdFor(target),
                                title)
                    }
                    return@post
                }
            }
            if (section == SECTION_COLORS) {
                ColorEditorModel.searchTargetFor(key)?.let { target ->
                    colorTarget = target.target
                    refreshColorEditor()
                    listView?.scrollToPosition(0)
                    listView?.post {
                        val title = findPreference<Preference>(key)?.title
                        colorEditor?.revealAndPulse(
                                colorButtonIdFor(target.target),
                                colorControlIdFor(target),
                                title)
                    }
                    return@post
                }
            }
            findPreference<Preference>(key)?.let(::scrollToAndPulsePreference)
        }
    }

    fun showSection(newSection: String) {
        section = newSection
        applySectionVisibility()
        listView?.scrollToPosition(0)
    }

    private fun applySectionVisibility() {
        if (preferenceScreen == null) return

        // Section -> categories and the full category list both live in SettingsCatalog, shared
        // with the settings search so a result can be navigated to the page it is actually on.
        // That object's doc carries the two structural rules these lists have to satisfy, and
        // SettingsCatalogTest pins them.
        val visibleCategories = SettingsCatalog.WATCH_SECTIONS[section]
            ?: SettingsCatalog.WATCH_SECTIONS.getValue(SECTION_STYLE)

        // Text and Color both present one contextual editor instead of their own category list.
        val compactTypography = section == SECTION_TYPOGRAPHY
        val compactColors = section == SECTION_COLORS
        val compactPanels = section == SECTION_PANELS
        val compactPlayer = section == SECTION_STYLE
        SettingsCatalog.WATCH_CATEGORIES.forEach { key ->
            val visible = key in visibleCategories &&
                    (!compactTypography || key == TYPOGRAPHY_EDITOR_CATEGORY) &&
                    (!compactColors || key == COLOR_EDITOR_CATEGORY) &&
                    (!compactPanels || key == PANEL_EDITOR_CATEGORY) &&
                    (!compactPlayer || key == PLAYER_EDITOR_CATEGORY)
            findPreference<Preference>(key)?.isVisible = visible
        }
        // Visible only on the Text page, and only once Google Sans Flex is actually chosen -
        // ANDed explicitly rather than folded into visibleCategories above, since this category's
        // visibility depends on a preference *value*, not just which section is showing.
        updateFlexAxesVisibility()
        // These categories/rows have a second prerequisite beyond their owning section. Apply the
        // value-dependent half after the catalog assignment so section visibility cannot reveal a
        // control that the selected renderer ignores.
        updatePlayerCapabilityVisibility()
        updateBackgroundCapabilityVisibility()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        // The swatch data has no route from a bare Preference to the live album accent, so it
        // must be refreshed here, right before the dialog is built - see
        // ColorTreatmentPreference's class doc.
        if (preference is ColorTreatmentPreference) {
            refreshColorTreatmentSwatches(preference)
        }
        super.onDisplayPreferenceDialog(preference)
        // Preference dialogs inflate with the static theme colors; once the dialog is up,
        // re-tint it with the accent currently on screen.
        view?.post { tintOpenLyraPreferenceDialog() }
    }

    /** Feeds a [ColorTreatmentPreference] the current album accent and effective Normal color so
     *  its dialog's swatches match what the preview would actually show for each option. */
    private fun refreshColorTreatmentSwatches(pref: ColorTreatmentPreference) {
        pref.albumAccents = (parentFragment as? WatchFaceFragment)?.currentAlbumAccents()
                ?: Triple(DEFAULT_SWATCH_COLOR, DEFAULT_SWATCH_COLOR, DEFAULT_SWATCH_COLOR)
        pref.globalTreatmentValue = readStringPreference("wear_color_treatment", "expressive")
        pref.colorModifierValue = readStringPreference("wear_color_modifier", "none")
        pref.colorHueShiftValue = store.getString("wear_color_hue_shift", null)?.toIntOrNull() ?: 0
        val targetCustomHex = pref.customColorKey
                ?.let { store.getString(it, null) }
                ?.takeUnless { it.isBlank() }
        val normalHex = targetCustomHex ?: store.getString("wear_normal_color", null)
        pref.normalPreviewColor = parseHexOrDefault(normalHex)
        pref.normalColorMulti = store.getBoolean("wear_normal_color_multi", true)
    }

    override fun onResume() {
        super.onResume()
        rawPrefs.registerOnSharedPreferenceChangeListener(faceChangeListener)
        // ViewPager2 keeps neighbouring pages alive in STARTED state. When this page becomes
        // current, re-read every scoped value for the currently selected face (a face swap or an
        // edit made on another page while this one stayed alive would otherwise leave stale
        // in-memory values here) and refresh dependent rows and summaries.
        rebindScopedValues()
        refreshColorTargetSummaries()
        refreshConditionalPreferences()
        refreshStreamingShortcutsGuide()
        refreshTypographyEditor()
        refreshColorEditor()
        // Reads the shortcut count refreshed just above, so it must follow that call.
        refreshPanelEditor()
        refreshPlayerEditor()
    }

    override fun onPause() {
        super.onPause()
        rawPrefs.unregisterOnSharedPreferenceChangeListener(faceChangeListener)
    }

    override fun onDestroyView() {
        typographyEditor?.releaseBoundView()
        colorEditor?.releaseBoundView()
        panelEditor?.releaseBoundView()
        playerEditor?.releaseBoundView()
        super.onDestroyView()
    }

    /** Re-reads every scoped preference from [store] for the current face and pushes the value
     *  into its control, so switching the face shows that face's own configuration. */
    private fun rebindScopedValues(group: PreferenceGroup? = preferenceScreen) {
        group ?: return
        for (index in 0 until group.preferenceCount) {
            val pref = group.getPreference(index)
            if (pref is PreferenceGroup) {
                rebindScopedValues(pref)
                continue
            }
            val key = pref.key?.takeIf { it.isNotBlank() } ?: continue
            if (!FaceScopedPreferences.isScoped(key)) continue
            when (pref) {
                is ListPreference -> store.getString(key, pref.value)?.let {
                    if (it != pref.value) pref.value = it
                }
                is TwoStatePreference -> {
                    val value = store.getBoolean(key, pref.isChecked)
                    if (value != pref.isChecked) pref.isChecked = value
                }
                is EditTextPreference -> {
                    val value = store.getString(key, pref.text)
                    if (value != pref.text) pref.text = value
                }
                is HexColorDotPreference -> pref.refreshDot()
            }
        }
    }

    private fun refreshColorTargetSummaries() {
        updateAccentColorTargetSummary(findPreference("wear_normal_color"),
                "wear_normal_color", R.string.setting_wear_normal_color_description)
        updateAccentColorTargetSummary(findPreference("wear_aod_custom_color"),
                "wear_aod_custom_color", R.string.setting_wear_aod_custom_color_description)
        updateAccentColorTargetSummary(findPreference("wear_clock_custom_color"),
                "wear_clock_custom_color", R.string.setting_wear_clock_custom_color_description)
        updateAccentColorTargetSummary(findPreference("wear_artist_custom_color"),
                "wear_artist_custom_color", R.string.setting_wear_artist_custom_color_description)
        updateAccentColorTargetSummary(findPreference("wear_progress_custom_color"),
                "wear_progress_custom_color", R.string.setting_wear_progress_custom_color_description)
        updateAccentColorTargetSummary(findPreference("wear_volume_custom_color"),
                "wear_volume_custom_color", R.string.setting_wear_volume_custom_color_description)
        updateAccentColorTargetSummary(findPreference("wear_quick_panel_custom_color"),
                "wear_quick_panel_custom_color", R.string.setting_wear_quick_panel_custom_color_description)
    }

    private fun applyArchivedOptionFilters() {
        val showArchived = rawPrefs.getBoolean("dev_show_archived", false)
        val face = ThemeAppearance.resolve(rawPrefs).baseFace
        if (!showArchived && readStringPreference(MiscPreferences.WEAR_FONT.key, "google_sans") ==
                "typewriter") {
            // A hidden current value would make the row claim another font while the watch kept
            // rendering Typewriter. Normalize it; enabling archived options makes it selectable
            // again without keeping a secret, mismatched active state.
            store.putString(MiscPreferences.WEAR_FONT.key, "google_sans")
        }
        if (readStringPreference(MiscPreferences.WEAR_SCREEN_THEME.key, "default") == "hidden") {
            // "Hidden" duplicated the dedicated Show player controls switch and was the most
            // common source of apparently broken styles. Migrate it losslessly: retain the clean
            // control-free look on configurable faces, while essential-control faces normalize
            // to Balanced (their transport cannot be hidden).
            store.putString(MiscPreferences.WEAR_SCREEN_THEME.key, "default")
            if (face !in setOf("material", "expressive")) {
                store.putBoolean(MiscPreferences.WEAR_PLAYER_CONTROLS_VISIBLE.key, false)
            }
        }
        filterArchivedListPreference(
                key = "wear_screen_face",
                entriesRes = R.array.wear_screen_face_entries,
                valuesRes = R.array.wear_screen_face_values,
                archived = archivedFaces,
                defaultValue = "classic",
                showArchived = showArchived)
        // Typewriter is intentionally absent from the normal catalog and is only offered when
        // archived options are on.
        applyGlobalFontEntries(showArchived)
        filterArchivedListPreference(
                key = "screen_buttons_bg_style",
                entriesRes = R.array.screen_buttons_bg_entries,
                valuesRes = R.array.screen_buttons_bg_values,
                archived = archivedMiniButtonBackgrounds,
                defaultValue = "glass",
                showArchived = showArchived)
        filterArchivedListPreference(
                key = "wear_overlay_backdrop_style",
                entriesRes = R.array.wear_overlay_backdrop_entries,
                valuesRes = R.array.wear_overlay_backdrop_values,
                archived = archivedOverlayBackdrops,
                defaultValue = "follow",
                showArchived = showArchived)
        filterArchivedListPreference(
                key = "screen_buttons_shape",
                entriesRes = R.array.screen_buttons_shape_entries,
                valuesRes = R.array.screen_buttons_shape_values,
                archived = archivedMiniButtonShapes,
                defaultValue = "pill",
                showArchived = showArchived)
        applyTitleFontEntries(showArchived)
        applyArtistFontEntries(showArchived)
        applyClockFontEntries(showArchived)
        applyTrackTimeFontEntries(showArchived)
        applyLyricsFontEntries(showArchived)
        applyTitleColorEntries()
        PanelOptionCatalog.apply(resources) { key -> findPreference(key) }
    }

    /**
     * Builds the title-colour picker from the shared component-treatment arrays with the
     * "keep this face's own colour" option on top.
     *
     * Derived rather than declared, for the reason [applyClockFontEntries] documents: a second copy
     * of nine treatment names would need re-translating into 12 locales every time a treatment is
     * added, and the moment it fell behind the picker would offer the wrong treatment for a value.
     * The extra leading option is the title's identity default - see
     * MiscPreferences.TITLE_COLOR_FACE_DEFAULT for why it cannot just reuse "follow".
     */
    private fun applyTitleColorEntries() {
        val pref = findPreference<ListPreference>(MiscPreferences.WEAR_TITLE_COLOR_MODE.key) ?: return
        val entries = resources.getStringArray(R.array.wear_component_color_treatment_entries)
        val values = resources.getStringArray(R.array.wear_component_color_treatment_values)
        pref.entries =
                (listOf(getString(R.string.wear_title_color_face_default)) + entries.toList())
                        .toTypedArray()
        pref.entryValues =
                (listOf(MiscPreferences.TITLE_COLOR_FACE_DEFAULT) + values.toList())
                .toTypedArray()
    }

    /** Returns the localized font names paired with their stable preference keys in display order. */
    private fun sortedFontChoices(): List<Pair<String, String>> {
        val entries = resources.getStringArray(R.array.wear_font_entries)
        val values = resources.getStringArray(R.array.wear_font_values)
        return entries.zip(values).sortedBy { it.first.lowercase() }
    }

    private fun availableFontChoices(
            showArchived: Boolean,
            current: String,
            keepCurrentArchived: Boolean = true
    ): List<Pair<String, String>> = sortedFontChoices().filter { (_, value) ->
        showArchived || value !in archivedFonts || (keepCurrentArchived && value == current)
    }

    private fun applyGlobalFontEntries(showArchived: Boolean) {
        val pref = findPreference<ListPreference>(MiscPreferences.WEAR_FONT.key) ?: return
        val current = pref.value ?: readStringPreference(
                MiscPreferences.WEAR_FONT.key, "google_sans")
        val choices = availableFontChoices(
                showArchived = showArchived,
                current = current,
                keepCurrentArchived = false)
        pref.entries = choices.map { it.first }.toTypedArray()
        pref.entryValues = choices.map { it.second }.toTypedArray()
    }

    /**
     * Builds the clock-font picker from the track-font catalog with a "Follow track font" row on
     * top.
     *
     * Derived rather than declared as its own pair of arrays: a second 27-entry list would have to
     * be extended *and* re-translated into 12 locales every time a font is added, and the moment it
     * fell behind the picker would silently offer the wrong typeface for a value (the same
     * entries/values drift that already bit the overlay backdrop list). The archived-font filter is
     * applied here too, so Typewriter stays hidden in both pickers or neither.
     */
    private fun applyClockFontEntries(showArchived: Boolean) {
        val pref = findPreference<ListPreference>(MiscPreferences.WEAR_CLOCK_FONT.key) ?: return
        val current = pref.value ?: readStringPreference(
                MiscPreferences.WEAR_CLOCK_FONT.key, WatchTypography.CLOCK_FONT_FOLLOW)
        val choices = availableFontChoices(showArchived, current)
        pref.entries =
                (listOf(getString(R.string.wear_clock_font_follow)) + choices.map { it.first })
                        .toTypedArray()
        pref.entryValues =
                (listOf(WatchTypography.CLOCK_FONT_FOLLOW) + choices.map { it.second })
                        .toTypedArray()
    }

    /**
     * Title and artist inherit the global track family by default, but can deliberately choose a
     * different row from that same catalog. Keeping their lists derived from the global picker
     * prevents an added typeface from silently becoming unavailable to only one text element.
     */
    private fun applyTitleFontEntries(showArchived: Boolean) = applyFollowGlobalFontEntries(
            key = MiscPreferences.WEAR_TITLE_FONT.key,
            defaultValue = WatchTypography.TITLE_FONT_FOLLOW,
            showArchived = showArchived)

    private fun applyArtistFontEntries(showArchived: Boolean) = applyFollowGlobalFontEntries(
            key = MiscPreferences.WEAR_ARTIST_FONT.key,
            defaultValue = WatchTypography.ARTIST_FONT_FOLLOW,
            showArchived = showArchived)

    private fun applyFollowGlobalFontEntries(
            key: String,
            defaultValue: String,
            showArchived: Boolean
    ) {
        val pref = findPreference<ListPreference>(key) ?: return
        val current = pref.value ?: readStringPreference(key, defaultValue)
        val choices = availableFontChoices(showArchived, current)
        pref.entries =
                (listOf(getString(R.string.wear_text_font_follow)) + choices.map { it.first })
                        .toTypedArray()
        pref.entryValues = (listOf(defaultValue) + choices.map { it.second }).toTypedArray()
    }

    /**
     * Builds the elapsed/total readout picker from the same font catalog as the other text
     * controls. Its identity option follows the *face design*, rather than the global track
     * typeface: several faces deliberately author this compact numeric readout differently.
     */
    private fun applyTrackTimeFontEntries(showArchived: Boolean) {
        val pref = findPreference<ListPreference>(MiscPreferences.WEAR_TRACK_TIME_FONT.key) ?: return
        val current = pref.value ?: readStringPreference(
                MiscPreferences.WEAR_TRACK_TIME_FONT.key,
                WatchTypography.TRACK_TIME_FONT_FOLLOW)
        val choices = availableFontChoices(showArchived, current)
        pref.entries =
                (listOf(getString(R.string.wear_track_time_font_follow)) +
                        choices.map { it.first })
                        .toTypedArray()
        pref.entryValues =
                (listOf(WatchTypography.TRACK_TIME_FONT_FOLLOW) + choices.map { it.second })
                        .toTypedArray()
    }

    /**
     * Builds the lyrics-font picker from the track-font catalog with a "Follow the design" row on
     * top - the same derivation, and for the same reasons, as [applyClockFontEntries].
     *
     * The leading option cannot be shared with the clock's: "follow" means a different thing here.
     * The clock follows the *track* font, while lyrics follow whatever the surface drawing them was
     * designed with - the UI font on the lyrics screen, a serif on the Verse face - which is what
     * makes this control additive rather than a silent restyle of every theme that already exists.
     * See WatchTypography.lyricsFontKey.
     */
    private fun applyLyricsFontEntries(showArchived: Boolean) {
        val pref = findPreference<ListPreference>(MiscPreferences.WEAR_LYRICS_FONT.key) ?: return
        val current = pref.value ?: readStringPreference(
                MiscPreferences.WEAR_LYRICS_FONT.key, WatchTypography.LYRICS_FONT_FOLLOW)
        val choices = availableFontChoices(showArchived, current)
        pref.entries =
                (listOf(getString(R.string.wear_lyrics_font_follow)) +
                        choices.map { it.first })
                        .toTypedArray()
        pref.entryValues =
                (listOf(WatchTypography.LYRICS_FONT_FOLLOW) + choices.map { it.second })
                        .toTypedArray()
    }

    private fun filterArchivedListPreference(
            key: String,
            entriesRes: Int,
            valuesRes: Int,
            archived: Set<String>,
            defaultValue: String,
            showArchived: Boolean,
            keepCurrentArchived: Boolean = true
    ) {
        val pref = findPreference<ListPreference>(key) ?: return
        val entries = resources.getStringArray(entriesRes)
        val values = resources.getStringArray(valuesRes)
        val current = pref.value ?: readStringPreference(key, defaultValue)
        val keep = values.indices.filter { index ->
            index < entries.size &&
                    (showArchived || values[index] !in archived ||
                            (keepCurrentArchived && values[index] == current))
        }
        pref.entries = keep.map { entries[it] }.toTypedArray()
        pref.entryValues = keep.map { values[it] }.toTypedArray()
    }

    private fun refreshConditionalPreferences() {
        applyArchivedOptionFilters()
        val face = readStringPreference("wear_screen_face", "classic")
        val aodStyle = readStringPreference("wear_aod_style", "follow")
        updateFaceDependencies(face, aodStyle)
        updateBlurRadiusEnabled(readStringPreference("album_art_style", "cover"))
        updateProgressGradientVisibility()
        updateFlexAxesVisibility()
        updateUnifiedColorTreatmentVisibility(
                readStringPreference("wear_color_treatment", "expressive"))
        updateAccentColorTargetDependencies(
            "wear_aod_color_mode",
            "wear_aod_custom_color",
            null,
            readStringPreference("wear_aod_color_mode", "white")
        )
        updateAccentColorTargetDependencies(
            "wear_clock_color_mode",
            "wear_clock_custom_color",
            null,
            readStringPreference("wear_clock_color_mode", "white")
        )
        listOf(
                "wear_artist_color_mode" to "wear_artist_custom_color",
                "wear_progress_color_mode" to "wear_progress_custom_color",
                "wear_volume_color_mode" to "wear_volume_custom_color",
                "wear_quick_panel_color_mode" to "wear_quick_panel_custom_color"
        ).forEach { (modeKey, colorKey) ->
            updateAccentColorTargetDependencies(
                    modeKey, colorKey, null, readStringPreference(modeKey, "follow"))
        }
        // The title defaults to its own identity value, not "follow", so it cannot join the loop
        // above - reading the wrong default would show the custom-colour row on an untouched
        // install.
        updateAccentColorTargetDependencies(
                "wear_title_color_mode",
                "wear_title_custom_color",
                null,
                readStringPreference(
                        "wear_title_color_mode", MiscPreferences.TITLE_COLOR_FACE_DEFAULT))
        updateTitleAdaptiveContrastVisibility()
    }

    /** Reads through [store] so dependency/visibility logic sees the same face-scoped value the
     *  control shows and the watch applies. */
    private fun readStringPreference(key: String, defaultValue: String): String =
            store.getString(key, defaultValue) ?: defaultValue

    /**
     * Streaming shortcuts are especially useful beside Quick Actions, so Panels exposes a
     * discoverability link to the existing editor. The editor remains the single source of truth;
     * this preference does not create a second copy of the shortcut library.
     */
    private fun initStreamingShortcutsGuide() {
        findPreference<Preference>("watch_streaming_shortcuts")?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    startActivity(Intent(requireContext(), PlaylistShortcutsActivity::class.java))
                    true
                }
        refreshStreamingShortcutsGuide()
    }

    private fun refreshStreamingShortcutsGuide() {
        if (!isAdded || preferenceScreen == null) return
        val count = PlaylistShortcutStorage.load(requireContext()).size
        findPreference<Preference>("watch_streaming_shortcuts")?.summary =
                if (count == 0) {
                    getString(R.string.watch_streaming_shortcuts_empty)
                } else {
                    resources.getQuantityString(
                            R.plurals.watch_streaming_shortcuts_summary,
                            count,
                            count
                    )
                }
    }

    /** The numeric preference stores strings to match wearutils; reject malformed/out-of-range
     *  input here so every persisted opacity is a valid percentage before it reaches the watch. */
    private fun initMiniButtonOpacityValidation() {
        validateNumericPercentage("screen_buttons_opacity", 0..100)
    }

    /** Reject invalid percentages before runtime clamps could disagree with the shown value. */
    private fun initAodPercentageValidation() {
        validateNumericPercentage("wear_aod_intensity", 20..100)
        validateNumericPercentage("ambient_album_art_opacity", 20..100)
    }

    /**
     * Same guard for the typography, Flex-axis and palette-shift numbers. Their ranges mirror the
     * clamps in `WatchTypography`/`SurfacePaletteResolver`, so a rejected value can never be
     * persisted as one number and rendered as another.
     */
    private fun initTypographyValidation() {
        for (key in listOf(
                "wear_title_font_weight", "wear_artist_font_weight", "wear_track_time_font_weight",
                "wear_clock_font_weight")) {
            validateNumericPercentage(
                    key, WatchTypography.FLEX_WEIGHT_MIN..WatchTypography.FLEX_WEIGHT_MAX)
        }
        for (key in listOf(
                "wear_title_font_scale", "wear_artist_font_scale", "wear_track_time_font_scale",
                "wear_clock_font_scale", "wear_source_icon_scale")) {
            validateNumericPercentage(
                    key, MiscPreferences.TYPOGRAPHY_MIN_SCALE..MiscPreferences.TYPOGRAPHY_MAX_SCALE)
        }
        for (key in listOf(
                "wear_title_font_opacity", "wear_artist_font_opacity", "wear_track_time_font_opacity",
                "wear_source_icon_opacity")) {
            validateNumericPercentage(key, MiscPreferences.TYPOGRAPHY_MIN_OPACITY..100)
        }
        for (key in listOf(
                "wear_title_font_tracking", "wear_artist_font_tracking",
                "wear_track_time_font_tracking", "wear_clock_font_tracking")) {
            validateNumericPercentage(
                    key,
                    MiscPreferences.TYPOGRAPHY_MIN_TRACKING..MiscPreferences.TYPOGRAPHY_MAX_TRACKING)
        }
        validateNumericPercentage(
                MiscPreferences.WEAR_CLOCK_OPACITY.key, ColorEditorModel.CLOCK_OPACITY_RANGE)
        listOf(
                "wear_font_flex",
                "wear_title_font_flex",
                "wear_artist_font_flex",
                "wear_clock_font_flex",
                "wear_lyrics_font_flex",
                "wear_track_time_font_flex").forEach(::validateFlexAxes)
        // The bound is ColorEditorModel's, so the slider and this typed-entry path cannot
        // disagree about it - see HUE_SHIFT_RANGE for why a full turn is excluded.
        validateNumericPercentage(
                MiscPreferences.WEAR_COLOR_HUE_SHIFT.key, ColorEditorModel.HUE_SHIFT_RANGE)
    }

    private fun validateFlexAxes(prefix: String) {
        validateNumericPercentage(
                "${prefix}_width",
                WatchTypography.FLEX_WIDTH_MIN.toInt()..WatchTypography.FLEX_WIDTH_MAX.toInt())
        validateNumericPercentage(
                "${prefix}_optical_size",
                WatchTypography.FLEX_OPTICAL_SIZE_MIN.toInt()..
                        WatchTypography.FLEX_OPTICAL_SIZE_MAX.toInt())
        validateNumericPercentage(
                "${prefix}_grade",
                WatchTypography.FLEX_GRADE_MIN.toInt()..WatchTypography.FLEX_GRADE_MAX.toInt())
        validateNumericPercentage(
                "${prefix}_roundness",
                WatchTypography.FLEX_ROUNDNESS_MIN.toInt()..
                        WatchTypography.FLEX_ROUNDNESS_MAX.toInt())
    }

    private fun validateNumericPercentage(key: String, range: IntRange) {
        findPreference<EditTextPreference>(key)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, candidate ->
                    val valid = candidate.toString().toIntOrNull() in range
                    if (!valid) {
                        Toast.makeText(
                                requireContext(),
                                getString(
                                        R.string.setting_numeric_range_error,
                                        range.first,
                                        range.last),
                                Toast.LENGTH_SHORT).show()
                    }
                    valid
                }
    }

    /**
     * Keeps the host preview in the same visual context as the row the user is manipulating.
     * Existing listeners own validation and dependency changes, so they always run exactly once;
     * a candidate is reported only after they accept it. The committed value is still observed by
     * the host's single SharedPreferences listener.
     */
    private fun wirePreviewInteractions() {
        val root = preferenceScreen ?: return
        wirePreviewInteractions(root)
    }

    private fun wirePreviewInteractions(group: PreferenceGroup) {
        for (index in 0 until group.preferenceCount) {
            val preference = group.getPreference(index)
            if (preference is PreferenceGroup) {
                wirePreviewInteractions(preference)
            } else {
                wrapPreviewInteraction(preference)
            }
        }
    }

    private fun wrapPreviewInteraction(preference: Preference) {
        val key = preference.key?.takeIf { it.isNotBlank() } ?: return

        val existingClickListener = preference.onPreferenceClickListener
        preference.onPreferenceClickListener = Preference.OnPreferenceClickListener { clicked ->
            notifyPreviewInteraction(key, null)
            existingClickListener?.onPreferenceClick(clicked) ?: false
        }

        val existingChangeListener = preference.onPreferenceChangeListener
        preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { changed, candidate ->
            val accepted = existingChangeListener?.onPreferenceChange(changed, candidate) ?: true
            if (accepted) {
                notifyPreviewInteraction(key, candidate)
            }
            accepted
        }
    }

    private fun notifyPreviewInteraction(key: String, candidateValue: Any?) {
        (parentFragment as? WatchFaceFragment)
                ?.onWatchPreferenceInteraction(section, key, candidateValue)
    }

    private fun initAppearanceResetActions() {
        findPreference<Preference>("reset_appearance")?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    AlertDialog.Builder(requireContext())
                            .setTitle(R.string.reset_appearance_confirm_title)
                            .setMessage(R.string.reset_appearance_confirm_message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.reset_appearance_confirm_button) { _, _ ->
                                resetCurrentFaceAppearance()
                                Toast.makeText(requireContext(),
                                        R.string.reset_appearance_done, Toast.LENGTH_SHORT).show()
                            }
                            .show()
                            .tintLyraButtons()
                    true
                }

    }

    /** Recolours a shown confirm dialog's buttons to match the accent currently on screen, the same
     *  runtime-accent treatment the Lyra preference/colour-picker dialogs get. Without it the buttons
     *  inflate with the static theme green instead of the active (including album-dynamic) accent. */
    private fun AlertDialog.tintLyraButtons() {
        val accent = lyraRuntimeAccent()
        val secondary = ContextCompat.getColor(requireContext(), R.color.lyra_text_secondary)
        getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondary)
        getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(secondary)
    }

    /** Clears every explicitly-set appearance value for the current scope, so the layout falls back
     *  to its per-face defaults - the "reset this layout to its original look" action. Only the
     *  scoped appearance keys are touched; behaviour prefs and the face selector are untouched.
     *  Writing (removing) the flat scoped keys triggers the normal phone -> watch sync, and the
     *  preview/controls re-read via refreshConditionalPreferences below. */
    private fun resetCurrentFaceAppearance() {
        val currentScope = FaceScopedPreferences.scopeFor(ThemeAppearance.resolve(rawPrefs))
        val editor = rawPrefs.edit()
        for (baseKey in FaceScopedPreferences.SCOPED_KEYS) {
            editor.remove(FaceScopedPreferences.scopedKey(baseKey, currentScope))
        }
        editor.apply()
        // The preference controls cache their shown values; re-inflate the screen so it reflects
        // the defaults immediately instead of only after a re-open. The data store binding on the
        // preference manager persists across this, so the rebuilt controls read the cleared values.
        setPreferencesFromResource(R.xml.watch_face_settings, null)
        wirePreferences()
    }

    /** Keeps face-specific settings aligned with the renderer selected in the preview. */
    private fun initFaceDependencies() {
        updateFaceDependencies()
        findPreference<ListPreference>("wear_screen_face")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    // The listener fires before the new value persists, so pass it along instead
                    // of re-reading the (still old) preference.
                    updateFaceDependencies(newValue as? String)
                    true
                }
        findPreference<ListPreference>("wear_aod_style")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    updateAodDetailVisibility(overrideStyle = newValue as? String)
                    true
                }
        findPreference<Preference>("wear_edge_progress_visible")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    updatePlayerCapabilityVisibility(overrideEdgeVisible = newValue as? Boolean)
                    true
                }
        findPreference<Preference>("wear_edge_seek_enabled")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    updatePlayerCapabilityVisibility(overrideEdgeSeekEnabled = newValue as? Boolean)
                    true
                }
    }

    private fun updateFaceDependencies(
            overrideFace: String? = null,
            overrideAodStyle: String? = null
    ) {
        updatePlayerCapabilityVisibility(overrideFace = overrideFace)
        updateBackgroundCapabilityVisibility(overrideFace)
        updateAodDetailVisibility(
                overrideFace = overrideFace,
                overrideStyle = overrideAodStyle
        )
    }

    /** Do not offer controls that the selected renderer cannot consume. This keeps switching
     * layouts from leaving apparently broken rows behind while preserving the saved value for
     * when the user returns to a compatible face. */
    private fun updatePlayerCapabilityVisibility(
            overrideFace: String? = null,
            overrideEdgeVisible: Boolean? = null,
            overrideEdgeSeekEnabled: Boolean? = null
    ) {
        val face = overrideFace ?: readStringPreference("wear_screen_face", "classic")
        val edgeVisible = overrideEdgeVisible ?: store.getBoolean("wear_edge_progress_visible", true)
        val edgeSeekEnabled =
                overrideEdgeSeekEnabled ?: store.getBoolean("wear_edge_seek_enabled", true)
        // Every layout now reads this through AdaptiveTitleText, not just Classic's own
        // OutlineTextView - it always applies.
        findPreference<Preference>("wear_title_text_mode")?.isVisible = true
        // Only the Expressive face reads this; every other face ignores the value entirely.
        findPreference<Preference>("wear_expressive_seek_mode")?.isVisible = face == "expressive"
        // Restyles a face's own icon glyphs only - see PlayerEditorModel.CONTROL_STYLE_FACES.
        findPreference<Preference>(MiscPreferences.WEAR_SCREEN_THEME.key)?.isVisible =
                face in PlayerEditorModel.CONTROL_STYLE_FACES
        // Same rule, same reason: the card silhouette is Carousel's own cover rail. It sat in the
        // Player category unconditioned, so every other layout offered a picker that changed
        // nothing - which reads as a broken setting rather than an inapplicable one.
        findPreference<Preference>(MiscPreferences.WEAR_CAROUSEL_CARD_SHAPE.key)?.isVisible =
                face == "carousel"
        // Same rule again for Note's cover disc, which is the other face that lets its artwork
        // be reshaped. Two keys, two faces - see MiscPreferences.WEAR_NOTE_COVER_SHAPE.
        findPreference<Preference>(MiscPreferences.WEAR_NOTE_COVER_SHAPE.key)?.isVisible =
                face == "note"
        // Metadata groups describe blocks rendered only by the Metadata face. Values remain
        // stored/scoped while hidden and reappear unchanged when that face is selected again.
        findPreference<Preference>("cat_wf_metadata")?.isVisible =
                section == SECTION_STYLE && face == "metadata"
        // Expressive and Material must keep their central transport visible. Other faces,
        // including Poster and Studio, can still be reduced to a clean metadata/artwork layout.
        findPreference<Preference>(MiscPreferences.WEAR_PLAYER_CONTROLS_VISIBLE.key)?.isVisible =
                face !in setOf("expressive", "material")
        // One list, owned by the Player editor's model rather than repeated here - see
        // PlayerEditorModel.INTERNAL_PROGRESS_FACES.
        findPreference<Preference>("wear_internal_progress_visible")?.isVisible =
                face in PlayerEditorModel.INTERNAL_PROGRESS_FACES
        // The ring's own style still matters even with the always-visible resting ring turned
        // off: CircularProgressSeekBar.shouldDrawEdgeProgress reveals it for the lifetime of an
        // active drag regardless of that setting, so hiding the picker whenever dragging can
        // still show the ring would leave no way to choose what that temporary reveal looks like.
        findPreference<Preference>("wear_progress_style")?.isVisible = edgeVisible || edgeSeekEnabled
        findPreference<Preference>("wear_progress_layout")?.isVisible = edgeVisible || edgeSeekEnabled
        // A face that hosts the mini-button row inside its own composition places and shapes those
        // buttons itself (Chat's circles are the configured slots), so neither the curve/rail
        // arrangement nor the pill shape reaches them. Same rule as Carousel's card shape above:
        // a picker that changes nothing reads as broken rather than as inapplicable. Background
        // and opacity are *not* hidden - those do apply, through the shared MiniButtonSurfaces.
        val hostedMiniButtons = MiniButtonPlacement.isHostedByFace(face)
        findPreference<Preference>(MiscPreferences.WEAR_SCREEN_BUTTONS_CURVE_STYLE.key)
                ?.isVisible = !hostedMiniButtons
        findPreference<Preference>(MiscPreferences.WEAR_SCREEN_BUTTONS_SHAPE.key)
                ?.isVisible = !hostedMiniButtons
        // Quadrant hint icons only exist on Classic - every Compose face hides them entirely.
        findPreference<Preference>("wear_quadrant_tap_flash")?.isVisible = face == "classic"
    }

    private fun updateBackgroundCapabilityVisibility(overrideFace: String? = null) {
        // Background and layout are independent now, including Eclipse: selecting Poster,
        // Material or Expressive here must work without replacing the structural renderer.
        findPreference<Preference>("album_art_style")?.isVisible = true
        findPreference<Preference>("wear_split_panel")?.isVisible =
                (overrideFace ?: readStringPreference("wear_screen_face", "classic")) == "split"
        updateBlurRadiusEnabled()

        listOf(
                "dim_album_art",
                "wear_player_shading_style",
                "album_art_dim_strength",
                "wear_shading_color_mode",
                "wear_album_art_fade"
        )
                .forEach { key -> findPreference<Preference>(key)?.isVisible = true }
        // The custom-color row is governed by initAccentColorTarget (visible only in "custom").
    }

    /** The three detailed AOD controls are rendered only by the visual (Compose) AOD faces. */
    private fun updateAodDetailVisibility(
            overrideFace: String? = null,
            overrideStyle: String? = null
    ) {
        val face = overrideFace ?: readStringPreference("wear_screen_face", "classic")
        val selectedStyle = overrideStyle ?: readStringPreference("wear_aod_style", "follow")
        val effectiveStyle = if (selectedStyle == "follow") face else selectedStyle
        val visualStyle = effectiveStyle in setOf(
            "expressive", "vinyl", "poster", "studio", "halo", "aurora", "eclipse",
            "spectrum", "material", "immersive", "depth", "carousel", "chat", "split", "note",
            "verse",
            "metadata", "ribbon", "frame"
        )
        findPreference<Preference>("wear_aod_show_transport")?.isVisible = visualStyle
        findPreference<Preference>("wear_aod_show_progress")?.isVisible = visualStyle
        // The Up Next pill is offered on every visual AOD face now (not just Expressive/Material).
        findPreference<Preference>("wear_aod_show_pills")?.isVisible = visualStyle
        val artworkSupported = effectiveStyle !in setOf("chrono", "eclipse")
        findPreference<Preference>("wear_aod_show_art")?.isVisible = artworkSupported
        findPreference<Preference>("wear_aod_art_treatment")?.isVisible = artworkSupported
        findPreference<Preference>("ambient_album_art_opacity")?.isVisible = artworkSupported
    }

    private fun initBlurRadiusDependency() {
        updateBlurRadiusEnabled()
        findPreference<ListPreference>("album_art_style")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    // The listener fires before the new value persists, so pass it along
                    // instead of re-reading the (still old) preference.
                    updateBlurRadiusEnabled(newValue as? String)
                    true
                }
        updateProgressGradientVisibility()
        findPreference<ListPreference>("wear_progress_style")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    updateProgressGradientVisibility(newValue as? String)
                    true
                }
    }

    /** Keeps the legacy Flex-axis rows hidden: the compact editor exposes the applicable global or
     *  element-owned axis button instead. */
    private fun initTypographyDependencies() {
        updateFlexAxesVisibility()
        findPreference<ListPreference>("wear_font")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    updateFlexAxesVisibility(newValue as? String)
                    true
                }
    }

    private fun updateFlexAxesVisibility(overrideValue: String? = null) {
        // Text renders through the compact editor. Keep the legacy category inflated for search,
        // defaults and dialogs, but never let this value-dependent callback reveal its old rows.
        findPreference<Preference>("cat_wf_typography_flex")?.isVisible = false
        if (overrideValue != null) refreshTypographyEditor()
    }

    private fun updateBlurRadiusEnabled(overrideValue: String? = null) {
        val value = overrideValue ?: readStringPreference("album_art_style", "cover")
        val blurStyle = PlayerBackgroundStyle.fromPreference(value).usesBlurRadius
        findPreference<Preference>("album_art_blur_radius")?.isVisible = blurStyle
    }

    /** The contrast correction measures the title against the artwork, which is meaningless while
     *  the title keeps the face's own designed colour - that colour is not derived from the art. */
    private fun updateTitleAdaptiveContrastVisibility(overrideValue: String? = null) {
        val mode = overrideValue ?: readStringPreference(
                "wear_title_color_mode", MiscPreferences.TITLE_COLOR_FACE_DEFAULT)
        findPreference<Preference>(MiscPreferences.WEAR_TITLE_ADAPTIVE_CONTRAST.key)?.isVisible =
                mode != MiscPreferences.TITLE_COLOR_FACE_DEFAULT
    }

    /** The palette blend only exists on the Solid ring; every other style draws a flat fill, so
     *  offering the switch beside them would promise something they cannot do. */
    private fun updateProgressGradientVisibility(overrideValue: String? = null) {
        val style = overrideValue ?: readStringPreference("wear_progress_style", "solid")
        findPreference<Preference>(MiscPreferences.WEAR_PROGRESS_GRADIENT.key)?.isVisible =
                style == "solid"
    }

    /** Custom color picker wiring plus enabling/disabling the color-mode-dependent rows for one
     *  artist, progress or AOD color target. */
    private fun initAccentColorTarget(
            modeKey: String,
            customColorKey: String,
            desaturatedKey: String?,
            customColorDescription: Int
    ) {
        val colorPref = findPreference<Preference>(customColorKey)
        updateAccentColorTargetSummary(colorPref, customColorKey, customColorDescription)
        colorPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // Read/write through the face-scoped store so each face keeps its own custom color.
            showLyraColorPickerDialog(
                    initialColor = parseHexOrDefault(store.getString(customColorKey, null)),
                    onReset = {
                        // Persist the empty definition value instead of removing the scoped key.
                        // Preference sync does not transmit removals, so deletion would leave the
                        // watch rendering its previous custom color indefinitely.
                        store.putString(customColorKey, "")
                        updateAccentColorTargetSummary(colorPref, customColorKey, customColorDescription)
                    },
                    onApply = { hex ->
                        store.putString(customColorKey, hex)
                        updateAccentColorTargetSummary(colorPref, customColorKey, customColorDescription)
                    },
                    onPreviewColor = { hex -> notifyPreviewInteraction(customColorKey, hex) },
                    onPreviewCancelled = {
                        notifyPreviewInteraction(customColorKey, store.getString(customColorKey, null))
                    }
            )
            true
        }

        updateAccentColorTargetDependencies(modeKey, customColorKey, desaturatedKey)
        findPreference<ListPreference>(modeKey)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val candidate = newValue as? String
                    updateAccentColorTargetDependencies(
                            modeKey, customColorKey, desaturatedKey, candidate)
                    if (modeKey == "wear_title_color_mode") {
                        updateTitleAdaptiveContrastVisibility(candidate)
                    }
                    true
                }
    }

    private fun updateAccentColorTargetSummary(pref: Preference?, customColorKey: String, descriptionRes: Int) {
        pref ?: return
        val saved = store.getString(customColorKey, null)?.takeUnless { it.isBlank() }
        pref.summary = if (saved != null) {
            getString(R.string.color_picker_current, saved)
        } else {
            getString(descriptionRes)
        }
        (pref as? HexColorDotPreference)?.refreshDot()
    }

    private fun updateAccentColorTargetDependencies(
            modeKey: String,
            customColorKey: String,
            desaturatedKey: String?,
            overrideMode: String? = null
    ) {
        val defaultMode = if (modeKey == "wear_aod_color_mode") "white" else "follow"
        val mode = overrideMode ?: readStringPreference(modeKey, defaultMode)
        findPreference<Preference>(customColorKey)?.isVisible = if (modeKey == "wear_aod_color_mode") {
            mode == "custom"
        } else {
            mode == "normal" || mode == "custom"
        }
        if (desaturatedKey != null) {
            findPreference<Preference>(desaturatedKey)?.isVisible = mode == "album"
        }
    }

    /** Migrates the old neutral/album/custom + soften matrix without collapsing the user's
     * artist and progress choices into one global policy. New installations simply inherit the
     * global treatment through the "follow" default. */
    private fun migrateLegacyColorSettings() {
        val context = ThemeAppearance.resolve(rawPrefs)
        val scope = FaceScopedPreferences.scopeFor(context)
        val scopedTreatment = FaceScopedPreferences.scopedKey("wear_color_treatment", scope)
        if (!rawPrefs.contains(scopedTreatment) && !rawPrefs.contains("wear_color_treatment")) {
            store.putString(
                    "wear_color_treatment",
                    if (store.getBoolean("wear_dynamic_accent", true)) "expressive" else "normal")
        }

        fun migrateTarget(modeKey: String, legacyDesaturatedKey: String) {
            val storedKey = FaceScopedPreferences.scopedKey(modeKey, scope)
            val rawMode = when {
                rawPrefs.contains(storedKey) -> rawPrefs.getString(storedKey, null)
                rawPrefs.contains(modeKey) -> rawPrefs.getString(modeKey, null)
                else -> null
            } ?: return
            val migrated = when (rawMode) {
                "neutral", "custom" -> "normal"
                "album" -> if (store.getBoolean(legacyDesaturatedKey, false)) {
                    "desaturated"
                } else {
                    "expressive"
                }
                else -> rawMode
            }
            if (migrated != rawMode) store.putString(modeKey, migrated)
        }

        migrateTarget("wear_artist_color_mode", "wear_artist_desaturated")
        migrateTarget("wear_progress_color_mode", "wear_progress_desaturated")
    }

    private fun initUnifiedColorTreatment() {
        val colorPref = findPreference<Preference>("wear_normal_color")
        updateAccentColorTargetSummary(
                colorPref, "wear_normal_color", R.string.setting_wear_normal_color_description)
        colorPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showLyraColorPickerDialog(
                    initialColor = parseHexOrDefault(store.getString("wear_normal_color", null)),
                    onReset = {
                        // Keep an explicit reset value so the Wear receiver overwrites, rather
                        // than retains, a previously synchronized custom color.
                        store.putString("wear_normal_color", "")
                        updateAccentColorTargetSummary(
                                colorPref, "wear_normal_color",
                                R.string.setting_wear_normal_color_description)
                    },
                    onApply = { hex ->
                        store.putString("wear_normal_color", hex)
                        updateAccentColorTargetSummary(
                                colorPref, "wear_normal_color",
                                R.string.setting_wear_normal_color_description)
                    },
                    onPreviewColor = { hex -> notifyPreviewInteraction("wear_normal_color", hex) },
                    onPreviewCancelled = {
                        notifyPreviewInteraction(
                                "wear_normal_color", store.getString("wear_normal_color", null))
                    }
            )
            true
        }
        updateUnifiedColorTreatmentVisibility(
                readStringPreference("wear_color_treatment", "expressive"))
        findPreference<ListPreference>("wear_color_treatment")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, candidate ->
                    updateUnifiedColorTreatmentVisibility(candidate as? String)
                    true
                }
    }

    private fun updateUnifiedColorTreatmentVisibility(override: String? = null) {
        val treatment = override ?: readStringPreference("wear_color_treatment", "expressive")
        val isNormal = treatment == "normal"
        // The colour *picker* is Normal's own - every other treatment derives its colour from the
        // artwork and has nothing for a picker to set.
        findPreference<Preference>("wear_normal_color")?.isVisible = isNormal
        // The palette switch is not: "use one colour instead of three" is a meaningful choice under
        // every treatment, and under the album-derived ones it is the only way to get the cover's
        // colour applied flat instead of as a light/dark ladder. It began life Normal-only, which
        // is why its key still says so.
        findPreference<Preference>("wear_normal_color_multi")?.isVisible = true
    }

}
