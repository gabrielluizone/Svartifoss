package com.svartifoss.snfell.view.queue

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.common.R as commonR
import com.svartifoss.snfell.common.TrackTimeFormat
import com.svartifoss.snfell.music.ActiveMediaSessionProvider
import com.svartifoss.snfell.music.ActiveQueueRow
import com.svartifoss.snfell.music.QueueArtworkResolver
import com.svartifoss.snfell.music.QueueJump
import com.svartifoss.snfell.notifications.AppGlyphStore
import com.svartifoss.snfell.notifications.MediaActionSemantics
import com.svartifoss.snfell.notifications.MediaNotificationActions
import com.svartifoss.snfell.notifications.PlayerNotificationButton
import com.svartifoss.snfell.notifications.inferMediaActionSemantic
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.applyLyraDialogStyling
import com.svartifoss.snfell.view.mainactivity.QueueAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The window the persistent notification's **Queue** button opens: the track playing, with the
 * player's own controls, above the playing app's queue.
 *
 * It has the shape of the old theme window: a transparent activity in a task of its own (see the
 * manifest) holding one dialog, so the queue can be read from the shade over whatever app is in
 * front, and closing it leaves the user exactly there.
 *
 * - The top is the playing track: cover, title, artist, the app it comes from (the glyph its media
 *   notification carries, as the shade shows it), a seek bar, and below them **the player's own
 *   buttons** - the row of its media notification, in its order, with its icons, each sending its
 *   own PendingIntent ([MediaNotificationActions.playerButtonsForSession]). Buttons that only look
 *   like the player's would be a second, weaker set of controls; these are the player's. Without a
 *   media notification (no notification access) it falls back to previous / play / next.
 * - The play button is always a light tone of the cover's colour with a dark glyph, the Material
 *   Expressive arrangement, so it reads the same on any cover. A glow of the same colour sits in
 *   the corner behind the title.
 * - Below, "Up next" lists the tracks after the playing one; "Played" brings the earlier ones back
 *   above it. The search field filters the whole queue by title or artist.
 * - The rows are the mini player's queue sheet ([QueueAdapter]). The queue is read the way
 *   [com.svartifoss.snfell.music.MusicService] reads it for the watch, the playing row is found by
 *   [ActiveQueueRow], and a tap jumps through [QueueJump], the watch's route.
 *
 * Not exported: only the notification's PendingIntent starts it.
 */
class PlaybackQueueActivity : AppCompatActivity() {

    private var dialog: AlertDialog? = null
    private lateinit var views: Views
    private lateinit var adapter: QueueAdapter

    private val sessions by lazy { ActiveMediaSessionProvider(applicationContext) }
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The session the provider reports (playing, or the paused one it kept). */
    private var playing: MediaController? = null

    /** The session whose queue is shown: [playing], or a sibling of it in the same app. */
    private var queueOwner: MediaController? = null

    /** The cover's colour as extracted, and the same kept legible on the dialog's background. */
    private var coverColor: Int? = null
    private var accent = Color.WHITE

    private var showPlayed = false
    private var query = ""
    private var seeking = false

    /** The list was scrolled to the playing row for this arrangement (first fill, or a toggle). */
    private var scrolledFor: String? = null

    private var shownCover: Bitmap? = null
    private var paletteJob: Job? = null
    private var controlsSignature: Any? = null
    private var sourceSignature: Any? = null

