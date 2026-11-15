package com.svartifoss.snfell.view.watchface

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import com.svartifoss.snfell.R
import com.svartifoss.snfell.NotificationService
import com.svartifoss.snfell.common.AlbumArtSource
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.SampledBitmaps
import com.svartifoss.snfell.music.CustomArtworkSource
import com.svartifoss.snfell.music.OnlineArtworkFetcher
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.databinding.FragmentWatchFaceBinding
import com.svartifoss.snfell.config.WatchInfoProvider
import com.svartifoss.snfell.config.WatchInfoWithIcons
import com.svartifoss.snfell.config.buttons.GlobalButtonConfig
import com.svartifoss.snfell.music.isPlaying
import com.svartifoss.snfell.notifications.AppGlyphStore
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.TitledActivity
import com.svartifoss.snfell.view.mainactivity.MainActivity
import com.svartifoss.snfell.view.settings.FaceScopedPreferenceDataStore
import com.svartifoss.snfell.view.settings.SectionNavigationItem
import com.svartifoss.snfell.view.watchface.home.PreviewCaption
import com.svartifoss.snfell.view.watchface.home.WatchLook
import com.svartifoss.snfell.view.watchface.home.WatchLookThumbnailer
import com.svartifoss.snfell.view.watchface.home.WatchLookTile
import com.svartifoss.snfell.view.watchface.home.WatchLooks
import com.svartifoss.snfell.view.watchface.home.WatchLooksAdapter
import com.svartifoss.snfell.view.watchface.home.WatchTabNavigation
import com.svartifoss.snfell.view.watchface.home.WatchTabPlace
import com.svartifoss.snfell.view.watchface.theme.QuickThemeSwitch
import com.svartifoss.snfell.view.watchface.theme.WatchThemeProfile
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import com.svartifoss.snfell.view.watchface.theme.WatchThemesActivity
import com.svartifoss.snfell.view.watchface.theme.activeQuickThemeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import timber.log.Timber
import com.svartifoss.snfell.view.NEUTRAL_WATCH_ACCENT
import com.svartifoss.snfell.util.WearableAvailability

private const val PLAYBACK_TICK_INTERVAL_MS = 500L

/** Every face-scoped appearance value can affect the preview. Deriving this listener inventory
 *  from the same registry used by themes/backups prevents a newly added editor from rendering only
 *  after the fragment is reopened. The small suffix contains deliberate global appearance keys. */
private val WATCH_APPEARANCE_PREF_KEYS = FaceScopedPreferences.SCOPED_KEYS + setOf(
    "wear_screen_face",
    "wear_active_custom_theme_id",
    "wear_custom_theme_schema",
    "wear_custom_theme_complete",
    "wear_custom_theme_revision",
    "wear_quick_panel_source",
    "queue_remote_artwork"
)

/**
 * "Watch" tab (the slot the Guide used to occupy - the guide now opens from the toolbar's
 * help button): visual customization of the watch's now-playing screen. A live miniature
 * ([WatchPreviewView]) is docked on top and re-renders on every preference change; below it the
 * tab shows one of two things.
 *
 * **Home** comes first: the themes drawn as pictures by the real preview, and the seven areas with
 * what each one holds. A person who has just installed the app sees what their watch looks like,
 * changes the look by pointing at another one, and sees where everything else lives, without a
 * tutorial and without meeting an editor first. **An area** is one of the existing editor pages,
 * opened from Home in one tap, with the rail of the seven kept for moving sideways and Back (or the
 * arrow before the rail) returning Home. The application-level preference sync coordinator pushes
 * committed changes, so delivery survives this Fragment's lifecycle while the real screen follows
 * the preview.
 *
 * When music is playing on the phone, the miniature shows the *actual* current track - album
 * art, title and artist from the active media session (the same one the mini player uses),
 * plus live playback state: a 500ms ticker (the watch's own position cadence) advances the
 * progress ring and track time, and pausing morphs the preview just like the real face. Falls
 * back to a built-in sample when nothing is playing. The theme strip's pictures are drawn on the
 * same cover.
 */
class WatchFaceFragment : Fragment() {

