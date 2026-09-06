package com.svartifoss.snfell.view.watchface.theme

import android.content.SharedPreferences
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.watchface.WatchPreviewView

/** Full-screen catalog for built-in presets and phone-local user themes. Tapping a row is an
 * explicit apply action; the repository performs one atomic preference materialization and the
 * application-level sync coordinator delivers it independently of this Activity's lifecycle. */
/**
 * Faces hidden from every picker unless the developer "Show archived options" switch is on.
 *
 * A single object because the face picker and the Watch themes list each used to keep their own
 * copy, and they drifted: a face archived in one kept showing in the other.
 */
/** Moved to `common` so the on-watch picker shares one list - see
 *  [com.svartifoss.snfell.common.ArchivedFaces]. Kept as an alias so the phone's call sites and
 *  their history stay put. */
typealias ArchivedFaces = com.svartifoss.snfell.common.ArchivedFaces

class WatchThemesActivity : AppCompatActivity() {

    private lateinit var repository: WatchThemeRepository
    private lateinit var defaultPrefs: SharedPreferences
    private lateinit var adapter: ThemeAdapter
    private lateinit var preview: WatchPreviewView
    private lateinit var activeName: TextView
    private lateinit var activeLayout: TextView
    private var accentColor: Int = Color.WHITE
    private val accountRepository = CommunityThemeAccountRepository()

