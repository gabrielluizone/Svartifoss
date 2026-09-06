package com.svartifoss.snfell.view.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.NinePatchDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.doAfterTextChanged
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.snackbar.Snackbar
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder
import com.matejdro.wearutils.miscutils.VibratorCompat
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.PlaylistShortcut
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import com.svartifoss.snfell.music.StreamingService
import com.svartifoss.snfell.music.StreamingContentType
import com.svartifoss.snfell.music.StreamingShortcutLinks
import com.svartifoss.snfell.view.LyraAccent

/**
 * Manages the streaming shortcuts shown by the watch: name + deep link pairs persisted via
 * [PlaylistShortcutStorage], reorderable by drag (the persisted order is what the watch menu
 * shows). The watch only ever *reads* this list (via OpenPlaylistShortcutsAction) - all editing
 * happens here on the phone, once.
 *
 * Also doubles as the chooser for the "Open specific playlist" action: launched with
 * [EXTRA_PICK_MODE], tapping a shortcut returns it as the activity result instead of opening
 * the edit dialog (adding/deleting/reordering still work, so a missing playlist can be created
 * on the spot and picked right away) - see [PlaylistShortcutPickerAction].
 */
class PlaylistShortcutsActivity : AppCompatActivity(), RecyclerViewDragDropManager.OnItemDragEventListener {

    companion object {
        const val EXTRA_PICK_MODE = "PickMode"
        const val EXTRA_PICKED_NAME = "PickedName"
        const val EXTRA_PICKED_LINK = "PickedLink"

        fun createPickIntent(context: Context): Intent =
                Intent(context, PlaylistShortcutsActivity::class.java)
                        .putExtra(EXTRA_PICK_MODE, true)
    }

    private lateinit var shortcuts: MutableList<PlaylistShortcut>
    private lateinit var listAdapter: ShortcutsAdapter
    private lateinit var adapter: RecyclerView.Adapter<ShortcutViewHolder>
    private lateinit var dragDropManager: RecyclerViewDragDropManager
    private lateinit var vibrator: Vibrator
    private lateinit var emptyLabel: TextView
    private lateinit var emptyContainer: View
    private lateinit var listSummary: TextView
    private var pickMode = false