    companion object {
        private const val STATE_PLACE = "watchTabPlace"
        private const val STATE_PREVIEW_PREFERENCE = "selectedWatchPreviewPreference"
        private const val STATE_PAGER_ATTACHED = "watchTabPagerAttached"
        private const val ARG_SECTION = "initialWatchSection"
        private const val ARG_HIGHLIGHT_KEY = "initialWatchHighlightKey"
        private const val HOME_FRAGMENT_TAG = "watchTabHome"
        private const val NEUTRAL_ACCENT = NEUTRAL_WATCH_ACCENT

        /** How long a run of edits is left to settle before the applied theme's picture is redrawn. */
        private const val THUMBNAIL_REFRESH_DELAY_MS = 600L

        /** As long as the Settings tab waits before saying no watch is connected. */
        private const val NO_WATCH_NOTE_DELAY_MS = 600L

        /** Widget.MaterialComponents.Snackbar's backgroundOverlayColorAlpha. */
        private const val SNACKBAR_OVERLAY_ALPHA = 0.8f

        /**
         * Where this process last left the tab. Switching to Controls and back reopens the page
         * someone was on; a new process opens Home. Never persisted: a restart is a fresh start.
         */
        private var lastShownPlace: WatchTabPlace? = null

        /** The seven areas, in the order of the rail and of Home's list. */
        internal val SECTIONS = listOf(
            SectionNavigationItem(
                WatchFacePrefsFragment.SECTION_STYLE,
                R.string.watch_section_style,
                R.string.watch_section_style_description,
                R.drawable.ic_player_theme
            ),
            SectionNavigationItem(
                WatchFacePrefsFragment.SECTION_BACKGROUND,
                R.string.watch_section_background,
                R.string.watch_section_background_description,
                R.drawable.ic_album
            ),
            SectionNavigationItem(
                WatchFacePrefsFragment.SECTION_COLORS,
                R.string.watch_section_colors,
                R.string.watch_section_colors_description,
                R.drawable.ic_colors
            ),
            SectionNavigationItem(
                WatchFacePrefsFragment.SECTION_TYPOGRAPHY,
                R.string.watch_section_typography,
                R.string.watch_section_typography_description,
                R.drawable.ic_text_fields
            ),
            SectionNavigationItem(
                WatchFacePrefsFragment.SECTION_AOD,
                R.string.watch_section_aod,
                R.string.watch_section_aod_description,
                R.drawable.ic_always_on
            ),
            SectionNavigationItem(
                WatchFacePrefsFragment.SECTION_PANELS,
                R.string.watch_section_panels,
                R.string.watch_section_panels_description,
                R.drawable.ic_quick_actions
            ),
            SectionNavigationItem(
                WatchFacePrefsFragment.SECTION_MINI_BUTTONS,
                R.string.watch_section_mini_buttons,
                R.string.watch_section_mini_buttons_description,
                R.drawable.ic_mini_buttons
            )
        )

        /** Opens straight at [section], scrolled to [highlightKey]. Used by the settings search;
         *  with both null this is the same as the plain constructor. */
        fun newInstance(section: String?, highlightKey: String?) = WatchFaceFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SECTION, section)
                putString(ARG_HIGHLIGHT_KEY, highlightKey)
            }
        }
    }

    private var _binding: FragmentWatchFaceBinding? = null
    private val binding get() = _binding!!
    private var preview: WatchPreviewView? = null

    /**
     * The enlarged copy, while the full-screen dialog is open.
     *
     * Held so the live feed keeps reaching it: the docked miniature is driven by a media callback
     * and a 500ms ticker, and a full-screen copy that only received a snapshot at open time would
     * freeze its progress ring and stop following the track -- on the screen a person opened
     * precisely to look at it closely.
     */
    private var fullScreenPreview: WatchPreviewView? = null

    /** Every preview currently on screen. Content and surface changes must reach all of them. */
    private fun previews(action: (WatchPreviewView) -> Unit) {
        preview?.let(action)
        fullScreenPreview?.let(action)
    }
    private var mediaController: MediaController? = null
    private var mediaCallbackRegistered = false
    private var previewPlayingConfig = true

    /** What is below the preview: Home, or one area. */
    private var place: WatchTabPlace = WatchTabPlace.Home

    /** The area the pager is on, kept while Home hides it so it reopens where it was. */
    private var pagerSection = SECTIONS.first().key

    /** Whether the pager has its pages yet. They are created the first time an area opens, so
     *  opening the tab on Home builds one preference page, not two. */
    private var pagerAttached = false

    /** The section whose page is on screen: the Home page or the open area's. */
    private val activeSection: String
        get() = when (val current = place) {
            WatchTabPlace.Home -> WatchFacePrefsFragment.SECTION_HOME
            is WatchTabPlace.Area -> current.section
        }

    private var selectedPreviewPreference: String? = null
    private var pendingHighlightKey: String? = null
    private var requestedSection: String? = null
    private var restoredFromState = false
    private var miniButtonsLoadJob: Job? = null
    private var tabMediator: TabLayoutMediator? = null
    private val themeRepository by lazy(LazyThreadSafetyMode.NONE) {
        WatchThemeRepository(requireContext())
    }
    private val defaultPrefs: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(requireContext())
    private val themeManagerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onThemeLibraryChanged()
    }

    /** Back in an area returns Home; on Home it does what Back did before this tab had levels. */
    private val backToHome = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showHome()
        }
    }

    // --- The theme strip on Home ---
    private var thumbnailer: WatchLookThumbnailer? = null
    private var looksAdapter: WatchLooksAdapter? = null
    /** The strip's list as Home last bound it; the adapter moves with it if the row is rebuilt. */
    private var looksList: RecyclerView? = null
    private var looksListState: Parcelable? = null
    private var looksScrolledToApplied = false
    private var thumbnailArtwork: Bitmap? = null
    private val activeThumbnailRefresh = Runnable { redrawActiveThumbnail() }

    /** The wait before [updateNoWatchNote] shows its line. */
    private var noWatchNoteJob: Job? = null

    /** The theme's name under the preview, read when the theme changes rather than per touch. */
    private var captionThemeName: String = ""

    /**
     * Re-reads the active theme after something outside this tab changed it.
     *
     * This tab's own preference listener is unregistered while another Activity is in front, so a
     * theme applied from the themes screen - or from the toolbar's community-gallery shortcut,
     * which opens the same gallery without going through this tab at all - lands while nothing
     * here is listening. Without this the caption, the strip and the miniature keep showing the
     * previous theme until the user happens to touch a setting.
     */
    internal fun onThemeLibraryChanged() {
        if (_binding == null) return
        refreshCaptionName()
        previews { it.refresh() }
        // A saved theme can have been edited, renamed or deleted there, so every picture is
        // suspect, not only the applied one.
        thumbnailer?.invalidateAll()
        refreshLooks()
        looksAdapter?.redrawAll()
    }

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val nextSection = SECTIONS[position].key
            val sectionChanged = pagerSection != nextSection
            pagerSection = nextSection
            describeSection(position)
            // The pager also reports a position when its pages are attached behind Home, after a
            // rotation; that must not open an area by itself.
            if (place !is WatchTabPlace.Area) return
            place = WatchTabPlace.Area(nextSection)
            lastShownPlace = place
            if (sectionChanged) selectedPreviewPreference = null
            previews { it.showSection(nextSection) }
            if (!sectionChanged) {
                selectedPreviewPreference?.let { key -> previews { it.showPreference(key) } }
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
        if (key == QuickThemeSwitch.SHOW_ARCHIVED_PREF) {
            refreshLooks()
            return@OnSharedPreferenceChangeListener
        }
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
                refreshCaptionName()
                refreshLooks()
            } else {
                preview?.refresh(baseKey)
                // The applied theme's picture shows these values. Redrawn once the edits settle
                // rather than on each one, since a slider can write many in a second.
                tickHandler.removeCallbacks(activeThumbnailRefresh)
                tickHandler.postDelayed(activeThumbnailRefresh, THUMBNAIL_REFRESH_DELAY_MS)
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
            if (playing == previewPlayingConfig) {
                previews { it.setButtonIcons(icons) }
                // The pictures in the theme strip and the pickers are the same watch.
                if (thumbnailer?.setButtonIcons(icons) == true) looksAdapter?.redrawAll()
            }
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
        preview?.onSurfaceChanged = { renderCaption() }
        restoredFromState = savedInstanceState != null

        val rawRequestedSection = arguments?.getString(ARG_SECTION)
            ?.takeIf { requested -> SECTIONS.any { it.key == requested } }
        val rawHighlightKey = arguments?.getString(ARG_HIGHLIGHT_KEY)
        val searchTarget = if (savedInstanceState == null &&
                rawRequestedSection != null && rawHighlightKey != null) {
            resolveSearchTarget(rawRequestedSection, rawHighlightKey)
        } else {
            SearchTarget(rawRequestedSection, rawHighlightKey, redirected = false)
        }
        requestedSection = searchTarget.section
        pendingHighlightKey = searchTarget.key
        selectedPreviewPreference = savedInstanceState?.getString(STATE_PREVIEW_PREFERENCE)

        place = WatchTabNavigation.resolve(
                restored = WatchTabNavigation.decode(savedInstanceState?.getString(STATE_PLACE)),
                searchSection = requestedSection.takeIf { savedInstanceState == null },
                lastShown = lastShownPlace,
                sections = SECTIONS.map { it.key })

        binding.watchPager.registerOnPageChangeCallback(pageCallback)
        binding.watchEditorHome.setOnClickListener { showHome() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backToHome)
        setUpThemeStrip()

        if (searchTarget.redirected) {
            Toast.makeText(
                requireContext(),
                R.string.settings_search_prerequisite,
                Toast.LENGTH_LONG
            ).show()
        }
        binding.root.addOnLayoutChangeListener(previewLayoutListener)
        bindPreviewSwipe()
        // The horizontal drag over the preview already switches sections, so this is a click
        // rather than any other gesture: the two do not compete, and a tap on something that
        // looks like a watch reads as "show me this properly".
        binding.watchPreview.isClickable = true
        binding.watchPreview.isFocusable = true
        binding.watchPreview.setOnClickListener { openFullScreenPreview() }
        ViewCompat.replaceAccessibilityAction(
                binding.watchPreview,
                AccessibilityActionCompat.ACTION_CLICK,
                getString(R.string.watch_preview_expand),
                null)
        tintNavigation()
        refreshCaptionName()

        // The pages a rotation restored have to be handed back to their adapter even when Home is
        // in front, or they would sit in the fragment manager with nowhere to go.
        if (savedInstanceState?.getBoolean(STATE_PAGER_ATTACHED) == true) attachPager()
        when (val opening = place) {
            WatchTabPlace.Home -> showHome(opening = true)
            is WatchTabPlace.Area -> openArea(opening.section, opening = true)
        }

        (activity as? MainActivity)?.activeMediaSession()?.observe(viewLifecycleOwner) { resource ->
            bindMediaController(resource?.data)
        }
        (activity as? MainActivity)?.connectedWatchInfo()?.observe(viewLifecycleOwner) { info ->
            val watch = info?.watchInfo
            val round = watch?.roundWatch
            val width = watch?.displayWidth ?: 0
            val height = watch?.displayHeight ?: 0
            val density = watch?.displayDensity ?: 1f
            previews { it.setDeviceProfile(round, width, height, density) }
            if (thumbnailer?.setDeviceProfile(round, width, height, density) == true) {
                looksAdapter?.redrawAll()
            }
            updateNoWatchNote(info)
        }
        GlobalButtonConfig.playingConfigSaved.observe(viewLifecycleOwner) {
            if (previewPlayingConfig) loadMiniButtons()
        }
        GlobalButtonConfig.stoppedConfigSaved.observe(viewLifecycleOwner) {
            if (!previewPlayingConfig) loadMiniButtons()
        }
    }

    // ------------------------------------------------------------------ Home and the areas

    /**
     * Shows Home below the preview.
     *
     * [opening] is the tab being built, when a restored preview focus is still worth showing;
     * every later return Home starts from the player, which is what Home is about.
     */
    private fun showHome(opening: Boolean = false) {
        if (_binding == null) return
        place = WatchTabPlace.Home
        lastShownPlace = place
        ensureHomePage()
        binding.watchEditorContainer.isVisible = false
        binding.watchHomeContainer.isVisible = true
        backToHome.isEnabled = false
        if (!opening) selectedPreviewPreference = null
        previews { it.showSection(WatchFacePrefsFragment.SECTION_HOME) }
        selectedPreviewPreference?.let { key -> previews { it.showPreference(key) } }
        renderCaption()
    }

    /** Opens the area [section], from Home's list, a search result or a restored state. */
    internal fun openArea(section: String, opening: Boolean = false) {
        if (_binding == null) return
        val index = SECTIONS.indexOfFirst { it.key == section }
        if (index < 0) return
        place = WatchTabPlace.Area(section)
        lastShownPlace = place
        attachPager()
        pagerSection = section
        binding.watchPager.setCurrentItem(index, false)
        describeSection(index)
        binding.watchHomeContainer.isVisible = false
        binding.watchEditorContainer.isVisible = true
        backToHome.isEnabled = true
        if (!opening) selectedPreviewPreference = null
        previews { it.showSection(section) }
        selectedPreviewPreference?.let { key -> previews { it.showPreference(key) } }
        renderCaption()
    }

    /**
     * Says so under the preview while no watch has reported in. Every change still shows on the
     * phone, and without this nothing explained why the wrist never followed.
     *
     * After the same short wait as the Settings tab's banner: the provider publishes nothing until
     * its first Data Layer read, and a connected watch's info can land a moment after that.
     */
    private fun updateNoWatchNote(info: WatchInfoWithIcons?) {
        noWatchNoteJob?.cancel()
        val note = _binding?.watchNoWatchNote ?: return
        note.isVisible = false
        val provider = (activity as? MainActivity)?.connectedWatchInfo() as? WatchInfoProvider
                ?: return
        if (info != null || !provider.hasResolvedInitialValue) return
        noWatchNoteJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(NO_WATCH_NOTE_DELAY_MS)
            if (provider.value != null || !provider.hasResolvedInitialValue) return@launch
            val shown = _binding?.watchNoWatchNote ?: return@launch
            // On a phone with no Data Layer at all, pairing would never help: say which it is.
            shown.setText(if (WearableAvailability.isAvailable(shown.context)) {
                R.string.setting_no_watch_banner
            } else {
                R.string.setting_no_wearable_api_banner
            })
            shown.isVisible = true
        }
    }

    /** Puts the area's one-line description under the rail; swiping to another area updates it. */
    private fun describeSection(index: Int) {
        val item = SECTIONS.getOrNull(index) ?: return
        _binding?.watchSectionDescription?.setText(item.description)
    }

    /** Creates Home's preference page the first time Home is shown; a rotation restores it. */
    private fun ensureHomePage() {
        if (childFragmentManager.findFragmentByTag(HOME_FRAGMENT_TAG) != null) return
        childFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.watch_home_container,
                        WatchFacePrefsFragment.newInstance(WatchFacePrefsFragment.SECTION_HOME),
                        HOME_FRAGMENT_TAG)
                .commitNow()
    }

    /**
     * Gives the pager its seven pages, once.
     *
     * Deferred until an area first opens, so a tab that opens on Home builds one preference page
     * rather than two. ViewPager2 keeps the state it restored until it has an adapter, so attaching
     * late still brings back the pages and the position a rotation saved.
     */
    private fun attachPager() {
        if (pagerAttached || _binding == null) return
        pagerAttached = true
        binding.watchPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = SECTIONS.size

            override fun createFragment(position: Int): Fragment {
                val section = SECTIONS[position].key
                val highlight = pendingHighlightKey?.takeIf {
                    !restoredFromState && section == requestedSection
                }
                if (highlight != null) pendingHighlightKey = null
                return WatchFacePrefsFragment.newInstance(section, highlight)
            }
        }
        // A tab tap jumps straight to its page. With the mediator's default smooth scroll, a tap
        // several tabs away animates through every page in between, and when one of those pages
        // finished laying out mid-scroll the pager could come to rest on a different page from the
        // selected tab - Always-on selected, Mini buttons on screen - with nothing to correct it.
        tabMediator = TabLayoutMediator(
                binding.watchTabs, binding.watchPager,
                /* autoRefresh = */ true, /* smoothScroll = */ false) { tab, position ->
            tab.setText(SECTIONS[position].title)
        }.also { it.attach() }
        tintNavigation()
    }

    // ------------------------------------------------------------------ the theme strip

    private fun setUpThemeStrip() {
        val pictures = WatchLookThumbnailer(requireContext())
        thumbnailer = pictures
        looksAdapter = WatchLooksAdapter(
                thumbnailer = pictures,
                profileFor = ::profileForLook,
                onPick = ::onLookPicked)
        looksScrolledToApplied = false
        refreshLooks()
    }

    /**
     * Hands the strip to the list Home has just bound, carrying its scroll position over when the
     * row was rebuilt, and on the first showing scrolls only as far as the applied theme.
     */
    internal fun showLooksIn(list: RecyclerView, accent: Int, surface: Int, background: Int) {
        val adapter = looksAdapter ?: return
        if (looksList !== list) {
            looksList?.layoutManager?.onSaveInstanceState()?.let { looksListState = it }
            looksList?.adapter = null
            looksList = list
        }
        if (list.layoutManager == null) {
            list.layoutManager = LinearLayoutManager(list.context, RecyclerView.HORIZONTAL, false)
        }
        adapter.setColors(accent, surface, background)
        if (list.adapter !== adapter) list.adapter = adapter
        val saved = looksListState
        if (saved != null) {
            looksListState = null
            list.layoutManager?.onRestoreInstanceState(saved)
        } else if (!looksScrolledToApplied) {
            looksScrolledToApplied = true
            val applied = adapter.appliedIndex
            if (applied > 0) list.doOnLayout { revealApplied(list, applied) }
        }
    }

    /**
     * Scrolls only as far as the applied tile needs to be whole on screen, so it lands at the end
     * of the row with the themes before it still in view. Scrolling to the position outright would
     * put it first, hiding everything ahead of it - often the user's own saved themes.
     */
    private fun revealApplied(list: RecyclerView, applied: Int) {
        if (looksList !== list) return
        val manager = list.layoutManager as? LinearLayoutManager ?: return
        val lastWhole = manager.findLastCompletelyVisibleItemPosition()
        if (lastWhole == RecyclerView.NO_POSITION || applied <= lastWhole) return
        val tileWidth = list.getChildAt(0)?.width ?: return
        val room = list.width - list.paddingStart - list.paddingEnd - tileWidth
        manager.scrollToPositionWithOffset(applied, room.coerceAtLeast(0))
    }

    private fun refreshLooks() {
        val adapter = looksAdapter ?: return
        val prefs = defaultPrefs
        adapter.submit(WatchLooks.build(
                profiles = themeRepository.load(),
                allowedFaces = ThemeAppearance.ALLOWED_BASE_FACES,
                archivedFaces = ArchivedFaces.KEYS,
                showArchived = prefs.getBoolean(QuickThemeSwitch.SHOW_ARCHIVED_PREF, false),
                active = ThemeAppearance.resolve(prefs)))
    }

    /**
     * The look to draw for a tile, read when its turn to be drawn comes.
     *
     * The applied theme is drawn from the live values, including edits not yet captured into a
     * saved theme, so its picture matches the preview above it; any other saved theme from the
     * library; a built-in layout from the values the user has given it, which is what applying it
     * gives.
     */
    private fun profileForLook(look: WatchLook): WatchThemeProfile? {
        if (_binding == null) return null
        val prefs = defaultPrefs
        val active = ThemeAppearance.resolve(prefs)
        return when (look) {
            is WatchLook.BuiltIn ->
                themeRepository.previewProfile(prefs, AppearanceContext.BuiltIn(look.face))
            is WatchLook.Saved ->
                if ((active as? AppearanceContext.Custom)?.themeId == look.profile.id) {
                    themeRepository.previewProfile(prefs, active)
                } else {
                    look.profile
                }
            WatchLook.Community -> null
        }
    }

    /**
     * The renderer a style picker draws its grid with: this tab's, because it already holds the
     * cover that is playing and the shape of the connected watch.
     */
    internal fun choicePictures(): WatchLookThumbnailer? = thumbnailer

    private fun redrawActiveThumbnail() {
        if (_binding == null) return
        val key = activeQuickThemeKey(ThemeAppearance.resolve(defaultPrefs))
        thumbnailer?.invalidate(key)
        looksAdapter?.redraw(key)
    }

    private fun onLookPicked(tile: WatchLookTile) {
        val look = tile.look
        if (look is WatchLook.Community) {
            openCommunityGallery()
            return
        }
        if (tile.applied) return
        val context = context ?: return
        val prefs = defaultPrefs
        val previous = activeQuickThemeKey(ThemeAppearance.resolve(prefs))
        // Captures the saved theme being left before applying, so edits made to it a moment ago
        // are kept in it rather than overwritten - see QuickThemeSwitch.apply.
        val name = QuickThemeSwitch.apply(context, prefs, look.key)
        if (name == null) {
            Toast.makeText(context, R.string.watch_theme_apply_error, Toast.LENGTH_LONG).show()
            return
        }
        showApplied(name, undoKey = previous)
    }

    /**
     * Says what was applied, with Undo. Applying is one tap and nothing asks first, so the way back
     * has to be just as close: Undo re-applies the theme that was in use, its own edits included,
     * since applying captured them.
     */
    private fun showApplied(name: String, undoKey: String?) {
        val root = _binding?.root ?: return
        val message = getString(R.string.watch_theme_apply_success, name)
        val snackbar = Snackbar.make(root, message, Snackbar.LENGTH_LONG)
        undoKey?.let { key ->
            snackbar.setAction(R.string.quick_panel_editor_undo) { undoApply(key) }
            // Legible on the bar itself, which is dark on a light theme and light on a dark one -
            // not on the page behind it. The layer is the one Material paints the bar with.
            val barColor = MaterialColors.layer(
                    snackbar.view,
                    com.google.android.material.R.attr.colorSurface,
                    com.google.android.material.R.attr.colorOnSurface,
                    SNACKBAR_OVERLAY_ALPHA)
            snackbar.setActionTextColor(LyraAccent.contrastSafe(
                    LyraAccent.resolve(requireContext()), barColor, minimumContrast = 4.5))
        }
        // Above the mini player and the navigation bar, not on top of them.
        activity?.findViewById<View>(R.id.bottom_container)?.let { snackbar.anchorView = it }
        snackbar.show()
    }

    private fun undoApply(key: String) {
        val context = context ?: return
        val name = QuickThemeSwitch.apply(context, defaultPrefs, key)
        if (name == null) {
            Toast.makeText(context, R.string.watch_theme_apply_error, Toast.LENGTH_LONG).show()
            return
        }
        showApplied(name, undoKey = null)
    }

    internal fun openThemeLibrary() {
        val context = context ?: return
        // Persist any edits made in the shared Watch editor before the manager reads its
        // phone-local library. Only the resulting Apply action is synchronized to Wear.
        themeRepository.captureActive(defaultPrefs)
        themeManagerLauncher.launch(Intent(context, WatchThemesActivity::class.java))
    }

    private fun openCommunityGallery() {
        (activity as? MainActivity)?.openCommunityGallery()
    }

    // ------------------------------------------------------------------ caption

    /** Reads the applied theme's name; called when the theme may have changed. */
    private fun refreshCaptionName() {
        if (_binding == null || !isAdded) return
        val prefs = defaultPrefs
        captionThemeName = when (val appearance = ThemeAppearance.resolve(prefs)) {
            is AppearanceContext.Custom ->
                themeRepository.activeProfile(prefs)?.name
                        ?: getString(R.string.watch_theme_unknown_name)
            is AppearanceContext.BuiltIn ->
                WatchThemeRepository.displayNameForFace(requireContext(), appearance.baseFace)
        }
        renderCaption()
    }

    /**
     * Names what is being edited under the preview: the theme, and the screen the preview has
     * switched to when that is not the player. The theme's name is on screen wherever something is
     * edited, because every edit lands in that theme and nothing else said so.
     */
    private fun renderCaption() {
        val caption = _binding?.watchPreviewCaption ?: return
        val name = captionThemeName
        val suffix = preview?.currentSurface?.let(PreviewCaption::suffixRes)?.let(::getString)
        if (suffix == null) {
            caption.text = name
            return
        }
        val joined = getString(R.string.watch_theme_custom_subtitle_authored, name, suffix)
        val start = joined.lastIndexOf(suffix)
        caption.text = SpannableString(joined).apply {
            if (start >= 0) {
                setSpan(
                        ForegroundColorSpan(ContextCompat.getColor(
                                requireContext(), R.color.lyra_text_secondary)),
                        start, start + suffix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    /**
     * Conditional rows remain searchable, but opening an invisible result would look broken.
     * Redirect to the choice that reveals it without changing any saved value on the user's behalf.
     */
    private fun resolveSearchTarget(section: String, key: String): SearchTarget {
        val rawPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val store = FaceScopedPreferenceDataStore(rawPrefs)
        val target = WatchSearchTargetResolver.resolve(
            section = section,
            key = key,
            readString = { preferenceKey, default ->
                store.getString(preferenceKey, default) ?: default
            },
            readBoolean = { preferenceKey, default ->
                store.getBoolean(preferenceKey, default)
            }
        )
        return SearchTarget(target.section, target.key, target.redirected)
    }

    /**
     * Tells the preview which panel the Panels editor has open.
     *
     * Its two page-wide controls sit above the tab rail and belong to no single surface, so the
     * preview cannot route them from the key alone. See `WatchPreviewView.setPanelEditorSurface`.
     */
    internal fun onPanelEditorTarget(section: String, surface: WatchPreviewView.PreviewSurface) {
        if (_binding == null || activeSection != section) return
        previews { it.setPanelEditorSurface(surface) }
    }

    /** Called only by the currently visible preference page. Candidate values let List/Switch
     *  choices paint before Android persists them; the shared-preference callback then replaces
     *  that transient state with the committed value. */
    internal fun onWatchPreferenceInteraction(
        section: String,
        key: String,
        candidateValue: Any?
    ) {
        if (_binding == null || activeSection != section) return
        selectedPreviewPreference = key
        // The source and the file it points at are two separate edits, and the miniature has to
        // answer both - so this runs for any interaction rather than being keyed to one row.
        refreshCustomBackdrop()
        previews { it.showPreference(key, candidateValue) }
    }

    /**
     * Opens the enlarged preview on exactly the surface the docked one is showing.
     *
     * The full-screen view starts from the same live preferences, so it only has to be told the
     * *context* the docked miniature is in — the section, and the specific preference being
     * edited if there is one, since that is what routes to a non-player surface. The live content
     * arrives immediately afterwards through the ordinary push paths, which now address both.
     */
    private fun openFullScreenPreview() {
        val activity = activity ?: return
        val docked = preview ?: return
        fullScreenPreview = WatchPreviewFullScreen.show(
                activity,
                onDismiss = { fullScreenPreview = null }
        ) { large ->
            large.showSection(activeSection)
            val focused = selectedPreviewPreference
            if (focused != null) {
                large.showPreference(focused)
            } else {
                large.showPreviewSurface(docked.currentSurface)
            }
        } ?: return
        // Re-run the live pushes so the enlarged copy starts with the current track and playback
        // rather than waiting for the next media callback, which may be a whole track away.
        pushNowPlaying(mediaController?.metadata)
        pushPlayback()
        loadMiniButtons()
    }

    /** Loads the device-local backdrop for whatever source the current face has, so the miniature
     *  is right on entry rather than only after the next preference touch. */
    private fun primeCustomBackdrop() {
        customBackdropKey = null
        refreshCustomBackdrop()
    }

    /** Exposes the preview's live album accent triple to the color-treatment picker dialog
     *  (see ColorTreatmentPreference/WatchFacePrefsFragment) so its swatches match what's on
     *  screen instead of re-extracting a possibly different palette. Falls back to a neutral gray
     *  if the preview hasn't been created yet - not expected in practice, since the prefs page is
     *  only shown alongside it. */
    internal fun currentAlbumAccents(): Triple<Int, Int, Int> =
            preview?.currentAlbumAccents() ?: Triple(NEUTRAL_ACCENT, NEUTRAL_ACCENT, NEUTRAL_ACCENT)

    private fun tintNavigation() {
        val activity = activity as? MainActivity ?: return
        activity.applyAccentToView(binding.watchTabs)
        binding.watchEditorHome.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.lyra_on_surface))
    }

    /** The preview stays fixed above/beside the pages, so in an area make horizontal drags over it
     * switch areas too. On Home there is nothing to switch between, and the drag does nothing. */
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
                    gestureResolved = place !is WatchTabPlace.Area
                }

                MotionEvent.ACTION_MOVE -> if (!gestureResolved) {
                    val deltaX = event.x - startX
                    val deltaY = event.y - startY
                    if (abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY)) {
                        val direction = if (deltaX < 0f) 1 else -1
                        val target = (binding.watchPager.currentItem + direction)
                            .coerceIn(SECTIONS.indices)
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
            min(min(rootWidth * 0.30f, rootHeight - dp(40).toFloat()), dp(240).toFloat())
        } else {
            min(
                min(rootWidth - dp(32).toFloat(), rootWidth * 0.48f),
                min(rootHeight * 0.30f, dp(200).toFloat())
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
        val art = customBackdrop
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        previews {
            it.setNowPlaying(
                    art,
                    metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                    metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            )
        }
        pushThumbnailArtwork(art)
        pushSourceGlyph()
    }

    /**
     * Gives the theme strip the cover now playing, redrawing its pictures only when the picture
     * really changed. A player republishes its metadata often, and every copy arrives as a new
     * bitmap of the same cover; redrawing every theme for each would be the most expensive way to
     * show nothing new.
     */
    private fun pushThumbnailArtwork(art: Bitmap?) {
        val previous = thumbnailArtwork
        val same = when {
            art === previous -> true
            art == null || previous == null -> false
            art.width != previous.width || art.height != previous.height -> false
            else -> runCatching { art.sameAs(previous) }.getOrDefault(false)
        }
        thumbnailArtwork = art
        if (same) return
        if (thumbnailer?.setArtwork(art) == true) looksAdapter?.redrawAll()
    }

    /**
     * The picture a device-local artwork source would put behind the player, once loaded.
     *
     * Substituted for the cover in the one place the miniature is handed artwork, which is the same
     * arrangement the watch has: `MusicService` resolves one backdrop and the faces draw it through
     * the ordinary pipeline, so every treatment, filter, shading layer and the album-accent palette
     * all follow the picture actually on screen. Doing it here rather than inside
     * `readPreferenceSnapshot` is what keeps a gallery decode off the synchronous path that runs on
     * every preference change.
     */
    private var customBackdrop: android.graphics.Bitmap? = null

    /** Which source and file the loaded [customBackdrop] belongs to, so an unchanged selection is
     *  not re-decoded on every preference touch. */
    private var customBackdropKey: String? = null

    /**
     * Loads the chosen picture when the selected source needs one, and drops it when it does not.
     *
     * Deliberately re-run on preference changes rather than only at start: choosing the source, and
     * then choosing the file, are two separate interactions, and the preview has to answer the
     * second one. A folder shows its first picture rather than a random draw - the miniature is a
     * still, and a background that changed every time a slider moved would read as a glitch instead
     * of as the feature.
     */
    private fun refreshCustomBackdrop() {
        val context = context ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val source = AlbumArtSource.fromPref(FaceScopedPreferences.getString(
                prefs, MiscPreferences.WEAR_ALBUM_ART_SOURCE, ThemeAppearance.resolve(prefs)))
        if (!source.isDeviceLocal) {
            if (customBackdrop != null || customBackdropKey != null) {
                customBackdrop = null
                customBackdropKey = null
                pushNowPlaying(mediaController?.metadata)
            }
            return
        }
        val stored = prefs.getString(
                if (source == AlbumArtSource.CUSTOM_FOLDER) {
                    MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key
                } else {
                    MiscPreferences.CUSTOM_ALBUM_ART_IMAGE.key
                }, "").orEmpty()
        val key = "${source.preferenceValue}|$stored"
        if (key == customBackdropKey) return
        customBackdropKey = key
        if (stored.isBlank()) {
            customBackdrop = null
            pushNowPlaying(mediaController?.metadata)
            return
        }
        val appContext = context.applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                CustomArtworkSource.previewArtwork(appContext, source)
            }
            if (_binding == null || key != customBackdropKey) return@launch
            customBackdrop = bytes?.let {
                SampledBitmaps.decode(it, OnlineArtworkFetcher.MAX_ARTWORK_PX)
            }
            pushNowPlaying(mediaController?.metadata)
        }
    }

    /**
     * The playing app's own mark, so the miniature shows the glyph the watch will actually draw
     * rather than a stand-in.
     *
     * [AppGlyphStore] is the same persistent store `MusicService` sends the watch from, and it is
     * consulted before the launcher icon for the reason recorded there: the notification small icon
     * is what the faces show. The store's bytes are a flat white template and must be reported as
     * tintable; a launcher icon is real artwork and must not be, which is why the two travel
     * together instead of being decided separately.
     */
    private fun pushSourceGlyph() {
        val context = context ?: return
        val packageName = mediaController?.packageName
        if (packageName == null) {
            showSourceGlyph(null, tintable = true)
            return
        }
        val template = AppGlyphStore.drawable(context, packageName)
        if (template != null) {
            showSourceGlyph(template.toBitmapOrNull(), tintable = true)
            return
        }
        val launcher = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        showSourceGlyph(launcher?.toBitmapOrNull(), tintable = false)
    }

    /** The mark on every preview, and on the pictures drawn by the same renderer. */
    private fun showSourceGlyph(glyph: Bitmap?, tintable: Boolean) {
        previews { it.setSourceGlyph(glyph, tintable) }
        if (thumbnailer?.setSourceGlyph(glyph, tintable) == true) looksAdapter?.redrawAll()
    }

    private fun Drawable.toBitmapOrNull(): Bitmap? {
        if (this is BitmapDrawable) return bitmap
        val width = intrinsicWidth.takeIf { it > 0 } ?: return null
        val height = intrinsicHeight.takeIf { it > 0 } ?: return null
        return try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                setBounds(0, 0, canvas.width, canvas.height)
                draw(canvas)
            }
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not rasterize the source glyph for the preview")
            null
        }
    }

    /** Pushes the current (interpolated) playback position/state into the preview - the state's
     *  position snapshot is anchored at lastPositionUpdateTime, so extrapolate while playing. */
    private fun pushPlayback() {
        val controller = mediaController
        val state = controller?.playbackState
        if (controller == null || state == null) {
            previews { it.setPlayback(null, -1, -1) }
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
        previews { it.setPlayback(playing, position, duration) }
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
        refreshCaptionName()
        refreshLooks()
        tintNavigation()
        loadMiniButtons()
        restartPlaybackTicker()
        // Re-primed rather than merely refreshed: the picture may have been re-picked, or the file
        // deleted, while another screen was in front of this one.
        primeCustomBackdrop()
    }

    override fun onStop() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        themeRepository.captureActive(prefs)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        unregisterMediaCallback()
        tickHandler.removeCallbacks(playbackTick)
        tickHandler.removeCallbacks(activeThumbnailRefresh)
        super.onStop()
    }

    override fun onDestroyView() {
        // The dialog holds a view that would outlive this fragment's view tree, and it is fed by
        // the ticker being torn down here, so it cannot be left standing.
        fullScreenPreview = null
        tickHandler.removeCallbacks(playbackTick)
        tickHandler.removeCallbacks(activeThumbnailRefresh)
        binding.root.removeOnLayoutChangeListener(previewLayoutListener)
        binding.watchPreviewPanel.setOnTouchListener(null)
        binding.watchPager.unregisterOnPageChangeCallback(pageCallback)
        tabMediator?.detach()
        tabMediator = null
        binding.watchPager.adapter = null
        pagerAttached = false
        looksList?.adapter = null
        looksList = null
        looksListState = null
        looksAdapter = null
        thumbnailer?.release()
        thumbnailer = null
        thumbnailArtwork = null
        miniButtonsLoadJob?.cancel()
        miniButtonsLoadJob = null
        unregisterMediaCallback()
        mediaController = null
        preview?.onSurfaceChanged = null
        preview = null
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PLACE, WatchTabNavigation.encode(place))
        outState.putString(STATE_PREVIEW_PREFERENCE, selectedPreviewPreference)
        outState.putBoolean(STATE_PAGER_ATTACHED, pagerAttached)
        super.onSaveInstanceState(outState)
    }

    private data class SearchTarget(
        val section: String?,
        val key: String?,
        val redirected: Boolean
    )
}
