package com.svartifoss.snfell.view.watchface

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.WatchTypography
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import androidx.core.content.ContextCompat
import com.svartifoss.snfell.view.settings.ColorTreatmentPreference
import com.svartifoss.snfell.view.settings.FaceScopedPreferenceDataStore
import com.svartifoss.snfell.view.settings.HexColorDotPreference
import com.svartifoss.snfell.view.settings.lyraRuntimeAccent
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import com.svartifoss.snfell.view.settings.PlaylistShortcutsActivity
import com.svartifoss.snfell.view.settings.SettingsCatalog
import com.svartifoss.snfell.view.settings.parseHexOrDefault
import com.svartifoss.snfell.view.settings.showLyraColorPickerDialog
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
        private const val DEFAULT_SWATCH_COLOR = 0xFF86A69D.toInt()

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
         *  the flat scoped keys triggers the normal phone -> watch sync. Extracted out of the
         *  fragment so [FaceResetMigrationPrompt] can call the same logic without a UI. */
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

    /** Re-reads scoped values after a face change and refreshes archived lists when their
     *  developer switch changes, even if this fragment remained alive beside Settings. */
    private val faceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            MiscPreferences.WEAR_SCREEN_FACE.key,
            MiscPreferences.WEAR_ACTIVE_CUSTOM_THEME_ID.key,
            MiscPreferences.WEAR_CUSTOM_THEME_SCHEMA.key,
            MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.key -> {
                migrateLegacyColorSettings()
                rebindScopedValues()
                refreshColorTargetSummaries()
                refreshConditionalPreferences()
            }
            "dev_show_archived" -> applyArchivedOptionFilters()
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
        // Scope every appearance preference to the selected face BEFORE inflating, so each control
        // reads/writes "<key>@<face>". Must be set before addPreferencesFromResource.
        rawPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        store = FaceScopedPreferenceDataStore(rawPrefs)
        migrateLegacyColorSettings()
        preferenceManager.preferenceDataStore = store
        addPreferencesFromResource(R.xml.watch_face_settings)
        wirePreferences()
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
        initApplyToAllFaces()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
            if (isAdded && findPreference<Preference>(key) != null) scrollToPreference(key)
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

        SettingsCatalog.WATCH_CATEGORIES.forEach { key ->
            findPreference<Preference>(key)?.isVisible = key in visibleCategories
        }
        // Visible only on the Text page, and only once Google Sans Flex is actually chosen -
        // ANDed explicitly rather than folded into visibleCategories above, since this category's
        // visibility depends on a preference *value*, not just which section is showing.
        updateFlexAxesVisibility()
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
    }

    override fun onPause() {
        super.onPause()
        rawPrefs.unregisterOnSharedPreferenceChangeListener(faceChangeListener)
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
        filterArchivedListPreference(
                key = "wear_font",
                entriesRes = R.array.wear_font_entries,
                valuesRes = R.array.wear_font_values,
                archived = archivedFonts,
                defaultValue = "google_sans",
                showArchived = showArchived,
                keepCurrentArchived = false)
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
        applyClockFontEntries(showArchived)
        applyTitleColorEntries()
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
        val entries = resources.getStringArray(R.array.wear_font_entries)
        val values = resources.getStringArray(R.array.wear_font_values)
        val current = pref.value ?: readStringPreference(
                MiscPreferences.WEAR_CLOCK_FONT.key, WatchTypography.CLOCK_FONT_FOLLOW)
        val keep = values.indices.filter { index ->
            index < entries.size &&
                    (showArchived || values[index] !in archivedFonts || values[index] == current)
        }
        pref.entries =
                (listOf(getString(R.string.wear_clock_font_follow)) + keep.map { entries[it] })
                        .toTypedArray()
        pref.entryValues =
                (listOf(WatchTypography.CLOCK_FONT_FOLLOW) + keep.map { values[it] })
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
        findPreference<EditTextPreference>("screen_buttons_opacity")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, candidate ->
                    candidate.toString().toIntOrNull()?.let { it in 0..100 } == true
                }
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
        for (key in listOf("wear_title_font_weight", "wear_artist_font_weight")) {
            validateNumericPercentage(
                    key, WatchTypography.FLEX_WEIGHT_MIN..WatchTypography.FLEX_WEIGHT_MAX)
        }
        for (key in listOf(
                "wear_title_font_scale", "wear_artist_font_scale", "wear_source_icon_scale")) {
            validateNumericPercentage(
                    key, MiscPreferences.TYPOGRAPHY_MIN_SCALE..MiscPreferences.TYPOGRAPHY_MAX_SCALE)
        }
        for (key in listOf(
                "wear_title_font_opacity", "wear_artist_font_opacity", "wear_source_icon_opacity")) {
            validateNumericPercentage(key, MiscPreferences.TYPOGRAPHY_MIN_OPACITY..100)
        }
        for (key in listOf("wear_title_font_tracking", "wear_artist_font_tracking")) {
            validateNumericPercentage(
                    key,
                    MiscPreferences.TYPOGRAPHY_MIN_TRACKING..MiscPreferences.TYPOGRAPHY_MAX_TRACKING)
        }
        validateNumericPercentage(
                "wear_font_flex_width",
                WatchTypography.FLEX_WIDTH_MIN.toInt()..WatchTypography.FLEX_WIDTH_MAX.toInt())
        validateNumericPercentage(
                "wear_font_flex_optical_size",
                WatchTypography.FLEX_OPTICAL_SIZE_MIN.toInt()..
                        WatchTypography.FLEX_OPTICAL_SIZE_MAX.toInt())
        validateNumericPercentage(
                "wear_font_flex_grade",
                WatchTypography.FLEX_GRADE_MIN.toInt()..WatchTypography.FLEX_GRADE_MAX.toInt())
        validateNumericPercentage(
                "wear_font_flex_roundness",
                WatchTypography.FLEX_ROUNDNESS_MIN.toInt()..
                        WatchTypography.FLEX_ROUNDNESS_MAX.toInt())
        // 359, not 360: a full turn is the same hue as 0, so allowing it would give two different
        // stored values that render identically.
        validateNumericPercentage("wear_color_hue_shift", 0..359)
    }

    private fun validateNumericPercentage(key: String, range: IntRange) {
        findPreference<EditTextPreference>(key)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, candidate ->
                    candidate.toString().toIntOrNull() in range
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

    private fun initApplyToAllFaces() {
        findPreference<Preference>("apply_appearance_to_all_faces")?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    AlertDialog.Builder(requireContext())
                            .setTitle(R.string.apply_appearance_confirm_title)
                            .setMessage(R.string.apply_appearance_confirm_message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.apply_appearance_confirm_button) { _, _ ->
                                applyCurrentAppearanceToAllFaces()
                                Toast.makeText(requireContext(),
                                        R.string.apply_appearance_done, Toast.LENGTH_SHORT).show()
                            }
                            .show()
                            .tintLyraButtons()
                    true
                }

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

        findPreference<Preference>("reset_all_faces")?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    AlertDialog.Builder(requireContext())
                            .setTitle(R.string.reset_all_faces_confirm_title)
                            .setMessage(R.string.reset_all_faces_confirm_message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.reset_all_faces_confirm_button) { _, _ ->
                                resetAllFacesAppearance()
                                Toast.makeText(requireContext(),
                                        R.string.reset_all_faces_done, Toast.LENGTH_SHORT).show()
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

    /** Restores every built-in face to its factory look - the recovery action for "someone changed
     *  the built-in themes and can't get back". Deactivates any active custom theme first (so the
     *  reset is actually visible and can't rewrite a saved profile), then clears every scoped
     *  appearance value for all faces plus the custom snapshot scope and the legacy global keys, so
     *  each face falls back purely to its per-face definition default. The chosen face, saved custom
     *  themes and behaviour prefs are left untouched. Removing the flat scoped keys triggers the
     *  normal phone -> watch sync; the screen is re-inflated so the controls show the defaults. */
    private fun resetAllFacesAppearance() {
        resetAllFaces(requireContext(), rawPrefs)
        setPreferencesFromResource(R.xml.watch_face_settings, null)
        wirePreferences()
    }

    /** Copies the current appearance scope's explicitly-set values onto every built-in face scope,
     *  so a look built on one face becomes the starting point for all of them. Keys the user never
     *  chose are left untouched, so each face keeps its own per-face default where nothing was set.
     *  Writing the flat scoped keys triggers the normal phone -> watch preference sync. */
    private fun applyCurrentAppearanceToAllFaces() {
        val currentScope = FaceScopedPreferences.scopeFor(ThemeAppearance.resolve(rawPrefs))
        val snapshot = rawPrefs.all
        val editor = rawPrefs.edit()
        for (baseKey in FaceScopedPreferences.SCOPED_KEYS) {
            val sourceKey = FaceScopedPreferences.scopedKey(baseKey, currentScope)
            val value = snapshot[sourceKey] ?: continue
            for (face in ThemeAppearance.ALLOWED_BASE_FACES) {
                val targetKey = FaceScopedPreferences.scopedKey(baseKey, face)
                if (targetKey == sourceKey) continue
                when (value) {
                    is String -> editor.putString(targetKey, value)
                    is Boolean -> editor.putBoolean(targetKey, value)
                    is Int -> editor.putInt(targetKey, value)
                    is Long -> editor.putLong(targetKey, value)
                    is Float -> editor.putFloat(targetKey, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(targetKey, value as Set<String>)
                }
            }
        }
        editor.apply()
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
        updateBackgroundCapabilityVisibility()
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
        // Expressive and Material must keep their central transport visible. Other faces,
        // including Poster and Studio, can still be reduced to a clean metadata/artwork layout.
        findPreference<Preference>(MiscPreferences.WEAR_PLAYER_CONTROLS_VISIBLE.key)?.isVisible =
                face !in setOf("expressive", "material")
        findPreference<Preference>("wear_internal_progress_visible")?.isVisible = face in setOf(
                "vinyl", "poster", "studio", "halo", "aurora", "eclipse", "spectrum"
        )
        // The ring's own style still matters even with the always-visible resting ring turned
        // off: CircularProgressSeekBar.shouldDrawEdgeProgress reveals it for the lifetime of an
        // active drag regardless of that setting, so hiding the picker whenever dragging can
        // still show the ring would leave no way to choose what that temporary reveal looks like.
        findPreference<Preference>("wear_progress_style")?.isVisible = edgeVisible || edgeSeekEnabled
        // Quadrant hint icons only exist on Classic - every Compose face hides them entirely.
        findPreference<Preference>("wear_quadrant_tap_flash")?.isVisible = face == "classic"
    }

    private fun updateBackgroundCapabilityVisibility() {
        // Background and layout are independent now, including Eclipse: selecting Poster,
        // Material or Expressive here must work without replacing the structural renderer.
        findPreference<Preference>("album_art_style")?.isVisible = true
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
            "spectrum", "material", "immersive", "depth", "carousel", "chat", "split", "note"
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

    /** The Google Sans Flex axis category is meaningless for every other font, so it stays hidden
     *  until that font is actually selected - same idiom as [initBlurRadiusDependency]. */
    private fun initTypographyDependencies() {
        updateFlexAxesVisibility()
        findPreference<ListPreference>("wear_font")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    updateFlexAxesVisibility(newValue as? String)
                    true
                }
    }

    private fun updateFlexAxesVisibility(overrideValue: String? = null) {
        val value = overrideValue ?: readStringPreference("wear_font", "google_sans")
        findPreference<Preference>("cat_wf_typography_flex")?.isVisible =
                section == SECTION_TYPOGRAPHY && WatchTypography.isFlexFont(value)
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
                    updateAccentColorTargetDependencies(modeKey, customColorKey, desaturatedKey, newValue as? String)
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
