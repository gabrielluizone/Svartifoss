package com.svartifoss.snfell.watch.communication

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.util.FloatPacker
import com.svartifoss.snfell.proto.CustomList
import com.svartifoss.snfell.proto.CustomListItemAction
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.proto.Notification
import com.svartifoss.snfell.watch.util.launchWithErrorHandling
import com.matejdro.wearutils.lifecycle.ListenableLiveData
import com.matejdro.wearutils.lifecycle.LiveDataLifecycleCombiner
import com.matejdro.wearutils.lifecycle.LiveDataLifecycleListener
import com.matejdro.wearutils.lifecycle.Resource
import com.matejdro.wearutils.lifecycle.SingleLiveEvent
import com.matejdro.wearutils.messages.getByteArrayAsset
import com.matejdro.wearutils.messages.getNearestNodeId
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import com.matejdro.wearutils.miscutils.BitmapUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneConnection @Inject constructor(@ApplicationContext private val context: Context) : DataClient.OnDataChangedListener,
        CapabilityClient.OnCapabilityChangedListener,
        LiveDataLifecycleListener {

    private var scope: CoroutineScope? = null

    companion object {
        const val MESSAGE_CLOSE_CONNECTION = 0
        const val CONNECTION_CLOSE_DELAY_MS = 15_000L
    }

    val musicState = ListenableLiveData<Resource<MusicState>>()
    val albumArt = ListenableLiveData<Bitmap?>()
    val customList = ListenableLiveData<CustomListWithBitmaps>()
    /** Persistent cache used only by Streaming shortcuts. Queue/search data cannot overwrite it. */
    val streamingShortcuts = ListenableLiveData<CustomListWithBitmaps>()

    val notification = SingleLiveEvent<com.svartifoss.snfell.watch.model.Notification>()

    val rawPlaybackConfig = MutableLiveData<DataItem>()
    val rawStoppedConfig = MutableLiveData<DataItem>()
    val rawActionMenuConfig = MutableLiveData<DataItem>()

    private val lifecycleObserver = LiveDataLifecycleCombiner(this)

    private val messageClient = Wearable.getMessageClient(context)
    private val dataClient = Wearable.getDataClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val closeHandler = ConnectionCloseHandler(WeakReference(this))

    // Cached phone node id, refreshed whenever capabilities change (connect/reconnect). Using it
    // avoids the extra getConnectedNodes() round-trip that sendMessageToNearestClient performs on
    // every send - that per-press lookup was the main source of control latency.
    @Volatile
    private var phoneNodeId: String? = null

    // Last music state delivered to observers, with albumArtPending cleared for comparison. A
    // track change arrives as two puts whose states are byte-identical except that flag (state
    // alone first, state+cover second) - re-delivering the second ran every observer's full
    // update pass (now-playing UI, media session, notification, recents label) twice per track
    // change. See deliverMusicState.
    private var lastDeliveredMusicState: MusicState? = null

    // Data Layer asset id (content-derived) of the currently decoded album art - lets an
    // unchanged cover riding along on every state put be skipped without decoding it again.
    private var lastAlbumArtAssetId: String? = null

    private var sendingVolume = false
    private var nextVolume = -1f

    private var sendingSeek = false
    private var nextSeekPositionMs = -1L

    private var running = AtomicBoolean(false)

    init {
        lifecycleObserver.addLiveData(musicState)
        lifecycleObserver.addLiveData(albumArt)
        lifecycleObserver.addLiveData(streamingShortcuts)
    }

    private fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        scope = CoroutineScope(Job() + Dispatchers.Main)

        // The loading value posted below overwrites whatever observers currently show, so the
        // seed/next state must always be delivered even if it equals the last delivered one.
        lastDeliveredMusicState = null

        scope?.launchWithErrorHandling(context, musicState) {
            // Loading goes out first so a "no phone" error posted below is not overwritten by it.
            musicState.postValue(Resource.loading(null))

            // This is a local Data Layer read, deliberately before capability discovery. Opening
            // Streaming shortcuts must never wait for a Bluetooth/phone round trip just to draw
            // rows the watch already cached.
            loadCurrentStreamingShortcuts()
            // Up Next uses the transient custom-list path. Seed a persisted queue before the
            // phone lookup as well; otherwise a process restart left AOD empty until opening
            // Quick Actions happened to request a fresh queue.
            loadCurrentPlaybackQueue()

            val capabilities = capabilityClient.getCapability(
                    CommPaths.PHONE_APP_CAPABILITY,
                    CapabilityClient.FILTER_REACHABLE
            ).await()

            onWatchConnectionUpdated(capabilities)

            dataClient.addListener(this)
            capabilityClient.addListener(this, CommPaths.PHONE_APP_CAPABILITY)

            loadCurrentActionConfig(CommPaths.DATA_PLAYING_ACTION_CONFIG, rawPlaybackConfig)
            loadCurrentActionConfig(CommPaths.DATA_STOPPING_ACTION_CONFIG, rawStoppedConfig)
            loadCurrentActionConfig(CommPaths.DATA_LIST_ITEMS, rawActionMenuConfig)

            // Seed music state from the DataItem already in the Data Layer store. The listener
            // above only fires on *future* changes, and the phone dedups the retransmit it does
            // on MESSAGE_WATCH_OPENED when nothing changed since its last put - so without this
            // read the state (and the media session driven by it) could stay empty until the
            // next actual track/playback change. Only done while the phone is reachable: with no
            // phone around the stored state is stale and the error above is the truthful UI.
            if (capabilities.nodes.any { it.isNearby }) {
                loadCurrentMusicState()
            }
        }
    }

    private fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        scope?.launchWithErrorHandling(context, musicState) {
            try {
                dataClient.removeListener(this)
                // start() registers both listeners; stop() only ever removed the data one, so the
                // capability listener leaked (and kept firing onCapabilityChanged after close).
                capabilityClient.removeListener(this)

                val phoneNode = nodeClient.getNearestNodeId()
                if (phoneNode != null) {
                    messageClient.sendMessage(phoneNode, CommPaths.MESSAGE_WATCH_CLOSED, null).await()
                }
            } finally {
                scope?.cancel()
            }

        }
    }

    private fun onWatchConnectionUpdated(capabilityInfo: CapabilityInfo) {
        val firstNode = capabilityInfo.nodes.firstOrNull { it.isNearby }
        phoneNodeId = firstNode?.id

        if (firstNode != null) {
            scope?.launchWithErrorHandling(context, musicState) {
                // Must target the same nearby node we cached above. nodes.first() is just the
                // first entry in the set, which - when more than one node is reachable (e.g. a
                // cloud node alongside the watch) - can be a different, non-nearby node, so the
                // phone never learned the watch opened and never started transmitting state.
                messageClient.sendMessage(firstNode.id, CommPaths.MESSAGE_WATCH_OPENED, null).await()
            }
        } else {
            musicState.postValue(Resource.error(context.getString(R.string.no_phone), null))
        }
    }

    suspend fun sendManualCloseMessage() {
        if (!running.get()) {
            return
        }

        // Activity closes when manual close happens, so we must ignore cancel signal here
        withContext(NonCancellable) {
            val phoneNode = nodeClient.getNearestNodeId()
            if (phoneNode != null) {
                messageClient.sendMessage(phoneNode, CommPaths.MESSAGE_WATCH_CLOSED_MANUALLY, null).await()
            }
        }
    }

    fun sendVolume(newVolume: Float) {
        scope?.launchWithErrorHandling(context, musicState) {
            nextVolume = -1f

            if (sendingVolume) {
                nextVolume = newVolume
                return@launchWithErrorHandling
            }

            try {
                sendingVolume = true

                sendToPhone(CommPaths.MESSAGE_CHANGE_VOLUME, FloatPacker.packFloat(newVolume))
            } finally {
                sendingVolume = false
                if (nextVolume >= 0) {
                    sendVolume(nextVolume)
                }
            }
        }
    }

    fun sendSeek(positionMs: Long) {
        scope?.launchWithErrorHandling(context, musicState) {
            nextSeekPositionMs = -1L

            if (sendingSeek) {
                nextSeekPositionMs = positionMs
                return@launchWithErrorHandling
            }

            try {
                sendingSeek = true

                sendToPhone(CommPaths.MESSAGE_SEEK_TO, ByteBuffer.allocate(8).putLong(positionMs).array())
            } finally {
                sendingSeek = false
                if (nextSeekPositionMs >= 0) {
                    sendSeek(nextSeekPositionMs)
                }
            }
        }
    }

    /**
     * Sends a message to the phone, preferring the cached [phoneNodeId] to skip the per-call
     * node-resolution round-trip. Falls back to nearest-client resolution if the cache is empty.
     */
    private suspend fun sendToPhone(path: String, data: ByteArray? = null) {
        val node = phoneNodeId
        if (node != null) {
            messageClient.sendMessage(node, path, data).await()
        } else {
            messageClient.sendMessageToNearestClient(nodeClient, path, data)
        }
    }

    suspend fun togglePlayPause() {
        sendToPhone(CommPaths.MESSAGE_TOGGLE_PLAY_PAUSE)
    }

    suspend fun sendSkipNext() {
        sendToPhone(CommPaths.MESSAGE_SKIP_NEXT)
    }

    suspend fun sendSkipPrevious() {
        sendToPhone(CommPaths.MESSAGE_SKIP_PREVIOUS)
    }

    /** [name] is one of "like"/"shuffle"/"repeat" - see MusicService.onMessageReceived on the phone. */
    suspend fun sendQuickAction(name: String) {
        sendToPhone(CommPaths.MESSAGE_QUICK_ACTION, name.toByteArray(Charsets.UTF_8))
    }

    suspend fun sendPlayFromSearch(query: String) {
        sendToPhone(CommPaths.MESSAGE_PLAY_FROM_SEARCH, query.toByteArray(Charsets.UTF_8))
    }

    suspend fun executeButtonAction(buttonInfo: ButtonInfo) {
        sendToPhone(CommPaths.MESSAGE_EXECUTE_ACTION, buttonInfo.buildProtoVersion().build().toByteArray())
    }

    suspend fun executeMenuAction(index: Int) {
        sendToPhone(CommPaths.MESSAGE_EXECUTE_MENU_ACTION, ByteBuffer.allocate(4).putInt(index).array())
    }

    suspend fun executeCustomMenuAction(listId: String, entryId: String) {
        sendToPhone(
                CommPaths.MESSAGE_CUSTOM_LIST_ITEM_SELECTED,
                CustomListItemAction.newBuilder()
                        .setListId(listId)
                        .setEntryId(entryId)
                        .build()
                        .toByteArray()
        )

    }

    /** Deletes one entry from a watch-managed deletable custom list (currently just search
     *  history) - see CommPaths.MESSAGE_DELETE_CUSTOM_LIST_ITEM. */
    suspend fun deleteCustomListItem(listId: String, entryId: String) {
        sendToPhone(
                CommPaths.MESSAGE_DELETE_CUSTOM_LIST_ITEM,
                CustomListItemAction.newBuilder()
                        .setListId(listId)
                        .setEntryId(entryId)
                        .build()
                        .toByteArray()
        )
    }


    private suspend fun sendAck() {
        messageClient.sendMessageToNearestClient(nodeClient, CommPaths.MESSAGE_ACK)
    }

    override fun onDataChanged(data: DataEventBuffer) {
        val frozenData = data.use { _ ->
            data.map { it.freeze() }
        }

        scope?.launchWithErrorHandling(context, musicState) {
            frozenData.filter { it.type == DataEvent.TYPE_CHANGED }
                    .map { it.dataItem }
                    .forEach {
                        when (it.uri.path) {
                            CommPaths.DATA_MUSIC_STATE -> {
                                val dataItem = it.freeze()

                                val receivedMusicState = MusicState.parseFrom(dataItem.data)

                                if (receivedMusicState.error) {
                                    lastDeliveredMusicState = null
                                    musicState.postValue(Resource.error(receivedMusicState.title, null))
                                } else {
                                    deliverMusicState(receivedMusicState)

                                    sendAck()

                                    deliverAlbumArt(dataItem, receivedMusicState)
                                }
                            }
                            CommPaths.DATA_NOTIFICATION -> {
                                val dataItem = it.freeze()
                                val receivedNotification = Notification.parseFrom(dataItem.data)

                                sendAck()

                                val pictureData = dataItem.assets[CommPaths.ASSET_NOTIFICATION_BACKGROUND]
                                        ?.let { asset -> dataClient.getByteArrayAsset(asset) }
                                val picture = pictureData?.let { bytes ->
                                    withContext(Dispatchers.Default) { BitmapUtils.deserialize(bytes) }
                                }

                                val mergedNotification = com.svartifoss.snfell.watch.model.Notification(
                                        receivedNotification.title,
                                        receivedNotification.description,
                                        picture,
                                        System.currentTimeMillis()
                                )

                                notification.postValue(mergedNotification)
                            }
                            CommPaths.DATA_PLAYING_ACTION_CONFIG -> rawPlaybackConfig.postValue(it.freeze())
                            CommPaths.DATA_STOPPING_ACTION_CONFIG -> rawStoppedConfig.postValue(it.freeze())
                            CommPaths.DATA_LIST_ITEMS -> rawActionMenuConfig.postValue(it.freeze())
                            CommPaths.DATA_CUSTOM_LIST ->
                                customList.postValue(decodeCustomList(it.freeze()))
                            CommPaths.DATA_STREAMING_SHORTCUTS ->
                                streamingShortcuts.postValue(decodeCustomList(it.freeze()))
                        }
                    }
        }
    }

    override fun onCapabilityChanged(capability: CapabilityInfo) {
        onWatchConnectionUpdated(capability)
    }

    /**
     * Posts [state] unless it matches the last delivered one modulo the albumArtPending flag -
     * skipping the second put of the two-put track change (see [lastDeliveredMusicState]).
     * Must be called without suspending after parsing, so the tracker is updated before the
     * next queued put's coroutine gets a chance to run.
     */
    private fun deliverMusicState(state: MusicState) {
        val comparable = state.toBuilder().setAlbumArtPending(false).build()
        if (comparable == lastDeliveredMusicState) {
            return
        }
        lastDeliveredMusicState = comparable
        musicState.postValue(Resource.success(state))
    }

    /**
     * Decodes and posts the album art attached to a music-state DataItem.
     *
     * The asset id is content-derived, so the unchanged cover riding along on every state put
     * (play/pause, volume, seek) is skipped outright - no re-decode, no re-delivery. That also
     * keeps the posted Bitmap reference-stable, which the identity checks downstream (crossfade
     * dedupe, palette cache, media-session metadata) rely on. The decode itself runs off the
     * main thread - it used to stall input and animation frames right as a track changed.
     */
    private suspend fun deliverAlbumArt(dataItem: DataItem, state: MusicState) {
        val asset = dataItem.assets[CommPaths.ASSET_ALBUM_ART]

        if (asset == null) {
            // The phone ships a track change as two puts: state alone first (fast, flagged
            // albumArtPending), then state+cover. While the cover is in transit, keep showing
            // the previous one - the new art crossfades in when it lands, instead of the screen
            // blanking between the two puts. A state with no asset and no pending flag genuinely
            // has no art: clear.
            if (!state.albumArtPending) {
                lastAlbumArtAssetId = null
                albumArt.postValue(null)
            }
            return
        }

        if (asset.id == lastAlbumArtAssetId && albumArt.value != null) {
            return
        }

        val albumArtData = dataClient.getByteArrayAsset(asset)
        val receivedAlbumArt = albumArtData?.let { bytes ->
            withContext(Dispatchers.Default) { BitmapUtils.deserialize(bytes) }
        }
        if (receivedAlbumArt != null) {
            lastAlbumArtAssetId = asset.id
            albumArt.postValue(receivedAlbumArt)
        } else if (!state.albumArtPending) {
            // Asset attached but unreadable and nothing newer on the way: clear rather than
            // keep showing the previous track's cover indefinitely.
            lastAlbumArtAssetId = null
            albumArt.postValue(null)
        }
    }

    private suspend fun loadCurrentMusicState() {
        val dataItems = dataClient.getDataItems(
                Uri.parse("wear://*${CommPaths.DATA_MUSIC_STATE}"),
                DataClient.FILTER_LITERAL)
                .await()

        val dataItem = try {
            dataItems.firstOrNull()?.freeze() ?: return
        } finally {
            dataItems.release()
        }

        val storedState = MusicState.parseFrom(dataItem.data)
        if (storedState.error) {
            // A stored error ("nothing playing" etc.) may predate whatever the phone is about to
            // send in response to MESSAGE_WATCH_OPENED - keep showing loading instead.
            return
        }

        deliverMusicState(storedState)
        deliverAlbumArt(dataItem, storedState)
    }

    private suspend fun loadCurrentActionConfig(configPath: String, targetLiveData: MutableLiveData<DataItem>) {
        val dataItems = dataClient.getDataItems(
                Uri.parse("wear://*$configPath"),
                DataClient.FILTER_LITERAL)
                .await()

        // release() must run even when the buffer is empty - the early `?: return` used to leak
        // the DataItemBuffer whenever no config item was present yet.
        try {
            val dataItem = dataItems.firstOrNull() ?: return
            targetLiveData.postValue(dataItem.freeze())
        } finally {
            dataItems.release()
        }
    }

    private suspend fun loadCurrentStreamingShortcuts() {
        val dataItems = dataClient.getDataItems(
                Uri.parse("wear://*${CommPaths.DATA_STREAMING_SHORTCUTS}"),
                DataClient.FILTER_LITERAL
        ).await()
        val dataItem = try {
            dataItems.firstOrNull()?.freeze() ?: return
        } finally {
            dataItems.release()
        }
        streamingShortcuts.postValue(decodeCustomList(dataItem))
    }

    private suspend fun loadCurrentPlaybackQueue() {
        val dataItems = dataClient.getDataItems(
                Uri.parse("wear://*${CommPaths.DATA_CUSTOM_LIST}"),
                DataClient.FILTER_LITERAL
        ).await()
        val dataItem = try {
            dataItems.firstOrNull()?.freeze() ?: return
        } finally {
            dataItems.release()
        }
        val decoded = decodeCustomList(dataItem)
        // Search results share DATA_CUSTOM_LIST but are not a playback queue and must never be
        // presented as Up Next after a restart.
        if (decoded.listId == CustomLists.PLAYLIST || decoded.listId == CustomLists.HISTORY) {
            customList.postValue(decoded)
        }
    }

    private suspend fun decodeCustomList(dataItem: DataItem): CustomListWithBitmaps {
        val received = CustomList.parseFrom(dataItem.data)
        val listItems = received.actionsList.mapIndexed { index, rawEntry ->
            val pictureData = dataItem.assets[index.toString()]
                    ?.let { asset -> dataClient.getByteArrayAsset(asset) }
            // Album thumbnails, when present, are decoded away from the main dispatcher.
            val picture = pictureData?.let { bytes ->
                withContext(Dispatchers.Default) { BitmapUtils.deserialize(bytes) }
            }
            CustomListItemWithIcon(rawEntry, picture)
        }
        return CustomListWithBitmaps(
                received.listTimestamp,
                received.listId,
                listItems,
                received.activeEntryId.takeIf { it.isNotEmpty() }
        )
    }

    override fun onInactive() {
        // Delay connection closing for a bit to make sure it is not just brief configuration change

        closeHandler.removeMessages(MESSAGE_CLOSE_CONNECTION)
        closeHandler.sendEmptyMessageDelayed(MESSAGE_CLOSE_CONNECTION, CONNECTION_CLOSE_DELAY_MS)
    }

    override fun onActive() {
        closeHandler.removeMessages(MESSAGE_CLOSE_CONNECTION)
        start()
    }

    suspend fun openPlaybackQueue() {
        messageClient.sendMessageToNearestClient(nodeClient, CommPaths.MESSAGE_OPEN_PLAYBACK_QUEUE)
    }

    private class ConnectionCloseHandler(val phoneConnection: WeakReference<PhoneConnection>) : Handler(Looper.getMainLooper()) {
        override fun dispatchMessage(msg: android.os.Message) {
            if (msg.what == MESSAGE_CLOSE_CONNECTION) {
                phoneConnection.get()?.stop()
            }
        }
    }
}
