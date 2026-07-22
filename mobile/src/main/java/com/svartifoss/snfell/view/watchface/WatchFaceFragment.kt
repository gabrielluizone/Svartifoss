package com.svartifoss.snfell.view.watchface

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.tabs.TabLayoutMediator
import com.svartifoss.snfell.R
import com.svartifoss.snfell.NotificationService
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.databinding.FragmentWatchFaceBinding
import com.svartifoss.snfell.config.buttons.GlobalButtonConfig
import com.svartifoss.snfell.music.isPlaying
import com.svartifoss.snfell.view.TitledActivity
import com.svartifoss.snfell.view.mainactivity.MainActivity
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import com.svartifoss.snfell.view.watchface.theme.WatchThemesActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val PLAYBACK_TICK_INTERVAL_MS = 500L

/** Only these default-preference writes affect the Watch appearance preview. Media history,
 *  dynamic app accent and unrelated Settings values share the same preferences file and must
 *  not pull a contextual AOD/panel preview back to the player or trigger a full watch push. */
private val WATCH_APPEARANCE_PREF_KEYS = setOf(
    "wear_screen_face",
    "wear_active_custom_theme_id",
    "wear_custom_theme_schema",
    "wear_custom_theme_complete",
    "wear_custom_theme_revision",
    "wear_show_track_title",
    "wear_show_track_artist",
    "wear_classic_icons_visible",
    "wear_internal_progress_visible",
    "wear_edge_progress_visible",
    "wear_edge_seek_enabled",
    "wear_expressive_seek_mode",
    "wear_screen_theme",
    "wear_font",
    "wear_title_text_mode",
    "wear_track_time_mode",
    "always_show_time",
    "wear_aod_style",
    "wear_aod_art_treatment",
    "wear_aod_color_mode",
    "wear_aod_custom_color",
    "wear_aod_intensity",
    "wear_aod_show_transport",
    "wear_aod_show_progress",
    "wear_aod_show_pills",
    "wear_aod_show_art",
    "ambient_album_art_opacity",
    "wear_aod_show_clock",
    "wear_aod_show_track_info",
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
    "album_art_blur_radius",
    "dim_album_art",
    "album_art_dim_strength",
    "wear_player_shading_style",
    "wear_player_shading_intensity",
    "wear_shading_color_mode",
    "wear_shading_custom_color",
    "wear_album_art_fade",
    "overlay_blur_radius",
    "wear_dynamic_accent",
    "wear_color_treatment",
    "wear_normal_color",
    "wear_artist_color_mode",
    "wear_artist_custom_color",
    "wear_artist_desaturated",
    "wear_progress_style",
    "wear_progress_color_mode",
    "wear_progress_custom_color",
    "wear_progress_desaturated",
    "wear_volume_color_mode",
    "wear_volume_custom_color",
    "wear_quick_panel_color_mode",
    "wear_quick_panel_custom_color",
    "screen_buttons_curve_style",
    "screen_buttons_bg_style",
    "screen_buttons_opacity",
    "screen_buttons_shape"
)

/**
 * "Watch" tab (the slot the Guide used to occupy - the guide now opens from the toolbar's
 * help button): visual customization of the watch's now-playing screen. A live miniature
 * ([WatchPreviewView]) is docked on top and re-renders on every preference change, while the
 * settings themselves live in the nested [WatchFacePrefsFragment] below it. The application-level
 * preference sync coordinator pushes committed changes, so delivery survives this Fragment's
 * lifecycle while the real screen follows the preview.
 *
 * When music is playing on the phone, the miniature shows the *actual* current track - album
 * art, title and artist from the active media session (the same one the mini player uses),
 * plus live playback state: a 500ms ticker (the watch's own position cadence) advances the
 * progress ring and track time, and pausing morphs the preview just like the real face. Falls
 * back to a built-in sample when nothing is playing.
 */
class WatchFaceFragment : Fragment() {

    companion object {
        private const val STATE_SELECTED_SECTION = "selectedWatchSection"
        private const val STATE_PREVIEW_PREFERENCE = "selectedWatchPreviewPreference"
        private var lastSelectedSection = 0
        private const val NEUTRAL_ACCENT = 0xFF86A69D.toInt()
    }

    private val sections = listOf(
        Section(WatchFacePrefsFragment.SECTION_STYLE, R.string.watch_section_style),
        Section(WatchFacePrefsFragment.SECTION_BACKGROUND, R.string.watch_section_background),
        Section(WatchFacePrefsFragment.SECTION_COLORS, R.string.watch_section_colors),
        Section(WatchFacePrefsFragment.SECTION_AOD, R.string.watch_section_aod),
        Section(WatchFacePrefsFragment.SECTION_PANELS, R.string.watch_section_panels),
        Section(WatchFacePrefsFragment.SECTION_MINI_BUTTONS, R.string.watch_section_mini_buttons)
    )

