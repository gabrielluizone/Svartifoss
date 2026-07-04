package com.svartifoss.snfell.music

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Persists [MusicService.recentTrackHistory] (newest first, capped) as a JSON array in the
 * default SharedPreferences, so "recently played" survives MusicService being torn down and
 * recreated (e.g. after the watch UI closes and music stops) - which used to reset the in-memory
 * list to empty. The history itself is already app-agnostic: it's recorded from whichever
 * MediaController is currently active, regardless of which app owns it.
 */
object TrackHistoryStorage {
    private const val PREF_KEY = "track_history"

    fun load(context: Context): List<TrackHistoryEntry> {
        val raw = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, null)
                ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val artist = entry.optString("artist")
                val title = entry.optString("title")
                if (title.isBlank()) null else TrackHistoryEntry(artist, title)
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun save(context: Context, entries: List<TrackHistoryEntry>) {
        val array = JSONArray()
        for (entry in entries) {
            array.put(
                    JSONObject()
                            .put("artist", entry.artist)
                            .put("title", entry.title)
            )
        }

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY, array.toString())
                .apply()
    }
}
