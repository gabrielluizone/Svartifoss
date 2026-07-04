package com.svartifoss.snfell.music

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One user-defined playlist shortcut shown on the watch: display name + deep link to open. */
data class PlaylistShortcut(val name: String, val link: String)

/**
 * Persists the playlist shortcuts as a JSON array in the default SharedPreferences. Configured
 * in [com.svartifoss.snfell.view.settings.PlaylistShortcutsActivity], read by
 * [com.svartifoss.snfell.actions.OpenPlaylistShortcutsAction] when the watch asks for
 * the list.
 */
object PlaylistShortcutStorage {
    private const val PREF_KEY = "playlist_shortcuts"

    fun load(context: Context): List<PlaylistShortcut> {
        val raw = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, null)
                ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val name = entry.optString("name")
                val link = entry.optString("link")
                if (name.isBlank() || link.isBlank()) null else PlaylistShortcut(name, link)
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** Human-readable description of a shortcut (source app + shuffle badge) - the secondary
     *  line on both the phone list and the watch menu. Never exposes the raw link. */
    fun describe(context: Context, shortcut: PlaylistShortcut): String {
        val host = Uri.parse(shortcut.link).host.orEmpty()
        val source = when {
            host.contains("youtube") -> context.getString(R.string.playlist_source_yt_music)
            host.isNotBlank() -> host.removePrefix("www.")
            else -> context.getString(R.string.playlist_source_link)
        }

        return if (shortcut.link.contains("shuffle=true")) {
            context.getString(
                    R.string.playlist_subtitle_format,
                    source,
                    context.getString(R.string.playlist_badge_shuffle)
            )
        } else {
            source
        }
    }

    fun save(context: Context, shortcuts: List<PlaylistShortcut>) {
        val array = JSONArray()
        for (shortcut in shortcuts) {
            array.put(
                    JSONObject()
                            .put("name", shortcut.name)
                            .put("link", shortcut.link)
            )
        }

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY, array.toString())
                .apply()
    }
}