    private val callback = object : MediaController.Callback() {
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    /** The player's notification changed (a like toggled, play became pause). */
    private val playerRowChanged: () -> Unit = { mainHandler.post { refresh() } }

    private var started = false

    /**
     * Moves the seek bar every frame while the track plays, so it glides instead of stepping, and
     * stops asking for frames while it is paused (the next state change draws it once).
     */
    private val frame = object : Runnable {
        override fun run() {
            if (!started) return
            refreshProgress()
            if (playing?.playbackState?.state == PlaybackState.STATE_PLAYING) {
                views.seek.postOnAnimation(this)
            }
        }
    }

    private fun startFrames() {
        views.seek.removeCallbacks(frame)
        if (started) views.seek.postOnAnimation(frame)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Always dark, on pure black: a player over other apps, drawn as AMOLED screens like it,
        // whatever the app's own theme. Set before the window exists, so its colours resolve dark.
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        super.onCreate(savedInstanceState)

        val content = layoutInflater.inflate(R.layout.dialog_playback_queue, null)
        views = Views(content)
        views.title.isSelected = true
        // The XML attribute is ignored before Android 12, which left the cover square.
        views.cover.clipToOutline = true
        accent = LyraAccent.contrastSafe(this, LyraAccent.resolve(this))

        adapter = QueueAdapter(lifecycleScope) { item -> jumpTo(item) }
        views.list.layoutManager = LinearLayoutManager(this)
        views.list.adapter = adapter
        // Nothing here animates on its own; the default change animation blinks the moved rows.
        views.list.itemAnimator = null

        wireControls()
        applyAccent()

        val shown = AlertDialog.Builder(this)
                .setView(content)
                .create()
        shown.setOnDismissListener { finish() }
        dialog = shown
        shown.show()
        shown.applyLyraDialogStyling()
        shown.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = DIALOG_CORNER_DP * resources.displayMetrics.density
        })

