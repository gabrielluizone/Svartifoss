package com.svartifoss.snfell.music

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONException

/**
 * Persists recent search queries (newest first, capped, deduplicated) as a JSON array in the
 * default SharedPreferences. Recorded on every [MusicService.playFromSearch] call, shown on the
 * watch by [com.svartifoss.snfell.actions.OpenSearchHistoryAction], and individually
 * deletable from the watch's menu (see CommPaths.MESSAGE_DELETE_CUSTOM_LIST_ITEM).
 */
object SearchHistoryStorage {
    private const val PREF_KEY = "search_history"
    private const val MAX_ENTRIES = 20

    fun load(context: Context): List<String> {
        val raw = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, null)
                ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index -> array.optString(index).ifBlank { null } }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** Moves [query] to the front of the list (removing any earlier duplicate), capped at
     *  [MAX_ENTRIES]. */
    fun record(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return
        }

        val updated = listOf(trimmed) + load(context).filterNot { it.equals(trimmed, ignoreCase = true) }
        save(context, updated.take(MAX_ENTRIES))
    }

    fun remove(context: Context, query: String) {
        save(context, load(context).filterNot { it == query })
    }

    private fun save(context: Context, queries: List<String>) {
        val array = JSONArray()
        queries.forEach { array.put(it) }

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY, array.toString())
                .apply()
    }
}