    private var _binding: FragmentWatchFaceBinding? = null
    private val binding get() = _binding!!
    private var preview: WatchPreviewView? = null
    private var mediaController: MediaController? = null
    private var mediaCallbackRegistered = false
    private var previewPlayingConfig = true
    private var selectedSection = 0
    private var selectedPreviewPreference: String? = null
    private var miniButtonsLoadJob: Job? = null
    private var tabMediator: TabLayoutMediator? = null
    private val themeRepository by lazy(LazyThreadSafetyMode.NONE) {
        WatchThemeRepository(requireContext())
    }
    private val themeManagerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && _binding != null) {
            updateThemeCard()
            preview?.refresh()
        }
    }

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val sectionChanged = selectedSection != position
            selectedSection = position
            lastSelectedSection = position
            if (sectionChanged) selectedPreviewPreference = null
            preview?.showSection(sections[position].key)
            if (!sectionChanged) {
                selectedPreviewPreference?.let { preview?.showPreference(it) }
            }
        }
    }

    private val previewLayoutListener = View.OnLayoutChangeListener {
            _, left, top, right, bottom, _, _, _, _ ->
        resizePreview(right - left, bottom - top)
    }

    private val tickHandler = Handler(Looper.getMainLooper())
    private val playbackTick = object : Runnable {
        override fun run() {
            pushPlayback()
            if (mediaController?.playbackState?.isPlaying() == true) {
                tickHandler.postDelayed(this, PLAYBACK_TICK_INTERVAL_MS)
            }
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // Appearance keys are stored scoped per face ("<baseKey>@<face>"); map back to the base
        // key so the preview surface routing and the appearance filter still match.
        val baseKey = key?.substringBefore(FaceScopedPreferences.SCOPE_SEPARATOR)
        if (baseKey != null && baseKey in WATCH_APPEARANCE_PREF_KEYS) {
            if (baseKey == "wear_quick_panel_source") {
                context?.applicationContext?.let { appContext ->
                    NotificationService.updateQuickActionsBinding(appContext)
                }
            }
            // Switching the face changes every scoped value at once - refresh the whole preview so
            // it re-reads the new face's configuration, not just the one key that changed.
            if (baseKey == "wear_screen_face" || baseKey.startsWith("wear_custom_theme_") ||
                    baseKey == "wear_active_custom_theme_id") {
                preview?.refresh()
                updateThemeCard()
            } else {
                preview?.refresh(baseKey)
            }
        }
    }

    /** Loads the user's configured mini buttons (their real icons) off the main thread and
     *  hands them to the preview. Re-run on start so a change made in the Playing-controls tab
     *  is reflected when the user comes back here. */
    private fun loadMiniButtons() {
        val context = context?.applicationContext ?: return
        val playing = previewPlayingConfig
        miniButtonsLoadJob?.cancel()
        miniButtonsLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val icons = withContext(Dispatchers.IO) {
                MiniButtonIconLoader.loadConfiguredIcons(context, playing)
            }
            // A media-state callback may have switched configs while this disk read was running.
            if (playing == previewPlayingConfig) preview?.setButtonIcons(icons)
        }
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            pushNowPlaying(metadata)
            pushPlayback()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            restartPlaybackTicker()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWatchFaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preview = binding.watchPreview

        binding.watchPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = sections.size

            override fun createFragment(position: Int): Fragment =
                WatchFacePrefsFragment.newInstance(sections[position].key)
        }
        selectedSection = (savedInstanceState?.getInt(STATE_SELECTED_SECTION)
            ?: lastSelectedSection).coerceIn(sections.indices)
        selectedPreviewPreference = savedInstanceState?.getString(STATE_PREVIEW_PREFERENCE)
        // Position the pager before callbacks/TabLayout attach so their initial selection cannot
        // overwrite the restored contextual surface (for example Queue inside Panels).
        binding.watchPager.setCurrentItem(selectedSection, false)
        binding.watchPager.registerOnPageChangeCallback(pageCallback)
        tabMediator = TabLayoutMediator(binding.watchTabs, binding.watchPager) { tab, position ->
            tab.setText(sections[position].title)
        }.also { it.attach() }
        preview?.showSection(sections[selectedSection].key)
        selectedPreviewPreference?.let { preview?.showPreference(it) }
        binding.root.addOnLayoutChangeListener(previewLayoutListener)
        bindPreviewSwipe()
        tintTabs()
        binding.watchThemeCard.setOnClickListener {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            // Persist any edits made in the shared Watch editor before the manager reads its
            // phone-local library. Only the resulting Apply action is synchronized to Wear.
            themeRepository.captureActive(prefs)
            themeManagerLauncher.launch(Intent(requireContext(), WatchThemesActivity::class.java))
        }
        updateThemeCard()

        (activity as? MainActivity)?.activeMediaSession()?.observe(viewLifecycleOwner) { resource ->
            bindMediaController(resource?.data)
        }
        (activity as? MainActivity)?.connectedWatchInfo()?.observe(viewLifecycleOwner) { info ->
            val watch = info?.watchInfo
            preview?.setDeviceProfile(
                round = watch?.roundWatch,
                displayWidthPx = watch?.displayWidth ?: 0,
                displayHeightPx = watch?.displayHeight ?: 0,
                density = watch?.displayDensity ?: 1f
            )
        }
        GlobalButtonConfig.playingConfigSaved.observe(viewLifecycleOwner) {
            if (previewPlayingConfig) loadMiniButtons()
        }
        GlobalButtonConfig.stoppedConfigSaved.observe(viewLifecycleOwner) {
            if (!previewPlayingConfig) loadMiniButtons()
        }
    }

    /** Called only by the currently visible preference page. Candidate values let List/Switch
     *  choices paint before Android persists them; the shared-preference callback then replaces
     *  that transient state with the committed value. */
    internal fun onWatchPreferenceInteraction(
        section: String,
        key: String,
        candidateValue: Any?
    ) {
        if (_binding == null || sections[binding.watchPager.currentItem].key != section) return
        selectedPreviewPreference = key
        preview?.showPreference(key, candidateValue)
    }

    /** Exposes the preview's live album accent triple to the color-treatment picker dialog
     *  (see ColorTreatmentPreference/WatchFacePrefsFragment) so its swatches match what's on
     *  screen instead of re-extracting a possibly different palette. Falls back to a neutral gray
     *  if the preview hasn't been created yet - not expected in practice, since the prefs page is
     *  only shown alongside it. */
    internal fun currentAlbumAccents(): Triple<Int, Int, Int> =
            preview?.currentAlbumAccents() ?: Triple(NEUTRAL_ACCENT, NEUTRAL_ACCENT, NEUTRAL_ACCENT)

    private fun tintTabs() {
        val activity = activity as? MainActivity ?: return
        activity.applyAccentToView(binding.watchTabs)
        val accent = activity.currentAccentTextColor()
        binding.watchThemeIcon.imageTintList = ColorStateList.valueOf(accent)
        binding.watchThemeCard.strokeWidth = 0
    }

    /** The compact identity row makes it explicit whether the six editors below currently mutate
     * a built-in layout or an isolated saved theme. */
    private fun updateThemeCard() {
        if (_binding == null || !isAdded) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val appearance = ThemeAppearance.resolve(prefs)
        val faceName = WatchThemeRepository.displayNameForFace(requireContext(), appearance.baseFace)
        val profile = themeRepository.activeProfile(prefs)
        binding.watchThemeName.text = when (appearance) {
            is com.svartifoss.snfell.common.AppearanceContext.Custom ->
                profile?.name ?: getString(R.string.watch_theme_unknown_name)
            is com.svartifoss.snfell.common.AppearanceContext.BuiltIn -> faceName
        }
        binding.watchThemeSummary.text = if (appearance is com.svartifoss.snfell.common.AppearanceContext.Custom) {
            getString(R.string.watch_theme_custom_subtitle, faceName)
        } else {
            getString(R.string.watch_theme_builtin_subtitle, faceName)
        }
        binding.watchThemeCard.contentDescription = buildString {
            append(binding.watchThemeName.text)
            append(". ")
            append(binding.watchThemeSummary.text)
            append(". ")
            append(getString(R.string.watch_theme_manage))
        }
    }

    /** The preview deliberately stays fixed while the preference pages move, so it is outside
     *  ViewPager2's touch area. Treat a horizontal drag over that prominent surface as the same
     *  previous/next-page gesture; otherwise swiping appears broken depending on where the user
     *  starts the gesture. */
    @SuppressLint("ClickableViewAccessibility")
    private fun bindPreviewSwipe() {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        var startX = 0f
        var startY = 0f
        var gestureResolved = false

        binding.watchPreviewPanel.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    gestureResolved = false
                }

                MotionEvent.ACTION_MOVE -> if (!gestureResolved) {
                    val deltaX = event.x - startX
                    val deltaY = event.y - startY
                    if (abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY)) {
                        val direction = if (deltaX < 0f) 1 else -1
                        val target = (binding.watchPager.currentItem + direction)
                            .coerceIn(sections.indices)
                        if (target != binding.watchPager.currentItem) {
                            binding.watchPager.setCurrentItem(target, true)
                        }
                        gestureResolved = true
                    } else if (abs(deltaY) > touchSlop && abs(deltaY) > abs(deltaX)) {
                        gestureResolved = true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> gestureResolved = true
            }
            true
        }
    }

    private fun resizePreview(rootWidth: Int, rootHeight: Int) {
        if (rootWidth <= 0 || rootHeight <= 0 || _binding == null) return
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val size = if (landscape) {
            min(min(rootWidth * 0.30f, rootHeight - dp(16).toFloat()), dp(240).toFloat())
        } else {
            min(
                min(rootWidth - dp(32).toFloat(), rootWidth * 0.60f),
                min(rootHeight * 0.40f, dp(240).toFloat())
            )
        }.roundToInt().coerceAtLeast(dp(80))

        val previewParams = binding.watchPreview.layoutParams
        if (previewParams.width != size || previewParams.height != size) {
            previewParams.width = size
            previewParams.height = size
            binding.watchPreview.layoutParams = previewParams
        }

        if (landscape) {
            val panelWidth = size + dp(24)
            val panelParams = binding.watchPreviewPanel.layoutParams
            if (panelParams.width != panelWidth) {
                panelParams.width = panelWidth
                binding.watchPreviewPanel.layoutParams = panelParams
            }
        }
    }

    private fun bindMediaController(controller: MediaController?) {
        unregisterMediaCallback()
        mediaController = controller
        registerMediaCallback()
        pushNowPlaying(controller?.metadata)
        restartPlaybackTicker()
    }

    private fun registerMediaCallback() {
        val controller = mediaController ?: return
        if (!mediaCallbackRegistered) {
            controller.registerCallback(mediaCallback)
            mediaCallbackRegistered = true
        }
    }

    private fun unregisterMediaCallback() {
        if (mediaCallbackRegistered) {
            mediaController?.unregisterCallback(mediaCallback)
            mediaCallbackRegistered = false
        }
    }

    private fun pushNowPlaying(metadata: MediaMetadata?) {
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        preview?.setNowPlaying(
                art,
                metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        )
    }

    /** Pushes the current (interpolated) playback position/state into the preview - the state's
     *  position snapshot is anchored at lastPositionUpdateTime, so extrapolate while playing. */
    private fun pushPlayback() {
        val controller = mediaController
        val state = controller?.playbackState
        if (controller == null || state == null) {
            preview?.setPlayback(null, -1, -1)
            if (!previewPlayingConfig) {
                previewPlayingConfig = true
                loadMiniButtons()
            }
            return
        }

        val playing = state.isPlaying()
        if (previewPlayingConfig != playing) {
            previewPlayingConfig = playing
            loadMiniButtons()
        }
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        val position = if (playing) {
            state.position +
                    ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        } else {
            state.position
        }
        preview?.setPlayback(playing, position, duration)
    }

    private fun restartPlaybackTicker() {
        tickHandler.removeCallbacks(playbackTick)
        playbackTick.run()
    }

    override fun onStart() {
        super.onStart()
        if (parentFragmentManager.findFragmentById(R.id.fragment_container) === this) {
            (activity as? TitledActivity)?.updateActivityTitle(getString(R.string.watch_face_header))
        }
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(prefListener)
        // LiveData does not necessarily emit the same MediaController version again when this
        // observer merely returns to STARTED, so explicitly resume its callbacks here.
        registerMediaCallback()
        preview?.refresh()
        updateThemeCard()
        tintTabs()
        loadMiniButtons()
        restartPlaybackTicker()
    }

    override fun onStop() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        themeRepository.captureActive(prefs)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        unregisterMediaCallback()
        tickHandler.removeCallbacks(playbackTick)
        super.onStop()
    }

    override fun onDestroyView() {
        tickHandler.removeCallbacks(playbackTick)
        binding.root.removeOnLayoutChangeListener(previewLayoutListener)
        binding.watchPreviewPanel.setOnTouchListener(null)
        binding.watchPager.unregisterOnPageChangeCallback(pageCallback)
        tabMediator?.detach()
        tabMediator = null
        binding.watchPager.adapter = null
        miniButtonsLoadJob?.cancel()
        miniButtonsLoadJob = null
        unregisterMediaCallback()
        mediaController = null
        preview = null
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_SECTION, selectedSection)
        outState.putString(STATE_PREVIEW_PREFERENCE, selectedPreviewPreference)
        super.onSaveInstanceState(outState)
    }

    private data class Section(
        val key: String,
        val title: Int
    )
}
