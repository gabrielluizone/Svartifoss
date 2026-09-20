package com.svartifoss.snfell.view.actionlist

import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ShortcutMenuAddition
import com.svartifoss.snfell.actions.ShortcutMenuPlanner
import com.svartifoss.snfell.music.PlaylistShortcut
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import com.svartifoss.snfell.music.ShortcutArtworkFetcher
import com.svartifoss.snfell.music.ShortcutArtworkStore
import com.svartifoss.snfell.music.StreamingService
import com.svartifoss.snfell.music.StreamingShortcutLinks
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.MusicLoadingBarsView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one place to turn a pasted streaming link into a shortcut - and, from the Actions tab, to put
 * it on the watch menu.
 *
 * In [Mode.MENU] (the Actions tab's add button) it is the whole of adding to the menu. Adding one used to be a walk through four screens - the action picker, its Streaming shortcuts
 * category, the saved-shortcuts list, then that list's own editor for the link and a name typed by
 * hand - repeated for every item, because a YouTube Music link carries no name and each round trip
 * ended back at the start. Here a pasted link is understood as it lands (its service and kind are
 * shown), its name is looked up from the service's public oEmbed record, and one tap both saves it
 * in the Streaming shortcuts library and adds it to the menu. The sheet stays open afterwards so
 * the next link can follow, and everything already saved can be ticked in bulk instead of re-picked
 * one at a time. Anything that is not a streaming link (playback controls, volume, apps, Tasker)
 * still goes through the full action picker, whose row is the first thing in the sheet.
 *
 * In [Mode.PICK] (the "Add a link" row inside Pick action) it is only the link half: the same
 * form, but it saves the shortcut and hands it straight back to the picker as the chosen action, so
 * a link can be assigned to a button without a second window to leave for and return from.
 *
 * The sheet never raises the keyboard by itself: a person who came to tap Paste or a saved row
 * should not have half the screen taken from them. The field takes focus, and the keyboard, only
 * when it is tapped.
 *
 * The name lookup is a network request, so it keeps the app's opt-in stance: it runs by itself only
 * when the user has turned on online shortcut artwork, and otherwise waits for a tap on "Get name".
 */
class StreamingLinkSheet(
        private val context: Context,
        private val host: Host,
        private val mode: Mode = Mode.MENU
) {
    enum class Mode {
        /** From the Actions tab: adds to the menu, stays open, lists what is already saved. */
        MENU,

        /** From Pick action: saves the link and returns it as the chosen action, then closes. */
        PICK
    }

    /** What a mode needs from its caller; the members of the other mode keep their no-op defaults. */
    interface Host {
        /** [Mode.MENU]: links of the shortcut actions the menu already holds. */
        fun menuShortcutLinks(): List<String> = emptyList()

        /** [Mode.MENU]: appends one menu row per shortcut, in order, and persists the menu. */
        fun addShortcutsToMenu(shortcuts: List<PlaylistShortcut>) = Unit

        /** [Mode.MENU]: opens the full action picker, for everything that is not a streaming link. */
        fun openFullPicker() = Unit

        /** [Mode.MENU]: a cover finished downloading, so rows already on the menu can be redrawn and re-sent. */
        fun onArtworkCached() = Unit

        /** [Mode.PICK]: the shortcut to use as the chosen action. */
        fun useShortcut(shortcut: PlaylistShortcut) = Unit
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val accent = LyraAccent.resolve(context)

    private lateinit var dialog: BottomSheetDialog
    private lateinit var rootView: View
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var otherRow: View
    private lateinit var orLinkLabel: TextView
    private lateinit var linkLayout: TextInputLayout
    private lateinit var linkInput: TextInputEditText
    private lateinit var nameLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var detectedView: TextView
    private lateinit var lookupRow: View
    private lateinit var lookupBars: MusicLoadingBarsView
    private lateinit var lookupButton: MaterialButton
    private lateinit var lookupStatus: TextView
    private lateinit var shuffleCheckbox: CheckBox
    private lateinit var addButton: MaterialButton
    private lateinit var statusView: TextView
    private lateinit var savedSection: View
    private lateinit var savedList: LinearLayout
    private lateinit var addSelectedButton: MaterialButton

    private var library: List<PlaylistShortcut> = emptyList()

    /** The normalized link in the field, or null while what is typed is not a usable link. */
    private var currentLink: String? = null

    /** True once the person has typed or edited the name, so a lookup never overwrites it. */
    private var nameTouchedByUser = false
    private var settingNameFromCode = false

    /** The link the name in the field was filled in for, when the name is not the person's own.
     *  A different link must not inherit it - a service with no lookup would otherwise leave the
     *  previous link's name on the next one, ready to be saved under the wrong title. */
    private var autoNameLink: String? = null
    private var lookupJob: Job? = null

    /** Saved shortcuts ticked for a bulk add, by canonical link. */
    private val selected = LinkedHashSet<String>()

    fun show() {
        library = PlaylistShortcutStorage.load(context)

        val view = LayoutInflater.from(context).inflate(R.layout.sheet_add_to_menu, null)
        dialog = BottomSheetDialog(context)
        dialog.setContentView(view)
        dialog.window?.apply {
            // The sheet paints its own background; the window behind it must not show a second one.
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Hidden, not unspecified: with a text field in the window the system would otherwise
            // raise the keyboard the moment the sheet opens.
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.setOnDismissListener {
            lookupJob?.cancel()
            scope.cancel()
        }

        bindViews(view)
        applyMode()
        styleForAccent()
        wireLinkField()
        wireNameField()
        wireButtons(view)
        renderSaved()
        updateAddEnabled()

        dialog.show()
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.background = ColorDrawable(Color.TRANSPARENT)
            BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun bindViews(view: View) {
        rootView = view.findViewById(R.id.sheet_root)
        titleView = view.findViewById(R.id.sheet_title)
        subtitleView = view.findViewById(R.id.sheet_subtitle)
        otherRow = view.findViewById(R.id.sheet_other_row)
        orLinkLabel = view.findViewById(R.id.sheet_or_link_label)
        linkLayout = view.findViewById(R.id.sheet_link_layout)
        linkInput = view.findViewById(R.id.sheet_link_input)
        nameLayout = view.findViewById(R.id.sheet_name_layout)
        nameInput = view.findViewById(R.id.sheet_name_input)
        detectedView = view.findViewById(R.id.sheet_detected)
        lookupRow = view.findViewById(R.id.sheet_lookup_row)
        lookupBars = view.findViewById(R.id.sheet_lookup_bars)
        lookupButton = view.findViewById(R.id.sheet_lookup_button)
        lookupStatus = view.findViewById(R.id.sheet_lookup_status)
        shuffleCheckbox = view.findViewById(R.id.sheet_shuffle)
        addButton = view.findViewById(R.id.sheet_add)
        statusView = view.findViewById(R.id.sheet_status)
        savedSection = view.findViewById(R.id.sheet_saved_section)
        savedList = view.findViewById(R.id.sheet_saved_list)
        addSelectedButton = view.findViewById(R.id.sheet_add_selected)
    }

    /** [Mode.PICK] is the link half only: what belongs to the menu (the way out to the picker, the
     *  list of what is already saved) is not shown, because the picker it was opened from already
     *  offers both. */
    private fun applyMode() {
        if (mode != Mode.PICK) return
        titleView.setText(R.string.add_link_title)
        subtitleView.setText(R.string.add_link_subtitle)
        otherRow.visibility = View.GONE
        orLinkLabel.visibility = View.GONE
        addButton.setText(R.string.add_link_use)
    }

    /** The sheet is inflated from the static theme, so everything that should follow the accent
     *  the rest of the app is showing (which may be picked or taken from the album) is set here. */
    private fun styleForAccent() {
        val secondary = ContextCompat.getColor(context, R.color.lyra_text_secondary)
        val divider = ContextCompat.getColor(context, R.color.lyra_divider)
        val accentForeground = LyraAccent.contrastSafe(context, accent)
        val onAccent = LyraAccent.foregroundFor(accent)

        linkLayout.boxStrokeColor = accent
        nameLayout.boxStrokeColor = accent
        LyraAccent.applyToEditText(linkInput, accent)
        LyraAccent.applyToEditText(nameInput, accent)
        lookupBars.setBarsColor(accent)
        dialog.findViewById<MaterialButton>(R.id.sheet_paste)?.setTextColor(accentForeground)
        lookupButton.setTextColor(accentForeground)
        orLinkLabel.setTextColor(accentForeground)
        shuffleCheckbox.buttonTintList = accentCheckTint(secondary)

        // A tinted button shows no disabled state of its own: without the extra state the
        // unavailable "Add" would be indistinguishable from the ready one.
        val enabledStates = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        for (button in listOf(addButton, addSelectedButton)) {
            button.backgroundTintList = ColorStateList(enabledStates, intArrayOf(divider, accent))
            button.setTextColor(ColorStateList(enabledStates, intArrayOf(secondary, onAccent)))
        }
    }

    private fun accentCheckTint(unchecked: Int) = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, unchecked)
    )

    private fun wireLinkField() {
        linkInput.doAfterTextChanged { onLinkChanged(it?.toString().orEmpty()) }
    }

    private fun wireNameField() {
        nameInput.doAfterTextChanged {
            if (!settingNameFromCode) {
                // Emptying the field hands the name back to the lookup; anything typed keeps it.
                nameTouchedByUser = !it.isNullOrBlank()
            }
            nameLayout.error = null
            updateAddEnabled()
        }
        nameInput.setOnEditorActionListener { _, _, _ ->
            if (addButton.isEnabled) addTypedLink()
            true
        }
    }

    private fun wireButtons(root: View) {
        root.findViewById<View>(R.id.sheet_paste).setOnClickListener { pasteFromClipboard() }
        lookupButton.setOnClickListener { currentLink?.let { startLookup(it, debounce = false) } }
        addButton.setOnClickListener { addTypedLink() }
        addSelectedButton.setOnClickListener { addSelected() }
        root.findViewById<View>(R.id.sheet_other_row).setOnClickListener {
            dialog.dismiss()
            host.openFullPicker()
        }
    }

    private fun normalized(raw: String): String =
            StreamingShortcutLinks.extractSharedLink(raw) ?: StreamingShortcutLinks.canonicalize(raw)

    private fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        val link = StreamingShortcutLinks.extractSharedLink(text)
                ?: StreamingShortcutLinks.canonicalize(text).takeIf(StreamingShortcutLinks::isSafeLink)
        if (link == null) {
            linkLayout.error = context.getString(R.string.playlist_shortcut_clipboard_invalid)
            return
        }
        // Share sheets hand over "<title> <link>"; keep the title as a starting name and leave
        // only the link in the field. A looked-up name replaces it if the lookup finds one.
        val guess = StreamingShortcutLinks.suggestedName(text)
        if (guess.isNotEmpty() && (!nameTouchedByUser || nameInput.text.isNullOrBlank())) {
            // Keyed the way onLinkChanged will see the link, or it would take the guess for a name
            // that belongs to some other link and clear it.
            setAutoName(guess, normalized(link))
        }
        linkInput.setText(link)
        linkInput.setSelection(linkInput.text?.length ?: 0)
    }

    private fun onLinkChanged(raw: String) {
        linkLayout.error = null
        val candidate = normalized(raw)
        val usable = raw.isNotBlank() && StreamingShortcutLinks.isSafeLink(candidate)
        currentLink = candidate.takeIf { usable }
        lookupJob?.cancel()

        if (!usable) {
            detectedView.visibility = View.GONE
            shuffleCheckbox.visibility = View.GONE
            shuffleCheckbox.isChecked = false
            lookupRow.visibility = View.GONE
            updateAddEnabled()
            return
        }

        if (!nameTouchedByUser && candidate != autoNameLink && !nameInput.text.isNullOrBlank()) {
            setAutoName("", null)
        }

        detectedView.text = PlaylistShortcutStorage.describe(context, PlaylistShortcut("", candidate))
        detectedView.visibility = View.VISIBLE
        val supportsShuffle = StreamingShortcutLinks.supportsShuffle(candidate)
        shuffleCheckbox.visibility = if (supportsShuffle) View.VISIBLE else View.GONE
        if (!supportsShuffle) shuffleCheckbox.isChecked = false

        refreshLookup(candidate)
        updateAddEnabled()
    }

    // region Name lookup

    private fun refreshLookup(link: String) {
        if (nameTouchedByUser && !nameInput.text.isNullOrBlank()) {
            lookupRow.visibility = View.GONE
            return
        }
        when {
            !ShortcutArtworkFetcher.supportsLookup(link) -> {
                showLookup(bars = false, button = false, status = R.string.add_menu_lookup_unsupported)
            }
            ShortcutArtworkFetcher.isEnabled(context) -> startLookup(link, debounce = true)
            else -> showLookup(bars = false, button = true, status = null)
        }
    }

    private fun startLookup(link: String, debounce: Boolean) {
        lookupJob?.cancel()
        lookupJob = scope.launch {
            // A link typed by hand changes on every keystroke; only the one that stays put is asked about.
            if (debounce) delay(LOOKUP_DEBOUNCE_MS)
            showLookup(bars = true, button = false, status = R.string.add_menu_looking_up)
            val title = withContext(Dispatchers.IO) { ShortcutArtworkFetcher.lookupInfo(link)?.title }
            // The link may have changed, or been cleared, while the request was out.
            if (currentLink != link) return@launch
            if (title == null) {
                showLookup(bars = false, button = true, status = R.string.add_menu_lookup_failed)
                return@launch
            }
            if (!nameTouchedByUser || nameInput.text.isNullOrBlank()) setAutoName(title, link)
            lookupRow.visibility = View.GONE
        }
    }

    private fun showLookup(bars: Boolean, button: Boolean, status: Int?) {
        lookupRow.visibility = View.VISIBLE
        lookupBars.visibility = if (bars) View.VISIBLE else View.GONE
        lookupButton.visibility = if (button) View.VISIBLE else View.GONE
        lookupStatus.visibility = if (status != null) View.VISIBLE else View.GONE
        if (status != null) lookupStatus.setText(status)
    }

    private fun setAutoName(name: String, forLink: String?) {
        settingNameFromCode = true
        nameInput.setText(name)
        nameInput.setSelection(name.length)
        settingNameFromCode = false
        autoNameLink = forLink
        updateAddEnabled()
    }

    // endregion

    // region Adding

    private fun updateAddEnabled() {
        if (!::addButton.isInitialized) return
        addButton.isEnabled = currentLink != null && !nameInput.text.isNullOrBlank()
    }

    private fun addTypedLink() {
        val candidate = currentLink ?: return
        val name = nameInput.text?.toString().orEmpty().trim()
        if (name.isEmpty()) {
            nameLayout.error = context.getString(R.string.playlist_shortcut_name_required)
            return
        }

        var link = StreamingShortcutLinks.stripShuffle(candidate)
        if (shuffleCheckbox.isChecked && StreamingShortcutLinks.supportsShuffle(link)) {
            link = StreamingShortcutLinks.withYoutubeShuffle(link)
        }

        // The menu is only consulted in MENU mode: a link picked for a button may well already be
        // on the menu, and that is no reason to refuse it.
        val menuLinks = if (mode == Mode.MENU) host.menuShortcutLinks() else emptyList()
        val plan = ShortcutMenuPlanner.plan(library, menuLinks, name, link)
        if (plan.addedToLibrary) {
            PlaylistShortcutStorage.save(context, plan.library)
            library = plan.library
        }

        if (mode == Mode.PICK) {
            useShortcut(plan)
            return
        }

        if (plan.addedToLibrary) cacheArtworkInBackground(plan.shortcut)
        if (plan.addedToMenu) {
            host.addShortcutsToMenu(listOf(plan.shortcut))
        }
        showStatus(context.getString(
                if (plan.addedToMenu) R.string.add_menu_added else R.string.add_menu_already,
                plan.shortcut.name))

        // Ready for the next link.
        lookupJob?.cancel()
        settingNameFromCode = true
        linkInput.setText("")
        nameInput.setText("")
        settingNameFromCode = false
        nameTouchedByUser = false
        autoNameLink = null
        releaseFocus()
        renderSaved()
    }

    /**
     * [Mode.PICK]: hands the shortcut back as the chosen action. A cover is fetched first when
     * online artwork is on, because the caller writes the action into a config that is sent to the
     * watch at once, with the icon rasterized then - a cover that arrives afterwards would never
     * reach it. The wait is short and bounded: past it the shortcut is used without, and the
     * download still finishes and is stored for the next time the config is sent.
     */
    private fun useShortcut(plan: ShortcutMenuAddition) {
        releaseFocus()
        if (!plan.addedToLibrary || !ShortcutArtworkFetcher.isEnabled(context)) {
            host.useShortcut(plan.shortcut)
            dialog.dismiss()
            return
        }
        addButton.isEnabled = false
        val appContext = context.applicationContext
        scope.launch {
            withTimeoutOrNull(COVER_WAIT_MS) {
                withContext(Dispatchers.IO) {
                    ShortcutArtworkFetcher.ensureCachedAll(appContext, listOf(plan.shortcut))
                }
            }
            host.useShortcut(plan.shortcut)
            dialog.dismiss()
        }
    }

    /** Puts the keyboard away and takes focus off the field, so what was just confirmed is not
     *  left half covered and the next tap on the field raises it deliberately. */
    private fun releaseFocus() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(rootView.windowToken, 0)
        rootView.requestFocus()
    }

    private fun addSelected() {
        val menuLinks = host.menuShortcutLinks()
        val additions = library.filter { shortcut ->
            StreamingShortcutLinks.canonicalize(shortcut.link) in selected &&
                    !ShortcutMenuPlanner.isInMenu(menuLinks, shortcut.link)
        }
        if (additions.isNotEmpty()) host.addShortcutsToMenu(additions)
        dialog.dismiss()
    }

    private fun showStatus(message: String) {
        statusView.text = message
        statusView.visibility = View.VISIBLE
        statusView.announceForAccessibility(message)
    }

    /**
     * Downloads the cover of a shortcut just added, when online artwork is on. It outlives the
     * sheet - closing it straight after adding must not abandon the download - and tells the host
     * afterwards, because the menu row was created and sent to the watch before the cover existed.
     */
    private fun cacheArtworkInBackground(shortcut: PlaylistShortcut) {
        if (!ShortcutArtworkFetcher.isEnabled(context)) return
        val appContext = context.applicationContext
        backgroundScope.launch {
            val changed = ShortcutArtworkFetcher.ensureCachedAll(appContext, listOf(shortcut))
            if (changed) {
                PlaylistShortcutStorage.syncToWatch(appContext)
                withContext(Dispatchers.Main) { host.onArtworkCached() }
            }
        }
    }

    // endregion

    // region Saved shortcuts

    private fun renderSaved() {
        savedList.removeAllViews()
        if (mode == Mode.PICK || library.isEmpty()) {
            savedSection.visibility = View.GONE
            return
        }
        savedSection.visibility = View.VISIBLE

        val menuLinks = host.menuShortcutLinks()
        val inflater = LayoutInflater.from(context)
        // What can still be added comes first; what is already on the menu sinks below it.
        val (inMenu, available) = library.partition { ShortcutMenuPlanner.isInMenu(menuLinks, it.link) }
        selected.retainAll(available.map { StreamingShortcutLinks.canonicalize(it.link) }.toSet())

        for (shortcut in available + inMenu) {
            val key = StreamingShortcutLinks.canonicalize(shortcut.link)
            val alreadyThere = shortcut in inMenu
            val row = inflater.inflate(R.layout.item_add_to_menu_saved, savedList, false)
            val check = row.findViewById<CheckBox>(R.id.saved_check)
            row.findViewById<TextView>(R.id.saved_name).text = shortcut.name
            row.findViewById<TextView>(R.id.saved_subtitle).text = buildString {
                append(PlaylistShortcutStorage.describe(context, shortcut))
                if (alreadyThere) append(" • ").append(context.getString(R.string.add_menu_in_menu))
            }
            bindIcon(row.findViewById(R.id.saved_icon), shortcut)
            check.buttonTintList = accentCheckTint(ContextCompat.getColor(context, R.color.lyra_text_secondary))
            check.isChecked = alreadyThere || key in selected
            check.isEnabled = !alreadyThere
            row.isEnabled = !alreadyThere
            row.alpha = if (alreadyThere) DIMMED_ALPHA else 1f
            // The checkbox is decoration for sight; the row is what a screen reader lands on, so
            // the row has to say it is a checkbox and whether it is ticked.
            ViewCompat.setAccessibilityDelegate(row, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = CheckBox::class.java.name
                    info.isCheckable = true
                    info.isChecked = check.isChecked
                }
            })
            if (!alreadyThere) {
                row.setOnClickListener {
                    if (!selected.add(key)) selected.remove(key)
                    check.isChecked = key in selected
                    row.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
                    updateAddSelectedButton()
                }
            }
            savedList.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        updateAddSelectedButton()
    }

    private fun updateAddSelectedButton() {
        addSelectedButton.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        addSelectedButton.text = context.resources.getQuantityString(
                R.plurals.add_menu_add_selected, selected.size, selected.size)
    }

    /** Cover if one was fetched, else the app's own icon, else a plain glyph - the order the
     *  shortcuts screen uses, so a shortcut looks the same here as where it was saved. */
    private fun bindIcon(view: ImageView, shortcut: PlaylistShortcut) {
        val service = StreamingShortcutLinks.detect(shortcut.link)
        val cover = ShortcutArtworkStore.get(context, shortcut.link)
                ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val appIcon = service.packageName?.let { packageName ->
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                null
            }
        }
        val padding = (GLYPH_PADDING_DP * context.resources.displayMetrics.density).toInt()
        when {
            cover != null -> {
                view.imageTintList = null
                view.setPadding(0, 0, 0, 0)
                view.scaleType = ImageView.ScaleType.CENTER_CROP
                view.clipToOutline = true
                view.setImageBitmap(cover)
            }
            appIcon != null -> {
                view.imageTintList = null
                view.setPadding(padding, padding, padding, padding)
                view.setImageDrawable(appIcon)
            }
            else -> {
                view.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.lyra_on_surface))
                view.setPadding(padding, padding, padding, padding)
                view.setImageResource(
                        if (service == StreamingService.YOUTUBE_MUSIC) R.drawable.ic_yt_music
                        else R.drawable.ic_music_note)
            }
        }
    }

    // endregion

    private companion object {
        const val LOOKUP_DEBOUNCE_MS = 400L
        const val COVER_WAIT_MS = 4_000L
        const val GLYPH_PADDING_DP = 9
        const val DIMMED_ALPHA = 0.55f

        /** Process-wide on purpose: a cover download has to finish after its sheet has gone. */
        val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
