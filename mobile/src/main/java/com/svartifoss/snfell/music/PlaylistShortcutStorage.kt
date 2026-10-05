package com.svartifoss.snfell.music

import android.content.Context
import android.net.Uri
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
import timber.log.Timber

/** One user-defined streaming shortcut shown on the watch: display name + link to open. */
data class PlaylistShortcut(
        val name: String,
        val link: String,
        /** Optional public preview details, kept with the shortcut rather than re-requested. */
        val publicMetadata: PublicLinkMetadata? = null,
        /** A failed/missing public answer is cached as well, so opening this screen does not retry
         * every unsupported link on every resume. The reload-covers button is the explicit retry. */
        val metadataChecked: Boolean = false
)

/**
 * What the watch's shortcut screen says about a destination beyond its name: the service and kind
 * line, and whether Shuffle sits beside Play. See [PlaylistShortcutStorage.describeForWatch].
 */
data class StreamingShortcutDescription(
        val subtitle: String,
        val shuffleable: Boolean,
        val creator: String? = null,
        val description: String? = null
)

/**
 * Persists streaming shortcuts as a JSON array in the default SharedPreferences. Configured
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
                        .copy(
                                publicMetadata = PublicLinkMetadata(
                                        creator = entry.optString("creator").takeIf(String::isNotBlank),
                                        description = entry.optString("description")
                                                .takeIf(String::isNotBlank)
                                ).takeIf { it.hasDetails },
                                metadataChecked = entry.optBoolean("metadata_checked", false))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /**
     * Human-readable description of a shortcut (source app + kind) - the secondary line on both
     * the phone list and the watch menu. Never exposes the raw link.
     *
     * [includeShuffleBadge] marks a YouTube Music link saved with its shuffle flag, which is still
     * how such a link plays from a button it is assigned to. The watch's list leaves it out: there
     * a shortcut opens with Play and Shuffle side by side, so a badge claiming it always shuffles
     * would describe something the next tap does not do.
     */
    fun describe(
            context: Context,
            shortcut: PlaylistShortcut,
            includeShuffleBadge: Boolean = true
    ): String {
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
            StreamingService.QOBUZ -> context.getString(R.string.playlist_source_qobuz)
            StreamingService.BANDCAMP -> context.getString(R.string.playlist_source_bandcamp)
            StreamingService.AUDIOMACK -> context.getString(R.string.playlist_source_audiomack)
            StreamingService.MIXCLOUD -> context.getString(R.string.playlist_source_mixcloud)
            StreamingService.PANDORA -> context.getString(R.string.playlist_source_pandora)
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
            if (includeShuffleBadge && service == StreamingService.YOUTUBE_MUSIC &&
                    StreamingShortcutLinks.hasShuffle(shortcut.link)) {
                add(context.getString(R.string.playlist_badge_shuffle))
            }
        }.joinToString(" • ")
    }

    /**
     * The watch-side description of [link], shared by the shortcut list and by actions in the
     * actions menu that start one destination, so the two cannot describe the same playlist
     * differently - or disagree about whether it can be shuffled.
     */
    fun describeForWatch(context: Context, name: String, link: String): StreamingShortcutDescription {
        // A parameterised action retains only name and link, whereas the saved library owns the
        // cached public details. Match by the launch link so an already-assigned button or quick
        // panel row gains the details on its next config sync without changing its action bundle.
        val metadata = load(context).firstOrNull { it.link == link }?.publicMetadata
        return StreamingShortcutDescription(
                subtitle = describe(context, PlaylistShortcut(name, link), includeShuffleBadge = false),
                shuffleable = StreamingShortcutLinks.detectContentType(link).offersShuffle,
                creator = metadata?.creator,
                description = metadata?.description)
    }

    fun save(context: Context, shortcuts: List<PlaylistShortcut>) {
        val array = JSONArray()
        for (shortcut in shortcuts) {
            array.put(
                JSONObject()
                            .put("name", shortcut.name)
                            .put("link", shortcut.link)
                            .apply {
                                shortcut.publicMetadata?.creator?.let { put("creator", it) }
                                shortcut.publicMetadata?.description?.let { put("description", it) }
                                if (shortcut.metadataChecked) put("metadata_checked", true)
                            }
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
                val targetPackage = StreamingShortcutRoutes.targetPackage(
                        context, prefs, service, openMode, shortcut.link)
                val primaryLink = StreamingShortcutRoutes.linkForTarget(
                        shortcut.link, service, targetPackage)

                val entryId = if (targetPackage != null) {
                    "$targetPackage|$primaryLink"
                } else {
                    primaryLink
                }

                val description = describeForWatch(context, shortcut.name, shortcut.link)
                CustomList.ListEntry.newBuilder()
                        .setEntryId(entryId)
                        .setEntryTitle(shortcut.name)
                        .setEntrySubtitle(description.subtitle)
                        .apply {
                            description.creator?.let { entryCreator = it }
                            description.description?.let { entryDescription = it }
                        }
                        // Sent for every entry, false included: its presence is what tells the
                        // watch this phone understands a play mode at all.
                        .setShuffleable(description.shuffleable)
                        .build()
            }
        }

        return CustomList.newBuilder()
                .addAllActions(entries)
                .setListId(CustomLists.PLAYLIST_SHORTCUTS)
                .setListTimestamp(timestamp)
                .build()
    }

    fun createDataRequest(
            context: Context,
            shortcuts: List<PlaylistShortcut> = load(context)
    ): PutDataRequest = PutDataRequest.create(CommPaths.DATA_STREAMING_SHORTCUTS).apply {
        data = buildCustomList(context, shortcuts).toByteArray()
        // Attach each shortcut's cover as a per-index asset - the watch menu reads
        // dataItem.assets[index] for the entry icon (same path as the queue's album art). The
        // entry index in buildCustomList matches the shortcut index when the list is non-empty.
        // A fetched thumbnail when there is one, else the drawn cover (see ShortcutCovers), so the
        // list and the shortcut's own screen never fall back to a placeholder of one fixed colour.
        shortcuts.forEachIndexed { index, shortcut ->
            ShortcutCovers.pngForLink(context, shortcut.link)?.let { png ->
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
        // Built off the main thread: every entry carries a cover now, and a drawn one is rendered
        // and encoded the first time it is asked for - callers include MusicService.onCreate. One
        // thread, so two quick edits still reach the watch in the order they were made.
        syncExecutor.execute {
            try {
                Wearable.getDataClient(appContext)
                        .putDataItem(createDataRequest(appContext, shortcuts))
                        .addOnFailureListener { error ->
                            Timber.w(error, "Could not cache streaming shortcuts on watch")
                        }
            } catch (e: RuntimeException) {
                Timber.w(e, "Could not build the streaming shortcut list for the watch")
            }
        }
    }

    private val syncExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

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
