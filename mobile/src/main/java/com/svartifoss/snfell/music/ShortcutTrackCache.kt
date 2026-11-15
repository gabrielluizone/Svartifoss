package com.svartifoss.snfell.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * The song lists [ShortcutTracksResponder] looked up, one small JSON file per playlist.
 *
 * In `cacheDir`, never `filesDir`, for the reason `OnlineArtworkCache` records: `ConfigBackup`
 * walks `filesDir`, and these are disposable answers, not user data. Kept at most
 * [ShortcutTrackListing.MAX_AGE_MS], inside the 30 days the YouTube API Services policies allow,
 * and trimmed to [MAX_FILES]. A playlist the API said does not exist (private, deleted) is stored
 * as a miss, so opening its shortcut again does not spend another quota unit on the same answer.
 */
internal object ShortcutTrackCache {

    private const val FOLDER = "shortcut_tracks"
    private const val MAX_FILES = 48

    /** A stored answer. [missing] is a recorded "this playlist cannot be listed". */
    class Entry(
            val fetchedAtMs: Long,
            val missing: Boolean,
            val total: Int?,
            val tracks: List<ShortcutTrackListing.Track>
    )

    private fun file(context: Context, playlistId: String): File =
            File(File(context.cacheDir, FOLDER).apply { mkdirs() }, "$playlistId.json")

    /** The stored answer for [playlistId], or null when there is none worth keeping. */
    fun read(context: Context, playlistId: String, nowMs: Long): Entry? {
        val file = file(context, playlistId)
        if (!file.isFile) return null
        val entry = try {
            val json = JSONObject(file.readText())
            val tracks = json.optJSONArray("tracks") ?: JSONArray()
            Entry(
                    fetchedAtMs = json.optLong("fetchedAt"),
                    missing = json.optBoolean("missing"),
                    total = json.takeIf { it.has("total") }?.optInt("total"),
                    tracks = (0 until tracks.length()).mapNotNull { index ->
                        val track = tracks.optJSONObject(index) ?: return@mapNotNull null
                        val videoId = track.optString("v")
                        val title = track.optString("t")
                        if (videoId.isEmpty() || title.isEmpty()) return@mapNotNull null
                        ShortcutTrackListing.Track(videoId, title,
                                track.optString("a").takeIf { it.isNotEmpty() })
                    })
        } catch (e: Exception) {
            Timber.w(e, "Unreadable song list cache entry; dropping it")
            file.delete()
            return null
        }
        if (ShortcutTrackListing.cacheUse(nowMs - entry.fetchedAtMs) ==
                ShortcutTrackListing.CacheUse.EXPIRED) {
            file.delete()
            return null
        }
        return entry
    }

    fun writeTracks(context: Context, playlistId: String, nowMs: Long, total: Int?,
                    tracks: List<ShortcutTrackListing.Track>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(JSONObject()
                    .put("v", track.videoId)
                    .put("t", track.title)
                    .apply { track.artist?.let { put("a", it) } })
        }
        write(context, playlistId, JSONObject()
                .put("fetchedAt", nowMs)
                .put("missing", false)
                .apply { total?.let { put("total", it) } }
                .put("tracks", array))
    }

    fun writeMissing(context: Context, playlistId: String, nowMs: Long) {
        write(context, playlistId, JSONObject().put("fetchedAt", nowMs).put("missing", true))
    }

    private fun write(context: Context, playlistId: String, json: JSONObject) {
        val target = file(context, playlistId)
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            temp.writeText(json.toString())
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not store a song list")
            temp.delete()
            return
        }
        trim(target.parentFile ?: return)
    }

    private fun trim(folder: File) {
        val files = folder.listFiles { file -> file.name.endsWith(".json") } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }
                .take(files.size - MAX_FILES)
                .forEach { it.delete() }
    }
}
