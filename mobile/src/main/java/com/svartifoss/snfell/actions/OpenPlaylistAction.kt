package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.proto.CustomList
import com.matejdro.wearutils.miscutils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class OpenPlaylistAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.open_playlist_menu)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_open_playlist)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<OpenPlaylistAction> {
        override suspend fun handleAction(action: OpenPlaylistAction) {
            val playlist = service.currentMediaController?.queue?.take(20)
            val putDataRequest = PutDataRequest.create(CommPaths.DATA_CUSTOM_LIST)

            // 96px suits the 30dp circular thumbnail every other queue style draws, but the Cover
            // style stretches the same art across the whole pill, where it looked visibly soft.
            // Sent larger only for that style so the other styles keep the smaller payload - a
            // queue is up to 20 entries and each one crosses Bluetooth.
            val thumbnailSize = if (coverQueueStyleActive()) 320 else 96

            val listId: String
            val protoList = if (playlist != null && playlist.isNotEmpty()) {
                listId = CustomLists.PLAYLIST
                playlist.mapIndexed { index, queueItem ->
                    // MediaDescription may carry the cover either as a ready Bitmap or as a local
                    // content URI. Attach only artwork the player actually publishes; the watch
                    // deliberately collapses the thumbnail slot when this is null.
                    val artwork = queueItem.description.iconBitmap
                            ?: loadLocalArtwork(queueItem.description.iconUri)
                    artwork?.let { bitmap ->
                        val thumbnail = BitmapUtils.shrinkPreservingRatio(
                                bitmap, thumbnailSize, thumbnailSize, true)
                        BitmapUtils.serialize(thumbnail)?.let { bytes ->
                            putDataRequest.putAsset(index.toString(), Asset.createFromBytes(bytes))
                        }
                    }
                    CustomList.ListEntry.newBuilder()
                            .setEntryId(queueItem.queueId.toString())
                            .setEntryTitle(queueItem.description.title?.toString() ?: "")
                            .setEntrySubtitle(queueItem.description.subtitle?.toString() ?: "")
                            .build()
                }
            } else if (service.recentTrackHistory.isNotEmpty()) {
                // The playing app doesn't expose a live queue (common on Android 10+) - fall
                // back to a locally tracked list of recently played tracks instead.
                listId = CustomLists.HISTORY
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

            val activeQueueItemId = service.currentMediaController?.playbackState?.activeQueueItemId
            if (listId == CustomLists.PLAYLIST &&
                    activeQueueItemId != null &&
                    activeQueueItemId != android.media.session.MediaSession.QueueItem.UNKNOWN_ID.toLong()
            ) {
                protoDataBuilder.activeEntryId = activeQueueItemId.toString()
            }

            val protoData = protoDataBuilder.build()

            putDataRequest.data = protoData.toByteArray()
            // This response feeds both the visible queue and AOD's Up Next row. Letting the Data
            // Layer batch it made an earlier request appear to complete only when Quick Actions
            // sent more traffic later.
            putDataRequest.setUrgent()

            Wearable.getDataClient(service).putDataItem(putDataRequest).await()
        }

        /** Whether the watch's queue is set to the cover-filled pill style, which needs the
         *  larger artwork. Read from the phone's own copy of the synced preference. */
        private fun coverQueueStyleActive(): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(service)
            return FaceScopedPreferences.getString(
                    prefs,
                    MiscPreferences.WEAR_QUEUE_STYLE,
                    ThemeAppearance.resolve(prefs)
            ) in MiscPreferences.COVER_LIST_STYLES
        }

        /** Remote http(s) covers are intentionally skipped: opening the queue must never wait on
         * network I/O. content/file URIs already granted to the media controller are cheap and
         * safe to decode on the IO dispatcher. */
        private suspend fun loadLocalArtwork(uri: Uri?): Bitmap? {
            if (uri == null || uri.scheme in setOf("http", "https")) return null
            return withContext(Dispatchers.IO) {
                try {
                    service.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
