package com.svartifoss.snfell.view.mainactivity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.Gravity
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.svartifoss.snfell.NotificationService
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.QueueArtworkResolver
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.databinding.ActivityMainBinding
import com.svartifoss.snfell.di.InjectableViewModelFactory
import com.svartifoss.snfell.music.isPlaying
import com.svartifoss.snfell.view.ActivityResultReceiver
import com.svartifoss.snfell.view.FabFragment
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.TitledActivity
import com.svartifoss.snfell.view.applyLyraDialogStyling
import com.svartifoss.snfell.view.actionlist.ActionListFragment
import com.svartifoss.snfell.view.buttonconfig.ControlsFragment
import com.svartifoss.snfell.view.settings.SettingsHomeFragment
import com.svartifoss.snfell.view.settings.SettingsSearchActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.svartifoss.snfell.view.watchface.WatchFaceFragment
import com.svartifoss.snfell.view.watchface.theme.OnlineThemesActivity
import android.graphics.Bitmap
import android.widget.SeekBar
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import androidx.lifecycle.lifecycleScope
import com.svartifoss.snfell.update.UpdateGateway
import kotlinx.coroutines.launch
import com.matejdro.wearutils.companionnotice.WearCompanionPhoneActivity
import com.matejdro.wearutils.preferences.definition.Preferences
import dagger.android.AndroidInjection
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import timber.log.Timber

private const val REQUEST_CODE_POST_NOTIFICATIONS = 1002
private const val REQUEST_CODE_SETTINGS_SEARCH = 1003

/** Phone-local UI state: whether the explaining notification-access dialog has been shown once.
 *  Deliberately not in MiscPreferences.EXPORTABLE - it describes this phone's prompt history, so
 *  it neither syncs to the watch nor survives into a config backup as a meaningful setting. */
private const val NOTIFICATION_ACCESS_PROMPTED_PREF = "notification_access_prompted"

private const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/gabrielsvafoss"
private const val KOFI_URL = "https://ko-fi.com/gabrielsvafoss"
private const val SVARTIFOSS_RELEASES_URL = "https://github.com/gabrielluizone/Svartifoss/releases"

