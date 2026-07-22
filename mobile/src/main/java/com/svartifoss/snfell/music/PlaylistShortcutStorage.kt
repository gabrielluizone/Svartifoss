package com.svartifoss.snfell.music

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.proto.CustomList
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
    private const val TAG = "StreamingShortcuts"

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
        val host = Uri.parse(StreamingShortcutLinks.canonicalize(shortcut.link)).host.orEmpty()
        val service = StreamingShortcutLinks.detect(shortcut.link)
        val source = when (service) {
            StreamingService.YOUTUBE_MUSIC -> context.getString(R.string.playlist_source_yt_music)
            StreamingService.SPOTIFY -> context.getString(R.string.playlist_source_spotify)
            StreamingService.DEEZER -> context.getString(R.string.playlist_source_deezer)
            StreamingService.TIDAL -> context.getString(R.string.playlist_source_tidal)
            StreamingService.APPLE_MUSIC -> context.getString(R.string.playlist_source_apple_music)
            StreamingService.AMAZON_MUSIC -> context.getString(R.string.playlist_source_amazon_music)
            StreamingService.SOUNDCLOUD -> context.getString(R.string.playlist_source_soundcloud)
            StreamingService.GENERIC -> if (host.isNotBlank()) host.removePrefix("www.")
                else context.getString(R.string.playlist_source_link)
        }

        val type = contentTypeName(
                context,
                StreamingShortcutLinks.detectContentType(shortcut.link)
        )
        return buildList {
            add(source)
            type?.let(::add)
            if (service == StreamingService.YOUTUBE_MUSIC &&
                    StreamingShortcutLinks.hasShuffle(shortcut.link)) {
                add(context.getString(R.string.playlist_badge_shuffle))
            }
        }.joinToString(" • ")
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

        // Keep a dedicated DataItem on the watch. Unlike the transient queue/list path this item
        // is never overwritten by playback updates, so opening Streaming shortcuts can render its
        // cached rows immediately and refresh in the background.
        syncToWatch(context, shortcuts)
    }

    fun buildCustomList(
            context: Context,
            shortcuts: List<PlaylistShortcut>,
            timestamp: Long = System.currentTimeMillis()
    ): CustomList {
        val entries = if (shortcuts.isEmpty()) {
            listOf(
                    CustomList.ListEntry.newBuilder()
                            .setEntryId(CustomLists.SPECIAL_ITEM_ERROR)
                            .setEntryTitle(context.getString(R.string.playlist_shortcuts_empty))
                            .build()
            )
        } else {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val openMode = prefs.getString(StreamingShortcutLinks.OPEN_MODE_KEY, null)
                    ?: if (prefs.getBoolean(
                                    StreamingShortcutLinks.PREFER_INSTALLED_APP_KEY,
                                    true)) {
                        StreamingShortcutLinks.OPEN_MODE_APP
                    } else {
                        StreamingShortcutLinks.OPEN_MODE_DEFAULT
                    }

            shortcuts.map { shortcut ->
                val service = StreamingShortcutLinks.detect(shortcut.link)
                val targetPackage = service.packageName?.takeIf { packageName ->
                    openMode == StreamingShortcutLinks.OPEN_MODE_APP && isPackageInstalled(context, packageName)
                }

                val primaryLink = if (targetPackage != null) {
                    StreamingShortcutLinks.forInstalledApp(shortcut.link)
                } else {
                    StreamingShortcutLinks.forBrowser(shortcut.link)
                }

                val entryId = if (targetPackage != null) {
                    "$targetPackage|$primaryLink"
                } else {
                    primaryLink
                }

                CustomList.ListEntry.newBuilder()
                        .setEntryId(entryId)
                        .setEntryTitle(shortcut.name)
                        .setEntrySubtitle(describe(context, shortcut))
                        .build()
            }
        }

        return CustomList.newBuilder()
                .addAllActions(entries)
                .setListId(CustomLists.PLAYLIST_SHORTCUTS)
                .setListTimestamp(timestamp)
                .build()
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun createDataRequest(
            context: Context,
            shortcuts: List<PlaylistShortcut> = load(context)
    ): PutDataRequest = PutDataRequest.create(CommPaths.DATA_STREAMING_SHORTCUTS).apply {
        data = buildCustomList(context, shortcuts).toByteArray()
        // Attach any cached online thumbnail as a per-index asset - the watch menu reads
        // dataItem.assets[index] for the entry icon (same path as the queue's album art). The
        // entry index in buildCustomList matches the shortcut index when the list is non-empty.
        shortcuts.forEachIndexed { index, shortcut ->
            ShortcutArtworkStore.get(context, shortcut.link)?.let { png ->
                putAsset(index.toString(), Asset.createFromBytes(png))
            }
        }
        setUrgent()
    }

    fun syncToWatch(
            context: Context,
            shortcuts: List<PlaylistShortcut> = load(context)
    ) {
        val appContext = context.applicationContext
        Wearable.getDataClient(appContext)
                .putDataItem(createDataRequest(appContext, shortcuts))
                .addOnFailureListener { error ->
                    Log.w(TAG, "Could not cache streaming shortcuts on watch", error)
                }
    }

    private fun contentTypeName(
            context: Context,
            type: StreamingContentType
    ): String? = when (type) {
        StreamingContentType.TRACK -> context.getString(R.string.playlist_type_track)
        StreamingContentType.PLAYLIST -> context.getString(R.string.playlist_type_playlist)
        StreamingContentType.ALBUM -> context.getString(R.string.playlist_type_album)
        StreamingContentType.ARTIST -> context.getString(R.string.playlist_type_artist)
        StreamingContentType.SHOW -> context.getString(R.string.playlist_type_show)
        StreamingContentType.EPISODE -> context.getString(R.string.playlist_type_episode)
        StreamingContentType.MIX -> context.getString(R.string.playlist_type_mix)
        StreamingContentType.UNKNOWN -> null
    }
}