    /**
     * This device's reserved community author name, once loaded - `null` until the fetch resolves
     * or when nobody is Google-signed-in / hasn't claimed one. A local theme was never signed, so
     * showing this here rather than any Firestore-stored provenance is what lets every local theme
     * carry a name at all, published or not.
     */
    private var communityAuthorName: String? = null
    private val onlineThemesLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // The gallery performs the actual install/apply, but this parent remains the route
            // back to the Watch tab. Forward the result so its active-theme card and live preview
            // are refreshed when the user returns there.
            setResult(RESULT_OK)
            refreshScreen()
        }
    }
    private val communitySubmissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // A successful queueing leaves the saved profile intact, but refresh the list in case
            // the user captured edits immediately before opening the submission screen.
            refreshScreen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_themes)

        repository = WatchThemeRepository(this)
        defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        repository.captureActive(defaultPrefs)
        accentColor = LyraAccent.contrastSafe(this, LyraAccent.resolve(this))

        preview = findViewById(R.id.theme_preview)
        activeName = findViewById(R.id.active_theme_name)
        activeLayout = findViewById(R.id.active_theme_layout)
        findViewById<View>(R.id.button_back).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.button_create_theme).apply {
            backgroundTintList = ColorStateList.valueOf(LyraAccent.resolve(this@WatchThemesActivity))
            setTextColor(onAccentColor(LyraAccent.resolve(this@WatchThemesActivity)))
            iconTint = ColorStateList.valueOf(onAccentColor(LyraAccent.resolve(this@WatchThemesActivity)))
            setOnClickListener { showCreateDialog() }
        }
        findViewById<ImageView>(R.id.community_themes_icon).imageTintList =
                ColorStateList.valueOf(LyraAccent.resolve(this))
        findViewById<MaterialCardView>(R.id.button_browse_community_themes).apply {
            setOnClickListener {
                onlineThemesLauncher.launch(
                        Intent(this@WatchThemesActivity, OnlineThemesActivity::class.java))
            }
        }

        adapter = ThemeAdapter(
                onApplyBuiltIn = ::applyBuiltIn,
                onApplyCustom = ::applyCustom,
                onMoreBuiltIn = ::showBuiltInMenu,
                onMoreCustom = ::showCustomMenu)
        findViewById<RecyclerView>(R.id.theme_list).apply {
            layoutManager = LinearLayoutManager(this@WatchThemesActivity)
            adapter = this@WatchThemesActivity.adapter
        }
        refreshScreen()
        // Fetched once per screen visit, not per row: this is a Firestore read, and every custom
        // row shares the same name. Rows show "Custom theme" until it resolves (or forever, if
        // nobody is signed in or hasn't claimed a name), then refreshScreen() re-binds them.
        lifecycleScope.launch { loadCommunityAuthorName() }
    }

    private suspend fun loadCommunityAuthorName() {
        val result = accountRepository.publicAuthorIdentity()
        val name = (result as? CommunityThemeAuthorIdentityLoadResult.Claimed)?.identity?.authorName
        if (name == communityAuthorName) return
        communityAuthorName = name
        refreshScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) {
            repository.captureActive(defaultPrefs)
            refreshScreen()
        }
    }

    private fun refreshScreen() {
        val appearance = ThemeAppearance.resolve(defaultPrefs)
        val profiles = repository.load()
        val activeProfile = (appearance as? AppearanceContext.Custom)?.let { custom ->
            profiles.firstOrNull { it.id == custom.themeId }
        }
        val displayName = when (appearance) {
            is AppearanceContext.Custom ->
                activeProfile?.name ?: getString(R.string.watch_theme_unknown_name)
            is AppearanceContext.BuiltIn ->
                WatchThemeRepository.displayNameForFace(this, appearance.baseFace)
        }
        val layoutName = WatchThemeRepository.displayNameForFace(this, appearance.baseFace)
        activeName.text = displayName
        activeLayout.text = getString(R.string.watch_theme_active_layout, layoutName)
        preview.contentDescription = getString(
                R.string.watch_theme_preview_description) + ". " + displayName
        preview.refresh()

        val rows = buildList<ThemeRow> {
            // The user's own themes lead, ahead of the eighteen built-in faces, matching the
            // on-watch picker. Someone who has saved themes is almost always switching between
            // *those*, and behind the whole built-in list they were a scroll away every time.
            // The header is omitted when there are none: an empty titled section promises content
            // that never arrives, and the Create button above already offers the way in.
            if (profiles.isNotEmpty()) {
                add(ThemeRow.Header(getString(R.string.watch_theme_custom_section)))
                profiles.forEach { add(ThemeRow.Custom(it)) }
            }
            add(ThemeRow.Header(getString(R.string.watch_theme_built_in_section)))
            val showArchived = defaultPrefs.getBoolean("dev_show_archived", false)
            ThemeAppearance.ALLOWED_BASE_FACES.filter { face ->
                showArchived || face !in ArchivedFaces.KEYS || appearance.baseFace == face
            }.forEach { face ->
                add(ThemeRow.BuiltIn(face))
            }
        }
        adapter.submit(rows, appearance)
    }

    private fun applyBuiltIn(face: String) {
        val active = ThemeAppearance.resolve(defaultPrefs)
        if (active is AppearanceContext.BuiltIn && active.baseFace == face) return
        if (!repository.applyBuiltIn(defaultPrefs, face)) {
            Toast.makeText(this, R.string.watch_theme_apply_error, Toast.LENGTH_LONG).show()
            return
        }
        val name = WatchThemeRepository.displayNameForFace(this, face)
        afterActiveMutation(name)
    }

    private fun applyCustom(profile: WatchThemeProfile) {
        val active = ThemeAppearance.resolve(defaultPrefs) as? AppearanceContext.Custom
        if (active?.themeId == profile.id) return
        if (!repository.applyProfile(defaultPrefs, profile)) {
            Toast.makeText(this, R.string.watch_theme_apply_error, Toast.LENGTH_LONG).show()
            return
        }
        afterActiveMutation(profile.name)
    }

    private fun afterActiveMutation(name: String) {
        setResult(RESULT_OK)
        refreshScreen()
        findViewById<View>(R.id.theme_list).announceForAccessibility(
                getString(R.string.watch_theme_apply_success, name))
        Toast.makeText(
                this, getString(R.string.watch_theme_apply_success, name), Toast.LENGTH_SHORT).show()
    }

    private fun showCreateDialog() {
        if (repository.profiles.size >= WatchThemeRepository.MAX_PROFILES) {
            Toast.makeText(this, R.string.watch_theme_limit_reached, Toast.LENGTH_LONG).show()
            return
        }
        val baseFace = ThemeAppearance.resolve(defaultPrefs).baseFace
        showThemeDialog(
                title = getString(R.string.watch_theme_create_title),
                initialName = getString(
                        R.string.watch_theme_default_name,
                        WatchThemeRepository.displayNameForFace(this, baseFace)),
                initialBaseFace = baseFace,
                showBaseFace = true,
                excludedProfileId = null
        ) { name, chosenFace ->
            try {
                val created = repository.create(name, chosenFace, defaultPrefs)
                if (repository.applyProfile(defaultPrefs, created)) {
                    afterActiveMutation(created.name)
                    // Creation continues directly in the existing six-section Watch editor,
                    // which now writes to this profile's isolated custom_active namespace.
                    finish()
                } else {
                    Toast.makeText(this, R.string.watch_theme_apply_error, Toast.LENGTH_LONG).show()
                }
            } catch (_: WatchThemeLimitReachedException) {
                Toast.makeText(this, R.string.watch_theme_limit_reached, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCustomMenu(anchor: View, profile: WatchThemeProfile) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_CUSTOMIZE, 0, R.string.watch_theme_customize)
            // A gallery install retains its public source so it cannot be re-submitted unchanged.
            // Duplicating it explicitly creates a user-owned fork, which can later be edited and
            // submitted like any other local theme.
            if (profile.publishedTheme == null) {
                menu.add(0, MENU_SUBMIT, 1, R.string.watch_theme_submit_community)
            }
            menu.add(0, MENU_RENAME, 2, R.string.watch_theme_rename)
            menu.add(0, MENU_DUPLICATE, 3, R.string.watch_theme_duplicate)
            menu.add(0, MENU_DELETE, 4, R.string.watch_theme_delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_CUSTOMIZE -> customize(profile)
                    MENU_SUBMIT -> submitToCommunity(profile)
                    MENU_RENAME -> showRenameDialog(profile)
                    MENU_DUPLICATE -> duplicate(profile)
                    MENU_DELETE -> confirmDelete(profile)
                }
                true
            }
            show()
        }
    }

    private fun showBuiltInMenu(anchor: View, face: String) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_DUPLICATE, 0, R.string.watch_theme_duplicate)
            setOnMenuItemClickListener { item ->
                if (item.itemId == MENU_DUPLICATE) {
                    duplicateBuiltIn(face)
                    true
                } else {
                    false
                }
            }
            show()
        }
    }

    private fun submitToCommunity(profile: WatchThemeProfile) {
        // Capture any changes made through the normal Watch editor before handing only the local
        // identifier to the submission screen. The profile itself stays private in this Activity;
        // the submission boundary reloads and validates it from the local library.
        repository.captureActive(defaultPrefs)
        // captureActive only refreshes whichever theme is currently active, which is not
        // necessarily this one. A theme saved before a later update added new appearance keys
        // needs the same backfill before it can pass the submission screen's complete-snapshot
        // check.
        repository.ensureCurrentSchema(profile.id, defaultPrefs)
        communitySubmissionLauncher.launch(Intent(
                this, SubmitCommunityThemeActivity::class.java).putExtra(
                SubmitCommunityThemeActivity.EXTRA_PROFILE_ID, profile.id))
    }

    private fun customize(profile: WatchThemeProfile) {
        val activeId = (ThemeAppearance.resolve(defaultPrefs) as? AppearanceContext.Custom)?.themeId
        if (activeId == profile.id) {
            setResult(RESULT_OK)
            finish()
            return
        }
        if (!repository.applyProfile(defaultPrefs, profile)) {
            Toast.makeText(this, R.string.watch_theme_apply_error, Toast.LENGTH_LONG).show()
            return
        }
        afterActiveMutation(profile.name)
        finish()
    }

    private fun showRenameDialog(profile: WatchThemeProfile) {
        showThemeDialog(
                title = getString(R.string.watch_theme_rename_title),
                initialName = profile.name,
                initialBaseFace = profile.baseFace,
                showBaseFace = false,
                excludedProfileId = profile.id
        ) { name, _ ->
            val active = (ThemeAppearance.resolve(defaultPrefs) as? AppearanceContext.Custom)
                    ?.themeId == profile.id
            val renamed = repository.rename(profile, name)
            if (active) {
                if (repository.applyProfile(defaultPrefs, renamed)) {
                    afterActiveMutation(renamed.name)
                } else {
                    Toast.makeText(this, R.string.watch_theme_apply_error, Toast.LENGTH_LONG).show()
                }
            } else {
                refreshScreen()
            }
        }
    }

    private fun duplicate(profile: WatchThemeProfile) {
        try {
            val copy = repository.duplicate(profile)
            refreshScreen()
            Toast.makeText(
                    this,
                    getString(R.string.watch_theme_duplicated, copy.name),
                    Toast.LENGTH_SHORT).show()
        } catch (_: WatchThemeLimitReachedException) {
            Toast.makeText(this, R.string.watch_theme_limit_reached, Toast.LENGTH_LONG).show()
        }
    }

    private fun duplicateBuiltIn(face: String) {
        try {
            val copy = repository.duplicateBuiltIn(face, defaultPrefs)
            refreshScreen()
            Toast.makeText(
                    this,
                    getString(R.string.watch_theme_duplicated, copy.name),
                    Toast.LENGTH_SHORT).show()
        } catch (_: WatchThemeLimitReachedException) {
            Toast.makeText(this, R.string.watch_theme_limit_reached, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete(profile: WatchThemeProfile) {
        val dialog = AlertDialog.Builder(this)
                .setTitle(getString(R.string.watch_theme_delete_title, profile.name))
                .setMessage(R.string.watch_theme_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.watch_theme_delete) { _, _ -> delete(profile) }
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accentColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accentColor)
        }
        dialog.show()
    }

    private fun delete(profile: WatchThemeProfile) {
        val active = (ThemeAppearance.resolve(defaultPrefs) as? AppearanceContext.Custom)
                ?.themeId == profile.id
        if (active && !repository.applyBuiltIn(defaultPrefs, profile.baseFace)) {
            Toast.makeText(this, R.string.watch_theme_apply_error, Toast.LENGTH_LONG).show()
            return
        }
        repository.delete(profile)
        refreshScreen()
        Toast.makeText(this, R.string.watch_theme_deleted, Toast.LENGTH_SHORT).show()
        if (active) {
            setResult(RESULT_OK)
        }
    }

    private fun showThemeDialog(
            title: String,
            initialName: String,
            initialBaseFace: String,
            showBaseFace: Boolean,
            excludedProfileId: String?,
            onAccepted: (String, String) -> Unit
    ) {
        val content = layoutInflater.inflate(R.layout.dialog_watch_theme, null)
        val nameLayout = content.findViewById<TextInputLayout>(R.id.theme_name_layout)
        val nameInput = content.findViewById<TextInputEditText>(R.id.theme_name_input)
        // Cursor/handles/highlight otherwise stay the theme's default sage green, not the accent.
        LyraAccent.applyToEditText(nameInput, accentColor)
        val baseContainer = content.findViewById<LinearLayout>(R.id.base_face_container)
        val spinner = content.findViewById<androidx.appcompat.widget.AppCompatSpinner>(
                R.id.base_face_spinner)
        // Keep the normal catalog focused, matching WatchFacePrefsFragment. An archived layout
        // remains selectable when it is already active so creating a theme never silently swaps
        // the user's base; developer archived options reveal the complete renderer catalog.
        val showArchived = defaultPrefs.getBoolean("dev_show_archived", false)
        val faces = ThemeAppearance.ALLOWED_BASE_FACES.filter { face ->
            showArchived || face !in ArchivedFaces.KEYS || face == initialBaseFace
        }
        val labels = faces.map { WatchThemeRepository.displayNameForFace(this, it) }
        spinner.adapter = ArrayAdapter(this, R.layout.item_spinner_lyra, labels).apply {
            // The closed control uses the vertically-centered Lyra item; the expanded list keeps
            // the platform dropdown row (checkmark + row height). Passing the dropdown item as the
            // closed view is what left the selected label misaligned against the control.
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.setSelection(faces.indexOf(initialBaseFace).coerceAtLeast(0))
        baseContainer.visibility = if (showBaseFace) View.VISIBLE else View.GONE
        nameInput.setText(initialName)
        nameInput.setSelection(initialName.length)

        val dialog = AlertDialog.Builder(this)
                .setTitle(title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create()
        dialog.setOnShowListener {
            // Buttons and the name field's box/label otherwise stay the theme's default sage
            // green like the EditText cursor did before LyraAccent.applyToEditText above.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accentColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accentColor)
            nameLayout.boxStrokeColor = accentColor
            nameLayout.defaultHintTextColor = ColorStateList.valueOf(accentColor)
            // The character counter ("12/48") otherwise stays Material's default sage green too.
            nameLayout.setCounterTextColor(ColorStateList.valueOf(accentColor))

            fun validate(): Boolean {
                val candidate = nameInput.text?.toString()?.trim().orEmpty()
                val error = when {
                    candidate.isBlank() -> getString(R.string.watch_theme_name_required)
                    !isNameAvailable(candidate, excludedProfileId) ->
                        getString(R.string.watch_theme_name_duplicate)
                    else -> null
                }
                nameLayout.error = error
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = error == null
                return error == null
            }
            nameInput.doAfterTextChanged { validate() }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!validate()) return@setOnClickListener
                val selectedFace = faces.getOrElse(spinner.selectedItemPosition) {
                    ThemeAppearance.DEFAULT_FACE
                }
                onAccepted(nameInput.text?.toString()?.trim().orEmpty(), selectedFace)
                dialog.dismiss()
            }
            validate()
            nameInput.requestFocus()
        }
        dialog.show()
    }

    private fun isNameAvailable(candidate: String, excludedProfileId: String?): Boolean =
            repository.profiles.none { profile ->
                profile.id != excludedProfileId && profile.name.equals(candidate, ignoreCase = true)
            }

    private fun onAccentColor(accent: Int): Int =
            if (ColorUtils.calculateContrast(Color.WHITE, accent) >= 4.5) Color.WHITE else Color.BLACK

    private sealed class ThemeRow {
        data class Header(val title: String) : ThemeRow()
        data class BuiltIn(val face: String) : ThemeRow()
        data class Custom(val profile: WatchThemeProfile) : ThemeRow()
    }

    private inner class ThemeAdapter(
            private val onApplyBuiltIn: (String) -> Unit,
            private val onApplyCustom: (WatchThemeProfile) -> Unit,
            private val onMoreBuiltIn: (View, String) -> Unit,
            private val onMoreCustom: (View, WatchThemeProfile) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var rows: List<ThemeRow> = emptyList()
        private var active: AppearanceContext = AppearanceContext.BuiltIn(ThemeAppearance.DEFAULT_FACE)

        fun submit(newRows: List<ThemeRow>, newActive: AppearanceContext) {
            rows = newRows
            active = newActive
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int =
                if (rows[position] is ThemeRow.Header) VIEW_HEADER else VIEW_THEME

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == VIEW_HEADER) {
                HeaderHolder(inflater.inflate(R.layout.item_watch_theme_section, parent, false))
            } else {
                ThemeHolder(inflater.inflate(R.layout.item_watch_theme, parent, false))
            }
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is ThemeRow.Header -> (holder as HeaderHolder).title.text = row.title
                is ThemeRow.BuiltIn -> (holder as ThemeHolder).bindBuiltIn(row.face)
                is ThemeRow.Custom -> (holder as ThemeHolder).bindCustom(row.profile)
            }
        }

        private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.section_title)
        }

        private inner class ThemeHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val card: MaterialCardView = view.findViewById(R.id.theme_card)
            private val icon: ImageView = view.findViewById(R.id.theme_icon)
            private val name: TextView = view.findViewById(R.id.theme_name)
            private val subtitle: TextView = view.findViewById(R.id.theme_subtitle)
            private val status: TextView = view.findViewById(R.id.theme_status)
            private val selected: ImageView = view.findViewById(R.id.theme_selected)
            private val more: ImageButton = view.findViewById(R.id.theme_more)

            fun bindBuiltIn(face: String) {
                val display = WatchThemeRepository.displayNameForFace(this@WatchThemesActivity, face)
                val description = getString(R.string.watch_theme_builtin_subtitle, display)
                val applied = active is AppearanceContext.BuiltIn && active.baseFace == face
                bindCommon(display, description, applied)
                more.visibility = View.VISIBLE
                more.setOnClickListener { onMoreBuiltIn(it, face) }
                card.setOnClickListener { onApplyBuiltIn(face) }
            }

            fun bindCustom(profile: WatchThemeProfile) {
                val layout = WatchThemeRepository.displayNameForFace(
                        this@WatchThemesActivity, profile.baseFace)
                // The author name (once loaded) replaces the generic "Custom theme" label - a
                // local profile has no stored provenance of its own, so this is this device's
                // *signed* community identity standing in for it, not per-theme authorship.
                val author = communityAuthorName
                val description = if (author != null) {
                    getString(R.string.watch_theme_custom_subtitle_authored, author, layout)
                } else {
                    getString(R.string.watch_theme_custom_subtitle, layout)
                }
                val applied = (active as? AppearanceContext.Custom)?.themeId == profile.id
                bindCommon(profile.name, description, applied)
                more.visibility = View.VISIBLE
                more.setOnClickListener { onMoreCustom(it, profile) }
                card.setOnClickListener { onApplyCustom(profile) }
            }

            private fun bindCommon(display: String, description: String, applied: Boolean) {
                name.text = display
                subtitle.text = description
                status.visibility = if (applied) View.VISIBLE else View.GONE
                selected.visibility = if (applied) View.VISIBLE else View.GONE
                status.setTextColor(accentColor)
                selected.imageTintList = ColorStateList.valueOf(accentColor)
                icon.imageTintList = ColorStateList.valueOf(
                        if (applied) accentColor else getColor(R.color.lyra_on_surface))
                // Selection is already communicated by the checkmark/status and accent icon.
                // Keep the card outline neutral so a custom theme row never gains the coloured
                // frame the user explicitly asked to remove.
                card.strokeColor = getColor(R.color.lyra_divider)
                val oneDp = (resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(1)
                card.strokeWidth = oneDp
                card.isSelected = applied
                card.contentDescription = getString(
                        if (applied) R.string.watch_theme_row_description_applied
                        else R.string.watch_theme_row_description,
                        display,
                        description)
            }
        }
    }

    private companion object {
        const val VIEW_HEADER = 0
        const val VIEW_THEME = 1
        const val MENU_CUSTOMIZE = 1
        const val MENU_RENAME = 2
        const val MENU_DUPLICATE = 3
        const val MENU_DELETE = 4
        const val MENU_SUBMIT = 5
    }
}
