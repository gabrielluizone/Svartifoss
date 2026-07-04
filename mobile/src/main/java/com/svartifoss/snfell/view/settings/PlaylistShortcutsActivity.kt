package com.svartifoss.snfell.view.settings

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.PlaylistShortcut
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import com.svartifoss.snfell.view.LyraAccent

/**
 * Manages the playlist shortcuts shown by the watch's "My playlists" action: a simple list of
 * name + deep link pairs persisted via [PlaylistShortcutStorage]. The watch only ever *reads*
 * this list (via OpenPlaylistShortcutsAction) - all editing happens here on the phone, once.
 *
 * Also doubles as the chooser for the "Open specific playlist" action: launched with
 * [EXTRA_PICK_MODE], tapping a shortcut returns it as the activity result instead of opening
 * the edit dialog (adding/deleting still works, so a missing playlist can be created on the
 * spot and picked right away) - see [PlaylistShortcutPickerAction].
 */
class PlaylistShortcutsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PICK_MODE = "PickMode"
        const val EXTRA_PICKED_NAME = "PickedName"
        const val EXTRA_PICKED_LINK = "PickedLink"

        fun createPickIntent(context: Context): Intent =
                Intent(context, PlaylistShortcutsActivity::class.java)
                        .putExtra(EXTRA_PICK_MODE, true)
    }

    private lateinit var shortcuts: MutableList<PlaylistShortcut>
    private lateinit var adapter: ShortcutsAdapter
    private lateinit var emptyLabel: TextView
    private var pickMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_shortcuts)

        pickMode = intent.getBooleanExtra(EXTRA_PICK_MODE, false)
        title = getString(
                if (pickMode) R.string.playlist_shortcut_pick_title
                else R.string.setting_playlist_shortcuts
        )

        shortcuts = PlaylistShortcutStorage.load(this).toMutableList()
        adapter = ShortcutsAdapter()
        emptyLabel = findViewById(R.id.empty_label)

        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@PlaylistShortcutsActivity)
            adapter = this@PlaylistShortcutsActivity.adapter
        }

        findViewById<MaterialButton>(R.id.button_add).apply {
            setOnClickListener { showShortcutDialog() }
            // Follow the accent currently on screen (dynamic/custom/default).
            val accent = LyraAccent.resolve(this@PlaylistShortcutsActivity)
            setTextColor(accent)
            iconTint = ColorStateList.valueOf(accent)
        }

        updateEmptyLabel()
    }

    /** Add ([editIndex] null) or edit an existing shortcut - same dialog, prefilled when
     *  editing. Shuffle is stored inside the link (see [addShuffleParam]), so editing shows
     *  the clean link and reflects shuffle through the checkbox instead. */
    private fun showShortcutDialog(editIndex: Int? = null) {
        val existing = editIndex?.let { shortcuts.getOrNull(it) }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val nameInput = EditText(this).apply {
            hint = getString(R.string.playlist_shortcut_name_hint)
            maxLines = 1
            setText(existing?.name.orEmpty())
        }
        val linkInput = EditText(this).apply {
            hint = getString(R.string.playlist_shortcut_link_hint)
            maxLines = 1
            setText(existing?.link?.let { stripShuffleParam(it) }.orEmpty())
        }
        val accent = LyraAccent.resolve(this)
        val secondary = ContextCompat.getColor(this, R.color.lyra_text_secondary)
        val shuffleCheckbox = CheckBox(this).apply {
            text = getString(R.string.playlist_shortcut_shuffle)
            // Same google_sans metric fix as LyraDialogButton - the default font padding
            // floats the label above the checkbox graphic.
            includeFontPadding = false
            buttonTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(accent, secondary)
            )
            isChecked = existing?.link?.contains("shuffle=true") == true
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(nameInput)
            addView(linkInput)
            addView(shuffleCheckbox)
        }

        val dialog = AlertDialog.Builder(this)
                .setTitle(
                        if (existing == null) R.string.playlist_shortcuts_add
                        else R.string.playlist_shortcuts_edit
                )
                .setView(container)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val name = nameInput.text.toString().trim()
                    var link = linkInput.text.toString().trim()
                    if (shuffleCheckbox.isChecked) {
                        link = addShuffleParam(link)
                    }
                    if (name.isEmpty() || link.isEmpty()) {
                        return@setPositiveButton
                    }
                    if (editIndex != null && editIndex in shortcuts.indices) {
                        shortcuts[editIndex] = PlaylistShortcut(name, link)
                        persist()
                        adapter.notifyItemChanged(editIndex)
                    } else {
                        shortcuts.add(PlaylistShortcut(name, link))
                        persist()
                        adapter.notifyItemInserted(shortcuts.size - 1)
                        updateEmptyLabel()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()

        // Button colors can't come from the (static) theme when they should follow the
        // runtime accent - same treatment as the gesture picker's OK/Cancel.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondary)
    }

    /** Bakes shuffle into the stored link itself (the watch round-trips the raw link as the
     *  entry id, so there's nowhere else to carry the flag). `shuffle=true` is the same query
     *  param YT Music's own Shuffle button appends - honored at playback start, unlike
     *  `MediaSession.setShuffleMode` (see PlayLikedSongsShuffledAction). */
    private fun addShuffleParam(link: String): String {
        if (link.contains("shuffle=")) {
            return link
        }
        val separator = if (link.contains('?')) "&" else "?"
        return link + separator + "shuffle=true"
    }

    /** Inverse of [addShuffleParam], for showing the clean link in the edit dialog. */
    private fun stripShuffleParam(link: String): String {
        return link
                .replace("?shuffle=true&", "?")
                .replace("&shuffle=true", "")
                .replace("?shuffle=true", "")
    }

    private fun removeShortcut(position: Int) {
        if (position !in shortcuts.indices) {
            return
        }
        shortcuts.removeAt(position)
        persist()
        adapter.notifyItemRemoved(position)
        updateEmptyLabel()
    }

    private fun persist() {
        PlaylistShortcutStorage.save(this, shortcuts)
    }

    private fun updateEmptyLabel() {
        emptyLabel.visibility = if (shortcuts.isEmpty()) TextView.VISIBLE else TextView.GONE
        if (pickMode) {
            emptyLabel.text = getString(R.string.playlist_shortcut_pick_empty)
        }
    }

    private inner class ShortcutsAdapter : RecyclerView.Adapter<ShortcutViewHolder>() {
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
            holder.sourceIcon.setImageResource(
                    if (shortcut.link.contains("youtube")) R.drawable.ic_yt_music
                    else R.drawable.ic_music_note
            )
            holder.delete.setOnClickListener { removeShortcut(holder.bindingAdapterPosition) }
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
    }

    private class ShortcutViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
        val sourceIcon: ImageView = view.findViewById(R.id.source_icon)
        val delete: ImageButton = view.findViewById(R.id.delete)
    }
}
