package com.svartifoss.snfell.view.watchface

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.doAfterTextChanged
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.svartifoss.snfell.R
import timber.log.Timber
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.svartifoss.snfell.common.AlbumArtSource
import com.svartifoss.snfell.common.DeviceLocalAppearance
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiniButtonPlacement
import com.svartifoss.snfell.common.ColorModifier
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.TextBackdropStyle
import com.svartifoss.snfell.common.TextOutlineStyle
import com.svartifoss.snfell.common.TextShadowColorMode
import com.svartifoss.snfell.common.TextShadowStyle
import com.svartifoss.snfell.common.OverlayBackdropResolver
import com.svartifoss.snfell.common.BackgroundLayer
import com.svartifoss.snfell.common.BackgroundLayerColor
import com.svartifoss.snfell.common.BackgroundLayerKind
import com.svartifoss.snfell.common.BackgroundLayerStack
import com.svartifoss.snfell.common.AccentFloorStyle
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.PlayerShadingStyle
import com.svartifoss.snfell.common.SHADING_MAX_PERCENT
import com.svartifoss.snfell.common.SurfaceColorTreatment
import com.svartifoss.snfell.common.SurfacePaletteResolver
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.TrackMetadataFields
import com.svartifoss.snfell.common.UserFontContract
import com.svartifoss.snfell.view.settings.UserFontStore
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
import com.svartifoss.snfell.view.NEUTRAL_WATCH_ACCENT

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
        private const val DEFAULT_SWATCH_COLOR = NEUTRAL_WATCH_ACCENT
        private const val TYPOGRAPHY_EDITOR_CATEGORY = "cat_wf_typography_editor"
        private const val TYPOGRAPHY_EDITOR_KEY = "typography_editor_surface"
        private const val TYPOGRAPHY_SIZE_STEP = 5
        private const val COLOR_EDITOR_CATEGORY = "cat_wf_colors_editor"
        private const val COLOR_EDITOR_KEY = "color_editor_surface"
        private const val PANEL_EDITOR_CATEGORY = "cat_wf_panels_editor"
        private const val PANEL_EDITOR_KEY = "panel_editor_surface"
        private const val PLAYER_EDITOR_CATEGORY = "cat_wf_player_editor"
        private const val PLAYER_EDITOR_KEY = "player_editor_surface"
        private const val BACKGROUND_EDITOR_CATEGORY = "cat_wf_background_editor"
        private const val BACKGROUND_EDITOR_KEY = "background_editor_surface"
        private const val AOD_EDITOR_CATEGORY = "cat_wf_aod_editor"
        private const val AOD_EDITOR_KEY = "aod_editor_surface"
        private const val MINI_BUTTON_EDITOR_CATEGORY = "cat_wf_mini_buttons_editor"
        private const val MINI_BUTTON_EDITOR_KEY = "mini_button_editor_surface"
        private val BACKGROUND_LAYERS_KEY = MiscPreferences.WEAR_BACKGROUND_LAYERS.key
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
    private var backgroundEditor: BackgroundEditorPreference? = null
    private var aodEditor: AodEditorPreference? = null
    private var miniButtonEditor: MiniButtonEditorPreference? = null

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
                refreshBackgroundEditor()
                // The ambient style resolves through the awake face when it is set to "follow",
                // and the mini-button arrangement rows depend on whether the face hosts the row.
                refreshAodEditor()
                refreshMiniButtonEditor()
            }
            "dev_show_archived" -> {
                applyArchivedOptionFilters()
                refreshTypographyEditor()
                // The overlay backdrop list hides an archived value, so its label can change here.
                refreshPanelEditor()
                // The face picker hides archived faces the same way.
                refreshPlayerEditor()
                // Every layer's style label comes from a picker that filters archived values.
                refreshBackgroundEditor()
                // The mini-button background and shape lists each hide an archived value.
                refreshMiniButtonEditor()
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
            // The stack has no Preference of its own, so nothing else would repaint the list -
            // and the artwork rows it sits over are edited from that same surface.
            MiscPreferences.WEAR_BACKGROUND_LAYERS.key,
            in BackgroundEditorModel.keys -> {
                rebindScopedValues()
                refreshBackgroundEditor()
            }
            in AodEditorModel.keys -> {
                rebindScopedValues()
                refreshAodEditor()
            }
            in MiniButtonEditorModel.keys -> {
                rebindScopedValues()
                refreshMiniButtonEditor()
            }
            else -> if (LyraAccent.affectsResolvedColor(baseKey)) {
                refreshTypographyEditor()
                refreshColorEditor()
                refreshPanelEditor()
                refreshPlayerEditor()
                refreshBackgroundEditor()
                refreshAodEditor()
                refreshMiniButtonEditor()
            }
        }
    }

    /** Values hidden from their normal pickers because they are archived. They come back when the
     *  developer-mode "Show archived options" switch is on. A value currently selected always
     *  stays listed so an existing configuration can be understood and changed without migration. */
    private val archivedFaces = com.svartifoss.snfell.view.watchface.theme.ArchivedFaces.KEYS
    /** "typewriter" (Mom's Typewriter) is retired rather than merely archived: the bundled font
     *  carried no redistribution license this project ever held, so the file itself was removed
     *  and the key can never come back even with "Show archived options" on - it no longer has an
     *  entry in `wear_font_values` at all. The set stays non-empty (required by
     *  CommunityThemeVocabularyParityTest, which looks up `archivedFonts` by name) and keeps
     *  "typewriter" out of the public vocabulary permanently; WatchFontCatalog/watchFontFamily
     *  still alias the key to Special Elite so an old saved config or downloaded theme renders
     *  something instead of silently falling back to Google Sans. */
    private val archivedFonts = setOf("typewriter")
    /** "liquid_glass" shipped and did not work in practice - archived rather than removed. */
    private val archivedOverlayBackdrops = setOf("liquid_glass")
    /**
     * The two device-local album-art sources, archived rather than removed.
     *
     * They shipped and did not work in practice - the same disposition `liquid_glass` got. Removing
     * the values instead would leave anyone who had selected one with a picker showing a raw string
     * and nothing checked, and would break the face silently; archiving keeps the whole path alive
     * and simply stops offering it. The file-picker rows they depend on follow the *source*, not
     * this set, so an install that already selected one can still change its picture.
     */
    private val archivedAlbumArtSources = setOf("custom_image", "custom_folder")

    private val archivedMiniButtonBackgrounds = setOf("solid_theme")
    private val archivedMiniButtonShapes = setOf(
            "pill_wide_large", "pill_wide_xlarge", "rounded_rect_medium", "rounded_rect_large")

    /**
     * Picks a font file to import - see [UserFontStore].
     *
     * `OpenDocument` rather than `GetContent`: the latter can hand back a URI from a provider that
     * only guarantees it for the duration of the result, and this reads the whole file. The MIME
     * filter is advisory, since providers disagree about font types and plenty report a perfectly
     * good TTF as `application/octet-stream` - the bytes are validated on import instead.
     */
    private val importFontLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
    ) { uri ->
        // A cancelled picker has to release the pending target too. Left set, the *next* import -
        // possibly started from the row rather than from a picker - would apply the font to
        // whichever control happened to ask last time.
        if (uri == null) pendingUserFontTarget = null else importUserFont(uri)
    }

    /** Picks the single picture behind the player for `AlbumArtSource.CUSTOM_IMAGE`. */
    private val pickArtworkImageLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { persistCustomArtwork(it, MiscPreferences.CUSTOM_ALBUM_ART_IMAGE.key) } }

    /**
     * Picks the folder `AlbumArtSource.CUSTOM_FOLDER` draws from.
     *
     * A document *tree*, not a multi-file selection: a tree keeps working as the user adds pictures
     * to the folder, which is the whole reason to choose a folder rather than one picture.
     */
    private val pickArtworkFolderLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { persistCustomArtwork(it, MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key) } }

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
        initAccentColorTarget(
                modeKey = "wear_lyrics_color_mode",
                customColorKey = "wear_lyrics_custom_color",
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_lyrics_custom_color_description
        )
        initAccentColorTarget(
                modeKey = "wear_queue_color_mode",
                customColorKey = "wear_queue_custom_color",
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_queue_custom_color_description
        )
        // Shading color modes are black/album/desaturated/custom; only "custom" reveals the color
        // row, which the shared dependency logic already produces since there is no "normal" here.
        initAccentColorTarget(
                modeKey = "wear_shading_color_mode",
                customColorKey = "wear_shading_custom_color",
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_shading_custom_color_description
        )
        initAccentColorTarget(
                modeKey = "wear_accent_floor_color_mode",
                customColorKey = "wear_accent_floor_custom_color",
                desaturatedKey = null,
                customColorDescription = R.string.setting_wear_accent_floor_custom_color_description
        )
        initAppearanceResetActions()
        initUserFontRow()
        initCustomArtworkRows()
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
        // The three text effects on Text -> Title/Artist. Their hex rows were the only custom
        // colours in the app with no picker wired to them at all: initAccentColorTarget covers the
        // nine colour *targets*, and these six were added later with the effects themselves. So
        // choosing Custom set the mode and left no way, on this page or anywhere else, to say which
        // colour it meant. Not initAccentColorTarget, because that also owns a mode row's
        // visibility dependencies and these modes have none - the compact editor decides what is on
        // screen here.
        listOf(
                MiscPreferences.WEAR_TITLE_SHADOW_CUSTOM_COLOR,
                MiscPreferences.WEAR_TITLE_OUTLINE_CUSTOM_COLOR,
                MiscPreferences.WEAR_TITLE_TEXT_BG_CUSTOM_COLOR,
                MiscPreferences.WEAR_ARTIST_SHADOW_CUSTOM_COLOR,
                MiscPreferences.WEAR_ARTIST_OUTLINE_CUSTOM_COLOR,
                MiscPreferences.WEAR_ARTIST_TEXT_BG_CUSTOM_COLOR
        ).forEach { initTextEffectCustomColorRow(it.key) }
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
        initBackgroundEditor()
        initAodEditor()
        initMiniButtonEditor()
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
            "album_art_filter",
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
        root.findViewById<MaterialButton>(R.id.typography_shadow_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.SHADOW)?.let(::openPreferenceDialog)
        }
        root.findViewById<MaterialButton>(R.id.typography_shadow_color_button).setOnClickListener {
            // The mode picker, not the hex dot: picking "Custom" is what makes the hex meaningful,
            // and the mode dialog is where that choice lives. HexColorDotPreference stays reachable
            // from search, which resolves to this same control.
            settingKey(typographyTarget, TypographyControl.SHADOW_COLOR)?.let(::openPreferenceDialog)
        }
        root.findViewById<MaterialButton>(R.id.typography_shadow_strength_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.SHADOW_STRENGTH)
                    ?.let(::showTypographySlider)
        }
        root.findViewById<MaterialButton>(R.id.typography_shadow_custom_color_button)
                .setOnClickListener {
                    customColorKey(typographyTarget, TypographyControl.SHADOW_COLOR)
                            ?.let(::openColorPicker)
                }
        root.findViewById<MaterialButton>(R.id.typography_outline_custom_color_button)
                .setOnClickListener {
                    customColorKey(typographyTarget, TypographyControl.OUTLINE_COLOR)
                            ?.let(::openColorPicker)
                }
        root.findViewById<MaterialButton>(R.id.typography_backdrop_custom_color_button)
                .setOnClickListener {
                    customColorKey(typographyTarget, TypographyControl.BACKDROP_COLOR)
                            ?.let(::openColorPicker)
                }
        root.findViewById<MaterialButton>(R.id.typography_outline_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.OUTLINE)?.let(::openPreferenceDialog)
        }
        root.findViewById<MaterialButton>(R.id.typography_outline_color_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.OUTLINE_COLOR)
                    ?.let(::openPreferenceDialog)
        }
        root.findViewById<MaterialButton>(R.id.typography_backdrop_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.BACKDROP)?.let(::openPreferenceDialog)
        }
        root.findViewById<MaterialButton>(R.id.typography_backdrop_color_button).setOnClickListener {
            settingKey(typographyTarget, TypographyControl.BACKDROP_COLOR)
                    ?.let(::openPreferenceDialog)
        }
        root.findViewById<MaterialButton>(R.id.typography_backdrop_opacity_button)
                .setOnClickListener {
                    settingKey(typographyTarget, TypographyControl.BACKDROP_OPACITY)
                            ?.let(::showTypographySlider)
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
                com.svartifoss.snfell.common.TextCase.TITLE_CASE -> R.drawable.ic_titlecase
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

        val shadowButton = root.findViewById<MaterialButton>(R.id.typography_shadow_button)
        val shadowColorButton = root.findViewById<MaterialButton>(R.id.typography_shadow_color_button)
        val shadowStrengthButton =
                root.findViewById<MaterialButton>(R.id.typography_shadow_strength_button)
        val shadowDetailRow = root.findViewById<View>(R.id.typography_shadow_detail_row)
        val shadowKey = settingKey(typographyTarget, TypographyControl.SHADOW)
        shadowButton.isVisible = shadowKey != null
        var shadowOn = false
        shadowKey?.let { key ->
            val value = readStringPreference(key, choiceDefault(key))
            shadowOn = TextShadowStyle.fromPreference(value) != TextShadowStyle.NONE
            shadowButton.text = choiceLabel(key, value)
            shadowButton.contentDescription = buildPreferenceDescription(key, shadowButton.text)
        }
        // Colour and intensity only exist once there is a shadow for them to change.
        shadowDetailRow.isVisible = shadowButton.isVisible && shadowOn
        var shadowCustom = false
        if (shadowDetailRow.isVisible) {
            settingKey(typographyTarget, TypographyControl.SHADOW_COLOR)?.let { key ->
                val value = readStringPreference(key, choiceDefault(key))
                shadowCustom = typographyCustomColorApplies(value)
                shadowColorButton.text = choiceLabel(key, value)
                shadowColorButton.contentDescription =
                        buildPreferenceDescription(key, shadowColorButton.text)
            }
            settingKey(typographyTarget, TypographyControl.SHADOW_STRENGTH)?.let { key ->
                val value = readTypographyNumber(key)
                shadowStrengthButton.text = "$value%"
                shadowStrengthButton.contentDescription =
                        buildPreferenceDescription(key, shadowStrengthButton.text)
            }
        }
        bindTypographyCustomColor(
                root.findViewById(R.id.typography_shadow_custom_color_button),
                TypographyControl.SHADOW_COLOR,
                shadowDetailRow.isVisible && shadowCustom)

        val outlineButton = root.findViewById<MaterialButton>(R.id.typography_outline_button)
        val outlineColorButton =
                root.findViewById<MaterialButton>(R.id.typography_outline_color_button)
        val outlineKey = settingKey(typographyTarget, TypographyControl.OUTLINE)
        outlineButton.isVisible = outlineKey != null
        var outlineOn = false
        outlineKey?.let { key ->
            val value = readStringPreference(key, choiceDefault(key))
            outlineOn = TextOutlineStyle.fromPreference(value) != TextOutlineStyle.NONE
            outlineButton.text = choiceLabel(key, value)
            outlineButton.contentDescription = buildPreferenceDescription(key, outlineButton.text)
        }
        outlineColorButton.isVisible = outlineButton.isVisible && outlineOn
        var outlineCustom = false
        if (outlineColorButton.isVisible) {
            settingKey(typographyTarget, TypographyControl.OUTLINE_COLOR)?.let { key ->
                val value = readStringPreference(key, choiceDefault(key))
                outlineCustom = typographyCustomColorApplies(value)
                outlineColorButton.text = choiceLabel(key, value)
                outlineColorButton.contentDescription =
                        buildPreferenceDescription(key, outlineColorButton.text)
            }
        }
        bindTypographyCustomColor(
                root.findViewById(R.id.typography_outline_custom_color_button),
                TypographyControl.OUTLINE_COLOR,
                outlineColorButton.isVisible && outlineCustom)

        val backdropButton = root.findViewById<MaterialButton>(R.id.typography_backdrop_button)
        val backdropColorButton =
                root.findViewById<MaterialButton>(R.id.typography_backdrop_color_button)
        val backdropOpacityButton =
                root.findViewById<MaterialButton>(R.id.typography_backdrop_opacity_button)
        val backdropDetailRow = root.findViewById<View>(R.id.typography_backdrop_detail_row)
        val backdropKey = settingKey(typographyTarget, TypographyControl.BACKDROP)
        backdropButton.isVisible = backdropKey != null
        var backdropOn = false
        backdropKey?.let { key ->
            val value = readStringPreference(key, choiceDefault(key))
            backdropOn = TextBackdropStyle.fromPreference(value) != TextBackdropStyle.NONE
            backdropButton.text = choiceLabel(key, value)
            backdropButton.contentDescription =
                    buildPreferenceDescription(key, backdropButton.text)
        }
        backdropDetailRow.isVisible = backdropButton.isVisible && backdropOn
        var backdropCustom = false
        if (backdropDetailRow.isVisible) {
            settingKey(typographyTarget, TypographyControl.BACKDROP_COLOR)?.let { key ->
                val value = readStringPreference(key, choiceDefault(key))
                backdropCustom = typographyCustomColorApplies(value)
                backdropColorButton.text = choiceLabel(key, value)
                backdropColorButton.contentDescription =
                        buildPreferenceDescription(key, backdropColorButton.text)
            }
            settingKey(typographyTarget, TypographyControl.BACKDROP_OPACITY)?.let { key ->
                val value = readTypographyNumber(key)
                backdropOpacityButton.text = "$value%"
                backdropOpacityButton.contentDescription =
                        buildPreferenceDescription(key, backdropOpacityButton.text)
            }
        }
        val backdropCustomColorButton = bindTypographyCustomColor(
                root.findViewById(R.id.typography_backdrop_custom_color_button),
                TypographyControl.BACKDROP_COLOR,
                backdropDetailRow.isVisible && backdropCustom)

        listOfNotNull(
                elementFontButton.takeIf { it.isVisible },
                elementFlexButton.takeIf { it.isVisible },
                weightButton.takeIf { it.isVisible },
                italicButton.takeIf { it.isVisible },
                *sizeControls.filter { it.isVisible }.toTypedArray(),
                opacityButton.takeIf { it.isVisible },
                trackingButton.takeIf { it.isVisible },
                caseButton.takeIf { it.isVisible },
                shadowButton.takeIf { it.isVisible },
                shadowColorButton.takeIf { shadowDetailRow.isVisible },
                shadowStrengthButton.takeIf { shadowDetailRow.isVisible },
                outlineButton.takeIf { it.isVisible },
                outlineColorButton.takeIf { it.isVisible },
                backdropButton.takeIf { it.isVisible },
                backdropColorButton.takeIf { backdropDetailRow.isVisible },
                backdropOpacityButton.takeIf { backdropDetailRow.isVisible },
                root.findViewById<MaterialButton>(R.id.typography_shadow_custom_color_button)
                        .takeIf { it.isVisible },
                root.findViewById<MaterialButton>(R.id.typography_outline_custom_color_button)
                        .takeIf { it.isVisible },
                backdropCustomColorButton.takeIf { it.isVisible },
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

        // LyraGestureButton sets iconTint=@null so the gesture/action pickers can hand-tint their
        // action icons; these rows never do, so their icon drawables' own flat white fill was
        // showing through unmodified - invisible on the light theme's surface. The colour-swatch
        // rows (bindColorSwatchButton/bindTypographyCustomColor) already set their own iconTint
        // from the picked colour and must stay out of this list.
        listOf(
                R.id.typography_font_button,
                R.id.typography_flex_button,
                R.id.typography_element_font_button,
                R.id.typography_element_flex_button,
                R.id.typography_behavior_button,
                R.id.typography_shadow_button,
                R.id.typography_shadow_color_button,
                R.id.typography_shadow_strength_button,
                R.id.typography_outline_button,
                R.id.typography_outline_color_button,
                R.id.typography_backdrop_button,
                R.id.typography_backdrop_color_button,
                R.id.typography_backdrop_opacity_button
        ).forEach { id -> root.findViewById<MaterialButton>(id)?.iconTint = neutralForegrounds }

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
    /**
     * Shows the picked-colour swatch for one of the three text effects, when it has one to show.
     *
     * The swatch exists because the mode row and the hex row behind it are one control on this
     * page: the mode button opens a `ListPreference` dialog, which only reports an actual change,
     * so re-picking Custom to reach the colour did nothing and the hex row - hidden here like every
     * other legacy row - had no other way in. Choosing Custom now reveals a second button, exactly
     * as the Colors page pairs its mode with a swatch.
     */
    private fun bindTypographyCustomColor(
            button: MaterialButton,
            control: TypographyControl,
            applies: Boolean
    ): MaterialButton {
        val key = customColorKey(typographyTarget, control)
        button.isVisible = applies && key != null
        if (button.isVisible && key != null) {
            bindColorSwatchButton(button, key, findPreference<Preference>(key)?.title)
        }
        return button
    }

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
        val valueLayout = content.findViewById<TextInputLayout>(
                R.id.typography_slider_value_layout)
        val valueInput = content.findViewById<TextInputEditText>(R.id.typography_slider_value)
        val minLabel = content.findViewById<TextView>(R.id.typography_slider_min)
        val maxLabel = content.findViewById<TextView>(R.id.typography_slider_max)
        val initial = store.getInt(key, defaultValue).coerceIn(range)
        var selected = initial
        var syncingManualValue = false
        lateinit var dialog: AlertDialog

        slider.valueFrom = range.first.toFloat()
        slider.valueTo = range.last.toFloat()
        slider.stepSize = 1f
        slider.value = selected.toFloat()
        minLabel.text = format(range.first)
        maxLabel.text = format(range.last)
        valueLayout.hint = preference.title
        fun renderValue(value: Int, updateInput: Boolean = true) {
            selected = value.coerceIn(range)
            if (updateInput) {
                val rawValue = selected.toString()
                if (valueInput.text?.toString() != rawValue) {
                    syncingManualValue = true
                    valueInput.setText(rawValue)
                    valueInput.setSelection(rawValue.length)
                    syncingManualValue = false
                }
            }
            valueLayout.error = null
            valueInput.contentDescription =
                    buildPreferenceDescription(key, format(selected))
        }
        renderValue(selected)
        valueInput.doAfterTextChanged { editable ->
            if (syncingManualValue) return@doAfterTextChanged
            val typed = editable?.toString()?.trim().orEmpty()
            val manualValue = typed.toIntOrNull()
            val valid = manualValue != null && manualValue in range
            valueLayout.error = if (valid) {
                null
            } else {
                getString(R.string.setting_numeric_range_error, range.first, range.last)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = valid
            if (!valid) return@doAfterTextChanged

            selected = manualValue
            if (slider.value != selected.toFloat()) slider.value = selected.toFloat()
            renderValue(selected, updateInput = false)
            notifyPreviewInteraction(key, selected.toString())
        }
        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            renderValue(value.toInt())
            notifyPreviewInteraction(key, selected.toString())
        }
        dialog = AlertDialog.Builder(requireContext())
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
            tintNumericSliderDialog(dialog, slider, valueLayout, valueInput)
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
            TypographyEditorModel.settingKeyFor(target, control)

    /** The picked-colour row behind a colour control, or null where that control has none. */
    private fun customColorKey(target: TypographyTarget, control: TypographyControl): String? =
            TypographyEditorModel.customColorKeyFor(target, control)

    /** Whether a [TextShadowColorMode] value is the one that makes a picked colour meaningful. */
    private fun typographyCustomColorApplies(mode: String?): Boolean =
            TextShadowColorMode.fromPreference(mode) == TextShadowColorMode.CUSTOM

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

    private fun controlIdFor(target: TypographySearchTarget): Int = when {
        target.hex -> when (target.control) {
            TypographyControl.SHADOW_COLOR -> R.id.typography_shadow_custom_color_button
            TypographyControl.OUTLINE_COLOR -> R.id.typography_outline_custom_color_button
            else -> R.id.typography_backdrop_custom_color_button
        }
        else -> controlIdForMode(target.control)
    }

    private fun controlIdForMode(control: TypographyControl): Int = when (control) {
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
        TypographyControl.SHADOW -> R.id.typography_shadow_button
        TypographyControl.SHADOW_COLOR -> R.id.typography_shadow_color_button
        TypographyControl.SHADOW_STRENGTH -> R.id.typography_shadow_strength_button
        TypographyControl.OUTLINE -> R.id.typography_outline_button
        TypographyControl.OUTLINE_COLOR -> R.id.typography_outline_color_button
        TypographyControl.BACKDROP -> R.id.typography_backdrop_button
        TypographyControl.BACKDROP_COLOR -> R.id.typography_backdrop_color_button
        TypographyControl.BACKDROP_OPACITY -> R.id.typography_backdrop_opacity_button
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
        root.findViewById<MaterialButton>(R.id.color_editor_tone_button).setOnClickListener {
            ColorEditorModel.keyFor(colorTarget, ColorControl.TONE)?.let(::openPreferenceDialog)
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

        // Only Title, Artist and Clock own a Tone. The panels take the watch-wide one, which is
        // on the global card above, so a per-element row there would be a second control for a
        // value they do not have.
        val toneKey = ColorEditorModel.keyFor(colorTarget, ColorControl.TONE)
        val toneButton = root.findViewById<MaterialButton>(R.id.color_editor_tone_button)
        toneButton.isVisible = toneKey != null
        toneKey?.let { key ->
            val label = choiceLabel(key, readStringPreference(key, colorChoiceDefault(key)))
            toneButton.text = "${findPreference<Preference>(key)?.title ?: key} · $label"
            toneButton.contentDescription = buildPreferenceDescription(key, label)
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
        // Every button in the rail, read off the group rather than from a list of ids.
        // The list version went stale the moment the rail grew: Lyrics and Queue were added and
        // not named here, so those two alone kept the static button style - no accent fill when
        // selected, a different label colour and a different stroke, in the same strip as six
        // buttons that followed the accent. Asking the group is what makes that impossible.
        targetButtons(root).forEach { button ->
            button.backgroundTintList = fills
            button.setTextColor(foregrounds)
            button.strokeColor = strokes
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

        // Same trap as tintTypographyEditor: LyraGestureButton's iconTint=@null leaves these
        // rows' static glyphs at the drawable's own white fill. color_editor_normal_color_button,
        // _mode_button and _custom_color_button are deliberately absent - tintColorButtonIcon
        // already tints their dot to the colour it names.
        listOf(
                R.id.color_editor_treatment_button,
                R.id.color_editor_tone_button,
                R.id.color_editor_opacity_button
        ).forEach { id -> root.findViewById<MaterialButton>(id)?.iconTint = neutralForegrounds }

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

    /** The rail's buttons in declaration order - see the styling loop for why this is not a list. */
    private fun targetButtons(root: View): List<MaterialButton> {
        val group = root.findViewById<MaterialButtonToggleGroup>(R.id.color_editor_target_group)
                ?: return emptyList()
        return (0 until group.childCount).mapNotNull { group.getChildAt(it) as? MaterialButton }
    }

    private fun colorButtonIdFor(target: ColorTarget): Int = when (target) {
        ColorTarget.TITLE -> R.id.color_editor_target_title
        ColorTarget.ARTIST -> R.id.color_editor_target_artist
        ColorTarget.CLOCK -> R.id.color_editor_target_clock
        ColorTarget.PROGRESS -> R.id.color_editor_target_progress
        ColorTarget.VOLUME -> R.id.color_editor_target_volume
        ColorTarget.QUICK_PANEL -> R.id.color_editor_target_quick_panel
        ColorTarget.LYRICS -> R.id.color_editor_target_lyrics
        ColorTarget.QUEUE -> R.id.color_editor_target_queue
    }

    private fun colorTargetForButtonId(id: Int): ColorTarget? = when (id) {
        R.id.color_editor_target_title -> ColorTarget.TITLE
        R.id.color_editor_target_artist -> ColorTarget.ARTIST
        R.id.color_editor_target_clock -> ColorTarget.CLOCK
        R.id.color_editor_target_progress -> ColorTarget.PROGRESS
        R.id.color_editor_target_volume -> ColorTarget.VOLUME
        R.id.color_editor_target_quick_panel -> ColorTarget.QUICK_PANEL
        R.id.color_editor_target_lyrics -> ColorTarget.LYRICS
        R.id.color_editor_target_queue -> ColorTarget.QUEUE
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
        ColorControl.TONE -> R.id.color_editor_tone_button
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
        root.findViewById<MaterialButton>(R.id.panel_editor_surface_backdrop_button)
                .setOnClickListener {
                    PanelEditorModel.keyFor(panelTarget, PanelControl.SURFACE_BACKDROP)
                            ?.let(::openPreferenceDialog)
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

        // The tab's own background. Every target has one, so it is always present - what changes
        // is which key it edits, exactly as the style and layout buttons below do.
        val surfaceBackdropKey =
                PanelEditorModel.keyFor(panelTarget, PanelControl.SURFACE_BACKDROP)
        val surfaceBackdropButton = root.findViewById<MaterialButton>(
                R.id.panel_editor_surface_backdrop_button)
        surfaceBackdropButton.isVisible = surfaceBackdropKey != null
        surfaceBackdropKey?.let { key ->
            val default = (PanelEditorModel.specFor(key)?.value as? PanelValueSpec.Choice)
                    ?.defaultValue ?: OverlayBackdropResolver.SHARED
            val label = choiceLabel(key, readStringPreference(key, default))
            surfaceBackdropButton.text =
                    "${findPreference<Preference>(key)?.title ?: key} · $label"
            surfaceBackdropButton.contentDescription = buildPreferenceDescription(key, label)
        }

        val blurKey = MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS.key
        val blurButton = root.findViewById<MaterialButton>(R.id.panel_editor_blur_button)
        // Most OverlayBackdrop treatments are solid fields or authored gradients this radius has
        // no effect on - see panelControlApplies(PanelControl.BLUR). Showing it regardless of the
        // chosen background made the control read as broken for whichever style was current.
        // The button is the sole child of its own fixed-height scroll row, so that row is hidden
        // too - otherwise it survives as an empty 56dp band between the backdrop picker and the
        // target tabs.
        val blurApplies = panelControlApplies(PanelControl.BLUR, blurKey)
        blurButton.isVisible = blurApplies
        root.findViewById<View>(R.id.panel_editor_global_scroll).isVisible = blurApplies
        if (blurApplies) {
            val blurValue = store.getInt(
                    blurKey, MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS.defaultValue)
            blurButton.text = "${getString(R.string.category_wf_panel_effects)} · $blurValue"
            blurButton.contentDescription = buildPreferenceDescription(blurKey, blurValue)
        }

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
        // Only a minority of OverlayBackdrop's ~35 treatments actually sample the blurred cover
        // (OverlayBackdrop.usesAlbumBlur) - the rest are solid fields or authored gradients this
        // radius has no effect on. The row is anchored to Volume (see PanelEditorModel's class
        // doc), so it resolves through that surface's own content style - the same resolution
        // OverlayBackdropDrawables.build ends up running for the real Volume overlay - rather
        // than the raw stored value alone, so "Follow style" is judged by what it actually
        // follows into instead of always reading as "no blur".
        PanelControl.BLUR ->
            OverlayBackdropResolver.resolve(
                    readStringPreference(
                            MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE.key,
                            MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE.defaultValue),
                    readStringPreference(
                            MiscPreferences.WEAR_VOLUME_STYLE.key,
                            MiscPreferences.WEAR_VOLUME_STYLE.defaultValue))
                    .usesAlbumBlur
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
                R.id.panel_editor_target_queue,
                R.id.panel_editor_target_lyrics
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

        // Same trap as tintTypographyEditor: LyraGestureButton's iconTint=@null leaves these
        // rows' static glyphs at the drawable's own white fill, invisible on the light theme.
        listOf(
                R.id.panel_editor_backdrop_button,
                R.id.panel_editor_ring_style_button,
                R.id.panel_editor_ring_layout_button,
                R.id.panel_editor_surface_backdrop_button,
                R.id.panel_editor_style_button,
                R.id.panel_editor_layout_button,
                R.id.panel_editor_row_size_button,
                R.id.panel_editor_up_next_style_button,
                R.id.panel_editor_source_button,
                R.id.panel_editor_shortcuts_button
        ).forEach { id -> root.findViewById<MaterialButton>(id)?.iconTint = neutralForegrounds }

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
        // Lyrics owns no style row - its only control is its background - so the style key is
        // not a safe stand-in for "what this tab is about" any more.
        (PanelEditorModel.keyFor(target, PanelControl.STYLE)
                ?: PanelEditorModel.keyFor(target, PanelControl.SURFACE_BACKDROP))
                ?.let { notifyPreviewInteraction(it, null) }
    }

    private fun panelButtonIdFor(target: PanelTarget): Int = when (target) {
        PanelTarget.VOLUME -> R.id.panel_editor_target_volume
        PanelTarget.SEEK -> R.id.panel_editor_target_seek
        PanelTarget.QUICK_PANEL -> R.id.panel_editor_target_quick
        PanelTarget.QUEUE -> R.id.panel_editor_target_queue
        PanelTarget.LYRICS -> R.id.panel_editor_target_lyrics
    }

    private fun panelTargetForButtonId(id: Int): PanelTarget? = when (id) {
        R.id.panel_editor_target_volume -> PanelTarget.VOLUME
        R.id.panel_editor_target_seek -> PanelTarget.SEEK
        R.id.panel_editor_target_quick -> PanelTarget.QUICK_PANEL
        R.id.panel_editor_target_queue -> PanelTarget.QUEUE
        R.id.panel_editor_target_lyrics -> PanelTarget.LYRICS
        else -> null
    }

    private fun panelControlIdFor(target: PanelSearchTarget): Int = when (target.control) {
        PanelControl.BACKDROP -> R.id.panel_editor_backdrop_button
        PanelControl.SURFACE_BACKDROP -> R.id.panel_editor_surface_backdrop_button
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
        // The one choice row gated by another preference rather than by the face: the position
        // mark is drawn on the shared edge ring, so switching that ring off leaves it with nothing
        // to sit on. A picker that changes nothing reads as broken, which is the same reason the
        // control-style and per-face rows above are hidden rather than merely inert.
        val edgeArcOn = store.getBoolean(
                MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE.key,
                MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE.defaultValue)
        renderPlayerChoiceRows(
                root.findViewById(R.id.player_editor_choice_rows),
                PlayerEditorModel.visibleIn(PlayerSlot.CHOICE, face).filter { spec ->
                    spec.control != PlayerControl.SEEK_MARKER || edgeArcOn
                })

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
            group.addView(
                    newEditorChip(
                            key = spec.key,
                            // The short noun where one exists; the sentence otherwise. See
                            // PlayerSettingSpec.
                            label = spec.chipLabelRes?.let(::getString) ?: title,
                            description = title,
                            checked = store.getBoolean(spec.key, toggle.defaultValue),
                            onToggle = { checked -> commitPlayerBoolean(spec.key, checked) }))
        }
    }

    /** Rebuilds the multi-way pickers. Cleared first, for the reason [renderPlayerChips] gives. */
    private fun renderPlayerChoiceRows(container: LinearLayout, specs: List<PlayerSettingSpec>) {
        container.removeAllViews()
        container.isVisible = specs.isNotEmpty()
        specs.forEach { spec ->
            val button = newEditorChoiceRow(spec.key) { openPreferenceDialog(spec.key) }
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

        // Same trap as tintTypographyEditor: LyraGestureButton's iconTint=@null leaves these
        // rows' static glyphs at the drawable's own white fill, invisible on the light theme.
        listOf(
                R.id.player_editor_face_button,
                R.id.player_editor_screen_theme_button,
                R.id.player_editor_reset_button
        ).forEach { id -> root.findViewById<MaterialButton>(id)?.iconTint = neutralForegrounds }

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
            if (section == SECTION_BACKGROUND) {
                BackgroundEditorModel.controlFor(key)?.let { control ->
                    // Six of these rows describe one particular layer, and a face can now carry
                    // several of each - so they resolve to the list rather than to a control, and
                    // the pulse points at where the answer moved to.
                    refreshBackgroundEditor()
                    listView?.scrollToPosition(0)
                    listView?.post {
                        backgroundEditor?.pulse(
                                backgroundControlIdFor(control),
                                findPreference<Preference>(key)?.title)
                    }
                    return@post
                }
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
            if (section == SECTION_AOD && key in AodEditorModel.keys) {
                // No tabs to select: the whole page is one surface, so the result only has to be
                // scrolled to the top and the control holding this key pulsed. A control the
                // current ambient style cannot draw is genuinely absent, and pulse() does nothing
                // rather than inventing a highlight for it.
                refreshAodEditor()
                listView?.scrollToPosition(0)
                listView?.post {
                    aodEditor?.pulse(key, findPreference<Preference>(key)?.title)
                }
                return@post
            }
            if (section == SECTION_MINI_BUTTONS && key in MiniButtonEditorModel.keys) {
                refreshMiniButtonEditor()
                listView?.scrollToPosition(0)
                listView?.post {
                    miniButtonEditor?.pulse(key, findPreference<Preference>(key)?.title)
                }
                return@post
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
        val compactBackground = section == SECTION_BACKGROUND
        val compactAod = section == SECTION_AOD
        val compactMiniButtons = section == SECTION_MINI_BUTTONS
        SettingsCatalog.WATCH_CATEGORIES.forEach { key ->
            val visible = key in visibleCategories &&
                    (!compactTypography || key == TYPOGRAPHY_EDITOR_CATEGORY) &&
                    (!compactColors || key == COLOR_EDITOR_CATEGORY) &&
                    (!compactPanels || key == PANEL_EDITOR_CATEGORY) &&
                    (!compactPlayer || key == PLAYER_EDITOR_CATEGORY) &&
                    (!compactBackground || key == BACKGROUND_EDITOR_CATEGORY) &&
                    (!compactAod || key == AOD_EDITOR_CATEGORY) &&
                    (!compactMiniButtons || key == MINI_BUTTON_EDITOR_CATEGORY)
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
        updateCustomArtworkVisibility()
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
        refreshBackgroundEditor()
        refreshAodEditor()
        refreshMiniButtonEditor()
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
        backgroundEditor?.releaseBoundView()
        aodEditor?.releaseBoundView()
        miniButtonEditor?.releaseBoundView()
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
        if (readStringPreference(MiscPreferences.WEAR_FONT.key, "google_sans") == "typewriter") {
            // Unlike the other archived pickers, "typewriter" can never come back - the bundled
            // font was removed outright (see archivedFonts's doc), so there is no row left to
            // select it from even with "Show archived options" on. Normalize unconditionally
            // rather than only while archived options are hidden, or a dev-mode install would keep
            // a value the picker can no longer display.
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
        // "typewriter" has no row in wear_font_values at all any more (see archivedFonts's doc),
        // so it is never offered here regardless of showArchived.
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
        // The imported font's row is archived too, and its visibility is not a picker filter, so
        // it is refreshed here - the one function every "Show archived options" change runs.
        refreshUserFontRow()
        filterArchivedListPreference(
                key = "wear_album_art_source",
                entriesRes = R.array.wear_album_art_source_entries,
                valuesRes = R.array.wear_album_art_source_values,
                archived = archivedAlbumArtSources,
                defaultValue = AlbumArtSource.DEFAULT.preferenceValue,
                showArchived = showArchived)
        applyTitleFontEntries(showArchived)
        applyArtistFontEntries(showArchived)
        applyClockFontEntries(showArchived)
        applyTrackTimeFontEntries(showArchived)
        applyLyricsFontEntries(showArchived)
        applyTitleColorEntries()
        applyElementToneEntries()
        PanelOptionCatalog.apply(resources) { key -> findPreference(key) }
        // After the additive catalog: it appends the extra backdrops to the shared picker, and
        // the per-surface pickers are built from whatever that picker ends up offering.
        applySurfaceBackdropEntries()
        AppearanceOptionCatalog.apply(resources) { key -> findPreference(key) }

        // The per-surface pickers are copies of the shared one, so they have to be rebuilt
        // whenever its option list changes - archived values are removed from it right here.
        applySurfaceBackdropEntries()
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
     * Builds the three per-element Tone pickers from the shared tone arrays with "follow the
     * watch tone" on top.
     *
     * Derived rather than declared, for the reason [applyTitleColorEntries] documents: a second
     * copy of the five tone names would need re-translating into every locale each time a tone is
     * added, and the moment it fell behind, the picker would offer the wrong tone for a value.
     */
    private fun applyElementToneEntries() {
        val entries = resources.getStringArray(R.array.wear_color_modifier_entries)
        val values = resources.getStringArray(R.array.wear_color_modifier_values)
        val withFollow = (listOf(getString(R.string.wear_color_modifier_follow)) +
                entries.toList()).toTypedArray()
        val withFollowValues = (listOf(ColorModifier.FOLLOW) + values.toList()).toTypedArray()
        listOf(
                MiscPreferences.WEAR_TITLE_COLOR_MODIFIER,
                MiscPreferences.WEAR_ARTIST_COLOR_MODIFIER,
                MiscPreferences.WEAR_CLOCK_COLOR_MODIFIER
        ).forEach { definition ->
            findPreference<ListPreference>(definition.key)?.let { pref ->
                pref.entries = withFollow
                pref.entryValues = withFollowValues
            }
        }
    }

    /**
     * Builds the five per-surface background pickers from the shared backdrop arrays with "follow
     * the shared choice" on top.
     *
     * Derived rather than declared, for the reason [applyTitleColorEntries] documents: this
     * vocabulary is the longest picker in the app, and a second copy of it would need
     * re-translating into every locale each time a background is added.
     */
    private fun applySurfaceBackdropEntries() {
        // Read off the shared picker, not the raw arrays. That picker is assembled from the base
        // arrays *plus* PanelOptionCatalog's additive ones, and then has archived values removed -
        // so building from the arrays alone offered a shorter list than the shared row it defers
        // to, which is exactly the drift a derived picker exists to prevent.
        val shared = findPreference<ListPreference>(
                MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE.key) ?: return
        val entries = shared.entries ?: return
        val values = shared.entryValues ?: return
        val withShared = (listOf<CharSequence>(getString(R.string.wear_backdrop_shared)) +
                entries.toList()).toTypedArray()
        val withSharedValues =
                (listOf<CharSequence>(OverlayBackdropResolver.SHARED) +
                        values.toList()).toTypedArray()
        listOf(
                MiscPreferences.WEAR_VOLUME_BACKDROP_STYLE,
                MiscPreferences.WEAR_PROGRESS_BACKDROP_STYLE,
                MiscPreferences.WEAR_QUICK_PANEL_BACKDROP_STYLE,
                MiscPreferences.WEAR_QUEUE_BACKDROP_STYLE,
                MiscPreferences.WEAR_LYRICS_BACKDROP_STYLE
        ).forEach { definition ->
            findPreference<ListPreference>(definition.key)?.let { pref ->
                pref.entries = withShared
                pref.entryValues = withSharedValues
            }
        }
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
    ): List<Pair<String, String>> {
        val catalog = sortedFontChoices().filter { (_, value) ->
            showArchived || value !in archivedFonts || (keepCurrentArchived && value == current)
        }
        // The imported font is injected at runtime rather than declared in `wear_font_entries`,
        // because whether it exists is a property of this phone: an XML row would offer a font that
        // is not there on every install that has not imported one, and would also have to be
        // translated into forty-five locales to say a name the user chose themselves. It leads the list
        // - it is the one entry the person put there, and an alphabetical position among 143
        // families would bury it.
        val userFont = importedFontChoice(showArchived, current) ?: return catalog
        return listOf(userFont) + catalog
    }

    /**
     * The imported font's picker row, or null when it should not be offered.
     *
     * **Archived**, like [archivedAlbumArtSources] and for the same reason: it shipped and did not
     * work in practice, so it is hidden rather than removed and comes back with the developer
     * "Show archived options" switch. It has no entry in an `archived*` set because it has no entry
     * in `wear_font_values` either - the row is injected here at runtime, since whether an imported
     * font exists is a property of this phone rather than of the catalogue.
     *
     * Two states keep it listed with the switch off, both following the rule the archived pickers
     * already follow - a value that is *currently in use* stays selectable, because a picker with
     * nothing checked cannot be understood and the only way out of it would be to guess. A face
     * still set to `user_font`, and a phone that still holds an imported file, are each that state:
     * the second matters because removing the font is only reachable from the row this list backs.
     */
    private fun importedFontChoice(showArchived: Boolean, current: String): Pair<String, String>? {
        val context = context ?: return null
        val name = UserFontStore.displayName(context)
        if (!showArchived && name == null && current != DeviceLocalAppearance.USER_FONT_KEY) {
            return null
        }
        return (name ?: getString(R.string.wear_font_user_import)) to
                DeviceLocalAppearance.USER_FONT_KEY
    }

    /**
     * The font key whose picker asked for an import, so the chosen file can be applied to it.
     *
     * Without it, importing from inside the Artist picker would leave the font imported and the
     * artist still on its previous family - the person did not open a file browser for its own
     * sake, they were choosing a typeface for one element.
     */
    private var pendingUserFontTarget: String? = null

    /**
     * Lets every font picker offer "My own font" whether or not one has been imported yet.
     *
     * The row is always listed, because a control that only appears once you have already found
     * some other screen is a control nobody discovers. Choosing it with nothing imported must not
     * persist `user_font` - that value would resolve through the catalogue's unknown-key fallback
     * and quietly select the default typeface - so the change is **rejected** and the importer
     * opened instead; the value is written afterwards, from [importUserFont], only if a real font
     * arrived.
     *
     * Each listener chains to whatever was already installed ([initTypographyDependencies] owns
     * `wear_font`'s), so this cannot silently drop an existing dependency callback.
     */
    private fun installUserFontPickerInterceptors() {
        DeviceLocalAppearance.FONT_KEYS.forEach { key ->
            val preference = findPreference<ListPreference>(key) ?: return@forEach
            val existing = preference.onPreferenceChangeListener
            preference.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { changed, candidate ->
                        if (candidate == DeviceLocalAppearance.USER_FONT_KEY &&
                                context?.let(UserFontStore::hasFont) != true) {
                            pendingUserFontTarget = key
                            launchFontPicker()
                            false
                        } else {
                            existing?.onPreferenceChange(changed, candidate) ?: true
                        }
                    }
        }
    }

    /**
     * Selects the freshly imported font for the picker that asked for it.
     *
     * Written through the `ListPreference` rather than into preferences directly, so the
     * face-scoped data store, the row's summary and the contextual editor all learn about it -
     * and after [refreshFontPickers], so the entry it is being set to already exists in the list.
     */
    private fun applyImportedFontTo(key: String) {
        val preference = findPreference<ListPreference>(key) ?: return
        preference.value = DeviceLocalAppearance.USER_FONT_KEY
        refreshTypographyEditor()
        notifyPreviewInteraction(key, DeviceLocalAppearance.USER_FONT_KEY)
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


    // ------------------------------------------------------------- Always-on editor

    /**
     * Replaces the eleven ambient rows with one contextual editor, on the same terms as
     * [initPlayerEditor]: the real Preference objects stay inflated and hidden, still owning
     * storage, validation, dialogs and search metadata.
     */
    private fun initAodEditor() {
        val editor = findPreference<AodEditorPreference>(AOD_EDITOR_KEY) ?: return
        aodEditor = editor
        editor.bindEditor = ::bindAodEditor
        editor.refresh()
    }

    private fun refreshAodEditor() {
        aodEditor?.refresh()
    }

    private fun bindAodEditor(root: View) {
        root.setTag(R.id.tag_handles_accent_locally, true)
        root.disableScrollbarsInSubtree()

        // Every picker opens the real Preference's own dialog, so the editor adds no second copy
        // of a list, its validation or its archived-option filtering.
        listOf(
                R.id.aod_editor_style_button to AodControl.STYLE,
                R.id.aod_editor_color_button to AodControl.COLOR_MODE,
                R.id.aod_editor_art_treatment_button to AodControl.ART_TREATMENT
        ).forEach { (id, control) ->
            root.findViewById<MaterialButton>(id).setOnClickListener {
                AodEditorModel.keyFor(control)?.let(::openPreferenceDialog)
            }
        }
        // The mode picker is what makes a picked colour meaningful, so the swatch beside it opens
        // the colour row's own listener rather than a second picker - see openColorPicker.
        root.findViewById<MaterialButton>(R.id.aod_editor_custom_color_button).setOnClickListener {
            AodEditorModel.keyFor(AodControl.CUSTOM_COLOR)?.let(::openColorPicker)
        }
        listOf(
                R.id.aod_editor_intensity_button to AodControl.INTENSITY,
                R.id.aod_editor_art_opacity_button to AodControl.ART_OPACITY
        ).forEach { (id, control) ->
            root.findViewById<MaterialButton>(id).setOnClickListener {
                AodEditorModel.keyFor(control)?.let(::showAodSlider)
            }
        }

        renderAodEditor(root)
    }

    private fun renderAodEditor(root: View) {
        val face = readStringPreference(
                MiscPreferences.WEAR_SCREEN_FACE.key,
                MiscPreferences.WEAR_SCREEN_FACE.defaultValue)
        val style = AodEditorModel.effectiveStyle(
                readStringPreference(
                        MiscPreferences.WEAR_AOD_STYLE.key,
                        MiscPreferences.WEAR_AOD_STYLE.defaultValue),
                face)

        bindAodValueButton(
                root.findViewById(R.id.aod_editor_style_button),
                MiscPreferences.WEAR_AOD_STYLE.key,
                withLabel = false)
        bindAodValueButton(
                root.findViewById(R.id.aod_editor_color_button),
                MiscPreferences.WEAR_AOD_COLOR_MODE.key)

        // The swatch is revealed by the Custom tint alone, exactly as the Colors page pairs a mode
        // with its colour: any other mode names a colour this hex could never describe.
        val customColorKey = AodEditorModel.keyFor(AodControl.CUSTOM_COLOR)
        val customColorButton = root.findViewById<MaterialButton>(
                R.id.aod_editor_custom_color_button)
        customColorButton.isVisible = customColorKey != null &&
                readStringPreference(
                        MiscPreferences.WEAR_AOD_COLOR_MODE.key,
                        MiscPreferences.WEAR_AOD_COLOR_MODE.defaultValue) == "custom"
        if (customColorButton.isVisible && customColorKey != null) {
            bindColorSwatchButton(
                    customColorButton,
                    customColorKey,
                    findPreference<Preference>(customColorKey)?.title)
            customColorButton.tag = customColorKey
        }

        bindAodNumberButton(
                root.findViewById(R.id.aod_editor_intensity_button),
                MiscPreferences.WEAR_AOD_INTENSITY.key)

        // The transport's own progress ring is the one element gated by a preference rather than
        // by the ambient style, so it is filtered here where a value can be read - the same split
        // the Player page makes for the seek marker, and what keeps appliesToStyle pure.
        val transportShown = store.getBoolean(
                MiscPreferences.WEAR_AOD_SHOW_TRANSPORT.key,
                MiscPreferences.WEAR_AOD_SHOW_TRANSPORT.defaultValue)
        renderAodChips(
                root.findViewById(R.id.aod_editor_element_chips),
                AodEditorModel.visibleIn(AodSlot.ELEMENT, style).filter { spec ->
                    spec.control != AodControl.SHOW_PROGRESS || transportShown
                })

        // Gone entirely when the style draws no artwork, or when the chip above has turned it off:
        // both rows would then describe a picture nobody sees.
        val artworkShown = AodEditorModel.appliesToStyle(AodControl.SHOW_ART, style) &&
                store.getBoolean(
                        MiscPreferences.WEAR_AOD_SHOW_ART.key,
                        MiscPreferences.WEAR_AOD_SHOW_ART.defaultValue)
        root.findViewById<View>(R.id.aod_editor_artwork_card).isVisible = artworkShown
        if (artworkShown) {
            bindAodValueButton(
                    root.findViewById(R.id.aod_editor_art_treatment_button),
                    MiscPreferences.WEAR_AOD_ART_TREATMENT.key)
            bindAodNumberButton(
                    root.findViewById(R.id.aod_editor_art_opacity_button),
                    MiscPreferences.AMBIENT_ALBUM_ART_OPACITY.key)
        }

        tintAodEditor(root)
        // Must run after the chips are built, not only in bindAodEditor: those views are created
        // here on every refresh, so a sweep done before this point never reaches them.
        root.disableScrollbarsInSubtree()
    }

    /** A picker row reads "<what it is> · <what it is set to>", unless its heading already says. */
    @SuppressLint("SetTextI18n") // "Label · value" is the editor's own notation, not prose.
    private fun bindAodValueButton(
            button: MaterialButton,
            key: String,
            withLabel: Boolean = true
    ) {
        val default = (AodEditorModel.specFor(key)?.value as? AodValueSpec.Choice)
                ?.defaultValue ?: ""
        val label = choiceLabel(key, readStringPreference(key, default))
        val prefix = AodEditorModel.specFor(key)?.labelRes?.takeIf { withLabel }?.let(::getString)
        button.text = if (prefix != null) "$prefix · $label" else label
        button.contentDescription = buildPreferenceDescription(key, label)
        button.tag = key
    }

    @SuppressLint("SetTextI18n") // Percentages are locale-independent editor notation.
    private fun bindAodNumberButton(button: MaterialButton, key: String) {
        val number = AodEditorModel.specFor(key)?.value as? AodValueSpec.Number ?: return
        val value = store.getInt(key, number.defaultValue).coerceIn(number.range)
        val prefix = AodEditorModel.specFor(key)?.labelRes?.let(::getString)
                ?: findPreference<Preference>(key)?.title
        button.text = "$prefix · $value%"
        button.contentDescription = buildPreferenceDescription(key, "$value%")
        button.tag = key
    }

    private fun renderAodChips(group: ChipGroup, specs: List<AodSettingSpec>) {
        group.removeAllViews()
        group.isVisible = specs.isNotEmpty()
        specs.forEach { spec ->
            val toggle = spec.value as? AodValueSpec.Toggle ?: return@forEach
            val title = findPreference<Preference>(spec.key)?.title
            group.addView(
                    newEditorChip(
                            key = spec.key,
                            // The short noun where one exists; the sentence otherwise.
                            label = spec.labelRes?.let(::getString) ?: title,
                            description = title,
                            checked = store.getBoolean(spec.key, toggle.defaultValue),
                            onToggle = { checked -> commitAodBoolean(spec.key, checked) }))
        }
    }

    private fun commitAodBoolean(key: String, value: Boolean) {
        val preference = findPreference<TwoStatePreference>(key)
        if (preference != null && preference.callChangeListener(value)) {
            preference.isChecked = value
        }
        // Refresh either way: a rejected change has to snap the control back to the stored value
        // rather than leave it showing one the watch will never receive.
        refreshAodEditor()
    }

    private fun showAodSlider(key: String) {
        val number = AodEditorModel.specFor(key)?.value as? AodValueSpec.Number ?: return
        showNumericSlider(
                key,
                number.range,
                number.defaultValue,
                format = { "$it%" },
                onCommit = { value -> commitAodNumber(key, value) })
    }

    private fun commitAodNumber(key: String, value: Int) {
        val number = AodEditorModel.specFor(key)?.value as? AodValueSpec.Number ?: return
        val candidate = value.coerceIn(number.range).toString()
        val preference = findPreference<EditTextPreference>(key) ?: return
        if (preference.callChangeListener(candidate)) {
            preference.text = candidate
            refreshAodEditor()
        }
    }

    private fun tintAodEditor(root: View) {
        val palette = editorPalette()

        listOf(
                R.id.aod_editor_style_heading,
                R.id.aod_editor_elements_heading,
                R.id.aod_editor_artwork_heading
        ).forEach { root.findViewById<TextView>(it)?.setTextColor(palette.textAccent) }

        tintEditorChipGroup(root.findViewById(R.id.aod_editor_element_chips), palette)

        listOf(
                R.id.aod_editor_style_button,
                R.id.aod_editor_color_button,
                R.id.aod_editor_intensity_button,
                R.id.aod_editor_art_treatment_button,
                R.id.aod_editor_art_opacity_button
        ).forEach { tintEditorRow(root.findViewById(it), palette) }
        // Not in the loop above: its icon is the picked colour itself, which
        // bindColorSwatchButton has already tinted - re-tinting would repaint the swatch grey.
        tintEditorRow(
                root.findViewById(R.id.aod_editor_custom_color_button),
                palette,
                tintIcon = false)
    }

    // ------------------------------------------------------------ Mini buttons editor

    /**
     * Replaces the Mini buttons page's row list with one contextual editor, on the same terms as
     * [initPlayerEditor].
     */
    private fun initMiniButtonEditor() {
        val editor = findPreference<MiniButtonEditorPreference>(MINI_BUTTON_EDITOR_KEY) ?: return
        miniButtonEditor = editor
        editor.bindEditor = ::bindMiniButtonEditor
        editor.refresh()
    }

    private fun refreshMiniButtonEditor() {
        miniButtonEditor?.refresh()
    }

    private fun bindMiniButtonEditor(root: View) {
        root.setTag(R.id.tag_handles_accent_locally, true)
        root.disableScrollbarsInSubtree()

        // What a mini button *does* is assigned on the Controls tab; this row runs the hint
        // preference's own listener rather than carrying a second route there.
        root.findViewById<MaterialButton>(R.id.mini_button_editor_assign_button)
                .setOnClickListener {
                    val key = MiniButtonEditorModel.keyFor(MiniButtonControl.ASSIGN)
                            ?: return@setOnClickListener
                    findPreference<Preference>(key)?.let { preference ->
                        preference.onPreferenceClickListener?.onPreferenceClick(preference)
                    }
                }
        root.findViewById<MaterialButton>(R.id.mini_button_editor_gestures_button)
                .setOnClickListener {
                    MiniButtonEditorModel.keyFor(MiniButtonControl.GESTURES_MODE)
                            ?.let(::openPreferenceDialog)
                }

        renderMiniButtonEditor(root)
    }

    private fun renderMiniButtonEditor(root: View) {
        val face = readStringPreference(
                MiscPreferences.WEAR_SCREEN_FACE.key,
                MiscPreferences.WEAR_SCREEN_FACE.defaultValue)

        MiniButtonEditorModel.keyFor(MiniButtonControl.ASSIGN)?.let { key ->
            val preference = findPreference<Preference>(key)
            val button = root.findViewById<MaterialButton>(R.id.mini_button_editor_assign_button)
            button.text = getString(R.string.mini_button_assign_action)
            button.contentDescription = "${preference?.title}. ${preference?.summary}"
            button.tag = key
            root.findViewById<TextView>(R.id.mini_button_editor_assign_note).text =
                    preference?.summary
        }

        renderMiniButtonRows(
                root.findViewById(R.id.mini_button_editor_row_controls),
                MiniButtonEditorModel.visibleIn(MiniButtonSlot.ROW, face))

        bindMiniButtonRow(
                root.findViewById(R.id.mini_button_editor_gestures_button),
                MiscPreferences.WEAR_GESTURES_MODE.key,
                withLabel = false)

        tintMiniButtonEditor(root)
        // Must run after the rows are built - they are created here on every refresh.
        root.disableScrollbarsInSubtree()
    }

    /** Rebuilds the style rows. Cleared first, for the reason [renderPlayerChips] gives. */
    private fun renderMiniButtonRows(
            container: LinearLayout,
            specs: List<MiniButtonSettingSpec>
    ) {
        container.removeAllViews()
        container.isVisible = specs.isNotEmpty()
        specs.forEach { spec ->
            val row = newEditorChoiceRow(spec.key) {
                when (spec.value) {
                    is MiniButtonValueSpec.Number -> showMiniButtonSlider(spec.key)
                    else -> openPreferenceDialog(spec.key)
                }
            }
            bindMiniButtonRow(row, spec.key)
            container.addView(row)
        }
    }

    @SuppressLint("SetTextI18n") // "Label · value" is the editor's own notation, not prose.
    private fun bindMiniButtonRow(
            button: MaterialButton,
            key: String,
            withLabel: Boolean = true
    ) {
        val spec = MiniButtonEditorModel.specFor(key) ?: return
        val label = when (val value = spec.value) {
            is MiniButtonValueSpec.Number ->
                "${store.getInt(key, value.defaultValue).coerceIn(value.range)}%"
            is MiniButtonValueSpec.Choice -> choiceLabel(key, readStringPreference(key, value.defaultValue))
            MiniButtonValueSpec.Action -> return
        }
        val prefix = spec.labelRes?.takeIf { withLabel }?.let(::getString)
                ?: findPreference<Preference>(key)?.title?.takeIf { withLabel }
        button.text = if (prefix != null) "$prefix · $label" else label
        button.contentDescription = buildPreferenceDescription(key, label)
        button.tag = key
    }

    private fun showMiniButtonSlider(key: String) {
        val number = MiniButtonEditorModel.specFor(key)?.value as? MiniButtonValueSpec.Number
                ?: return
        showNumericSlider(
                key,
                number.range,
                number.defaultValue,
                format = { "$it%" },
                onCommit = { value -> commitMiniButtonNumber(key, value) })
    }

    private fun commitMiniButtonNumber(key: String, value: Int) {
        val number = MiniButtonEditorModel.specFor(key)?.value as? MiniButtonValueSpec.Number
                ?: return
        val candidate = value.coerceIn(number.range).toString()
        val preference = findPreference<EditTextPreference>(key) ?: return
        if (preference.callChangeListener(candidate)) {
            preference.text = candidate
            refreshMiniButtonEditor()
        }
    }

    private fun tintMiniButtonEditor(root: View) {
        val palette = editorPalette()

        listOf(
                R.id.mini_button_editor_row_heading,
                R.id.mini_button_editor_gestures_heading
        ).forEach { root.findViewById<TextView>(it)?.setTextColor(palette.textAccent) }

        tintEditorRow(root.findViewById(R.id.mini_button_editor_assign_button), palette)
        tintEditorRow(root.findViewById(R.id.mini_button_editor_gestures_button), palette)
        val rows = root.findViewById<LinearLayout>(R.id.mini_button_editor_row_controls)
        (0 until rows.childCount)
                .mapNotNull { rows.getChildAt(it) as? MaterialButton }
                .forEach { tintEditorRow(it, palette) }
    }

    // ------------------------------------------------------- shared editor building blocks

    /**
     * The colours every contextual editor tints itself from.
     *
     * Resolved once per render rather than per control: [lyraRuntimeAccent] follows the album art,
     * so a control tinted from its own lookup could land a frame behind its neighbours.
     */
    private data class EditorPalette(
            val surface: Int,
            val accent: Int,
            val textAccent: Int,
            val onSurface: Int,
            val secondary: Int,
            val divider: Int)

    private fun editorPalette(): EditorPalette {
        val surface = ContextCompat.getColor(requireContext(), R.color.lyra_surface)
        val rawAccent = lyraRuntimeAccent()
        return EditorPalette(
                surface = surface,
                accent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 3.0),
                textAccent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 4.5),
                onSurface = ContextCompat.getColor(requireContext(), R.color.lyra_on_surface),
                secondary = ContextCompat.getColor(requireContext(), R.color.lyra_text_secondary),
                divider = ContextCompat.getColor(requireContext(), R.color.lyra_divider))
    }

    /**
     * Tints every accent-bearing part of the shared numeric dialog.
     *
     * The slider thumb and active track were already recoloured, but Material's tick/inactive
     * states and, most visibly, TextInputLayout's focused outline still came from the theme's
     * static green `colorControlActivated`. Styling the EditText cursor alone cannot reach its
     * parent box, so all of them are handled together here for every consumer of the dialog.
     */
    private fun tintNumericSliderDialog(
            dialog: AlertDialog,
            slider: Slider,
            valueLayout: TextInputLayout,
            valueInput: TextInputEditText
    ) {
        val palette = editorPalette()
        val accent = ColorStateList.valueOf(palette.accent)
        val divider = ColorStateList.valueOf(palette.divider)

        slider.thumbTintList = accent
        slider.trackActiveTintList = accent
        slider.trackInactiveTintList = divider
        slider.tickActiveTintList = accent
        slider.tickInactiveTintList = divider
        slider.haloTintList = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(palette.accent, 0x33))

        valueLayout.boxStrokeColor = palette.accent
        valueLayout.hintTextColor = ColorStateList.valueOf(palette.textAccent)
        valueLayout.defaultHintTextColor = ColorStateList.valueOf(palette.secondary)
        LyraAccent.applyToEditText(valueInput, palette.accent)
        dialog.applyLyraDialogStyling(
                accent = palette.accent,
                positiveColor = palette.textAccent)
    }

    /** An outlined picker row: transparent, hairline stroke, greyed when disabled. */
    private fun tintEditorRow(
            button: MaterialButton?,
            palette: EditorPalette,
            tintIcon: Boolean = true
    ) {
        button ?: return
        val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        val foregrounds = ColorStateList(states, intArrayOf(palette.secondary, palette.onSurface))
        button.backgroundTintList = ColorStateList(
                states, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT))
        button.setTextColor(foregrounds)
        if (tintIcon) button.iconTint = foregrounds
        button.strokeColor = ColorStateList(states, intArrayOf(palette.divider, palette.divider))
    }

    /**
     * A checked chip is filled with a blend rather than the raw accent, and its content is
     * contrast-corrected against that blend - the treatment the Community gallery's filters
     * already use, so a selected chip means the same thing everywhere in the app.
     */
    private fun tintEditorChipGroup(group: ChipGroup?, palette: EditorPalette) {
        group ?: return
        val selectedContainer = ColorUtils.blendARGB(palette.surface, palette.accent, 0.16f)
        val selectedContent = LyraAccent.contrastSafe(
                palette.accent, selectedContainer, minimumContrast = 4.5)
        for (index in 0 until group.childCount) {
            val chip = group.getChildAt(index) as? Chip ?: continue
            val selected = chip.isChecked
            chip.chipBackgroundColor = ColorStateList.valueOf(
                    if (selected) selectedContainer else palette.surface)
            chip.chipStrokeColor = ColorStateList.valueOf(
                    if (selected) selectedContent else palette.divider)
            chip.setTextColor(if (selected) selectedContent else palette.onSurface)
            chip.rippleColor = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.lyra_ripple))
        }
    }

    /** One checkable chip, carrying its preference key as the tag the search pulse looks for. */
    private fun newEditorChip(
            key: String,
            label: CharSequence?,
            description: CharSequence?,
            checked: Boolean,
            onToggle: (Boolean) -> Unit
    ): Chip = Chip(requireContext()).apply {
        setChipDrawable(
                ChipDrawable.createFromAttributes(
                        requireContext(), null, 0, R.style.LyraCommunityGalleryChip))
        text = label
        isCheckable = true
        isChecked = checked
        // The chip shows a noun, so the full row title is what a screen reader needs; the checked
        // state is announced by the widget itself.
        contentDescription = description
        tag = key
        setOnClickListener { onToggle(isChecked) }
    }

    /**
     * One generated picker row, matching the rows inflated from `LyraGestureButton`.
     *
     * The attribute belongs to the Material library's R, not the app's - the app's R.attr has no
     * such entry and resolving it against the wrong one is a compile error, not a silently
     * unstyled button.
     */
    private fun newEditorChoiceRow(key: String, onClick: () -> Unit): MaterialButton =
            MaterialButton(
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
                // The same google_sans metric fix LyraGestureButton carries, and for the same
                // reason: this font's ascent leaves the label riding a few dp high in a
                // fixed-height row, so a picker row built here sat visibly higher in its box than
                // the identical-looking rows inflated from that style.
                includeFontPadding = false
                // 12sp, and the insets zeroed to match LyraGestureButton. A MaterialButton keeps
                // 6dp of inset top and bottom by default, which leaves a 48dp row only 36dp of
                // content box; a 13sp line plus its font padding overran that, and an overrun is
                // exactly what makes a button report a scroll range and paint a thumb down its own
                // edge. Nothing here scrolls.
                textSize = 12f
                insetTop = 0
                insetBottom = 0
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                tag = key
                setOnClickListener { onClick() }
            }


    /**
     * Replaces the eleven Background rows with the artwork underneath and the stack over it, on
     * the same terms as [initTypographyEditor] and its three siblings.
     */
    private fun initBackgroundEditor() {
        val editor = findPreference<BackgroundEditorPreference>(BACKGROUND_EDITOR_KEY) ?: return
        backgroundEditor = editor
        editor.bindEditor = ::bindBackgroundEditor
        editor.refresh()
    }

    private fun refreshBackgroundEditor() {
        backgroundEditor?.refresh()
    }

    private fun bindBackgroundEditor(root: View) {
        root.setTag(R.id.tag_handles_accent_locally, true)
        root.disableScrollbarsInSubtree()

        root.findViewById<MaterialButton>(R.id.background_editor_source_button)
                .setOnClickListener {
                    openPreferenceDialog(MiscPreferences.WEAR_ALBUM_ART_SOURCE.key)
                }
        root.findViewById<MaterialButton>(R.id.background_editor_artwork_button)
                .setOnClickListener { openPreferenceDialog(MiscPreferences.ALBUM_ART_STYLE.key) }
        root.findViewById<MaterialButton>(R.id.background_editor_filter_button)
                .setOnClickListener { openPreferenceDialog(MiscPreferences.ALBUM_ART_FILTER.key) }
        root.findViewById<MaterialButton>(R.id.background_editor_blur_button)
                .setOnClickListener {
                    openPreferenceDialog(MiscPreferences.ALBUM_ART_BLUR_RADIUS.key)
                }
        root.findViewById<MaterialButton>(R.id.background_editor_add_button)
                .setOnClickListener { showAddBackgroundLayerDialog() }
        root.findViewById<MaterialButton>(R.id.background_editor_reset_button)
                .setOnClickListener { confirmResetBackgroundLayers() }

        renderBackgroundEditor(root)
    }

    @SuppressLint("SetTextI18n") // "Title · value" is the editor's own notation, not prose.
    private fun renderBackgroundEditor(root: View) {
        val sourceKey = MiscPreferences.WEAR_ALBUM_ART_SOURCE.key
        val sourceButton = root.findViewById<MaterialButton>(R.id.background_editor_source_button)
        val sourceValue = readStringPreference(
                sourceKey, MiscPreferences.WEAR_ALBUM_ART_SOURCE.defaultValue)
        sourceButton.text = choiceLabel(sourceKey, sourceValue)
        sourceButton.contentDescription = buildPreferenceDescription(sourceKey, sourceButton.text)

        // Shown only for a source that needs a file named. It has to be here rather than as a
        // preference row, because the rows on this page are hidden behind this very editor - a
        // picker declared in XML alone would be searchable and invisible, which is the exact state
        // this page's own test exists to catch.
        val pictureButton =
                root.findViewById<MaterialButton>(R.id.background_editor_picture_button)
        val pictureKey = when (AlbumArtSource.fromPref(sourceValue)) {
            AlbumArtSource.CUSTOM_IMAGE -> MiscPreferences.CUSTOM_ALBUM_ART_IMAGE.key
            AlbumArtSource.CUSTOM_FOLDER -> MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key
            else -> null
        }
        pictureButton.isVisible = pictureKey != null
        if (pictureKey != null) {
            val isTree = pictureKey == MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key
            val chosen = storedCustomArtworkName(pictureKey, isTree)
            val label = findPreference<Preference>(pictureKey)?.title
            pictureButton.text = if (chosen == null) {
                getString(R.string.setting_custom_album_art_none)
            } else {
                "$label · $chosen"
            }
            // Both are Material Symbols vectors that tint at usage. ic_menu_gallery, which this
            // used first, is a legacy asset with a hardcoded black fill and no `android:tint`, so
            // it ignored the editor's icon tint and drew solid black against the Lyra surface.
            pictureButton.setIconResource(
                    if (isTree) R.drawable.ic_folder_open else R.drawable.ic_imagesearch_roller)
            pictureButton.contentDescription =
                    buildPreferenceDescription(pictureKey, pictureButton.text)
            pictureButton.setOnClickListener {
                if (isTree) pickArtworkFolderLauncher.launch(null)
                else pickArtworkImageLauncher.launch(arrayOf("image/*"))
            }
        }

        val artworkKey = MiscPreferences.ALBUM_ART_STYLE.key
        val artworkButton = root.findViewById<MaterialButton>(R.id.background_editor_artwork_button)
        val artworkValue = readStringPreference(
                artworkKey, MiscPreferences.ALBUM_ART_STYLE.defaultValue)
        artworkButton.text = choiceLabel(artworkKey, artworkValue)
        artworkButton.contentDescription = buildPreferenceDescription(artworkKey, artworkButton.text)

        val filterKey = MiscPreferences.ALBUM_ART_FILTER.key
        val filterButton = root.findViewById<MaterialButton>(R.id.background_editor_filter_button)
        val filterValue = readStringPreference(
                filterKey, MiscPreferences.ALBUM_ART_FILTER.defaultValue)
        filterButton.text = choiceLabel(filterKey, filterValue)
        filterButton.contentDescription = buildPreferenceDescription(filterKey, filterButton.text)

        // Blurring is the only artwork control that does not apply to every treatment, and a
        // radius beside a style that never blurs reads as broken rather than as inapplicable -
        // the rule updatePlayerCapabilityVisibility already follows for the per-face rows.
        val blurKey = MiscPreferences.ALBUM_ART_BLUR_RADIUS.key
        val blurButton = root.findViewById<MaterialButton>(R.id.background_editor_blur_button)
        blurButton.isVisible = PlayerBackgroundStyle.fromPreference(artworkValue).usesBlurRadius
        if (blurButton.isVisible) {
            val radius = store.getInt(blurKey, MiscPreferences.ALBUM_ART_BLUR_RADIUS.defaultValue)
            blurButton.text = "${findPreference<Preference>(blurKey)?.title} · $radius"
            blurButton.contentDescription = buildPreferenceDescription(blurKey, radius)
        }

        bindPanelSwitch(
                root.findViewById(R.id.background_editor_fade_switch),
                MiscPreferences.WEAR_ALBUM_ART_FADE.key,
                MiscPreferences.WEAR_ALBUM_ART_FADE.defaultValue)

        renderBackgroundLayerList(root)
        tintBackgroundEditor(root)
    }

    private fun renderBackgroundLayerList(root: View) {
        val layers = currentBackgroundLayers()
        val container = root.findViewById<LinearLayout>(R.id.background_editor_layer_list)
        container.removeAllViews()

        // Topmost first, the way a layer stack is drawn everywhere people have seen one. The
        // model is in draw order (index 0 is painted first, closest to the artwork), so the list
        // walks it backwards and the arrows say what they look like they say.
        layers.indices.reversed().forEach { index ->
            val layer = layers[index]
            val position = layers.lastIndex - index
            val row = layoutInflater.inflate(R.layout.item_background_layer, container, false)
            val button = row.findViewById<MaterialButton>(R.id.background_layer_row)
            button.text = "${backgroundLayerKindLabel(layer.kind)} · ${backgroundLayerStyleLabel(layer)}"
            button.setIconResource(backgroundLayerIcon(layer.kind))
            button.contentDescription = backgroundLayerDescription(layer, position, layers.size)
            button.setOnClickListener { showBackgroundLayerStyleDialog(index) }

            row.findViewById<ImageButton>(R.id.background_layer_up).apply {
                isEnabled = index < layers.lastIndex
                alpha = if (isEnabled) 1f else .35f
                setOnClickListener { moveBackgroundLayer(index, 1) }
            }
            row.findViewById<ImageButton>(R.id.background_layer_down).apply {
                isEnabled = index > 0
                alpha = if (isEnabled) 1f else .35f
                setOnClickListener { moveBackgroundLayer(index, -1) }
            }
            row.findViewById<ImageButton>(R.id.background_layer_more)
                    .setOnClickListener { showBackgroundLayerMenu(index) }
            container.addView(row)
        }

        root.findViewById<TextView>(R.id.background_editor_empty).isVisible = layers.isEmpty()
        root.findViewById<TextView>(R.id.background_editor_layers_count).text = getString(
                R.string.background_editor_layers_count, layers.size, BackgroundLayerStack.MAX_LAYERS)
        root.findViewById<MaterialButton>(R.id.background_editor_add_button).isEnabled =
                layers.size < BackgroundLayerStack.MAX_LAYERS
        // Offered only once there is something to go back *to*: with no explicit stack stored the
        // list is already the legacy arrangement, so the button would undo nothing.
        root.findViewById<MaterialButton>(R.id.background_editor_reset_button).isVisible =
                BackgroundLayerStack.isExplicit(readStringPreference(BACKGROUND_LAYERS_KEY, ""))
    }

    /**
     * The stack this face is rendering, explicit or the equivalent of the legacy rows.
     *
     * Reading through [BackgroundLayerStack.resolve] rather than only the stored key is what lets
     * the page open on the arrangement already on screen instead of an empty list beside controls
     * that clearly do something - and it makes adopting the stack a visual no-op, since the first
     * edit is saved from exactly what was being displayed.
     */
    private fun currentBackgroundLayers(): List<BackgroundLayer> = BackgroundLayerStack.resolve(
            raw = readStringPreference(BACKGROUND_LAYERS_KEY, ""),
            background = PlayerBackgroundStyle.fromPreference(readStringPreference(
                    MiscPreferences.ALBUM_ART_STYLE.key,
                    MiscPreferences.ALBUM_ART_STYLE.defaultValue)),
            dimEnabled = store.getBoolean(
                    MiscPreferences.DIM_ALBUM_ART.key, MiscPreferences.DIM_ALBUM_ART.defaultValue),
            dimPercent = store.getInt(
                    MiscPreferences.ALBUM_ART_DIM_STRENGTH.key,
                    MiscPreferences.ALBUM_ART_DIM_STRENGTH.defaultValue),
            shading = PlayerShadingStyle.fromPreference(readStringPreference(
                    MiscPreferences.WEAR_PLAYER_SHADING_STYLE.key,
                    MiscPreferences.WEAR_PLAYER_SHADING_STYLE.defaultValue)),
            shadingColor = BackgroundLayerColor.fromPreference(readStringPreference(
                    MiscPreferences.WEAR_SHADING_COLOR_MODE.key,
                    MiscPreferences.WEAR_SHADING_COLOR_MODE.defaultValue)),
            floor = AccentFloorStyle.fromPreference(readStringPreference(
                    MiscPreferences.WEAR_ACCENT_FLOOR.key,
                    MiscPreferences.WEAR_ACCENT_FLOOR.defaultValue)),
            floorColor = BackgroundLayerColor.fromPreference(readStringPreference(
                    MiscPreferences.WEAR_ACCENT_FLOOR_COLOR_MODE.key,
                    MiscPreferences.WEAR_ACCENT_FLOOR_COLOR_MODE.defaultValue)),
            baseWashDrawn = readStringPreference("wear_screen_face", "classic") !in
                    BackgroundLayerStack.SELF_BACKDROP_FACES)

    /**
     * Persists [layers] and repaints both the list and the miniature.
     *
     * Written straight through [store] rather than through a Preference, because there is no row
     * for this key: the stack is a list the user builds, not a value a picker holds. The store is
     * the same one every Watch-tab row writes through, so the value still lands in `custom_active`
     * while a theme is active and in `key@<face>` otherwise.
     */
    private fun writeBackgroundLayers(layers: List<BackgroundLayer>) {
        val encoded = BackgroundLayerStack.encode(layers.take(BackgroundLayerStack.MAX_LAYERS))
        store.putString(BACKGROUND_LAYERS_KEY, encoded)
        notifyPreviewInteraction(BACKGROUND_LAYERS_KEY, encoded)
        refreshBackgroundEditor()
    }

    private fun moveBackgroundLayer(index: Int, delta: Int) {
        val layers = currentBackgroundLayers()
        val moved = BackgroundLayerStack.move(layers, index, delta)
        if (moved !== layers) writeBackgroundLayers(moved)
    }

    private fun showAddBackgroundLayerDialog() {
        val kinds = BackgroundLayerKind.entries
        val labels = kinds.map<BackgroundLayerKind, CharSequence>(::backgroundLayerKindLabel)
                .toTypedArray()
        showLyraChoiceDialog(getString(R.string.background_layer_add_title), labels) { index ->
            val kind = kinds[index]
            val added = BackgroundLayerStack.add(
                    currentBackgroundLayers(),
                    BackgroundLayer(kind, BackgroundLayerStack.defaultStyleFor(kind)))
            writeBackgroundLayers(added)
            // Straight into the style picker: "add a shading" is almost never the whole intent,
            // and an extra tap to say which one is the kind of friction a list of eight invites.
            // The new layer is on top, which is the last entry in draw order and the first row.
            showBackgroundLayerStyleDialog(added.lastIndex)
        }
    }

    /**
     * Picks a layer's treatment from the very row that owns that vocabulary.
     *
     * The entries come from the real ListPreference - so archived options stay filtered out and
     * every label stays translated in one place - narrowed to the values this kind of layer can
     * carry. Only the storage differs, because a layer's style is not a preference.
     */
    private fun showBackgroundLayerStyleDialog(index: Int) {
        val layers = currentBackgroundLayers()
        val layer = layers.getOrNull(index) ?: return
        val sourceKey = BackgroundEditorModel.styleSourceKey(layer.kind)
        val offered = findPreference<ListPreference>(sourceKey)?.entryValues
                ?.map(CharSequence::toString)
                ?.filter { BackgroundLayerStack.accepts(layer.kind, it) }
                ?: BackgroundLayerStack.stylesFor(layer.kind)
        if (offered.isEmpty()) return
        val labels = offered.map { choiceLabel(sourceKey, it) }.toTypedArray()
        showLyraChoiceDialog(
                getString(R.string.background_layer_style_title,
                        backgroundLayerKindLabel(layer.kind)),
                labels,
                checkedItem = offered.indexOf(layer.style)
        ) { chosen ->
            writeBackgroundLayers(layers.toMutableList().also {
                it[index] = layer.copy(style = offered[chosen])
            })
        }
    }

    private fun showBackgroundLayerMenu(index: Int) {
        val layers = currentBackgroundLayers()
        val layer = layers.getOrNull(index) ?: return
        // A wash composes several album tones itself, so there is no single colour to offer it.
        val tintable = layer.kind != BackgroundLayerKind.WASH
        val actions = buildList {
            add(getString(R.string.background_layer_opacity) to { showBackgroundLayerOpacity(index) })
            if (tintable) {
                add(getString(R.string.background_layer_color) to {
                    showBackgroundLayerColorDialog(index)
                })
            }
            add(getString(R.string.background_layer_duplicate) to {
                writeBackgroundLayers(BackgroundLayerStack.duplicate(layers, index))
            })
            add(getString(R.string.background_layer_remove) to {
                writeBackgroundLayers(BackgroundLayerStack.remove(layers, index))
            })
        }
        showLyraChoiceDialog(
                "${backgroundLayerKindLabel(layer.kind)} · ${backgroundLayerStyleLabel(layer)}",
                actions.map<Pair<String, () -> Unit>, CharSequence> { it.first }.toTypedArray()
        ) { chosen -> actions[chosen].second() }
    }

    private fun showBackgroundLayerOpacity(index: Int) {
        val layers = currentBackgroundLayers()
        val layer = layers.getOrNull(index) ?: return
        showBackgroundSlider(
                title = "${backgroundLayerKindLabel(layer.kind)} · " +
                        getString(R.string.background_layer_opacity),
                range = 0..SHADING_MAX_PERCENT,
                initial = layer.opacityPercent,
                defaultValue = BackgroundLayerStack.DEFAULT_OPACITY_PERCENT
        ) { value ->
            writeBackgroundLayers(layers.toMutableList().also {
                it[index] = layer.copy(opacityPercent = value)
            })
        }
    }

    private fun showBackgroundLayerColorDialog(index: Int) {
        val layers = currentBackgroundLayers()
        val layer = layers.getOrNull(index) ?: return
        val modes = if (layer.kind == BackgroundLayerKind.FLOOR) {
            BackgroundLayerColor.FLOOR_MODES
        } else {
            BackgroundLayerColor.SHADE_MODES
        }
        val labels = modes.map<BackgroundLayerColor, CharSequence>(::backgroundLayerColorLabel)
                .toTypedArray()
        showLyraChoiceDialog(
                getString(R.string.background_layer_color),
                labels,
                checkedItem = modes.indexOf(layer.effectiveColor)
        ) { chosen ->
            val mode = modes[chosen]
            if (mode == BackgroundLayerColor.CUSTOM) {
                showBackgroundLayerCustomColor(index)
            } else {
                writeBackgroundLayers(layers.toMutableList().also {
                    it[index] = layer.copy(color = mode, customColor = "")
                })
            }
        }
    }

    private fun showBackgroundLayerCustomColor(index: Int) {
        val layers = currentBackgroundLayers()
        val layer = layers.getOrNull(index) ?: return
        showLyraColorPickerDialog(
                initialColor = parseHexOrDefault(layer.customColor.takeIf { it.isNotBlank() }),
                onReset = {
                    writeBackgroundLayers(layers.toMutableList().also {
                        it[index] = layer.copy(
                                color = BackgroundLayerColor.DEFAULT, customColor = "")
                    })
                },
                onApply = { hex ->
                    writeBackgroundLayers(layers.toMutableList().also {
                        it[index] = layer.copy(
                                color = BackgroundLayerColor.CUSTOM,
                                customColor = hex.uppercase())
                    })
                })
    }

    private fun confirmResetBackgroundLayers() {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.background_layer_reset_title)
                .setMessage(R.string.background_layer_reset_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.background_editor_reset_layers) { _, _ ->
                    // Written as empty rather than removed: preference sync does not transmit
                    // removals, so a cleared key would leave the watch on the old stack forever.
                    store.putString(BACKGROUND_LAYERS_KEY, "")
                    notifyPreviewInteraction(BACKGROUND_LAYERS_KEY, "")
                    refreshBackgroundEditor()
                }
                .show()
                .tintLyraButtons()
    }

    /** [showNumericSlider] for a value that has no Preference of its own to take a title from. */
    private fun showBackgroundSlider(
            title: CharSequence,
            range: IntRange,
            initial: Int,
            defaultValue: Int,
            onCommit: (Int) -> Unit
    ) {
        val content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_typography_slider, null)
        val slider = content.findViewById<Slider>(R.id.typography_slider)
        val valueLayout = content.findViewById<TextInputLayout>(
                R.id.typography_slider_value_layout)
        val valueInput = content.findViewById<TextInputEditText>(R.id.typography_slider_value)
        content.findViewById<TextView>(R.id.typography_slider_min).text = "${range.first}%"
        content.findViewById<TextView>(R.id.typography_slider_max).text = "${range.last}%"
        var selected = initial.coerceIn(range)
        var syncingManualValue = false
        lateinit var dialog: AlertDialog

        slider.valueFrom = range.first.toFloat()
        slider.valueTo = range.last.toFloat()
        slider.stepSize = 1f
        slider.value = selected.toFloat()
        valueLayout.hint = title
        fun renderValue(value: Int, updateInput: Boolean = true) {
            selected = value.coerceIn(range)
            if (updateInput) {
                val rawValue = selected.toString()
                if (valueInput.text?.toString() != rawValue) {
                    syncingManualValue = true
                    valueInput.setText(rawValue)
                    valueInput.setSelection(rawValue.length)
                    syncingManualValue = false
                }
            }
            valueLayout.error = null
            valueInput.contentDescription = "$title. $selected%"
        }
        renderValue(selected)
        valueInput.doAfterTextChanged { editable ->
            if (syncingManualValue) return@doAfterTextChanged
            val typed = editable?.toString()?.trim().orEmpty()
            val manualValue = typed.toIntOrNull()
            val valid = manualValue != null && manualValue in range
            valueLayout.error = if (valid) {
                null
            } else {
                getString(R.string.setting_numeric_range_error, range.first, range.last)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = valid
            if (!valid) return@doAfterTextChanged

            selected = manualValue
            if (slider.value != selected.toFloat()) slider.value = selected.toFloat()
            renderValue(selected, updateInput = false)
        }
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) renderValue(value.toInt())
        }
        dialog = AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(content)
                .setNeutralButton(R.string.pref_reset_default, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> onCommit(selected) }
                .create()
        dialog.setOnShowListener {
            tintNumericSliderDialog(dialog, slider, valueLayout, valueInput)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                slider.value = defaultValue.toFloat()
                renderValue(defaultValue)
            }
        }
        dialog.show()
    }

    /** One list dialog for the whole editor, styled the way every other Lyra dialog is. */
    private fun showLyraChoiceDialog(
            title: CharSequence,
            labels: Array<CharSequence>,
            checkedItem: Int = -1,
            onChosen: (Int) -> Unit
    ) {
        val builder = AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setNegativeButton(android.R.string.cancel, null)
        // A single-choice list when there is a current value to show as chosen, a plain item list
        // when the entries are actions - the same distinction the preference dialogs already draw.
        if (checkedItem >= 0) {
            builder.setSingleChoiceItems(labels, checkedItem) { dialog, index ->
                dialog.dismiss()
                onChosen(index)
            }
        } else {
            builder.setItems(labels) { _, index -> onChosen(index) }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            val surface = ContextCompat.getColor(requireContext(), R.color.lyra_surface)
            val accent = LyraAccent.contrastSafe(lyraRuntimeAccent(), surface, 4.5)
            dialog.applyLyraDialogStyling(accent = accent)
        }
        dialog.show()
    }

    private fun backgroundControlIdFor(control: BackgroundControl): Int = when (control) {
        BackgroundControl.SOURCE -> R.id.background_editor_source_button
        BackgroundControl.PICTURE -> R.id.background_editor_picture_button
        BackgroundControl.ARTWORK -> R.id.background_editor_artwork_button
        BackgroundControl.FILTER -> R.id.background_editor_filter_button
        BackgroundControl.BLUR -> R.id.background_editor_blur_button
        BackgroundControl.FADE -> R.id.background_editor_fade_switch
        BackgroundControl.LAYERS -> R.id.background_editor_layer_list
    }

    private fun backgroundLayerKindLabel(kind: BackgroundLayerKind): String = getString(
            when (kind) {
                BackgroundLayerKind.WASH -> R.string.background_layer_kind_wash
                BackgroundLayerKind.SHADE -> R.string.background_layer_kind_shade
                BackgroundLayerKind.FLOOR -> R.string.background_layer_kind_floor
            })

    private fun backgroundLayerColorLabel(color: BackgroundLayerColor): String = getString(
            when (color) {
                BackgroundLayerColor.DEFAULT -> R.string.background_layer_color_default
                BackgroundLayerColor.ALBUM -> R.string.background_layer_color_album
                BackgroundLayerColor.SECONDARY -> R.string.background_layer_color_secondary
                BackgroundLayerColor.TERTIARY -> R.string.background_layer_color_tertiary
                BackgroundLayerColor.DESATURATED -> R.string.background_layer_color_desaturated
                BackgroundLayerColor.BLACK -> R.string.background_layer_color_black
                BackgroundLayerColor.CUSTOM -> R.string.background_layer_color_custom
            })

    private fun backgroundLayerStyleLabel(layer: BackgroundLayer): CharSequence =
            choiceLabel(BackgroundEditorModel.styleSourceKey(layer.kind), layer.style)

    private fun backgroundLayerIcon(kind: BackgroundLayerKind): Int = when (kind) {
        BackgroundLayerKind.WASH -> R.drawable.ic_format_paint
        BackgroundLayerKind.SHADE -> R.drawable.ic_dim_style
        BackgroundLayerKind.FLOOR -> R.drawable.ic_colors
    }

    /** Position is read out too: a screen reader has no arrows to see, and order is the point. */
    private fun backgroundLayerDescription(
            layer: BackgroundLayer,
            position: Int,
            total: Int
    ): CharSequence = "${backgroundLayerKindLabel(layer.kind)}. " +
            "${backgroundLayerStyleLabel(layer)}. " +
            "${layer.opacityPercent}%. " +
            getString(R.string.background_editor_layers_count, position + 1, total)

    private fun tintBackgroundEditor(root: View) {
        val surface = ContextCompat.getColor(requireContext(), R.color.lyra_surface)
        val rawAccent = lyraRuntimeAccent()
        val accent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 3.0)
        val textAccent = LyraAccent.contrastSafe(rawAccent, surface, minimumContrast = 4.5)
        val onSurface = ContextCompat.getColor(requireContext(), R.color.lyra_on_surface)
        val secondary = ContextCompat.getColor(requireContext(), R.color.lyra_text_secondary)
        val divider = ContextCompat.getColor(requireContext(), R.color.lyra_divider)

        listOf(R.id.background_editor_artwork_heading, R.id.background_editor_layers_heading)
                .forEach { root.findViewById<TextView>(it)?.setTextColor(textAccent) }

        val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        val foregrounds = ColorStateList(states, intArrayOf(secondary, onSurface))
        val fills = ColorStateList(states, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT))
        val strokes = ColorStateList(states, intArrayOf(divider, divider))
        val accented = ColorStateList(states, intArrayOf(secondary, textAccent))

        listOf(
                R.id.background_editor_source_button,
                R.id.background_editor_picture_button,
                R.id.background_editor_artwork_button,
                R.id.background_editor_filter_button,
                R.id.background_editor_blur_button
        ).forEach { id ->
            root.findViewById<MaterialButton>(id)?.apply {
                backgroundTintList = fills
                setTextColor(foregrounds)
                iconTint = foregrounds
                strokeColor = strokes
            }
        }
        listOf(
                R.id.background_editor_add_button,
                R.id.background_editor_reset_button
        ).forEach { id ->
            root.findViewById<MaterialButton>(id)?.apply {
                backgroundTintList = fills
                setTextColor(accented)
                iconTint = accented
                strokeColor = ColorStateList(states, intArrayOf(divider, accent))
            }
        }

        val list = root.findViewById<LinearLayout>(R.id.background_editor_layer_list)
        for (index in 0 until list.childCount) {
            val row = list.getChildAt(index)
            row.findViewById<MaterialButton>(R.id.background_layer_row)?.apply {
                backgroundTintList = fills
                setTextColor(foregrounds)
                iconTint = ColorStateList(states, intArrayOf(secondary, accent))
                strokeColor = strokes
            }
            listOf(
                    R.id.background_layer_up,
                    R.id.background_layer_down,
                    R.id.background_layer_more
            ).forEach { id ->
                row.findViewById<ImageButton>(id)?.imageTintList =
                        ColorStateList.valueOf(onSurface)
            }
        }

        root.findViewById<SwitchMaterial>(R.id.background_editor_fade_switch)?.apply {
            setTextColor(onSurface)
            thumbTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(accent, secondary))
            trackTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(
                            ColorUtils.setAlphaComponent(accent, 0x66),
                            ColorUtils.setAlphaComponent(divider, 0x99)))
        }
    }

    // ------------------------------------------------------ imported font / own pictures

    /**
     * Wires the "My own font" row: import when empty, replace-or-remove when loaded.
     *
     * A dialog rather than two rows, because "remove" is only meaningful once something is loaded
     * and a permanently-present remove button on an empty slot reads as broken. The summary carries
     * the loaded name so the row answers "which font" without being opened.
     */
    private fun initUserFontRow() {
        val preference = findPreference<Preference>("user_font_import") ?: return
        preference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val context = requireContext()
            if (UserFontStore.hasFont(context)) {
                AlertDialog.Builder(context)
                        .setTitle(R.string.setting_user_font)
                        .setMessage(UserFontStore.displayName(context))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(R.string.user_font_remove) { _, _ -> removeUserFont() }
                        .setPositiveButton(R.string.user_font_replace) { _, _ -> launchFontPicker() }
                        .show()
                        .tintLyraButtons()
            } else {
                launchFontPicker()
            }
            true
        }
        refreshUserFontRow()
        // After initTypographyDependencies, so chaining picks up the listener it installed.
        installUserFontPickerInterceptors()
    }

    private fun launchFontPicker() {
        try {
            importFontLauncher.launch(UserFontContract.PICKER_MIME_TYPES)
        } catch (e: android.content.ActivityNotFoundException) {
            // A watch-paired phone without any documents provider is unusual but not impossible,
            // and an unhandled crash here would take down the settings screen.
            Timber.w(e, "No document picker is available for the font import")
            Toast.makeText(requireContext(), R.string.user_font_error_unreadable,
                    Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Copies the picked file in, then refreshes every picker that can now offer it.
     *
     * The import reads and validates up to two megabytes, so it runs off the main thread; the
     * result is applied back on it because it rebuilds preference rows.
     */
    private fun importUserFont(uri: android.net.Uri) {
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { UserFontStore.import(context, uri) }
            if (!isAdded) {
                pendingUserFontTarget = null
                return@launch
            }
            when (result) {
                is UserFontStore.ImportResult.Imported -> {
                    Toast.makeText(context,
                            getString(R.string.user_font_imported, result.displayName),
                            Toast.LENGTH_LONG).show()
                    // Rebuild the lists first: applyImportedFontTo selects an entry, and it has to
                    // be one the picker already carries.
                    refreshFontPickers()
                    pendingUserFontTarget?.let(::applyImportedFontTo)
                }
                is UserFontStore.ImportResult.TooLarge -> Toast.makeText(context,
                        getString(R.string.user_font_error_too_large,
                                android.text.format.Formatter.formatShortFileSize(
                                        context, result.byteCount),
                                android.text.format.Formatter.formatShortFileSize(
                                        context, UserFontContract.MAX_FONT_BYTES.toLong())),
                        Toast.LENGTH_LONG).show()
                UserFontStore.ImportResult.NotAFont -> Toast.makeText(context,
                        R.string.user_font_error_not_a_font, Toast.LENGTH_LONG).show()
                UserFontStore.ImportResult.Unreadable -> Toast.makeText(context,
                        R.string.user_font_error_unreadable, Toast.LENGTH_LONG).show()
            }
            // Cleared on every path, success included: a target left behind would apply the next
            // import to a picker the person had since moved on from.
            pendingUserFontTarget = null
        }
    }

    private fun removeUserFont() {
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { UserFontStore.clear(context) }
            if (!isAdded) return@launch
            Toast.makeText(context, R.string.user_font_removed, Toast.LENGTH_SHORT).show()
            refreshFontPickers()
        }
    }

    /**
     * Re-derives every picker that can offer the imported font, and the row that manages it.
     *
     * All six font controls, not only the global one: each is built from the same catalog, so an
     * import that refreshed one would leave the other five unable to select the font that is now
     * on the phone.
     */
    private fun refreshFontPickers() {
        // The one function that rebuilds every font picker from the catalog, so an import cannot
        // reach some of the six and not others.
        applyArchivedOptionFilters()
        refreshUserFontRow()
        refreshTypographyEditor()
        notifyPreviewInteraction(MiscPreferences.WEAR_FONT.key, null)
    }

    private fun refreshUserFontRow() {
        val preference = findPreference<Preference>("user_font_import") ?: return
        val name = context?.let(UserFontStore::displayName)
        // Archived along with the picker row it feeds, and visible on the same two terms: with the
        // developer switch on, or while this phone still holds a font - because removing one is
        // only reachable from here, and hiding the row unconditionally would strand it.
        preference.isVisible = archivedOptionsVisible() || name != null
        preference.summary = if (name == null) {
            getString(R.string.setting_user_font_empty)
        } else {
            getString(R.string.setting_user_font_loaded, name)
        }
    }

    /** The developer "Show archived options" switch, read the same way
     *  [applyArchivedOptionFilters] reads it. */
    private fun archivedOptionsVisible(): Boolean = rawPrefs.getBoolean("dev_show_archived", false)

    /** Opens the picker for each device-local artwork source, and keeps both summaries current. */
    private fun initCustomArtworkRows() {
        findPreference<Preference>(MiscPreferences.CUSTOM_ALBUM_ART_IMAGE.key)
                ?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            pickArtworkImageLauncher.launch(arrayOf("image/*"))
            true
        }
        findPreference<Preference>(MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key)
                ?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            pickArtworkFolderLauncher.launch(null)
            true
        }
        refreshCustomArtworkRows()
    }

    /**
     * Takes a lasting read grant on the picked URI and stores it.
     *
     * The grant is the whole point of taking it: without `takePersistableUriPermission` the URI
     * works until this process ends and then silently stops resolving, which would present as a
     * background that disappears the next time the phone reboots. A provider that refuses to make
     * one persistable throws, and that is reported rather than stored - a URI that cannot outlive
     * the picker is not a setting.
     *
     * Written straight to the default preferences rather than through this screen's preference
     * data store: these two keys are global, and going through a face-scoped store would be relying
     * on its pass-through for something that is not an appearance value at all.
     */
    private fun persistCustomArtwork(uri: android.net.Uri, key: String) {
        val context = requireContext()
        try {
            context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Timber.w(e, "Could not take a lasting grant on the picked artwork")
            Toast.makeText(context, R.string.custom_album_art_error, Toast.LENGTH_LONG).show()
            return
        }
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit()
                .putString(key, uri.toString())
                .apply()
        Toast.makeText(
                context,
                if (key == MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key) {
                    R.string.custom_album_art_folder_set
                } else {
                    R.string.custom_album_art_image_set
                },
                Toast.LENGTH_SHORT).show()
        refreshCustomArtworkRows()
        notifyPreviewInteraction(MiscPreferences.WEAR_ALBUM_ART_SOURCE.key, null)
    }

    /** Each row is shown only while the source that reads it is the selected one - a picker for a
     *  file nothing is going to draw reads as broken, the same rule the face-specific rows follow. */
    private fun updateCustomArtworkVisibility() {
        val source = AlbumArtSource.fromPref(
                readStringPreference(MiscPreferences.WEAR_ALBUM_ART_SOURCE.key,
                        AlbumArtSource.DEFAULT.preferenceValue))
        findPreference<Preference>(MiscPreferences.CUSTOM_ALBUM_ART_IMAGE.key)?.isVisible =
                source == AlbumArtSource.CUSTOM_IMAGE
        findPreference<Preference>(MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key)?.isVisible =
                source == AlbumArtSource.CUSTOM_FOLDER
        refreshCustomArtworkRows()
    }

    /**
     * Puts the chosen file or folder's own name in each row's summary.
     *
     * Read through `DocumentFile` rather than from the URI's last path segment, which for a tree is
     * an opaque document id and for a MediaStore image is a row number - the same reason
     * `TrackMetadataReader` queries `DISPLAY_NAME` instead of parsing the URI. A name that cannot
     * be read at all is reported as "not chosen": the grant is gone, so from here the picture is
     * exactly as absent as if it had never been picked.
     */
    private fun refreshCustomArtworkRows() {
        val context = context ?: return
        listOf(
                MiscPreferences.CUSTOM_ALBUM_ART_IMAGE.key,
                MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key
        ).forEach { key ->
            val preference = findPreference<Preference>(key) ?: return@forEach
            val name = storedCustomArtworkName(
                    key, isTree = key == MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key)
            preference.summary = if (name.isNullOrBlank()) {
                getString(R.string.setting_custom_album_art_none)
            } else {
                getString(R.string.setting_custom_album_art_chosen, name)
            }
        }
    }

    /**
     * The name a picked document or folder shows in its own provider.
     *
     * Queried rather than parsed out of the URI: a tree URI's last segment is an opaque document
     * id and a MediaStore image's is a row number, so both would put a number where the row
     * promises a name. A tree has to be resolved to its own document first - the tree URI itself
     * is not queryable for a display name.
     *
     * Null on any failure, including the one that matters: a grant that has been revoked or a file
     * that has been deleted. The caller reports that as "not chosen", which is what it now is.
     */
    /** The stored pick's own name for [key], or null when nothing is chosen or it can no longer
     *  be read. Shared by the hidden row's summary and the Background editor's button so the two
     *  cannot report different things about one choice. */
    private fun storedCustomArtworkName(key: String, isTree: Boolean): String? {
        val context = context ?: return null
        val stored = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
                .getString(key, "")
                ?.takeIf { it.isNotBlank() }
                ?: return null
        return documentDisplayName(context, android.net.Uri.parse(stored), isTree)
    }

    private fun documentDisplayName(
            context: android.content.Context,
            uri: android.net.Uri,
            isTree: Boolean
    ): String? = try {
        val target = if (isTree) {
            DocumentsContract.buildDocumentUriUsingTree(
                    uri, DocumentsContract.getTreeDocumentId(uri))
        } else {
            uri
        }
        context.contentResolver.query(
                target,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    } catch (e: Exception) {
        Timber.d(e, "Could not read the picked artwork's name")
        null
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
        findPreference<Preference>("album_art_filter")?.isVisible = true
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
        // One list, owned by the Always-on editor's model rather than repeated here - see
        // AodEditorModel.VISUAL_STYLES. It was written twice, here and in
        // WatchSearchTargetResolver, which is how a second copy of a decision list goes stale
        // silently when a face is added.
        val effectiveStyle = AodEditorModel.effectiveStyle(selectedStyle, face)
        // Only the rows a style actually gates. The rest are owned elsewhere - notably the custom
        // tint row, whose visibility belongs to initAccentColorTarget ("custom" alone) and which a
        // blanket sweep would claim back on every face change.
        AodEditorModel.specs
                .filter { it.control in AodEditorModel.STYLE_GATED_CONTROLS }
                .forEach { spec ->
                    findPreference<Preference>(spec.key)?.isVisible =
                            AodEditorModel.appliesToStyle(spec.control, effectiveStyle)
                }
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

    /**
     * Wires one text-effect hex row to the shared Lyra picker.
     *
     * The same write path [initAccentColorTarget] uses - through the face-scoped store, and
     * persisting `""` on reset rather than removing the key, because preference sync carries no
     * removals and the watch would keep rendering the old colour forever. No summary description:
     * these rows never had one, and the compact editor labels its swatch from the row's title.
     */
    private fun initTextEffectCustomColorRow(customColorKey: String) {
        val colorPref = findPreference<Preference>(customColorKey) ?: return
        updateTextEffectCustomColorSummary(colorPref, customColorKey)
        colorPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showLyraColorPickerDialog(
                    initialColor = parseHexOrDefault(store.getString(customColorKey, null)),
                    onReset = {
                        store.putString(customColorKey, "")
                        updateTextEffectCustomColorSummary(colorPref, customColorKey)
                    },
                    onApply = { hex ->
                        store.putString(customColorKey, hex)
                        updateTextEffectCustomColorSummary(colorPref, customColorKey)
                    },
                    onPreviewColor = { hex -> notifyPreviewInteraction(customColorKey, hex) },
                    onPreviewCancelled = {
                        notifyPreviewInteraction(
                                customColorKey, store.getString(customColorKey, null))
                    }
            )
            true
        }
    }

    private fun updateTextEffectCustomColorSummary(pref: Preference, customColorKey: String) {
        val saved = store.getString(customColorKey, null)?.takeUnless { it.isBlank() }
        pref.summary = saved?.let { getString(R.string.color_picker_current, it) }
        (pref as? HexColorDotPreference)?.refreshDot()
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