        MediaNotificationActions.addPlayerRowListener(playerRowChanged)
        sessions.observe(this) { resource -> bind(resource?.data) }
    }

    override fun onStart() {
        super.onStart()
        started = true
        startFrames()
    }

    override fun onStop() {
        started = false
        views.seek.removeCallbacks(frame)
        super.onStop()
    }

    override fun onDestroy() {
        MediaNotificationActions.removePlayerRowListener(playerRowChanged)
        mainHandler.removeCallbacksAndMessages(null)
        unregister()
        views.list.adapter = null
        // A dialog still on screen when the activity goes (a rotation recreates it) would leak its
        // window; the recreated activity shows a fresh one.
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    private fun wireControls() {
        views.playedToggle.setOnClickListener {
            showPlayed = !showPlayed
            scrolledFor = null
            refresh()
        }
        views.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) views.elapsed.text = TrackTimeFormat.format(positionFor(progress))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seeking = false
                val controller = playing ?: return
                if (durationMs(controller) > 0 && seekBar != null) {
                    controller.transportControls.seekTo(positionFor(seekBar.progress))
                }
            }
        })
        views.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                scrolledFor = null
                refresh()
            }
        })
    }

    private fun bind(reported: MediaController?) {
        // Paused music is still the music this window is about.
        val controller = reported ?: sessions.mostRecentSession()
        if (controller != null && controller.sessionToken == playing?.sessionToken) {
            refresh()
            return
        }
        unregister()
        playing = controller
        controller?.registerCallback(callback, mainHandler)
        refresh()
    }

    private fun unregister() {
        playing?.unregisterCallback(callback)
        queueOwner?.takeIf { it !== playing }?.unregisterCallback(callback)
        queueOwner = null
    }

    private fun refresh() {
        val controller = playing
        refreshNowPlaying(controller)
        refreshSource(controller)
        refreshControls(controller)
        refreshProgress()
        startFrames()
        refreshQueue(controller)
    }

    private fun refreshNowPlaying(controller: MediaController?) {
        val metadata = controller?.metadata
        // Written only when they change: setting a TextView's text, even to the same words,
        // restarts its marquee, and every pause, like or position update came through here.
        setIfChanged(views.title, metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: getString(R.string.queue_unavailable))
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        setIfChanged(views.artist, artist.orEmpty())
        views.artist.visibility = if (artist.isNullOrBlank()) View.GONE else View.VISIBLE

        val cover = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.description?.iconBitmap
        if (cover !== shownCover) {
            shownCover = cover
            if (cover != null) {
                views.cover.scaleType = ImageView.ScaleType.CENTER_CROP
                views.cover.imageTintList = null
                views.cover.setImageBitmap(cover)
                takeColourFrom(cover)
            } else {
                views.cover.scaleType = ImageView.ScaleType.CENTER
                views.cover.setImageResource(R.drawable.ic_music_note)
                views.cover.imageTintList = ColorStateList.valueOf(secondaryText())
            }
        }
    }

    /**
     * The app the music comes from, drawn as its media notification draws it: the notification's
     * small icon, the glyph the shade and status bar show, tinted like the text beside it. The
     * remembered glyph ([AppGlyphStore]) stands in before the notification is read, and the
     * launcher icon only when neither exists.
     */
    private fun refreshSource(controller: MediaController?) {
        val pkg = controller?.packageName
        val glyphPng = controller?.let {
            MediaNotificationActions.smallIconForSession(it.packageName, it.sessionToken)
        }
        val signature = pkg to glyphPng?.contentHashCode()
        if (signature == sourceSignature) return
        sourceSignature = signature

        val info = pkg?.let {
            try {
                packageManager.getApplicationInfo(it, 0)
            } catch (e: Exception) {
                null
            }
        }
        if (pkg == null || info == null) {
            views.source.visibility = View.GONE
            return
        }
        val glyph: Drawable? = glyphPng?.let { decode(it) }?.let { BitmapDrawable(resources, it) }
                ?: AppGlyphStore.drawable(this, pkg)
        if (glyph != null) {
            views.sourceIcon.setImageDrawable(glyph)
            views.sourceIcon.imageTintList = ColorStateList.valueOf(secondaryText())
        } else {
            views.sourceIcon.setImageDrawable(packageManager.getApplicationIcon(info))
            views.sourceIcon.imageTintList = null
        }
        views.sourceName.text = packageManager.getApplicationLabel(info)
        views.source.visibility = View.VISIBLE
    }

    // --- Colour ------------------------------------------------------------------------------

    private fun takeColourFrom(cover: Bitmap) {
        paletteJob?.cancel()
        paletteJob = lifecycleScope.launch {
            val picked = withContext(Dispatchers.Default) {
                val palette = Palette.from(cover).maximumColorCount(16).generate()
                (palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.mutedSwatch
                        ?: palette.dominantSwatch)?.rgb
            } ?: return@launch
            coverColor = picked
            accent = LyraAccent.contrastSafe(this@PlaybackQueueActivity, picked)
            applyAccent()
        }
    }

    private fun applyAccent() {
        val tint = ColorStateList.valueOf(accent)
        views.seek.progressTintList = tint
        views.seek.thumbTintList = tint
        views.section.setTextColor(accent)
        // The song's title wears the play button's fill: the same light tone of the cover's hue.
        views.title.setTextColor(playColours().first)
        adapter.accentColor = accent
        views.root.background = cornerGlow()
        controlsSignature = null
        refreshControls(playing)
    }

    /**
     * The window's black, with a soft pool of the cover's colour in the top corner behind the
     * title: a radial gradient cut to the dialog's own rounded corners, so it never spills past them.
     */
    private fun cornerGlow(): Drawable {
        val density = resources.displayMetrics.density
        val colour = coverColor ?: accent
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = DIALOG_CORNER_DP * density
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(GLOW_CENTER_X, GLOW_CENTER_Y)
            gradientRadius = GLOW_RADIUS_DP * density
            // Opaque stops over black: the window behind is black too, and an opaque gradient
            // ending in black leaves no seam where the glow fades out.
            colors = intArrayOf(
                    ColorUtils.compositeColors(ColorUtils.setAlphaComponent(colour, GLOW_ALPHA), Color.BLACK),
                    ColorUtils.compositeColors(ColorUtils.setAlphaComponent(colour, GLOW_ALPHA / 3), Color.BLACK),
                    Color.BLACK)
        }
    }

    /**
     * The play button's fill and glyph: always a light tone with a dark glyph, the arrangement
     * Material Expressive uses, so the button reads the same whatever the cover. A cover with no
     * colour to speak of (black, white, grey) gets a neutral pair rather than its noise hue.
     */
    private fun playColours(): Pair<Int, Int> {
        val colour = coverColor ?: accent
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colour, hsl)
        return if (hsl[1] < QueueWindowRules.NEUTRAL_SATURATION) {
            ColorUtils.HSLToColor(floatArrayOf(0f, 0f, QueueWindowRules.CONTAINER_LIGHTNESS)) to
                    ColorUtils.HSLToColor(floatArrayOf(0f, 0f, QueueWindowRules.GLYPH_LIGHTNESS))
        } else {
            PaletteTransforms.sameHueTone(colour, QueueWindowRules.CONTAINER_LIGHTNESS) to
                    PaletteTransforms.sameHueTone(colour, QueueWindowRules.GLYPH_LIGHTNESS)
        }
    }

    private fun secondaryText() = ContextCompat.getColor(this, R.color.lyra_text_secondary)

    private fun setIfChanged(view: TextView, text: CharSequence) {
        if (view.text.toString() != text.toString()) view.text = text
    }

    // --- The player's buttons ----------------------------------------------------------------

    /** One button of the row, wherever it came from. */
    private class Control(
            val label: String,
            val icon: Drawable,
            val semantic: String,
            val primary: Boolean,
            val press: () -> Unit
    )

    /**
     * The player's buttons: previous / play / next from its media notification, and beside them
     * the extras [QueueWindowRules.placeExtras] chooses from that notification and from the
     * session's custom actions. Both are the player's own (its icons, its commands). The session is
     * read too because a player may draw its notification with its own layout, whose buttons no
     * other app can see: Retro Music shows shuffle there, but offers it to other apps only as a
     * session action.
     */
    private fun refreshControls(controller: MediaController?) {
        if (!::views.isInitialized) return
        val buttons = controller?.let {
            MediaNotificationActions.playerButtonsForSession(
                    it.packageName, it.sessionToken, sessions.isOnlySessionOfPackage(it.packageName))
        }.orEmpty()
        val custom = controller?.playbackState?.customActions.orEmpty()
        val isPlaying = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
        val signature = listOf(
                buttons.map { Triple(it.label, it.semantic, it.iconPng?.contentHashCode()) },
                custom.map { Triple(it.action, it.name?.toString(), it.icon) },
                isPlaying,
                controller != null,
                accent,
                coverColor)
        if (signature == controlsSignature) return
        controlsSignature = signature

        views.controls.removeAllViews()
        if (controller == null) return

        val notificationTransport = buttons.filter { it.semantic in TRANSPORT_ORDER }
                .sortedBy { TRANSPORT_ORDER.indexOf(it.semantic) }
                .mapNotNull { notificationControl(it, controller) }
        val transport = if (notificationTransport.any { it.primary }) {
            notificationTransport
        } else {
            fallbackTransport(controller, isPlaying)
        }
        val placement = QueueWindowRules.placeExtras(
                fromPlayer = buttons.filter { it.semantic !in TRANSPORT_ORDER }
                        .mapNotNull { notificationControl(it, controller) }
                        .map { QueueWindowRules.Extra(it, it.semantic) },
                fromSession = custom.mapNotNull { sessionControl(it, controller) }
                        .map { QueueWindowRules.Extra(it, it.semantic) })
        val row = placement.left + transport + placement.right

        // The play button sits in the middle whatever surrounds it: the side with fewer buttons
        // gets empty places of a button's size.
        val centre = row.indexOfFirst { it.primary }
        val imbalance = if (centre < 0) 0 else centre - (row.size - 1 - centre)
        repeat((-imbalance).coerceAtLeast(0)) { addSpacer() }
        row.forEach { addControl(it.icon, it.label, it.primary, it.semantic, it.press) }
        repeat(imbalance.coerceAtLeast(0)) { addSpacer() }
    }

    /** An empty place the size of a button, so the play button stays centred. */
    private fun addSpacer() {
        val density = resources.displayMetrics.density
        val size = (SECONDARY_DP * density).toInt()
        val margin = (CONTROL_GAP_DP * density).toInt()
        views.controls.addView(View(this), LinearLayout.LayoutParams(size, size).apply {
            marginStart = margin
            marginEnd = margin
        })
    }

    /** One of the notification's buttons, as the player draws it, doing what the player's does. */
    private fun notificationControl(button: PlayerNotificationButton, controller: MediaController): Control? {
        val icon = button.iconPng?.let { decode(it) }?.let { BitmapDrawable(resources, trimmed(it)) }
                ?: fallbackIcon(button.semantic)
                ?: return null
        val primary = button.semantic == MediaActionSemantics.PLAY ||
                button.semantic == MediaActionSemantics.PAUSE
        return Control(button.label, icon, button.semantic, primary) {
            if (!button.press()) {
                // The player withdrew the action (its notification moved on): do the same thing
                // through its session rather than nothing.
                pressThroughSession(button.semantic, controller)
            }
            settleSoon()
        }
    }

    /**
     * One of the session's custom actions, with the player's own icon rendered from its resources
     * (as the watch's quick panel does) and sent as the player's own command.
     */
    private fun sessionControl(action: PlaybackState.CustomAction, controller: MediaController): Control? {
        val label = action.name?.toString().orEmpty()
        val semantic = inferMediaActionSemantic(label, action.action)
        val png = MediaNotificationActions.loadRemoteActionIcon(this, controller.packageName, action.icon)
        val icon = png?.let { decode(it) }?.let { BitmapDrawable(resources, trimmed(it)) } ?: return null
        return Control(label, icon, semantic, primary = false) {
            controller.transportControls.sendCustomAction(action, action.extras)
            settleSoon()
        }
    }

    private fun fallbackTransport(controller: MediaController, isPlaying: Boolean): List<Control> =
            listOfNotNull(
                    drawable(R.drawable.ic_mini_prev)?.let {
                        Control(getString(R.string.action_skip_prev), it,
                                MediaActionSemantics.PREVIOUS, false) {
                            controller.transportControls.skipToPrevious()
                        }
                    },
                    drawable(if (isPlaying) commonR.drawable.action_pause_filled else commonR.drawable.action_play_filled)?.let {
                        Control(getString(if (isPlaying) R.string.action_pause else R.string.action_play), it,
                                if (isPlaying) MediaActionSemantics.PAUSE else MediaActionSemantics.PLAY, true) {
                            if (isPlaying) controller.transportControls.pause() else controller.transportControls.play()
                            settleSoon()
                        }
                    },
                    drawable(R.drawable.ic_mini_next)?.let {
                        Control(getString(R.string.action_skip_next), it,
                                MediaActionSemantics.NEXT, false) {
                            controller.transportControls.skipToNext()
                        }
                    })

    private fun pressThroughSession(semantic: String, controller: MediaController) {
        val controls = controller.transportControls
        when (semantic) {
            MediaActionSemantics.PLAY -> controls.play()
            MediaActionSemantics.PAUSE -> controls.pause()
            MediaActionSemantics.NEXT -> controls.skipToNext()
            MediaActionSemantics.PREVIOUS -> controls.skipToPrevious()
        }
    }

    /** Our own glyph for an action whose icon could not be read, where we have one. */
    private fun fallbackIcon(semantic: String): Drawable? = when (semantic) {
        MediaActionSemantics.PLAY -> drawable(commonR.drawable.action_play_filled)
        MediaActionSemantics.PAUSE -> drawable(commonR.drawable.action_pause_filled)
        MediaActionSemantics.NEXT -> drawable(R.drawable.ic_mini_next)
        MediaActionSemantics.PREVIOUS -> drawable(R.drawable.ic_mini_prev)
        else -> null
    }

    private fun addControl(
            icon: Drawable?,
            label: String,
            primary: Boolean,
            semantic: String = MediaActionSemantics.UNKNOWN,
            onPress: () -> Unit
    ) {
        icon ?: return
        val density = resources.displayMetrics.density
        val size = ((if (primary) PRIMARY_DP else SECONDARY_DP) * density).toInt()
        val button = ImageButton(this).apply {
            setImageDrawable(icon.mutate())
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = label
            // The glyph area is the button less this padding: the same for every player's icon.
            val padding = (size - (if (primary) PRIMARY_GLYPH_DP else SECONDARY_GLYPH_DP) * density).toInt() / 2
            // A triangle centred by its box looks shifted left, so play is nudged right by a
            // fraction of its width, the optical centring players apply to it.
            val nudge = if (semantic == MediaActionSemantics.PLAY) (PLAY_NUDGE_DP * density).toInt() else 0
            setPadding(padding + nudge, padding, padding - nudge, padding)
            if (primary) {
                val (fill, glyph) = playColours()
                setBackgroundResource(R.drawable.circle_icon_bg)
                backgroundTintList = ColorStateList.valueOf(fill)
                imageTintList = ColorStateList.valueOf(glyph)
            } else {
                setBackgroundResource(selectableBorderless())
                imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(this@PlaybackQueueActivity, R.color.lyra_on_surface))
            }
            setOnClickListener { onPress() }
        }
        val margin = (CONTROL_GAP_DP * density).toInt()
        views.controls.addView(button, LinearLayout.LayoutParams(size, size).apply {
            marginStart = margin
            marginEnd = margin
        })
    }

    /** The player answers a press through its session and then its notification; look again. */
    private fun settleSoon() {
        mainHandler.postDelayed({ refresh() }, SETTLE_SHORT_MS)
        mainHandler.postDelayed({ refresh() }, SETTLE_LONG_MS)
    }

    private fun drawable(id: Int): Drawable? = ContextCompat.getDrawable(this, id)

    /**
     * [icon] cut to the pixels it actually draws. A player's glyph arrives on its own canvas with
     * whatever margin that canvas has, and not always an even one, so it was drawn off the centre
     * of its button and at a size that depended on the player. Cut to its ink, every glyph is
     * fitted into the same area and sits exactly on the button's centre.
     */
    private fun trimmed(icon: Bitmap): Bitmap {
        val width = icon.width
        val height = icon.height
        val row = IntArray(width)
        var left = width
        var right = -1
        var top = height
        var bottom = -1
        for (y in 0 until height) {
            icon.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                if ((row[x] ushr 24) > VISIBLE_ALPHA) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        if (right < left || bottom < top) return icon
        return Bitmap.createBitmap(icon, left, top, right - left + 1, bottom - top + 1)
    }

    private fun decode(png: ByteArray): Bitmap? = try {
        BitmapFactory.decodeByteArray(png, 0, png.size)
    } catch (e: RuntimeException) {
        null
    }

    private var cachedBorderless: Int? = null

    private fun selectableBorderless(): Int {
        cachedBorderless?.let { return it }
        val typed = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typed, true)
        return typed.resourceId.also { cachedBorderless = it }
    }

    // --- Progress ---------------------------------------------------------------------------

    private fun durationMs(controller: MediaController): Long =
            controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

    /** The position now, extrapolated from the session's last sample the way the watch does. */
    private fun livePositionMs(controller: MediaController): Long {
        val state = controller.playbackState ?: return 0L
        var position = state.position
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) *
                    state.playbackSpeed).toLong()
        }
        val duration = durationMs(controller)
        return if (duration > 0) position.coerceIn(0L, duration) else position.coerceAtLeast(0L)
    }

    private fun positionFor(progress: Int): Long {
        val duration = playing?.let(::durationMs) ?: 0L
        return duration * progress / SEEK_MAX
    }

    private fun refreshProgress() {
        if (seeking) return
        val controller = playing
        val duration = controller?.let(::durationMs) ?: 0L
        val position = controller?.let(::livePositionMs) ?: 0L
        val known = duration > 0
        if (views.seek.isEnabled != known) views.seek.isEnabled = known
        views.seek.progress = if (known) (position * SEEK_MAX / duration).toInt() else 0
        // The texts only change once a second; writing them every frame would relayout the row.
        setIfChanged(views.elapsed, TrackTimeFormat.format(position))
        setIfChanged(views.duration, if (known) TrackTimeFormat.format(duration) else "")
    }

    // --- The queue ---------------------------------------------------------------------------

    private fun refreshQueue(controller: MediaController?) {
        val ownQueue = controller?.queue?.takeIf { it.isNotEmpty() }
        val source = when {
            controller == null -> null
            ownQueue != null -> controller to ownQueue
            else -> sessions.siblingQueueSourceForPackage(controller.packageName, controller)
                    ?.let { it.controller to it.items }
        }
        val owner = source?.first
        if (owner?.sessionToken != queueOwner?.sessionToken) {
            queueOwner?.takeIf { it !== playing }?.unregisterCallback(callback)
            queueOwner = owner
            owner?.takeIf { it.sessionToken != controller?.sessionToken }
                    ?.registerCallback(callback, mainHandler)
        }

        val queue = source?.second.orEmpty()
        val activeIndex = ActiveQueueRow.index(
                queueIds = queue.map { it.queueId },
                titles = queue.map { it.description.title?.toString() },
                activeQueueId = controller?.playbackState?.activeQueueItemId,
                playingTitle = controller?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
        val allRows = queue.mapIndexed { index, item ->
            QueueAdapter.Row(
                    item = item,
                    position = index + 1,
                    isPlaying = index == activeIndex,
                    // Only dim when the playing entry is known; otherwise every row is "past".
                    isPast = activeIndex >= 0 && index < activeIndex)
        }

        val searching = query.isNotBlank()
        val rows = when {
            searching -> allRows.filter {
                QueueWindowRules.matches(query, it.item.description.title?.toString(),
                        it.item.description.subtitle?.toString())
            }
            activeIndex < 0 || showPlayed -> allRows
            else -> allRows.drop(activeIndex)
        }

        // The heading says what the list is: search results, what comes next, or the whole queue
        // when the playing track cannot be found in it.
        views.section.text = when {
            searching -> "${getString(R.string.quick_tool_search)} · ${rows.size}"
            activeIndex >= 0 -> "${getString(R.string.quick_panel_default_up_next)} · " +
                    "${queue.size - activeIndex - 1}"
            queue.isNotEmpty() -> resources.getQuantityString(
                    R.plurals.queue_header_count, queue.size, queue.size)
            else -> getString(R.string.queue_header)
        }
        val playedCount = activeIndex.coerceAtLeast(0)
        views.playedToggle.visibility = if (!searching && playedCount > 0) View.VISIBLE else View.GONE
        views.playedToggle.text = getString(R.string.queue_window_played, playedCount)
        views.setPlayedExpanded(showPlayed)

        views.searchBox.visibility =
                if (queue.size > SEARCH_MIN_ROWS || searching) View.VISIBLE else View.GONE
        views.empty.text = getString(if (searching) R.string.queue_window_no_matches else R.string.queue_unavailable)
        views.empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        views.list.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE

        adapter.setAllowRemoteArtwork(QueueArtworkResolver.remoteArtworkEnabled(this))
        sizeList(rows.size)
        val arrangement = "$searching|$showPlayed|${queue.size}"
        val playingRow = rows.indexOfFirst { it.isPlaying }
        adapter.submitList(rows) {
            // Open on the playing track, with a row of context above it when the played tracks are
            // shown. Once per arrangement, so a refresh cannot yank a scrolled list back.
            if (scrolledFor != arrangement && rows.isNotEmpty()) {
                scrolledFor = arrangement
                (views.list.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset((playingRow - 1).coerceAtLeast(0), 0)
            }
        }
    }

    /** As tall as the rows, up to what the screen leaves after the player and the controls. */
    private fun sizeList(rowCount: Int) {
        val density = resources.displayMetrics.density
        val wanted = (rowCount * ROW_HEIGHT_DP * density).toInt()
        val screen = resources.displayMetrics.heightPixels
        val ceiling = (screen * MAX_SCREEN_SHARE - CHROME_HEIGHT_DP * density).toInt()
                .coerceAtLeast((MIN_LIST_ROWS * ROW_HEIGHT_DP * density).toInt())
        val height = wanted.coerceAtMost(ceiling)
        val params = views.list.layoutParams
        if (params.height != height) {
            params.height = height
            views.list.layoutParams = params
        }
    }

    private fun jumpTo(item: MediaSession.QueueItem) {
        val controller = playing
        val pending = QueueJump.issue(
                queueId = item.queueId,
                mediaId = item.description.mediaId,
                playing = controller,
                owner = queueOwner)
        if (pending != null) {
            QueueJump.settleLater(this, pending) { controller }
        }
        // The callback normally reports the move; this is for a player that changes the active
        // item without publishing a new state.
        mainHandler.postDelayed({ refresh() }, QueueJump.VERIFY_MS)
    }

    private class Views(val root: View) {
        val cover: ImageView = root.findViewById(R.id.queue_window_cover)
        val title: TextView = root.findViewById(R.id.queue_window_title)
        val artist: TextView = root.findViewById(R.id.queue_window_artist)
        val source: View = root.findViewById(R.id.queue_window_source)
        val sourceIcon: ImageView = root.findViewById(R.id.queue_window_source_icon)
        val sourceName: TextView = root.findViewById(R.id.queue_window_source_name)
        val seek: SeekBar = root.findViewById(R.id.queue_window_seek)
        val elapsed: TextView = root.findViewById(R.id.queue_window_elapsed)
        val duration: TextView = root.findViewById(R.id.queue_window_duration)
        val controls: LinearLayout = root.findViewById(R.id.queue_window_controls)
        val searchBox: View = root.findViewById(R.id.queue_window_search_box)
        val search: EditText = root.findViewById(R.id.queue_window_search)
        val section: TextView = root.findViewById(R.id.queue_window_section)
        val playedToggle: TextView = root.findViewById(R.id.queue_window_played_toggle)
        val list: RecyclerView = root.findViewById(R.id.playback_queue_list)
        val empty: TextView = root.findViewById(R.id.playback_queue_empty)

        private val chevron = playedToggle.compoundDrawablesRelative[2]
        private val chevronDown = chevron?.let {
            android.graphics.drawable.RotateDrawable().apply {
                drawable = it.constantState?.newDrawable()?.mutate() ?: it
                fromDegrees = 90f
                toDegrees = 90f
                level = 10000
            }
        }
        private var playedExpanded: Boolean? = null

        /** The toggle's chevron points right while the played tracks are hidden, down while shown. */
        fun setPlayedExpanded(expanded: Boolean) {
            if (playedExpanded == expanded) return
            playedExpanded = expanded
            playedToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null, null, if (expanded) chevronDown else chevron, null)
        }
    }

    private companion object {
        /** The transport row, in the order it is drawn. */
        val TRANSPORT_ORDER = listOf(MediaActionSemantics.PREVIOUS, MediaActionSemantics.PLAY,
                MediaActionSemantics.PAUSE, MediaActionSemantics.NEXT)

        /** Fine enough that a frame's movement is under a pixel on any phone. */
        const val SEEK_MAX = 10_000
        const val SETTLE_SHORT_MS = 500L
        const val SETTLE_LONG_MS = 1500L

        /** The player's buttons: the play button, and every other one. */
        const val PRIMARY_DP = 60
        const val SECONDARY_DP = 48

        /** The area a glyph is fitted into, once cut to its ink (see [trimmed]). */
        const val PRIMARY_GLYPH_DP = 22
        const val SECONDARY_GLYPH_DP = 20

        /** How far the play triangle sits right of its box's centre. */
        const val PLAY_NUDGE_DP = 1.5f

        /** Alpha above which an icon pixel counts as ink rather than anti-aliasing spill. */
        const val VISIBLE_ALPHA = 12
        const val CONTROL_GAP_DP = 4

        /** dialog_bg_lyra's corner, so the glow is cut where the dialog is. */
        const val DIALOG_CORNER_DP = 12
        const val GLOW_CENTER_X = 0.92f
        const val GLOW_CENTER_Y = 0f
        const val GLOW_RADIUS_DP = 260
        const val GLOW_ALPHA = 0x70

        /** item_queue_song: a 44dp cover plus 6dp above and below. */
        const val ROW_HEIGHT_DP = 56
        const val MIN_LIST_ROWS = 3
        const val MAX_SCREEN_SHARE = 0.86f

        /** Roughly everything above and below the list: the player, controls and search. */
        const val CHROME_HEIGHT_DP = 360

        /** A queue this short is read at a glance; the search field would only take its space. */
        const val SEARCH_MIN_ROWS = 8
    }
}
