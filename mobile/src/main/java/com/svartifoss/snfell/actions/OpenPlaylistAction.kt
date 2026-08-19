package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.R
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.QueueEntry
import com.svartifoss.snfell.common.QueuePaging
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.music.QueueArtworkResolver
import com.svartifoss.snfell.proto.CustomList
import com.matejdro.wearutils.miscutils.BitmapUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/** Matches the now-playing cover's quality in `MusicService.transmitToWear`; visually lossless at
 *  a 30dp thumbnail while keeping the 20-entry payload small. */
private const val THUMBNAIL_JPEG_QUALITY = 85

/** Page size and ceiling both ends agree on - see [QueuePaging]. */
const val DEFAULT_QUEUE_PAGE_SIZE = QueuePaging.PAGE_SIZE

/** Ceiling on resolving one entry's cover. Tighter than QueueArtworkResolver's own 8s connect +
 *  8s read, so a stalled request cannot come anywhere near doubling the time the queue takes to
 *  appear on the watch. Entries resolve concurrently, so this is roughly the whole step's budget. */
private const val ARTWORK_RESOLVE_TIMEOUT_MS = 10_000L

class OpenPlaylistAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    /**
     * How many queue entries to send. Deliberately *not* persisted with the action: it is a
     * property of one request from the watch ("give me another page"), not of the button the user
     * assigned, so an action restored from a config bundle always starts at the first page again.
     */
    var entryLimit: Int = DEFAULT_QUEUE_PAGE_SIZE

    override fun retrieveTitle(): String = context.getString(R.string.open_playlist_menu)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_open_playlist)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<OpenPlaylistAction> {
        override suspend fun handleAction(action: OpenPlaylistAction) {
            val fullQueue = service.resolvePlaybackQueue()
            val limit = action.entryLimit.coerceIn(1, QueuePaging.MAX_ENTRIES)
            val playlist = fullQueue?.take(limit)
            val putDataRequest = PutDataRequest.create(CommPaths.DATA_CUSTOM_LIST)

            // Read once per queue rather than per entry: 20 items would otherwise hit the same
            // preference 20 times, and a toggle flipped mid-build would give a half-remote queue.
            val allowRemoteArtwork = QueueArtworkResolver.remoteArtworkEnabled(service)

            // 96px suits the 30dp circular thumbnail every other queue style draws, but the Cover
            // style stretches the same art across the whole pill, where it looked visibly soft.
            // Sent larger only for that style so the other styles keep the smaller payload - a
            // queue is up to 20 entries and each one crosses Bluetooth.
            val thumbnailSize = if (coverQueueStyleActive()) 320 else 96

            // The row that is actually playing can reuse the cover already decoded for the player,
            // with no network call and no media permission - see MusicService.currentAlbumArt for
            // why streaming clients leave that entry's own description empty.
            val playingQueueId = service.currentMediaController?.playbackState?.activeQueueItemId

            val listId: String
            val protoList = if (playlist != null && playlist.isNotEmpty()) {
                listId = CustomLists.PLAYLIST

                // Apps publish queue covers in several different places - a ready Bitmap, a local
                // content URI, an extras key, a MediaStore library id, or a remote URL.
                // QueueArtworkResolver walks all of them cheapest-first; the watch still collapses
                // the thumbnail slot when nothing is reachable. Resolved concurrently rather than
                // one entry at a time - up to 20 entries each with their own network/disk step
                // would otherwise serialize into several seconds of wall-clock time before the
                // queue appears, even though every individual fetch is already off the main thread.
                val artworks = coroutineScope {
                    playlist.map { queueItem ->
                        async {
                            try {
                                // Capped so one unreachable cover cannot hold up the whole queue:
                                // the DataItem is only sent once every entry has resolved, and with
                                // the remote fetch on that means waiting on real network I/O. A
                                // slow entry simply arrives coverless rather than delaying the list.
                                withTimeoutOrNull(ARTWORK_RESOLVE_TIMEOUT_MS) {
                                    QueueArtworkResolver.resolve(
                                            service, queueItem.description, allowRemoteArtwork,
                                            // Hosts that take the size from the URL are asked for
                                            // exactly what this queue style needs - see
                                            // QueueArtworkResolver.sizedArtworkUrl.
                                            targetPx = thumbnailSize)
                                }
                                // Applied after the timeout, never inside it: this one is an
                                // already-decoded in-memory bitmap, so the playing row keeps its
                                // cover even when the network side gave up.
                                        ?: service.currentAlbumArt?.takeIf {
                                            playingQueueId != null &&
                                                    queueItem.queueId == playingQueueId
                                        }
                            } catch (e: Exception) {
                                Timber.w(e, "Queue cover resolution failed for %s",
                                        queueItem.description.title)
                                null
                            }
                        }
                    }.map { it.await() }
                }

                // One line that answers "why are the covers missing" without reading twenty
                // per-entry lines: how many entries resolved, and whether the remote fetch that
                // most streaming clients depend on was even permitted to run.
                Timber.i(
                        "Queue built: %d of %d entries, %d covers resolved, remote fetch %s",
                        playlist.size,
                        fullQueue?.size ?: 0,
                        artworks.count { it != null },
                        if (allowRemoteArtwork) "enabled" else "DISABLED")

                playlist.mapIndexed { index, queueItem ->
                    artworks[index]?.let { bitmap ->
                        val thumbnail = BitmapUtils.shrinkPreservingRatio(
                                bitmap, thumbnailSize, thumbnailSize, true)
                        encodeThumbnail(thumbnail)?.let { bytes ->
                            putDataRequest.putAsset(index.toString(), Asset.createFromBytes(bytes))
                        }
                    }
                    CustomList.ListEntry.newBuilder()
                            .setEntryId(QueueEntry.encode(queueItem.queueId, queueItem.description.mediaId))
                            .setEntryTitle(queueItem.description.title?.toString() ?: "")
                            .setEntrySubtitle(queueItem.description.subtitle?.toString() ?: "")
                            .build()
                }
            } else if (service.recentTrackHistory.isNotEmpty()) {
                // The playing app doesn't expose a live queue (common on Android 10+) - fall
                // back to a locally tracked list of recently played tracks instead. The watch
                // labels this as "Recently played" rather than rendering it as a queue: these are
                // tracks that already played, they carry no artwork (a history entry is only
                // artist/title text), and tapping one re-searches for it instead of jumping.
                listId = CustomLists.HISTORY
                // The live-queue branch logs why covers are missing; this branch answers the
                // question before it, "why are these not the tracks I'm about to hear".
                Timber.i(
                        "Queue unavailable from %s - sending %d recently played tracks instead",
                        service.currentMediaController?.packageName,
                        service.recentTrackHistory.size)
                service.recentTrackHistory.mapIndexed { index, entry ->
                    CustomList.ListEntry.newBuilder()
                            .setEntryId(index.toString())
                            .setEntryTitle(entry.title)
                            .setEntrySubtitle(entry.artist)
                            .build()
                }
            } else {
                listId = CustomLists.PLAYLIST
                listOf(
                        CustomList.ListEntry.newBuilder()
                                .setEntryId(CustomLists.SPECIAL_ITEM_ERROR)
                                .setEntryTitle(service.getString(R.string.error_playlist_not_supported))
                                .build()
                )
            }

            val protoDataBuilder = CustomList.newBuilder()
                    .addAllActions(protoList)
                    .setListId(listId)
                    .setListTimestamp(System.currentTimeMillis())

            // What the watch compares against the rows it received to decide whether "load more"
            // has anything left to fetch. Only the live queue is paged - the history fallback and
            // the single error row are sent whole, so for those the total IS what was sent.
            protoDataBuilder.totalEntryCount = if (listId == CustomLists.PLAYLIST && fullQueue != null) {
                minOf(fullQueue.size, QueuePaging.MAX_ENTRIES)
            } else {
                protoList.size
            }

            val activeQueueItemId = service.currentMediaController?.playbackState?.activeQueueItemId
            if (listId == CustomLists.PLAYLIST &&
                    activeQueueItemId != null &&
                    activeQueueItemId != android.media.session.MediaSession.QueueItem.UNKNOWN_ID.toLong()
            ) {
                // Encoded the same way as the entries themselves (QueueEntry) so the watch's plain
                // string-equality match against each row's entryId still finds the playing one.
                // Looked up in the whole queue rather than in the page just sent: the playing track
                // can sit past the page boundary, and encoding it without its media id there would
                // produce an id that fails to match its own row once that page is loaded.
                val activeMediaId = fullQueue?.firstOrNull { it.queueId == activeQueueItemId }
                        ?.description?.mediaId
                protoDataBuilder.activeEntryId = QueueEntry.encode(activeQueueItemId, activeMediaId)
            }

            val protoData = protoDataBuilder.build()

            putDataRequest.data = protoData.toByteArray()
            // This response feeds both the visible queue and AOD's Up Next row. Letting the Data
            // Layer batch it made an earlier request appear to complete only when Quick Actions
            // sent more traffic later.
            putDataRequest.setUrgent()

            Wearable.getDataClient(service).putDataItem(putDataRequest).await()
        }

        /**
         * JPEG bytes for one queue thumbnail, or null when the bitmap cannot be encoded.
         *
         * Deliberately **not** `BitmapUtils.serialize`, which encodes lossless PNG: cover art is
         * photographic and has no alpha, so PNG comes out several times larger than JPEG for no
         * visible gain - the exact reasoning `MusicService.transmitToWear` already records for the
         * now-playing cover, which is why that one arrives reliably. A queue sends up to 20 of
         * these in a single DataItem, so the multiplier matters far more here: at the 320px size a
         * Cover style asks for, twenty PNGs are megabytes of Bluetooth transfer, which is slow
         * enough to look like the covers simply never arrive.
         */
        private fun encodeThumbnail(bitmap: android.graphics.Bitmap?): ByteArray? {
            if (bitmap == null) return null
            return java.io.ByteArrayOutputStream().use { stream ->
                if (!bitmap.compress(
                                android.graphics.Bitmap.CompressFormat.JPEG,
                                THUMBNAIL_JPEG_QUALITY,
                                stream)) {
                    return null
                }
                stream.toByteArray()
            }
        }

        /** Whether the watch's queue is set to the cover-filled pill style, which needs the
         *  larger artwork. Read from the phone's own copy of the synced preference. */
        private fun coverQueueStyleActive(): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(service)
            val appearance = ThemeAppearance.resolve(prefs)
            // The Carousel face draws these same thumbnails as full-screen-width cards, so it needs
            // the large size for the same reason the cover queue styles do - at the 96px list size
            // a hero card is visibly pixelated.
            if (appearance.baseFace == "carousel") return true
            return FaceScopedPreferences.getString(
                    prefs,
                    MiscPreferences.WEAR_QUEUE_STYLE,
                    appearance
            ) in MiscPreferences.COVER_LIST_STYLES
        }

    }
}