    @javax.inject.Inject
    @com.svartifoss.snfell.di.GlobalConfig
    lateinit var actionConfig: com.svartifoss.snfell.config.ActionConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        dagger.android.AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_shortcuts)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        pickMode = intent.getBooleanExtra(EXTRA_PICK_MODE, false)
        title = getString(
                if (pickMode) R.string.playlist_shortcut_pick_title
                else R.string.setting_playlist_shortcuts
        )
        findViewById<TextView>(R.id.page_title).setText(
                if (pickMode) R.string.playlist_shortcut_pick_title
                else R.string.setting_playlist_shortcuts
        )

        shortcuts = PlaylistShortcutStorage.load(this).toMutableList()
        // Migrates existing installs to the dedicated watch-side cache even before the user
        // edits anything in this redesigned screen.
        PlaylistShortcutStorage.syncToWatch(this, shortcuts)
        emptyLabel = findViewById(R.id.empty_label)
        emptyContainer = findViewById(R.id.empty_container)
        listSummary = findViewById(R.id.list_summary)

        val recycler = findViewById<RecyclerView>(R.id.recycler)

        dragDropManager = RecyclerViewDragDropManager()
        dragDropManager.setInitiateOnLongPress(true)
        dragDropManager.setInitiateOnMove(false)
        dragDropManager.setInitiateOnTouch(false)
        dragDropManager.onItemDragEventListener = this
        dragDropManager.setDraggingItemShadowDrawable(ResourcesCompat.getDrawable(
                resources,
                R.drawable.material_shadow_z3,
                null) as NinePatchDrawable)

        listAdapter = ShortcutsAdapter()
        @Suppress("UNCHECKED_CAST")
        adapter = dragDropManager.createWrappedAdapter(listAdapter) as RecyclerView.Adapter<ShortcutViewHolder>
        recycler.adapter = adapter
        recycler.itemAnimator = DraggableItemAnimator()
        recycler.layoutManager = LinearLayoutManager(this)
        dragDropManager.attachRecyclerView(recycler)

        val accent = LyraAccent.resolve(this@PlaylistShortcutsActivity)
        val accentForeground = LyraAccent.contrastSafe(this, accent)
        val onAccent = bestOnAccentColor(accent)
        findViewById<ImageView>(R.id.intro_icon).imageTintList =
                ColorStateList.valueOf(accentForeground)
        findViewById<TextView>(R.id.beta_badge).apply {
            setTextColor(accentForeground)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * resources.displayMetrics.density
                setColor(ColorUtils.setAlphaComponent(accent, 24))
                setStroke((resources.displayMetrics.density + 0.5f).toInt(), accentForeground)
            }
        }
        findViewById<View>(R.id.button_back).setOnClickListener { finish() }
        findViewById<View>(R.id.button_reload_covers).setOnClickListener { reloadAllArtwork() }
        findViewById<MaterialButton>(R.id.button_paste).apply {
            setOnClickListener { pasteShortcutFromClipboard() }
            backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                            this@PlaylistShortcutsActivity,
                            R.color.lyra_divider
                    )
            )
            val neutralForeground = ContextCompat.getColor(
                    this@PlaylistShortcutsActivity,
                    R.color.lyra_on_surface
            )
            setTextColor(neutralForeground)
            iconTint = ColorStateList.valueOf(neutralForeground)
        }
        findViewById<MaterialButton>(R.id.button_add).apply {
            setOnClickListener { showShortcutDialog() }
            backgroundTintList = ColorStateList.valueOf(accent)
            setTextColor(onAccent)
            iconTint = ColorStateList.valueOf(onAccent)
        }

        updateEmptyState()
        if (savedInstanceState == null) handleIncomingShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_SEND) {
            pickMode = false
            title = getString(R.string.setting_playlist_shortcuts)
            findViewById<TextView>(R.id.page_title)
                    .setText(R.string.setting_playlist_shortcuts)
            updateEmptyState()
            handleIncomingShare(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // The reload button's visibility depends on the Settings toggle, which may have changed
        // while this screen was backgrounded.
        updateEmptyState()
        // Downloads any missing thumbnails (once) - covers returning here after enabling the
        // toggle in Settings, and shortcuts added on a previous run before it was on.
        refreshArtwork()
    }

    override fun onDestroy() {
        super.onDestroy()
        dragDropManager.release()
    }

    private fun buzz() {
        if (isHapticEnabled()) {
            VibratorCompat.vibrate(vibrator, 25)
        }
    }

    private fun isHapticEnabled(): Boolean {
        val setting = Settings.System.getInt(
                contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0)
        return setting != 0
    }

    override fun onItemDragStarted(position: Int) = buzz()
    override fun onItemDragPositionChanged(fromPosition: Int, toPosition: Int) = Unit
    override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) = buzz()
    override fun onItemDragMoveDistanceUpdated(offsetX: Int, offsetY: Int) = Unit

    /** Accepts the standard text payload emitted by music apps' Share buttons. The editor remains
     * the confirmation boundary: receiving a link never saves or opens it automatically. */
    private fun handleIncomingShare(incomingIntent: Intent) {
        if (incomingIntent.action != Intent.ACTION_SEND ||
                incomingIntent.type?.startsWith("text/") != true) return

        val sharedText = incomingIntent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                ?.toString().orEmpty()
        val link = StreamingShortcutLinks.extractSharedLink(sharedText)
        if (link == null) {
            Toast.makeText(this, R.string.playlist_shortcut_import_invalid, Toast.LENGTH_LONG).show()
            return
        }
        showShortcutDialog(
                initialName = StreamingShortcutLinks.suggestedName(
                        sharedText,
                        incomingIntent.getStringExtra(Intent.EXTRA_SUBJECT)
                ),
                initialLink = link
        )
    }

    private fun pasteShortcutFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val link = StreamingShortcutLinks.extractSharedLink(text)
        if (link == null) {
            Toast.makeText(this, R.string.playlist_shortcut_clipboard_invalid, Toast.LENGTH_LONG)
                    .show()
            return
        }
        showShortcutDialog(
                initialName = StreamingShortcutLinks.suggestedName(text),
                initialLink = link
        )
    }

    /** Moves a shortcut without requiring a drag gesture. TalkBack exposes this through the
     *  custom "Move up/down" actions below; hardware keyboards use Ctrl+Up/Down. */
    private fun moveItemAccessibly(holder: ShortcutViewHolder, offset: Int): Boolean {
        val fromPosition = holder.bindingAdapterPosition
        val toPosition = fromPosition + offset
        if (fromPosition == RecyclerView.NO_POSITION || toPosition !in shortcuts.indices) {
            return false
        }

        val name = shortcuts[fromPosition].name
        val moved = shortcuts.removeAt(fromPosition)
        shortcuts.add(toPosition, moved)
        persist()
        listAdapter.notifyItemMoved(fromPosition, toPosition)

        holder.itemView.post {
            holder.itemView.requestFocus()
            holder.itemView.announceForAccessibility(
                    getString(
                            R.string.actions_moved_to_position,
                            name,
                            toPosition + 1,
                            shortcuts.size
                    )
            )
        }
        return true
    }

    /** Add ([editIndex] null), import or edit a shortcut in the redesigned editor. Link parsing is
     * live: provider and track/playlist/album kind are visible before Save. */
    private fun showShortcutDialog(
            editIndex: Int? = null,
            initialName: String = "",
            initialLink: String = ""
    ) {
        val existing = editIndex?.let { shortcuts.getOrNull(it) }
        val editor = layoutInflater.inflate(R.layout.dialog_streaming_shortcut, null)
        val nameLayout = editor.findViewById<TextInputLayout>(R.id.name_layout)
        val linkLayout = editor.findViewById<TextInputLayout>(R.id.link_layout)
        val nameInput = editor.findViewById<TextInputEditText>(R.id.name_input).apply {
            setText(existing?.name ?: initialName)
            LyraAccent.applyToEditText(this)
        }
        val linkInput = editor.findViewById<TextInputEditText>(R.id.link_input).apply {
            setText(StreamingShortcutLinks.stripShuffle(existing?.link ?: initialLink))
            LyraAccent.applyToEditText(this)
        }
        editor.findViewById<TextView>(R.id.editor_title).setText(
                if (existing == null) R.string.playlist_shortcuts_add
                else R.string.playlist_shortcuts_edit
        )
        val accent = LyraAccent.resolve(this)
        val secondary = ContextCompat.getColor(this, R.color.lyra_text_secondary)
        nameLayout.boxStrokeColor = accent
        linkLayout.boxStrokeColor = accent
        linkLayout.setEndIconTintList(ColorStateList.valueOf(accent))
        editor.findViewById<ImageView>(R.id.editor_icon).imageTintList =
                ColorStateList.valueOf(accent)
        val detectedIcon = editor.findViewById<ImageView>(R.id.detected_icon)
        val detectedTitle = editor.findViewById<TextView>(R.id.detected_title)
        val detectedSubtitle = editor.findViewById<TextView>(R.id.detected_subtitle)
        val shuffleCheckbox = editor.findViewById<CheckBox>(R.id.shuffle_checkbox).apply {
            buttonTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(accent, secondary)
            )
            isChecked = (existing?.link ?: initialLink).let {
                StreamingShortcutLinks.supportsShuffle(it) &&
                        StreamingShortcutLinks.hasShuffle(it)
            }
        }
        fun normalizedCandidate(rawText: String): String =
                StreamingShortcutLinks.extractSharedLink(rawText)
                        ?: StreamingShortcutLinks.canonicalize(rawText)

        fun updateLinkFeedback(rawText: String) {
            val link = normalizedCandidate(rawText)
            val supported = StreamingShortcutLinks.supportsShuffle(link)
            shuffleCheckbox.visibility = if (supported) View.VISIBLE else View.GONE
            if (!supported) shuffleCheckbox.isChecked = false

            if (!StreamingShortcutLinks.isSafeLink(link)) {
                detectedIcon.imageTintList = ColorStateList.valueOf(secondary)
                detectedIcon.setImageResource(R.drawable.ic_music_note)
                detectedTitle.setText(R.string.playlist_shortcut_detection_waiting)
                detectedSubtitle.text = supportedStreamingServiceNames()
                return
            }
            val info = StreamingShortcutLinks.inspect(link)
            val service = info.service
            val serviceName = streamingServiceName(service)
            val typeName = streamingContentTypeName(info.contentType)
            detectedTitle.text = if (typeName == null) {
                serviceName
            } else {
                getString(R.string.playlist_shortcut_detected_format, serviceName, typeName)
            }
            detectedSubtitle.text = when {
                service == StreamingService.GENERIC ->
                    getString(R.string.playlist_shortcut_service_generic)
                service.packageName?.let(::isPackageInstalled) == true ->
                    getString(
                            R.string.playlist_shortcut_service_installed,
                            streamingServiceName(service)
                    )
                else -> getString(
                        R.string.playlist_shortcut_service_not_installed,
                            streamingServiceName(service)
                )
            }
            val installedIcon = service.packageName?.let { packageName ->
                try {
                    packageManager.getApplicationIcon(packageName)
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    null
                }
            }
            if (installedIcon != null) {
                detectedIcon.imageTintList = null
                detectedIcon.setImageDrawable(installedIcon)
            } else {
                detectedIcon.imageTintList = ColorStateList.valueOf(accent)
                detectedIcon.setImageResource(
                        if (service == StreamingService.YOUTUBE_MUSIC) R.drawable.ic_yt_music
                        else R.drawable.ic_music_note
                )
            }
        }
        nameInput.doAfterTextChanged { nameLayout.error = null }
        linkInput.doAfterTextChanged {
            linkLayout.error = null
            updateLinkFeedback(it?.toString().orEmpty())
        }
        linkLayout.setEndIconOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            val pasted = StreamingShortcutLinks.extractSharedLink(text)
                    ?: StreamingShortcutLinks.canonicalize(text)
            if (StreamingShortcutLinks.isSafeLink(pasted)) {
                linkInput.setText(pasted)
                linkInput.setSelection(linkInput.text?.length ?: 0)
            } else {
                linkLayout.error = getString(R.string.playlist_shortcut_link_invalid)
            }
        }
        updateLinkFeedback(linkInput.text.toString())
        val dialog = AlertDialog.Builder(this)
                .setView(editor)
                .create()
        editor.findViewById<MaterialButton>(R.id.button_cancel).setOnClickListener {
            dialog.dismiss()
        }
        editor.findViewById<MaterialButton>(R.id.button_save).apply {
            backgroundTintList = ColorStateList.valueOf(accent)
            val onAccent = bestOnAccentColor(accent)
            setTextColor(onAccent)
            iconTint = ColorStateList.valueOf(onAccent)
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                val candidate = normalizedCandidate(linkInput.text.toString())
                var valid = true
                if (name.isBlank()) {
                    nameLayout.error = getString(R.string.playlist_shortcut_name_required)
                    valid = false
                }
                if (!StreamingShortcutLinks.isSafeLink(candidate)) {
                    linkLayout.error = getString(R.string.playlist_shortcut_link_invalid)
                    valid = false
                }
                if (!valid) return@setOnClickListener

                var link = StreamingShortcutLinks.stripShuffle(candidate)
                if (shuffleCheckbox.isChecked && StreamingShortcutLinks.supportsShuffle(link)) {
                    link = StreamingShortcutLinks.withYoutubeShuffle(link)
                }
                if (editIndex != null && editIndex in shortcuts.indices) {
                    val previousLink = shortcuts[editIndex].link
                    shortcuts[editIndex] = PlaylistShortcut(name, link)
                    persist()
                    listAdapter.notifyItemChanged(editIndex)
                    // Keep copies already bound to buttons/gestures/quick panel/action list in sync
                    // with the edited name/link (they baked the old values at assignment time).
                    com.svartifoss.snfell.actions.PlaylistShortcutActionSync.propagateEdit(
                            this@PlaylistShortcutsActivity, actionConfig, previousLink, name, link)
                } else {
                    shortcuts.add(PlaylistShortcut(name, link))
                    persist()
                    listAdapter.notifyItemInserted(shortcuts.size - 1)
                    updateEmptyState()
                }
                // Fetch the new/edited shortcut's online thumbnail (opt-in), then re-sync.
                refreshArtwork()
                dialog.dismiss()
            }
        }
        dialog.show()
        dialog.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
        )

        nameInput.requestFocus()
    }

    private fun openShortcutMenu(anchor: View, position: Int) {
        val shortcut = shortcuts.getOrNull(position) ?: return
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.playlist_shortcut_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.shortcut_edit -> showShortcutDialog(position)
                R.id.shortcut_open -> openShortcutLink(shortcut)
                R.id.shortcut_copy -> copyShortcutLink(shortcut)
                R.id.shortcut_duplicate -> duplicateShortcut(position)
                R.id.shortcut_share -> shareShortcut(shortcut)
                R.id.shortcut_delete -> removeShortcut(position)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    /** Uses the same app/default/chooser policy as a launch requested from the watch. */
    private fun openShortcutLink(shortcut: PlaylistShortcut) {
        if (!StreamingShortcutLinks.isSafeLink(shortcut.link)) {
            Toast.makeText(this, R.string.playlist_shortcut_open_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val openMode = preferences.getString(StreamingShortcutLinks.OPEN_MODE_KEY, null)
                ?: if (preferences.getBoolean(
                                StreamingShortcutLinks.PREFER_INSTALLED_APP_KEY,
                                true
                        )) StreamingShortcutLinks.OPEN_MODE_APP
                else StreamingShortcutLinks.OPEN_MODE_DEFAULT
        val browserLink = StreamingShortcutLinks.forBrowser(shortcut.link)
        if (openMode == StreamingShortcutLinks.OPEN_MODE_CHOOSER) {
            val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_VIEW, Uri.parse(browserLink)),
                    getString(R.string.streaming_shortcut_choose_app)
            )
            try {
                startActivity(chooser)
                return
            } catch (_: ActivityNotFoundException) {
                // Fall through to the regular error below.
            } catch (_: SecurityException) {
                // Fall through to the regular error below.
            }
        } else {
            val service = StreamingShortcutLinks.detect(shortcut.link)
            val targetPackage = service.packageName?.takeIf {
                openMode == StreamingShortcutLinks.OPEN_MODE_APP && isPackageInstalled(it)
            }
            val primaryLink = if (targetPackage != null) {
                StreamingShortcutLinks.forInstalledApp(shortcut.link)
            } else {
                browserLink
            }
            if (startStreamingLink(primaryLink, targetPackage)) return
            if (targetPackage != null && startStreamingLink(browserLink, null)) return
        }

        Toast.makeText(this, R.string.playlist_shortcut_open_failed, Toast.LENGTH_SHORT).show()
    }

    private fun startStreamingLink(link: String, targetPackage: String?): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
            setPackage(targetPackage)
        })
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun copyShortcutLink(shortcut: PlaylistShortcut) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(shortcut.name, shortcut.link))
        Toast.makeText(this, R.string.playlist_shortcut_link_copied, Toast.LENGTH_SHORT).show()
    }

    private fun duplicateShortcut(position: Int) {
        val source = shortcuts.getOrNull(position) ?: return
        var copyName = getString(R.string.playlist_shortcut_copy_name, source.name)
        var suffix = 2
        while (shortcuts.any { it.name.equals(copyName, ignoreCase = true) }) {
            copyName = getString(R.string.playlist_shortcut_copy_name_numbered, source.name, suffix++)
        }
        val insertAt = (position + 1).coerceAtMost(shortcuts.size)
        shortcuts.add(insertAt, source.copy(name = copyName))
        persist()
        listAdapter.notifyItemInserted(insertAt)
        updateEmptyState()
        Snackbar.make(
                findViewById(R.id.recycler),
                getString(R.string.playlist_shortcut_duplicated, copyName),
                Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun shareShortcut(shortcut: PlaylistShortcut) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, shortcut.name)
            putExtra(Intent.EXTRA_TEXT, "${shortcut.name}\n${shortcut.link}")
        }
        try {
            startActivity(Intent.createChooser(
                    sendIntent,
                    getString(R.string.playlist_shortcut_share_title)
            ))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.playlist_shortcut_share_failed, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.playlist_shortcut_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** Material's static on-primary color cannot follow the user/album accent resolved at
     * runtime. Pick whichever neutral has the stronger contrast so light accents never leave a
     * white label or icon unreadable. */
    private fun bestOnAccentColor(accent: Int): Int {
        val opaqueAccent = ColorUtils.setAlphaComponent(accent, 255)
        return if (ColorUtils.calculateContrast(Color.BLACK, opaqueAccent) >=
                ColorUtils.calculateContrast(Color.WHITE, opaqueAccent)) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    private fun streamingServiceName(service: StreamingService): String = getString(
            when (service) {
                StreamingService.YOUTUBE_MUSIC -> R.string.playlist_source_yt_music
                StreamingService.SPOTIFY -> R.string.playlist_source_spotify
                StreamingService.DEEZER -> R.string.playlist_source_deezer
                StreamingService.TIDAL -> R.string.playlist_source_tidal
                StreamingService.APPLE_MUSIC -> R.string.playlist_source_apple_music
                StreamingService.AMAZON_MUSIC -> R.string.playlist_source_amazon_music
                StreamingService.SOUNDCLOUD -> R.string.playlist_source_soundcloud
                StreamingService.QOBUZ -> R.string.playlist_source_qobuz
                StreamingService.BANDCAMP -> R.string.playlist_source_bandcamp
                StreamingService.AUDIOMACK -> R.string.playlist_source_audiomack
                StreamingService.MIXCLOUD -> R.string.playlist_source_mixcloud
                StreamingService.PANDORA -> R.string.playlist_source_pandora
                StreamingService.GENERIC -> R.string.playlist_source_link
            }
    )

    /** Brand names need no sentence-level translation. Deriving this list from the enum keeps the
     * editor accurate whenever a supported provider is added or removed. */
    private fun supportedStreamingServiceNames(): String = StreamingService.values()
            .filter { it.packageName != null }
            .joinToString(" • ") { streamingServiceName(it) }

    private fun streamingContentTypeName(type: StreamingContentType): String? = when (type) {
        StreamingContentType.TRACK -> getString(R.string.playlist_type_track)
        StreamingContentType.PLAYLIST -> getString(R.string.playlist_type_playlist)
        StreamingContentType.ALBUM -> getString(R.string.playlist_type_album)
        StreamingContentType.ARTIST -> getString(R.string.playlist_type_artist)
        StreamingContentType.SHOW -> getString(R.string.playlist_type_show)
        StreamingContentType.EPISODE -> getString(R.string.playlist_type_episode)
        StreamingContentType.MIX -> getString(R.string.playlist_type_mix)
        StreamingContentType.UNKNOWN -> null
    }

    private fun removeShortcut(position: Int) {
        if (position !in shortcuts.indices) {
            return
        }
        val removed = shortcuts.removeAt(position)
        persist()
        listAdapter.notifyItemRemoved(position)
        updateEmptyState()

        Snackbar.make(
                findViewById(R.id.recycler),
                getString(R.string.playlist_shortcut_deleted, removed.name),
                Snackbar.LENGTH_LONG
        ).setAction(R.string.playlist_shortcut_undo) {
            val restoreAt = position.coerceAtMost(shortcuts.size)
            shortcuts.add(restoreAt, removed)
            persist()
            listAdapter.notifyItemInserted(restoreAt)
            updateEmptyState()
        }.show()
    }

    /** Resets a recycled source-icon view from the full-bleed thumbnail presentation back to the
     *  padded, centered glyph/app-icon look. */
    private fun restoreGlyphSlot(view: ImageView) {
        val pad = (10 * resources.displayMetrics.density).toInt()
        view.setPadding(pad, pad, pad, pad)
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        view.clipToOutline = false
    }

    private fun persist() {
        PlaylistShortcutStorage.save(this, shortcuts)
        // A removed shortcut's cached thumbnail is no longer needed.
        com.svartifoss.snfell.music.ShortcutArtworkStore.retainOnly(
                this, shortcuts.map { it.link })
    }

    /**
     * When online artwork is enabled, downloads a thumbnail (once) for any shortcut that lacks
     * one, then refreshes the list and re-syncs to the watch. No-op when the feature is off, so
     * nothing touches the network unless the user opted in.
     */
    private fun refreshArtwork() {
        if (!com.svartifoss.snfell.music.ShortcutArtworkFetcher.isEnabled(this)) return
        val snapshot = shortcuts.toList()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val changed = com.svartifoss.snfell.music.ShortcutArtworkFetcher.ensureCachedAll(
                    this@PlaylistShortcutsActivity, snapshot)
            if (changed) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    listAdapter.notifyDataSetChanged()
                }
                PlaylistShortcutStorage.syncToWatch(this@PlaylistShortcutsActivity)
            }
        }
    }

    private fun updateEmptyState() {
        val empty = shortcuts.isEmpty()
        emptyContainer.visibility = if (empty) View.VISIBLE else View.GONE
        listSummary.visibility = if (empty) TextView.GONE else TextView.VISIBLE
        emptyLabel.setText(
                if (pickMode) R.string.playlist_shortcut_pick_empty
                else R.string.playlist_shortcuts_none
        )
        if (!empty) {
            listSummary.text = resources.getQuantityString(
                    R.plurals.playlist_shortcuts_summary, shortcuts.size, shortcuts.size)
        }
        findViewById<View>(R.id.button_reload_covers).visibility =
                if (!empty && com.svartifoss.snfell.music.ShortcutArtworkFetcher.isEnabled(this))
                    View.VISIBLE
                else
                    View.GONE
    }

    /** Force-refetches every saved shortcut's cover, bypassing the "already cached" skip -
     *  unlike [refreshArtwork], which only fills in what is missing. Lets the user pick up a
     *  changed remote cover, or benefit from a raised [com.svartifoss.snfell.music.ShortcutArtworkFetcher.MAX_THUMBNAIL_PX]
     *  on thumbnails that were cached before it increased. */
    private fun reloadAllArtwork() {
        if (shortcuts.isEmpty()) return
        val snapshot = shortcuts.toList()
        Toast.makeText(this, R.string.playlist_shortcuts_reloading_covers, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val changed = com.svartifoss.snfell.music.ShortcutArtworkFetcher.ensureCachedAll(
                    this@PlaylistShortcutsActivity, snapshot, force = true)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (changed) listAdapter.notifyDataSetChanged()
                Toast.makeText(
                        this@PlaylistShortcutsActivity,
                        R.string.playlist_shortcuts_covers_reloaded,
                        Toast.LENGTH_SHORT
                ).show()
            }
            if (changed) PlaylistShortcutStorage.syncToWatch(this@PlaylistShortcutsActivity)
        }
    }

    private inner class ShortcutsAdapter : RecyclerView.Adapter<ShortcutViewHolder>(),
            DraggableItemAdapter<ShortcutViewHolder> {
        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_playlist_shortcut, parent, false)
            return ShortcutViewHolder(view)
        }

        override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
            val shortcut = shortcuts[position]
            holder.name.text = shortcut.name
            holder.subtitle.text = PlaylistShortcutStorage.describe(
                    this@PlaylistShortcutsActivity, shortcut)
            val service = StreamingShortcutLinks.detect(shortcut.link)
            val thumbnailPng = com.svartifoss.snfell.music.ShortcutArtworkStore.get(
                    this@PlaylistShortcutsActivity, shortcut.link)
            val thumbnail = thumbnailPng?.let {
                android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
            }
            val installedIcon = service.packageName?.let { packageName ->
                try {
                    packageManager.getApplicationIcon(packageName)
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    null
                }
            }
            if (thumbnail != null) {
                // Online cover art (opt-in): full colour, filling the circular slot (the oval
                // background clips it via clipToOutline), never tinted or padded.
                holder.sourceIcon.imageTintList = null
                holder.sourceIcon.setPadding(0, 0, 0, 0)
                holder.sourceIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                holder.sourceIcon.clipToOutline = true
                holder.sourceIcon.setImageBitmap(thumbnail)
            } else if (installedIcon != null) {
                restoreGlyphSlot(holder.sourceIcon)
                holder.sourceIcon.imageTintList = null
                holder.sourceIcon.setImageDrawable(installedIcon)
            } else {
                restoreGlyphSlot(holder.sourceIcon)
                holder.sourceIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@PlaylistShortcutsActivity,
                        R.color.lyra_on_surface
                    )
                )
                holder.sourceIcon.setImageResource(
                    if (service == StreamingService.YOUTUBE_MUSIC) R.drawable.ic_yt_music
                    else R.drawable.ic_music_note
                )
            }
            holder.sourceIcon.contentDescription = holder.subtitle.text
            holder.moreOptions.contentDescription =
                    getString(R.string.playlist_shortcut_more_options, shortcut.name)
            holder.moreOptions.setOnClickListener {
                openShortcutMenu(holder.moreOptions, holder.bindingAdapterPosition)
            }
            holder.itemView.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index == RecyclerView.NO_POSITION) {
                    return@setOnClickListener
                }

                if (pickMode) {
                    val picked = shortcuts[index]
                    setResult(RESULT_OK, Intent()
                            .putExtra(EXTRA_PICKED_NAME, picked.name)
                            .putExtra(EXTRA_PICKED_LINK, picked.link))
                    finish()
                } else {
                    showShortcutDialog(index)
                }
            }
        }

        override fun getItemCount(): Int = shortcuts.size

        override fun getItemId(position: Int): Long =
                (shortcuts[position].name + shortcuts[position].link).hashCode().toLong()

        override fun onGetItemDraggableRange(holder: ShortcutViewHolder, position: Int): ItemDraggableRange? = null

        override fun onCheckCanDrop(draggingPosition: Int, dropPosition: Int): Boolean = true

        override fun onCheckCanStartDrag(holder: ShortcutViewHolder, position: Int, x: Int, y: Int): Boolean = true

        override fun onMoveItem(fromPosition: Int, toPosition: Int) {
            val moved = shortcuts.removeAt(fromPosition)
            shortcuts.add(toPosition, moved)
            persist()
        }

        override fun onItemDragStarted(position: Int) = Unit
        override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) = Unit
    }

    private inner class ShortcutViewHolder(itemView: View) : AbstractDraggableItemViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.name)
        val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val sourceIcon: ImageView = itemView.findViewById(R.id.source_icon)
        val moreOptions: ImageButton = itemView.findViewById(R.id.more_options)

        init {
            ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return

                    if (position > 0) {
                        info.addAction(
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        R.id.accessibility_action_move_up,
                                        getString(R.string.actions_move_up)
                                )
                        )
                    }
                    if (position < shortcuts.lastIndex) {
                        info.addAction(
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        R.id.accessibility_action_move_down,
                                        getString(R.string.actions_move_down)
                                )
                        )
                    }
                }

                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?
                ): Boolean = when (action) {
                    R.id.accessibility_action_move_up -> moveItemAccessibly(this@ShortcutViewHolder, -1)
                    R.id.accessibility_action_move_down -> moveItemAccessibly(this@ShortcutViewHolder, 1)
                    else -> super.performAccessibilityAction(host, action, args)
                }
            })

            itemView.setOnKeyListener { _, keyCode, event ->
                val reorderKey = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                        keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                if (!event.isCtrlPressed || !reorderKey) {
                    false
                } else {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        moveItemAccessibly(
                                this@ShortcutViewHolder,
                                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                        )
                    }
                    true
                }
            }
        }
    }
}
