package com.svartifoss.snfell.view.actionconfigs

import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.symfonium.SymfoniumMediaStartAction

class SymfoniumMediaStartConfigFragment :
        ActionConfigFragment<SymfoniumMediaStartAction>(R.layout.fragment_config_symfonium_media_start) {
    private val mediaTypes = listOf(
            SymfoniumMediaStartAction.TYPE_SONG,
            SymfoniumMediaStartAction.TYPE_PLAYLIST,
            SymfoniumMediaStartAction.TYPE_ALBUM,
            SymfoniumMediaStartAction.TYPE_ARTIST,
            SymfoniumMediaStartAction.TYPE_GENRE,
            SymfoniumMediaStartAction.TYPE_SONG_MIX,
            SymfoniumMediaStartAction.TYPE_ALBUM_MIX,
            SymfoniumMediaStartAction.TYPE_INTERNET_RADIO)

    override fun onLoad(action: SymfoniumMediaStartAction) {
        val view = requireView()
        val type = view.findViewById<Spinner>(R.id.symfonium_media_type)
        type.adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.symfonium_media_type_entries,
                android.R.layout.simple_spinner_item).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        type.setSelection(mediaTypes.indexOf(action.mediaType).coerceAtLeast(0))

        val queue = view.findViewById<Spinner>(R.id.symfonium_queue_mode)
        queue.adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.symfonium_queue_mode_entries,
                android.R.layout.simple_spinner_item).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        queue.setSelection(action.queue.coerceIn(0, 2))

        view.findViewById<EditText>(R.id.symfonium_media_name).setText(action.name)
        view.findViewById<EditText>(R.id.symfonium_media_artist).setText(action.artist)
        view.findViewById<EditText>(R.id.symfonium_media_album).setText(action.album)
        view.findViewById<EditText>(R.id.symfonium_media_id).setText(action.id?.toString().orEmpty())
        view.findViewById<CheckBox>(R.id.symfonium_resume).isChecked = action.resume
        view.findViewById<CheckBox>(R.id.symfonium_shuffle).isChecked = action.shuffle
    }

    override fun save(action: SymfoniumMediaStartAction) {
        val view = requireView()
        val type = view.findViewById<Spinner>(R.id.symfonium_media_type)
        action.mediaType = mediaTypes.getOrElse(type.selectedItemPosition) { mediaTypes.first() }
        action.queue = view.findViewById<Spinner>(R.id.symfonium_queue_mode)
                .selectedItemPosition.coerceIn(0, 2)
        action.name = view.findViewById<EditText>(R.id.symfonium_media_name).text.toString()
        action.artist = view.findViewById<EditText>(R.id.symfonium_media_artist).text.toString()
        action.album = view.findViewById<EditText>(R.id.symfonium_media_album).text.toString()
        action.id = view.findViewById<EditText>(R.id.symfonium_media_id).text.toString()
                .trim().toLongOrNull()
        action.resume = view.findViewById<CheckBox>(R.id.symfonium_resume).isChecked
        action.shuffle = view.findViewById<CheckBox>(R.id.symfonium_shuffle).isChecked
    }
}
