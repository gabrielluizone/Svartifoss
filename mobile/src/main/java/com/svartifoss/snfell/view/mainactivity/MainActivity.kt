package com.svartifoss.snfell.view.mainactivity

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.svartifoss.snfell.NotificationService
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.config.WatchInfoWithIcons
import com.svartifoss.snfell.databinding.ActivityMainBinding
import com.svartifoss.snfell.di.InjectableViewModelFactory
import com.svartifoss.snfell.music.isPlaying
import com.svartifoss.snfell.view.ActivityResultReceiver
import com.svartifoss.snfell.view.FabFragment
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.TitledActivity
import com.svartifoss.snfell.view.actionlist.ActionListFragment
import com.svartifoss.snfell.view.buttonconfig.ButtonConfigFragment
import com.svartifoss.snfell.view.settings.MiscSettingsFragment
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
import com.matejdro.wearutils.companionnotice.WearCompanionPhoneActivity
import dagger.android.AndroidInjection
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

private const val REQUEST_CODE_POST_NOTIFICATIONS = 1002

class MainActivity : WearCompanionPhoneActivity(),
        TitledActivity, ActivityResultReceiver, HasAndroidInjector {

    private lateinit var binding: ActivityMainBinding

    private var currentFragment: Fragment? = null
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
    private var detailQueueContainer: LinearLayout? = null
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
    }

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
        val isMiniPlayerVisible = binding.miniPlayer.visibility == View.VISIBLE
        val isFabFragment = currentFragment is FabFragment
        binding.fabPlay.visibility = if (!isMiniPlayerVisible && !isFabFragment) View.VISIBLE else View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        viewmodel.watchInfoProvider.observe(this, watchInfoObserver)

        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        }, false)

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupNavDrawerHeader()

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tutorial -> {
                    updateActivityTitle(getString(R.string.tutorial_header))
                    swapFragment(TutorialFragment())
                }
                R.id.playing_controls -> {
                    updateActivityTitle(getString(R.string.playing_controls))
                    swapFragment(ButtonConfigFragment.newInstance(true))
                }
                R.id.stopped_controls -> {
                    updateActivityTitle(getString(R.string.stopped_controls))
                    swapFragment(ButtonConfigFragment.newInstance(false))
                }
                R.id.actions_menu -> {
                    updateActivityTitle(getString(R.string.actions_menu))
                    swapFragment(ActionListFragment())
                }
                R.id.settings -> {
                    updateActivityTitle(getString(R.string.action_settings))
                    swapFragment(MiscSettingsFragment())
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
                setMiniPlayerVisible(true)
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

    override fun onResume() {
        super.onResume()
        showNotificationServiceWarning()
        applyAccentColor(dynamicAccentColor ?: resolveDefaultAccent())
    }

    override fun onDestroy() {
        miniPlayerController?.unregisterCallback(miniPlayerCallback)
        stopProgressUpdates()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onDestroy()
    }

    private fun setBottomNavVisible(visible: Boolean) {
        binding.bottomNav.visibility = if (visible) View.VISIBLE else View.GONE
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
        if (isDarkThemeActive() && prefs.getBoolean("desaturated_color", false)) {
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
        detailSeekBar?.progressTintList = csl
        detailSeekBar?.thumbTintList = csl
        detailPlayPause?.imageTintList = csl

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val colors = intArrayOf(
            color,
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
        if (prefs.getBoolean("dynamic_accent_color", false)) {
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
        if (!prefs.getBoolean("dynamic_accent_color", false)) {
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
            if (prefs.getBoolean("desaturated_color", false)) {
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
        binding.miniPlayPause.setImageResource(
            if (playing) R.drawable.ic_nav_stopped else R.drawable.ic_nav_playing
        )
        binding.miniPlayPause.contentDescription = getString(
            if (playing) R.string.action_pause else R.string.action_play
        )
        updatePlayFabVisibility(state)

        // Update dialog play/pause button if showing
        if (mediaDetailsDialog?.isShowing == true) {
            detailPlayPause?.setImageResource(
                if (playing) R.drawable.ic_nav_stopped else R.drawable.ic_nav_playing
            )
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun updateQueueList() {
        val container = detailQueueContainer ?: return
        container.removeAllViews()

        val queue = miniPlayerController?.queue
        if (queue.isNullOrEmpty()) {
            val noQueueTv = TextView(this).apply {
                text = getString(R.string.queue_unavailable)
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.lyra_text_secondary))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 24)
            }
            container.addView(noQueueTv)
            return
        }

        // Get currently active queue item id from playback state
        val activeQueueId = miniPlayerController?.playbackState?.activeQueueItemId ?: -1L
        val accentColor = dynamicAccentColor ?: resolveDefaultAccent()

        val inflater = layoutInflater
        for (item in queue) {
            val itemView = inflater.inflate(R.layout.item_queue_song, container, false)
            val titleTv = itemView.findViewById<TextView>(R.id.queue_item_title)
            val artistTv = itemView.findViewById<TextView>(R.id.queue_item_artist)
            val activeBar = itemView.findViewById<android.view.View>(R.id.queue_item_active_bar)
            val playingIcon = itemView.findViewById<ImageView>(R.id.queue_item_playing_icon)

            titleTv.text = item.description.title ?: "—"
            artistTv.text = item.description.subtitle ?: ""

            val isActive = item.queueId == activeQueueId
            if (isActive) {
                activeBar.visibility = android.view.View.VISIBLE
                playingIcon.visibility = android.view.View.VISIBLE
                titleTv.setTextColor(accentColor)
                titleTv.typeface = android.graphics.Typeface.DEFAULT_BOLD
                playingIcon.imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
                activeBar.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
                
                val activeBg = ContextCompat.getDrawable(this, R.drawable.queue_item_active_bg)?.mutate()
                if (activeBg != null) {
                    val alphaColor = androidx.core.graphics.ColorUtils.setAlphaComponent(accentColor, 38) // ~15% opacity
                    DrawableCompat.setTint(activeBg, alphaColor)
                    itemView.background = activeBg
                }
            } else {
                activeBar.visibility = android.view.View.INVISIBLE
                playingIcon.visibility = android.view.View.GONE
                itemView.background = ContextCompat.getDrawable(this, android.R.color.transparent)
                titleTv.setTextColor(ContextCompat.getColor(this, R.color.lyra_on_surface))
                titleTv.typeface = android.graphics.Typeface.DEFAULT
            }

            itemView.setOnClickListener {
                miniPlayerController?.transportControls?.skipToQueueItem(item.queueId)
                // Refresh highlights after skip
                progressHandler.postDelayed({ updateQueueList() }, 300)
            }

            container.addView(itemView)
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
        detailQueueContainer = dialogView.findViewById(R.id.detail_queue_container)

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
            detailQueueContainer = null
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

    private val watchInfoObserver = Observer<WatchInfoWithIcons?> {
        if (it != null) {
            setBottomNavVisible(true)
            if (currentFragment == null || currentFragment is NoWatchFragment) {
                swapFragment(ButtonConfigFragment.newInstance(true))
                binding.bottomNav.selectedItemId = R.id.playing_controls
            }
        } else {
            setBottomNavVisible(false)
            swapFragment(NoWatchFragment())
        }
    }

    private fun setupNavDrawerHeader() {
        val header = binding.navView.getHeaderView(0)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        header.findViewById<TextView>(R.id.drawer_version_text)?.text =
            getString(R.string.drawer_version_format, versionName)
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

    private fun applyAccentColorToViewTree(view: View, color: Int) {
        val staticAccent = ContextCompat.getColor(this, R.color.lyra_accent)
        val previousAccent = lastAppliedAccentColor
        val csl = android.content.res.ColorStateList.valueOf(color)
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
            if (usesAccentTint(view.iconTint?.defaultColor, staticAccent, previousAccent)) {
                view.iconTint = csl
            }
            if (view.getTag(R.id.tag_uses_accent) == true ||
                usesAccentTint(view.currentTextColor, staticAccent, previousAccent)) {
                view.setTextColor(color)
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
                view.setTextColor(color)
                view.setTag(R.id.tag_uses_accent, true)
            }
        } else if (view is ImageView) {
            if (view.getTag(R.id.tag_uses_accent) == true ||
                usesAccentTint(view.imageTintList?.defaultColor, staticAccent, previousAccent)) {
                view.imageTintList = csl
                view.setTag(R.id.tag_uses_accent, true)
            }
        } else if (view is android.widget.EditText) {
            // Selection UI (highlight, cursor, teardrop handles) and the focused underline all
            // come from the theme's colorControlActivated, resolved once at inflation - they can
            // never follow a runtime accent and stayed default-sage under any custom accent.
            // Must be handled before the TextView branch below (EditText is a TextView).
            view.highlightColor = ColorUtils.setAlphaComponent(color, 0x55)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                view.textCursorDrawable = view.textCursorDrawable?.mutate()?.apply { setTint(color) }
                // Explicit setter calls: the handle getters are @Nullable but the setters are
                // @NonNull, so Kotlin exposes these as read-only properties, not vars.
                view.textSelectHandle?.mutate()?.apply { setTint(color) }?.let { view.setTextSelectHandle(it) }
                view.textSelectHandleLeft?.mutate()?.apply { setTint(color) }?.let { view.setTextSelectHandleLeft(it) }
                view.textSelectHandleRight?.mutate()?.apply { setTint(color) }?.let { view.setTextSelectHandleRight(it) }
            }
            view.backgroundTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(color, ContextCompat.getColor(this, R.color.lyra_divider))
            )
        } else if (view is android.widget.TextView) {
            if (view.getTag(R.id.tag_uses_accent) == true ||
                usesAccentTint(view.currentTextColor, staticAccent, previousAccent)) {
                view.setTextColor(color)
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

    private fun showNotificationServiceWarning() {
        if (NotificationService.isEnabled(this)) return

        AlertDialog.Builder(this)
                .setTitle(getString(R.string.error_service_not_enabled))
                .setNegativeButton(android.R.string.cancel, null)
                .setMessage(getString(R.string.error_service_not_enabled_description))
                .setPositiveButton(getString(R.string.action_open_settings)) { _, _ ->
                    openNotificationListener()
                }
                .show()
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
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        currentFragment?.onActivityResult(requestCode, resultCode, data)
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

    // Base WearCompanionPhoneActivity implementation (no override needed): offers to open this
    // app's Play Store listing directly on the watch (mobile and wear share the same
    // applicationId) when the wear app isn't detected as installed yet.

    @Suppress("UNCHECKED_CAST")
    override fun androidInjector(): AndroidInjector<Any> {
        return fragmentInjector as AndroidInjector<Any>
    }
}