class MainActivity : WearCompanionPhoneActivity(),
        TitledActivity, ActivityResultReceiver, HasAndroidInjector {

    private companion object {
        /** Fallback refresh for a player that moves the active entry without publishing a new
         *  PlaybackState. The controller callback normally beats this. */
        const val QUEUE_SELECTION_SETTLE_MS = 300L
    }


    private lateinit var binding: ActivityMainBinding

    private var currentFragment: Fragment? = null

    /**
     * The community gallery, opened from the toolbar rather than through the Watch tab.
     *
     * Started for a result for the one thing that route skips: the gallery installs and applies a
     * theme itself, and the Watch tab - which may be the very screen underneath - has its own
     * preference listener unregistered while another Activity is in front. So it is told directly
     * on the way back, exactly as the themes screen already tells it.
     */
    private val communityGalleryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            (currentFragment as? WatchFaceFragment)?.onThemeLibraryChanged()
        }
    }

    /** (tab, section, preference key) a settings-search result is waiting to land on. */
    private var pendingSearchTarget: Triple<String, String, String>? = null
    private var miniPlayerController: MediaController? = null

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = Runnable { updateMiniPlayerProgress() }

    private var mediaDetailsDialog: BottomSheetDialog? = null
    private var detailSeekBar: SeekBar? = null
    private var detailTimeElapsed: TextView? = null
    private var detailTimeTotal: TextView? = null
    private var detailTitle: TextView? = null
    private var detailArtist: TextView? = null
    private var detailPlayPause: ImageButton? = null
    private var detailAlbumArt: ImageView? = null
    private var detailQueueList: androidx.recyclerview.widget.RecyclerView? = null
    private var detailQueueEmpty: TextView? = null
    private var detailQueueHeader: TextView? = null
    private var queueAdapter: QueueAdapter? = null
    private var isSeeking = false
    // Holds the currently extracted dynamic accent color (null = use default lyra_accent)
    private var dynamicAccentColor: Int? = null
    private var paletteGeneration = 0
    private var lastPaletteArt: Bitmap? = null
    private var lastPaletteDescription: String? = null
    private var lastAppliedAccentColor: Int? = null

    @Inject
    lateinit var fragmentInjector: DispatchingAndroidInjector<Fragment>

    @Inject
    lateinit var viewModelFactory: InjectableViewModelFactory<MainActivityViewModel>

    private val viewmodel: MainActivityViewModel by viewModels { viewModelFactory }

    private val miniPlayerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMiniPlayerMetadata(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMiniPlayerPlayState(state)
            if (state?.isPlaying() == true) startProgressUpdates() else stopProgressUpdates()
        }
    }

    private val preferenceChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "dynamic_accent_color" || key == "desaturated_color" || key == "custom_accent_color") {
            updateMiniPlayerMetadata(miniPlayerController?.metadata)
        }
        if (key == MiscPreferences.MINI_PLAYER_ENABLED.key) {
            setMiniPlayerVisible(miniPlayerController != null && miniPlayerPreferenceEnabled())
        }
    }

    private fun miniPlayerPreferenceEnabled(): Boolean = Preferences.getBoolean(
            PreferenceManager.getDefaultSharedPreferences(this), MiscPreferences.MINI_PLAYER_ENABLED
    )

    fun onCustomAccentColorChanged(hex: String?) {
        val color = if (hex != null) {
            try { android.graphics.Color.parseColor(hex) } catch (e: Exception) { resolveDefaultAccent() }
        } else {
            resolveDefaultAccent()
        }
        dynamicAccentColor = if (hex != null) color else null
        applyAccentColor(color)
    }

    private fun resolveDefaultAccent(): Int {
        val customHex = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("custom_accent_color", null)
        return if (customHex != null) {
            try { android.graphics.Color.parseColor(customHex) } catch (e: Exception) {
                ContextCompat.getColor(this, R.color.lyra_accent)
            }
        } else {
            ContextCompat.getColor(this, R.color.lyra_accent)
        }
    }

    private val playFabRunnable = Runnable {
        // Whether something is actually playing, not the mini player's own visibility - those
        // used to be the same thing, but the mini player can now also be hidden by user
        // preference while a session is still active, and the Play FAB (which means "tap to
        // resume") must not appear in that case.
        val isFabFragment = currentFragment is FabFragment
        // The Watch tab is excluded outright. Its whole surface is the live preview plus the
        // appearance list, and a floating button parked over the bottom-right corner covers the
        // controls being edited - on a tab where "resume playback" is not what the user came for.
        val hidesPlayFab = isFabFragment || currentFragment is WatchFaceFragment
        binding.fabPlay.visibility =
                if (miniPlayerController == null && !hidesPlayFab) View.VISIBLE else View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        // Covers users who open the app without playing music (MusicService has the same
        // throttled check for the reverse case).
        lifecycleScope.launch {
            UpdateGateway.maybeCheckInBackground(this@MainActivity)
        }

        // First launch after an install that bumped the versionCode: show the same Updates
        // screen used everywhere else, now reporting "you're current" plus its release notes,
        // instead of leaving the update a silent, undiscoverable fact. (No-op on the Play build,
        // which has no in-app update screen.)
        if (UpdateGateway.consumePostUpdateWelcome(this)) {
            UpdateGateway.openUpdateScreen(this)
        }

        // Users updating from a build older than 3.0's per-face appearance scoping: their old
        // single global appearance value now bleeds into whichever per-face defaults it happens
        // to match, so recommend resetting watch appearance to actually land on the new defaults.
        com.svartifoss.snfell.view.watchface.FaceResetMigrationPrompt.maybeShow(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // DrawerLayout paints its own status-bar scrim in the top inset region, defaulting to the
        // theme's colorPrimaryDark (lyra_sage_dark, green). Opaque status bars used to hide it, but
        // targetSdk 35 forces edge-to-edge on Android 15 (statusBarColor is ignored), exposing the
        // green scrim. Match it to the app background so the status-bar area reads as one surface.
        binding.drawerLayout.setStatusBarBackground(R.color.lyra_background)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        alignToolbarTitleWithNavigationIcon()

        // Apply accent color as soon as each fragment's view is ready (commit() is async)
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                val color = dynamicAccentColor ?: resolveDefaultAccent()
                applyAccentColorToViewTree(v, color)
                if (f is androidx.preference.PreferenceFragmentCompat) {
                    f.listView?.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                        override fun onChildViewAttachedToWindow(view: View) {
                            val c = dynamicAccentColor ?: resolveDefaultAccent()
                            applyAccentColorToViewTree(view, c)
                        }
                        override fun onChildViewDetachedFromWindow(view: View) {}
                    })
                }
            }
        }, true)

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupNavDrawerHeader()

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.watch_face -> {
                    updateActivityTitle(getString(R.string.watch_face_header))
                    val target = consumeSearchTarget(SettingsSearchActivity.TAB_WATCH_FACE)
                    swapFragment(WatchFaceFragment.newInstance(target?.first, target?.second))
                }
                R.id.controls -> {
                    updateActivityTitle(getString(R.string.controls_header))
                    swapFragment(ControlsFragment())
                }
                R.id.actions_menu -> {
                    updateActivityTitle(getString(R.string.actions_menu))
                    swapFragment(ActionListFragment())
                }
                R.id.settings -> {
                    updateActivityTitle(getString(R.string.action_settings))
                    val target = consumeSearchTarget(SettingsSearchActivity.TAB_SETTINGS)
                    swapFragment(SettingsHomeFragment.newInstance(target?.first, target?.second))
                }
            }
            true
        }

        binding.fab.setOnClickListener {
            (currentFragment as? FabFragment)?.onFabClicked()
        }

        binding.miniPrev.setOnClickListener {
            miniPlayerController?.transportControls?.skipToPrevious()
        }
        binding.miniNext.setOnClickListener {
            miniPlayerController?.transportControls?.skipToNext()
        }
        binding.miniPlayPause.setOnClickListener {
            val controls = miniPlayerController?.transportControls ?: return@setOnClickListener
            if (miniPlayerController?.isPlaying() == true) controls.pause() else controls.play()
        }

        // Enable marquee for scrolling song titles
        binding.miniTitle.isSelected = true
        binding.miniArtist.isSelected = true

        // Click to expand song details
        binding.miniMetadataContainer.setOnClickListener {
            showMediaDetailsDialog()
        }

        // Listen for seeking in mini progress bar
        binding.miniProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
                stopProgressUpdates()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                val controller = miniPlayerController
                val duration = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
                if (duration > 0 && seekBar != null) {
                    val newPos = (seekBar.progress.toFloat() / 1000 * duration).toLong()
                    controller?.transportControls?.seekTo(newPos)
                }
                if (controller?.isPlaying() == true) {
                    startProgressUpdates()
                }
            }
        })

        viewmodel.activeMediaSessionProvider.observe(this) { resource ->
            val controller = resource?.data
            miniPlayerController?.unregisterCallback(miniPlayerCallback)
            miniPlayerController = controller
            controller?.registerCallback(miniPlayerCallback)

            if (controller != null) {
                updateMiniPlayerMetadata(controller.metadata)
                updateMiniPlayerPlayState(controller.playbackState)
                setMiniPlayerVisible(miniPlayerPreferenceEnabled())
            } else {
                setMiniPlayerVisible(false)
            }
        }

        // Play FAB click: resume the active session or send a system media key if none is bound yet
        binding.fabPlay.setOnClickListener {
            val controls = miniPlayerController?.transportControls
            if (controls != null) {
                controls.play()
            } else {
                val am = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
            }
        }

        updateCurrentFragment(supportFragmentManager.findFragmentById(R.id.fragment_container))

        // Default landing screen on a fresh launch (no fragment restored by the FragmentManager
        // yet). Deliberately independent of whether a watch is currently paired/connected -
        // Controls (like Watch, Actions and Settings) is fully usable without one;
        // only its "physical buttons" section needs live watch data, and that section explains
        // the disconnected state while the other inputs remain editable.
        if (currentFragment == null) {
            binding.bottomNav.selectedItemId = R.id.controls
            // selectedItemId normally invokes the listener above. Keep a defensive fallback for
            // restored view state where Controls was already marked selected before attachment.
            if (currentFragment == null) {
                swapFragment(ControlsFragment())
            }
        }

        binding.notificationAccessBanner.setOnClickListener { showNotificationAccessDialog() }

        maybeRequestNotificationPermission()
    }

    /** targetSdk 33+ (Android 13) gates notifications behind POST_NOTIFICATIONS. MusicService posts
     *  a persistent "control active" notification, so request it once to preserve the pre-33
     *  behavior of that notification simply showing. If the user denies it, the service still
     *  runs - only the notification is suppressed, same as a manual denial. */
    private fun maybeRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_POST_NOTIFICATIONS
            )
        }
    }

    /** The active phone media session, for fragments that want live now-playing data (the
     *  Watch tab's preview shows the actual current track/art through this). */
    fun activeMediaSession(): androidx.lifecycle.LiveData<com.matejdro.wearutils.lifecycle.Resource<MediaController>> =
            viewmodel.activeMediaSessionProvider

    /** The exact display profile reported by the connected watch. The Watch preview uses it to
     *  reproduce round/square geometry and the real dp canvas instead of assuming every device
     *  is a 192dp circle. */
    fun connectedWatchInfo(): androidx.lifecycle.LiveData<com.svartifoss.snfell.config.WatchInfoWithIcons?> =
            viewmodel.watchInfoProvider

    /** Used by contextual links such as "Swipe gestures" and "Mini buttons" so explanatory
     *  settings rows lead directly to the place where those controls are assigned. */
    fun openControls() {
        binding.bottomNav.selectedItemId = R.id.controls
    }

    /** The mirror of [openControls], used by the Controls screen's "Quick actions panel" row.
     *  Lands on the Actions tab, whose Quick panel entry is the first thing on it - deliberately
     *  the same shape as the links pointing the other way, rather than reaching across fragments
     *  to open the slot dialog directly. */
    fun openActionsMenu() {
        binding.bottomNav.selectedItemId = R.id.actions_menu
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_main, menu)
        return true
    }

    /** Shows a green "update available" toolbar action next to help while a checked release is
     *  newer than what's installed - a nudge for someone who dismissed or missed the one-shot
     *  update notification. Re-evaluated every time the menu is invalidated (see onResume), so it
     *  picks up both a fresh background check and an update just installed via the updater.
     *  Always hidden on the Play build ([UpdateGateway.hasPendingUpdate] is a no-op there). */
    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        menu.findItem(R.id.menu_update_available)?.isVisible = UpdateGateway.hasPendingUpdate(this)
        // A setting should be discoverable before the user knows whether it lives under Watch or
        // Settings. Keep search available from every primary tab; choosing a result performs the
        // cross-tab navigation and opens its owning section.
        menu.findItem(R.id.menu_search_settings)?.isVisible = true
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_search_settings -> {
                startActivityForResult(
                        SettingsSearchActivity.createIntent(this), REQUEST_CODE_SETTINGS_SEARCH)
                return true
            }
            R.id.menu_community_themes -> {
                communityGalleryLauncher.launch(
                        Intent(this, OnlineThemesActivity::class.java))
                return true
            }
            R.id.menu_help -> {
                startActivity(Intent(this, HelpActivity::class.java))
                return true
            }
            R.id.menu_update_available -> {
                UpdateGateway.openUpdateScreen(this)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        showNotificationServiceWarning()
        applyAccentColor(dynamicAccentColor ?: resolveDefaultAccent())
        invalidateOptionsMenu()
    }

    override fun onDestroy() {
        miniPlayerController?.unregisterCallback(miniPlayerCallback)
        stopProgressUpdates()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onDestroy()
    }

    private fun setMiniPlayerVisible(visible: Boolean) {
        binding.miniPlayer.visibility = if (visible) View.VISIBLE else View.GONE
        val navH = resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
        val miniH = if (visible) resources.getDimensionPixelSize(R.dimen.mini_player_height) else 0
        binding.fragmentContainer.setPadding(0, 0, 0, navH + miniH)

        val margin = navH + miniH + resources.getDimensionPixelSize(R.dimen.fab_margin)
        val fabParams = binding.fab.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
        fabParams.bottomMargin = margin
        binding.fab.layoutParams = fabParams

        val fabPlayParams = binding.fabPlay.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
        fabPlayParams.bottomMargin = margin
        binding.fabPlay.layoutParams = fabPlayParams

        updatePlayFabVisibility(null)

        if (visible) startProgressUpdates() else stopProgressUpdates()
    }

    private fun isDarkThemeActive(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "system")
        return when (theme) {
            "dark" -> true
            "light" -> false
            else -> {
                val nightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun adjustColorForContrast(color: Int, isDarkTheme: Boolean): Int {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceAtMost(0.65f) // Desaturate slightly
        if (isDarkTheme) {
            if (hsl[2] < 0.65f) {
                hsl[2] = 0.65f
            }
        } else {
            if (hsl[2] > 0.45f) {
                hsl[2] = 0.45f
            }
        }
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
    }

    private fun updateMiniPlayerMetadata(metadata: MediaMetadata?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "—"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""

        binding.miniTitle.text = title
        binding.miniArtist.text = artist

        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (art != null) {
            binding.miniAlbumArt.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            binding.miniAlbumArt.setImageBitmap(art)
            updateDynamicAccentFromArt(art, albumArtDescription(metadata))
        } else {
            binding.miniAlbumArt.scaleType = android.widget.ImageView.ScaleType.CENTER
            binding.miniAlbumArt.setImageResource(R.drawable.ic_music_note)
            updateDynamicAccentFromArt(null, null)
        }

        if (mediaDetailsDialog?.isShowing == true) {
            detailTitle?.text = title
            detailArtist?.text = artist
            if (art != null) {
                detailAlbumArt?.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                detailAlbumArt?.setImageBitmap(art)
            } else {
                detailAlbumArt?.scaleType = android.widget.ImageView.ScaleType.CENTER
                detailAlbumArt?.setImageResource(R.drawable.ic_music_note)
            }
            updateQueueList()
        }
    }

    private fun updatePlayFabVisibility(state: PlaybackState?) {
        progressHandler.removeCallbacks(playFabRunnable)
        progressHandler.postDelayed(playFabRunnable, 250)
    }

    private fun showPlayFab(show: Boolean) {
        // Obsolete: Handled by playFabRunnable debounce
    }

    private fun contrastIconColor(bgColor: Int): android.content.res.ColorStateList {
        // Dark theme + desaturated accents: adjustColorForContrast floors the accent's lightness
        // at 0.65, so every possible FAB background is light - but its luminance can still land
        // just under the 0.35 threshold below (saturated blues/reds), picking a WHITE icon on a
        // light pill. Skip the heuristic and always use the dark icon in that combination.
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (isDarkThemeActive() && prefs.getBoolean("desaturated_color", true)) {
            return android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
        }

        val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(bgColor)
        val iconColor = if (luminance > 0.35) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        return android.content.res.ColorStateList.valueOf(iconColor)
    }

    private fun applyAccentColor(color: Int) {
        // Persist the accent actually being displayed so standalone dialog activities
        // (action picker/editor) can match it via LyraAccent.resolve - the dynamic
        // album-art color exists nowhere else.
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getInt(LyraAccent.CURRENT_ACCENT_PREF, 0) != color) {
            prefs.edit().putInt(LyraAccent.CURRENT_ACCENT_PREF, color).apply()
        }

        val csl = android.content.res.ColorStateList.valueOf(color)
        val fabIconCsl = contrastIconColor(color)
        binding.miniProgress.progressTintList = csl
        binding.miniProgress.thumbTintList = csl
        binding.miniPlayPause.imageTintList = csl
        binding.fabPlay.backgroundTintList = csl
        binding.fabPlay.imageTintList = fabIconCsl
        binding.fab.backgroundTintList = csl
        binding.fab.imageTintList = fabIconCsl
        binding.notificationAccessBannerIcon.imageTintList = csl
        detailSeekBar?.progressTintList = csl
        detailSeekBar?.thumbTintList = csl
        detailPlayPause?.imageTintList = csl

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val colors = intArrayOf(
            accentTextColor(color),
            ContextCompat.getColor(this, R.color.lyra_nav_inactive)
        )
        val colorStateList = android.content.res.ColorStateList(states, colors)
        binding.bottomNav.itemIconTintList = colorStateList
        binding.bottomNav.itemTextColor = colorStateList

        // Apply to nav drawer header (logo + ABOUT label)
        val navHeader = binding.navView.getHeaderView(0)
        applyAccentColorToViewTree(navHeader, color)
        navHeader.findViewById<ImageView>(R.id.drawer_app_logo)?.imageTintList = csl

        if (mediaDetailsDialog?.isShowing == true) {
            updateQueueList()
        }

        applyAccentColorToFragmentView(currentFragment, color)
        lastAppliedAccentColor = color
    }

    private fun startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
        updateMiniPlayerProgress()
    }

    private fun stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
    }

    private fun updateMiniPlayerProgress() {
        val controller = miniPlayerController ?: return
        val state = controller.playbackState ?: return
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        if (duration > 0) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            val pos = (state.position + (elapsed * state.playbackSpeed).toLong()).coerceAtLeast(0L)
            val progressPercent = ((pos.toFloat() / duration) * 1000).toInt().coerceIn(0, 1000)

            if (!isSeeking) {
                binding.miniProgress.progress = progressPercent
            }

            // Update dialog progress if showing
            if (mediaDetailsDialog?.isShowing == true && !isSeeking) {
                detailSeekBar?.progress = progressPercent
                detailTimeElapsed?.text = formatTime(pos)
                detailTimeTotal?.text = formatTime(duration)
            }
        }
        if (controller.isPlaying()) {
            progressHandler.postDelayed(progressRunnable, 500)
        }

        // Some media apps publish metadata in two steps (text, then artwork moments later) without
        // firing another onMetadataChanged — poll while playing so the accent tracks new art.
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("dynamic_accent_color", true)) {
            val metadata = controller.metadata
            val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            updateDynamicAccentFromArt(art, albumArtDescription(metadata))
        }
    }

    private fun albumArtDescription(metadata: MediaMetadata?): String? {
        if (metadata == null) return null
        return metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
    }

    private fun updateDynamicAccentFromArt(art: Bitmap?, description: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("dynamic_accent_color", true)) {
            if (dynamicAccentColor != null) {
                dynamicAccentColor = null
                lastPaletteArt = null
                lastPaletteDescription = null
                applyAccentColor(resolveDefaultAccent())
            }
            return
        }

        if (art === lastPaletteArt && description == lastPaletteDescription && dynamicAccentColor != null) {
            return
        }
        lastPaletteArt = art
        lastPaletteDescription = description

        if (art == null) {
            dynamicAccentColor = null
            applyAccentColor(resolveDefaultAccent())
            return
        }

        val generation = ++paletteGeneration
        Palette.from(art).generate { palette ->
            if (generation != paletteGeneration || art !== lastPaletteArt) {
                return@generate
            }

            var extracted = extractAccentFromPalette(palette)
            if (prefs.getBoolean("desaturated_color", true)) {
                extracted = adjustColorForContrast(extracted, isDarkThemeActive())
            }

            dynamicAccentColor = extracted
            applyAccentColor(extracted)
        }
    }

    private fun extractAccentFromPalette(palette: Palette?): Int {
        if (palette == null) {
            return resolveDefaultAccent()
        }
        return listOf(
            palette.vibrantSwatch,
            palette.mutedSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch,
            palette.lightMutedSwatch,
            palette.darkMutedSwatch,
            palette.dominantSwatch
        ).firstNotNullOfOrNull { it?.rgb } ?: resolveDefaultAccent()
    }

    private fun updateMiniPlayerPlayState(state: PlaybackState?) {
        val playing = state?.isPlaying() == true
        val icon = if (playing) R.drawable.ic_nav_stopped else R.drawable.ic_nav_playing
        // The label has to move with the icon, not just accompany it: a button that reads
        // "Play" while showing a pause glyph is worse for a screen reader than an unlabelled
        // one. The details dialog carried the icon swap alone until it was given a label at all.
        val label = getString(if (playing) R.string.action_pause else R.string.action_play)
        binding.miniPlayPause.setImageResource(icon)
        binding.miniPlayPause.contentDescription = label
        updatePlayFabVisibility(state)

        // Update dialog play/pause button if showing
        if (mediaDetailsDialog?.isShowing == true) {
            detailPlayPause?.setImageResource(icon)
            detailPlayPause?.contentDescription = label
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    /**
     * Feeds the queue sheet.
     *
     * Builds the whole row list and hands it to the adapter, which diffs it - so a refresh while
     * the sheet is open (a track change, an accent change) redraws only what moved instead of
     * tearing the list down and rebuilding every row, which is what the hand-inflated column had
     * to do and what made a scroll position impossible to keep.
     */
    private fun updateQueueList() {
        val adapter = queueAdapter ?: return
        val list = detailQueueList ?: return
        val queue = miniPlayerController?.queue

        val empty = queue.isNullOrEmpty()
        detailQueueEmpty?.visibility = if (empty) View.VISIBLE else View.GONE
        list.visibility = if (empty) View.GONE else View.VISIBLE
        detailQueueHeader?.text = if (empty) {
            getString(R.string.queue_header)
        } else {
            // The count is the one thing the header can say that the list itself cannot: how much
            // is below the fold.
            resources.getQuantityString(
                    R.plurals.queue_header_count, queue!!.size, queue.size)
        }
        if (empty) {
            adapter.submitList(emptyList())
            return
        }

        val activeQueueId = miniPlayerController?.playbackState?.activeQueueItemId ?: -1L
        // Resolved once per rebuild rather than per row: the switch is one preference read, and a
        // toggle landing mid-build would otherwise give a half-remote list.
        adapter.setAllowRemoteArtwork(QueueArtworkResolver.remoteArtworkEnabled(this))
        adapter.accentColor = dynamicAccentColor ?: resolveDefaultAccent()

        val activeIndex = queue.indexOfFirst { it.queueId == activeQueueId }
        val rows = queue.mapIndexed { index, item ->
            QueueAdapter.Row(
                    item = item,
                    position = index + 1,
                    isPlaying = item.queueId == activeQueueId,
                    // Only dim when the playing entry is actually known: with no active id every
                    // row would compare as "past" and the whole list would fade out.
                    isPast = activeIndex >= 0 && index < activeIndex)
        }
        val hadRows = adapter.itemCount > 0
        adapter.submitList(rows) {
            // Open on the track being played rather than at the top, the way the watch queue does
            // - on anything longer than a screenful the playing entry is otherwise below the fold.
            // Only on the first fill, so a later refresh cannot yank a scrolled list back.
            if (!hadRows && activeIndex > 0) {
                // The row *before* the playing one goes to the top, so there is a line of context
                // above it rather than the playing track pinned flush against the edge. Expressed
                // as a position rather than a pixel offset, which would have to track row height.
                (list.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
                        ?.scrollToPositionWithOffset(activeIndex - 1, 0)
            }
        }
    }

    private fun showMediaDetailsDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_media_details, null)
        dialog.setContentView(dialogView)

        // Fix status bar color: ensure dialog window background matches app theme
        dialog.window?.apply {
            val bgColor = ContextCompat.getColor(this@MainActivity, R.color.lyra_background)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            statusBarColor = bgColor
        }

        mediaDetailsDialog = dialog

        detailAlbumArt = dialogView.findViewById(R.id.detail_album_art)
        detailTitle = dialogView.findViewById(R.id.detail_title)
        detailArtist = dialogView.findViewById(R.id.detail_artist)
        detailSeekBar = dialogView.findViewById(R.id.detail_seek_bar)
        detailTimeElapsed = dialogView.findViewById(R.id.detail_time_elapsed)
        detailTimeTotal = dialogView.findViewById(R.id.detail_time_total)
        detailPlayPause = dialogView.findViewById(R.id.detail_play_pause)
        detailQueueList = dialogView.findViewById(R.id.detail_queue_list)
        detailQueueEmpty = dialogView.findViewById(R.id.detail_queue_empty)
        detailQueueHeader = dialogView.findViewById(R.id.queue_header)
        val adapter = QueueAdapter(lifecycleScope) { item ->
            miniPlayerController?.transportControls?.skipToQueueItem(item.queueId)
            // The controller reports the move through onPlaybackStateChanged, which already
            // refreshes this list; the delayed pass is the fallback for a player that changes the
            // active item without publishing a new state.
            progressHandler.postDelayed({ updateQueueList() }, QUEUE_SELECTION_SETTLE_MS)
        }
        queueAdapter = adapter
        detailQueueList?.layoutManager =
                androidx.recyclerview.widget.LinearLayoutManager(this)
        detailQueueList?.adapter = adapter
        // Nothing in this list animates on its own; the default change animation made a row blink
        // white every time the playing entry moved.
        detailQueueList?.itemAnimator = null

        detailTitle?.isSelected = true
        detailArtist?.isSelected = true

        detailAlbumArt?.setOnClickListener {
            val art = miniPlayerController?.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: miniPlayerController?.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            if (art != null) showAlbumArtFullscreen(art)
        }

        val prevBtn = dialogView.findViewById<ImageButton>(R.id.detail_prev)
        val nextBtn = dialogView.findViewById<ImageButton>(R.id.detail_next)

        prevBtn.setOnClickListener {
            miniPlayerController?.transportControls?.skipToPrevious()
        }
        nextBtn.setOnClickListener {
            miniPlayerController?.transportControls?.skipToNext()
        }
        detailPlayPause?.setOnClickListener {
            val controls = miniPlayerController?.transportControls ?: return@setOnClickListener
            if (miniPlayerController?.isPlaying() == true) controls.pause() else controls.play()
        }

        detailSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                val controller = miniPlayerController
                val duration = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
                if (duration > 0 && seekBar != null) {
                    val newPos = (seekBar.progress.toFloat() / 1000 * duration).toLong()
                    controller?.transportControls?.seekTo(newPos)
                }
            }
        })

        dialog.setOnDismissListener {
            mediaDetailsDialog = null
            detailAlbumArt = null
            detailTitle = null
            detailArtist = null
            detailSeekBar = null
            detailTimeElapsed = null
            detailTimeTotal = null
            detailPlayPause = null
            detailQueueList?.adapter = null
            detailQueueList = null
            detailQueueEmpty = null
            detailQueueHeader = null
            queueAdapter = null
        }

        // Show first so views are attached to the window
        dialog.show()

        // Fix sheet size: make the sheet non-draggable, stay wrap_content, and set to STATE_EXPANDED
        // so it opens fully at its natural compact height and cannot be dragged to the top.
        val bottomSheetView = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheetView?.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        if (bottomSheetView != null) {
            val behavior = BottomSheetBehavior.from(bottomSheetView)
            behavior.skipCollapsed = true
            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        // Then populate with current data (views are now ready)
        val metadata = miniPlayerController?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "\u2014"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        detailTitle?.text = title
        detailTitle?.isSelected = true
        detailArtist?.text = artist
        detailArtist?.isSelected = true

        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (art != null) {
            detailAlbumArt?.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            detailAlbumArt?.setImageBitmap(art)
        } else {
            detailAlbumArt?.scaleType = android.widget.ImageView.ScaleType.CENTER
            detailAlbumArt?.setImageResource(R.drawable.ic_music_note)
        }

        // Apply current dynamic color to the dialog SeekBar and play button
        val accentColor = dynamicAccentColor ?: resolveDefaultAccent()
        val accentCsl = android.content.res.ColorStateList.valueOf(accentColor)
        detailSeekBar?.progressTintList = accentCsl
        detailSeekBar?.thumbTintList = accentCsl
        detailPlayPause?.imageTintList = accentCsl

        updateMiniPlayerPlayState(miniPlayerController?.playbackState)
        updateMiniPlayerProgress()
        updateQueueList()
    }

    private fun showAlbumArtFullscreen(bitmap: android.graphics.Bitmap) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = android.widget.ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val downloadBtn = android.widget.Button(this).apply {
            text = getString(R.string.download_album_art)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(48, 24, 48, 24)
        }
        downloadBtn.setOnClickListener { saveAlbumArtToGallery(bitmap) }

        val root = android.widget.FrameLayout(this)
        root.addView(imageView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val btnLp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
        ).also { it.bottomMargin = 64 }
        root.addView(downloadBtn, btnLp)

        root.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(root)
        dialog.show()
    }

    private fun saveAlbumArtToGallery(bitmap: android.graphics.Bitmap) {
        val title = miniPlayerController?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") ?: "album_art"
        val filename = "${title}_${System.currentTimeMillis()}.jpg"

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/WearMusicCenter")
                }
                val uri = contentResolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    android.widget.Toast.makeText(this, R.string.album_art_saved, android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val path = android.provider.MediaStore.Images.Media.insertImage(
                    contentResolver, bitmap, title, null)
                if (path != null) {
                    android.widget.Toast.makeText(this, R.string.album_art_saved, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, R.string.album_art_save_error, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavDrawerHeader() {
        val header = binding.navView.getHeaderView(0)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        header.findViewById<TextView>(R.id.drawer_version_text)?.let { versionText ->
            versionText.text = getString(R.string.drawer_version_format, versionName)
        }

        // LyraGestureButton sets iconTint=@null so screens that hand-tint an action icon (the
        // gesture/action pickers) aren't fought by a style default - but this row never tints its
        // own icon, so ic_buy_me_a_coffee's plain white fill was showing through unmodified,
        // invisible on the light theme's surface. Same trap as the Watch tab's contextual editors.
        header.findViewById<MaterialButton>(R.id.drawer_support_button)?.apply {
            iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.lyra_on_surface))
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BUY_ME_A_COFFEE_URL)))
            }
        }

        // ic_kofi is deliberately left untinted - it's a real, multi-colour brand mark (see its
        // own comment), not a template glyph.
        header.findViewById<View>(R.id.drawer_kofi_button)?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)))
        }
    }

    private fun swapFragment(newFragment: Fragment) {
        supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, newFragment)
                .commitNow()
        updateCurrentFragment(newFragment)
    }

    private fun updateCurrentFragment(newFragment: Fragment?) {
        currentFragment = newFragment
        if (newFragment == null) return

        if (newFragment is FabFragment) {
            binding.fab.let {
                it.show()
                newFragment.prepareFab(it)
            }
        } else {
            binding.fab.hide()
        }

        // Keep play FAB visibility updated based on fragment changes
        updatePlayFabVisibility(miniPlayerController?.playbackState)
        // Accent color is applied via FragmentLifecycleCallbacks.onFragmentViewCreated
    }

    private fun applyAccentColorToFragmentView(fragment: Fragment?, color: Int) {
        val view = fragment?.view ?: return
        applyAccentColorToViewTree(view, color)

        if (fragment is androidx.preference.PreferenceFragmentCompat) {
            val recyclerView = fragment.listView ?: return
            for (i in 0 until recyclerView.childCount) {
                applyAccentColorToViewTree(recyclerView.getChildAt(i), color)
            }
            if (recyclerView.getTag(R.id.tag_accent_listener_attached) != true) {
                recyclerView.setTag(R.id.tag_accent_listener_attached, true)
                recyclerView.addOnChildAttachStateChangeListener(
                    object : androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener {
                        override fun onChildViewAttachedToWindow(child: View) {
                            val accent = dynamicAccentColor ?: resolveDefaultAccent()
                            applyAccentColorToViewTree(child, accent)
                        }

                        override fun onChildViewDetachedFromWindow(child: View) {}
                    }
                )
            }
        }
    }

    fun applyAccentToView(view: View) {
        val color = dynamicAccentColor ?: resolveDefaultAccent()
        applyAccentColorToViewTree(view, color)
    }

    /** The accent actually on screen right now - the live dynamic (album-art) color when one
     *  is active, else custom/default. Unlike LyraAccent.resolve this never returns a stale
     *  persisted value, so in-activity UI (settings dialogs, the accent dot) should prefer it. */
    fun currentAccentColor(): Int = dynamicAccentColor ?: resolveDefaultAccent()

    /**
     * Accent adapted for small labels drawn directly on the app background. Album artwork can
     * yield almost any color; using it unmodified for selected tab/navigation text made some
     * combinations unreadable even though the decorative indicator itself still looked fine.
     */
    fun currentAccentTextColor(): Int = accentTextColor(currentAccentColor())

    private fun accentTextColor(accent: Int): Int {
        val background = ContextCompat.getColor(this, R.color.lyra_background)
        val opaqueAccent = ColorUtils.setAlphaComponent(accent, 255)
        if (ColorUtils.calculateContrast(opaqueAccent, background) >= 4.5) {
            return opaqueAccent
        }

        val target = if (ColorUtils.calculateLuminance(background) > 0.5) {
            android.graphics.Color.BLACK
        } else {
            android.graphics.Color.WHITE
        }
        var low = 0f
        var high = 1f
        repeat(12) {
            val amount = (low + high) / 2f
            val candidate = ColorUtils.blendARGB(opaqueAccent, target, amount)
            if (ColorUtils.calculateContrast(candidate, background) >= 4.5) {
                high = amount
            } else {
                low = amount
            }
        }
        return ColorUtils.blendARGB(opaqueAccent, target, high)
    }

    private fun applyAccentColorToViewTree(view: View, color: Int) {
        // Complex controls with stateful foreground/background pairs must recolor themselves as a
        // unit. Traversing their children would flatten those state lists to one accent color and
        // can produce accent text on an accent fill (the compact Watch Text editor is one such
        // surface). The owning view refreshes itself whenever the runtime accent changes.
        if (view.getTag(R.id.tag_handles_accent_locally) == true) return

        if (view is TabLayout) {
            view.setSelectedTabIndicatorColor(color)
            val inactive = ContextCompat.getColor(this, R.color.lyra_text_secondary)
            val selected = accentTextColor(color)
            val currentColors = view.tabTextColors
            val currentSelected = currentColors?.getColorForState(
                intArrayOf(android.R.attr.state_selected),
                currentColors.defaultColor
            )

            // Material rebuilds/re-measures every tab label whenever setTabTextColors is called,
            // even when both colors are unchanged. Avoiding that redundant update removes the
            // brief vertical jump that used to happen as a page became selected.
            if (currentColors?.defaultColor != inactive || currentSelected != selected) {
                view.setTabTextColors(inactive, selected)
            }
            // TabLayout owns internal TextViews whose implementation is not part of its public
            // contract. Tint the component once and do not recolor those children with the raw
            // album accent below.
            return
        }

        val staticAccent = ContextCompat.getColor(this, R.color.lyra_accent)
        val previousAccent = lastAppliedAccentColor
        val csl = android.content.res.ColorStateList.valueOf(color)
        val readableAccent = accentTextColor(color)
        val readableCsl = android.content.res.ColorStateList.valueOf(readableAccent)
        // Disabled state must come first: ColorStateList picks the first array entry whose
        // state spec matches, and a disabled-but-checked switch matches both "disabled" and
        // "checked" specs - it needs to hit the disabled (grayed out) one, not the checked
        // (accent) one, or a switch turned off by a dependency stays full-color forever.
        val switchStates = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        if (view is SwitchCompat) {
            val disabledThumb = ContextCompat.getColor(this, R.color.lyra_divider)
            val disabledTrack = ContextCompat.getColor(this, R.color.lyra_divider)
            view.thumbTintList = android.content.res.ColorStateList(
                switchStates,
                intArrayOf(
                    disabledThumb,
                    color,
                    ContextCompat.getColor(this, R.color.lyra_stone)
                )
            )
            view.trackTintList = android.content.res.ColorStateList(
                switchStates,
                intArrayOf(
                    ColorUtils.setAlphaComponent(disabledTrack, 0x60),
                    ColorUtils.setAlphaComponent(color, 0x80),
                    ContextCompat.getColor(this, R.color.lyra_divider)
                )
            )
            view.jumpDrawablesToCurrentState()
        } else if (view is android.widget.CompoundButton) {
            view.buttonTintList = csl
        } else if (view is android.widget.SeekBar) {
            view.progressTintList = csl
            view.thumbTintList = csl
        } else if (view is com.google.android.material.floatingactionbutton.FloatingActionButton) {
            view.backgroundTintList = csl
        } else if (view is com.google.android.material.button.MaterialButton) {
            // Stroke/background were previously overwritten unconditionally on every
            // MaterialButton, regardless of whether that button's style actually wanted an
            // accent-colored stroke or fill. That turned e.g. LyraGestureButton's neutral gray
            // divider stroke into a stray pink outline, and silently solidified a TextButton's
            // near-transparent ripple-tint background into an opaque accent-colored block
            // (hiding its own text, since the text got recolored to the same accent). Both are
            // now gated the same way text color already was: only follow the accent if the
            // button's OWN stroke/background was already tracking it.
            if (view.getTag(R.id.tag_uses_accent_stroke) == true ||
                usesAccentTint(view.strokeColor?.defaultColor, staticAccent, previousAccent)) {
                view.strokeColor = csl
                view.setTag(R.id.tag_uses_accent_stroke, true)
            }
            // Icon tint follows the accent only when it was accent to begin with (e.g. the
            // palette icon on "Change icon") - unconditionally tinting used to recolor every
            // action icon on the gesture buttons. Ripple is deliberately untouched: press
            // highlights are theme-neutral now (see colorControlHighlight in styles.xml), and
            // the old unconditional opaque-accent ripple was part of the mismatched-color mess.
            if (view.getTag(R.id.tag_uses_accent_icon) == true ||
                usesAccentTint(view.iconTint?.defaultColor, staticAccent, previousAccent)) {
                view.iconTint = readableCsl
                view.setTag(R.id.tag_uses_accent_icon, true)
            }
            if (view.getTag(R.id.tag_uses_accent) == true ||
                usesAccentTint(view.currentTextColor, staticAccent, previousAccent)) {
                view.setTextColor(readableAccent)
                view.setTag(R.id.tag_uses_accent, true)
            }
            // Uses its own tag, never the text-color one above: those used to share a single
            // tag, so a button with accent-colored TEXT (extremely common, e.g. the dialog's OK
            // button) would flag the tag as "true" once, and from then on this background block
            // would also fire for that same view even though its background was never meant to
            // track the accent at all.
            val originalBackgroundAlpha = view.backgroundTintList?.defaultColor
                ?.let { android.graphics.Color.alpha(it) } ?: 0
            // A translucent/near-transparent tint (a TextButton/OutlinedButton's ripple-mask
            // color, typically) is never a genuine solid fill - leave it alone entirely rather
            // than guessing at how to retint it. Only a clearly opaque fill (a real filled CTA
            // button) is eligible to follow the accent.
            if (originalBackgroundAlpha > 200 &&
                (view.getTag(R.id.tag_uses_accent_background) == true ||
                    usesAccentTint(view.backgroundTintList?.defaultColor, staticAccent, previousAccent))) {
                view.backgroundTintList = csl
                view.setTag(R.id.tag_uses_accent_background, true)
            }
        } else if (view is android.widget.Button) {
            if (view.getTag(R.id.tag_uses_accent) == true ||
                usesAccentTint(view.currentTextColor, staticAccent, previousAccent)) {
                view.setTextColor(readableAccent)
                view.setTag(R.id.tag_uses_accent, true)
            }
        } else if (view is ImageView) {
            if (view.getTag(R.id.tag_uses_accent) == true ||
                usesAccentTint(view.imageTintList?.defaultColor, staticAccent, previousAccent)) {
                view.imageTintList = readableCsl
                view.setTag(R.id.tag_uses_accent, true)
            }
        } else if (view is android.widget.EditText) {
            // Selection UI (highlight, cursor, teardrop handles) and the focused underline all come
            // from the theme's colorControlActivated (static sage), resolved once at inflation, and
            // can never follow a runtime accent - so tint them explicitly. Shared with the
            // standalone dialog activities via LyraAccent. Handled before the TextView branch below
            // (EditText is a TextView).
            LyraAccent.applyToEditText(view, color)
        } else if (view is android.widget.TextView) {
            if (view.getTag(R.id.tag_uses_accent) == true ||
                usesAccentTint(view.currentTextColor, staticAccent, previousAccent)) {
                view.setTextColor(readableAccent)
                view.setTag(R.id.tag_uses_accent, true)
            }
        }

        // Plain View with solid accent background (e.g. active queue indicator bar)
        // Plain decorative Views only (e.g. the active-queue indicator bar). Buttons/labels/icons
        // are TextView/ImageView subclasses and MUST be excluded: they set the shared
        // tag_uses_accent for their TEXT/image tint above, and this block reading that same tag
        // used to then flood their BACKGROUND with opaque accent too - that was the "solid block
        // hiding its own text" bug on the dialog's OK / Change-icon buttons. It also gets its own
        // background tag now, so no text-tracking view can ever opt it in by accident.
        if (view !is android.view.ViewGroup &&
            view !is android.widget.TextView &&
            view !is ImageView) {
            if (view.getTag(R.id.tag_uses_accent_background) == true ||
                usesAccentTint(view.backgroundTintList?.defaultColor, staticAccent, previousAccent) ||
                usesAccentTint(
                    (view.background as? android.graphics.drawable.ColorDrawable)?.color,
                    staticAccent,
                    previousAccent
                )) {
                view.backgroundTintList = csl
                view.setTag(R.id.tag_uses_accent_background, true)
            }
        }

        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                applyAccentColorToViewTree(view.getChildAt(i), color)
            }
        }
    }

    private fun usesAccentTint(color: Int?, staticAccent: Int, previousAccent: Int?): Boolean {
        if (color == null) return false
        return color == staticAccent || color == previousAccent
    }

    /**
     * Surfaces the missing notification-access permission, which the app cannot read any media
     * session without. The explaining dialog interrupts **once** per install - it is worth one
     * interruption because nothing in the app works until it is granted - and from then on the
     * state lives in the persistent banner instead. Re-showing the modal on every [onResume] (the
     * previous behavior) taxed exactly the user who had already read it and chosen to grant it
     * later, since the dialog is dismissible and the condition therefore stays true.
     *
     * Both paths open the same dialog, so the explanation of *why* the permission is needed is
     * never more than one tap away.
     */
    private fun showNotificationServiceWarning() {
        refreshNotificationAccessBanner()
        if (NotificationService.isEnabled(this)) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(NOTIFICATION_ACCESS_PROMPTED_PREF, false)) return
        prefs.edit().putBoolean(NOTIFICATION_ACCESS_PROMPTED_PREF, true).apply()

        showNotificationAccessDialog()
    }

    /** Visible exactly while the permission is missing. Driven from [onResume], so returning from
     *  the system settings screen with it granted clears the banner with no further interaction. */
    private fun refreshNotificationAccessBanner() {
        binding.notificationAccessBanner.visibility =
                if (NotificationService.isEnabled(this)) View.GONE else View.VISIBLE
    }

    private fun showNotificationAccessDialog() {
        AlertDialog.Builder(this)
                .setTitle(getString(R.string.error_service_not_enabled))
                .setNegativeButton(android.R.string.cancel, null)
                .setMessage(getString(R.string.error_service_not_enabled_description))
                .setPositiveButton(getString(R.string.action_open_settings)) { _, _ ->
                    openNotificationListener()
                }
                .show()
                .applyLyraDialogStyling(accent = currentAccentColor())
    }

    private fun openNotificationListener() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (e: ActivityNotFoundException) {
            AlertDialog.Builder(this)
                    .setTitle(R.string.error_service_not_enabled)
                    .setMessage(getString(R.string.error_no_notification_service_support))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                    .applyLyraDialogStyling(accent = currentAccentColor())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SETTINGS_SEARCH) {
            if (resultCode == RESULT_OK && data != null) openSearchResult(data)
            return
        }
        currentFragment?.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Navigation half of the settings search: the search screen returns which tab, section and
     * preference key was chosen, and this puts the user on it.
     *
     * The target is stashed rather than passed, because switching tabs goes through the bottom
     * navigation listener, which is what constructs the fragment - so the listener picks it up on
     * its way past. When the wanted tab is already selected, setting `selectedItemId` to it fires
     * nothing at all, so that case swaps the fragment directly instead of silently doing nothing.
     */
    private fun openSearchResult(data: Intent) {
        val tab = data.getStringExtra(SettingsSearchActivity.EXTRA_TARGET_TAB) ?: return
        val section = data.getStringExtra(SettingsSearchActivity.EXTRA_TARGET_SECTION) ?: return
        val key = data.getStringExtra(SettingsSearchActivity.EXTRA_TARGET_KEY) ?: return
        pendingSearchTarget = Triple(tab, section, key)

        val targetItem = when (tab) {
            SettingsSearchActivity.TAB_WATCH_FACE -> R.id.watch_face
            else -> R.id.settings
        }
        if (binding.bottomNav.selectedItemId == targetItem) {
            val target = consumeSearchTarget(tab)
            swapFragment(when (tab) {
                SettingsSearchActivity.TAB_WATCH_FACE ->
                    WatchFaceFragment.newInstance(target?.first, target?.second)
                else -> SettingsHomeFragment.newInstance(target?.first, target?.second)
            })
        } else {
            binding.bottomNav.selectedItemId = targetItem
        }
    }

    /** Section + preference key a pending search result wants on [tab], consumed on read. Any
     *  pending target is dropped on the first navigation regardless of tab, so a result the user
     *  navigated away from cannot resurface later. */
    private fun consumeSearchTarget(tab: String): Pair<String, String>? {
        val target = pendingSearchTarget?.takeIf { it.first == tab }
        pendingSearchTarget = null
        return target?.let { it.second to it.third }
    }

    override fun updateActivityTitle(newTitle: String) {
        supportActionBar?.title = newTitle
        alignToolbarTitleWithNavigationIcon()
    }

    private fun alignToolbarTitleWithNavigationIcon() {
        binding.toolbar.post {
            val titleTextView = binding.toolbar.findViewById<TextView>(
                androidx.appcompat.R.id.action_bar_title
            ) ?: return@post
            titleTextView.gravity = Gravity.CENTER_VERTICAL
            titleTextView.includeFontPadding = false
            val params = titleTextView.layoutParams as? androidx.appcompat.widget.Toolbar.LayoutParams
            if (params != null) {
                params.gravity = Gravity.CENTER_VERTICAL
                titleTextView.layoutParams = params
            }
        }
    }

    override fun getWatchAppPresenceCapability(): String = CommPaths.WATCH_APP_CAPABILITY

    // The base implementation remote-opens a market:// URI on the WATCH - a dead end now that
    // the app isn't on the Play Store (this used to be the "dummy Google Play page" users hit).
    // There's also no useful action a watch browser could take anyway: installing the watch APK
    // is a phone-side Wear Installer sideload, not something triggered from a watch web link. So
    // this opens the GitHub releases page locally on the phone instead, mirroring the fix already
    // applied to the watch's equivalent "phone app missing" notice (which opens GitHub, not the
    // Play Store, via PhoneAppNoticeActivity in wearutils).
    override fun openWatchPlayStorePage() {
        val releasesIntent = Intent(Intent.ACTION_VIEW, Uri.parse(SVARTIFOSS_RELEASES_URL))
        try {
            startActivity(releasesIntent)
        } catch (e: Exception) {
            Timber.e(e, "Activity start crash")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun androidInjector(): AndroidInjector<Any> {
        return fragmentInjector as AndroidInjector<Any>
    }
}
