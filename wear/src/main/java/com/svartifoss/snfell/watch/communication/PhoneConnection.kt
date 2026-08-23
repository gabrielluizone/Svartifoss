package com.svartifoss.snfell.watch.communication

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.BitmapBorderTrim
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.PlaybackSyncPolicy
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.QueuePaging
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.util.FloatPacker
import com.svartifoss.snfell.proto.CustomList
import com.svartifoss.snfell.proto.CustomListItemAction
import com.svartifoss.snfell.proto.LyricsRequest
import com.svartifoss.snfell.proto.LyricsResponse
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.proto.PlaybackSync
import com.svartifoss.snfell.proto.TrackMetadata
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneConnection @Inject constructor(@ApplicationContext private val context: Context) : DataClient.OnDataChangedListener,
        CapabilityClient.OnCapabilityChangedListener,
        MessageClient.OnMessageReceivedListener,
        LiveDataLifecycleListener {

    private var scope: CoroutineScope? = null

    companion object {
        const val MESSAGE_CLOSE_CONNECTION = 0
        const val CONNECTION_CLOSE_DELAY_MS = 15_000L
    }

    val musicState = ListenableLiveData<Resource<MusicState>>()
    val albumArt = ListenableLiveData<Bitmap?>()
    /** Icon of the app currently playing (or null when unavailable / the element is off). */
    val sourceIcon = ListenableLiveData<Bitmap?>()
    val customList = ListenableLiveData<CustomListWithBitmaps>()
    /** Persistent cache used only by Streaming shortcuts. Queue/search data cannot overwrite it. */
    val streamingShortcuts = ListenableLiveData<CustomListWithBitmaps>()

    /**
     * Incoming phone notifications.
     *
     * A plain [MutableLiveData], **not** a `SingleLiveEvent`, even though each notification should
     * pop up exactly once. That "once" belongs to the screen showing it, not to this bus:
     * SingleLiveEvent throws `IllegalStateException("Multiple observers registered...")` the moment
     * a second active observer appears, and this object is a `@Singleton` while its observer was an
     * Activity - so any overlap of two MainActivity instances killed the app during onCreate with
     * "Unable to start activity". [MusicViewModel] re-exposes this as its own per-Activity
     * SingleLiveEvent and drops replays by timestamp, which keeps the popup showing once without
     * making a process-lifetime bus single-listener.
     */
    val notification = MutableLiveData<com.svartifoss.snfell.watch.model.Notification>()

    /**
     * The phone's most recent answer to a lyrics request.
     *
     * A plain [MutableLiveData], so the last answer *is* replayed to a screen that opens later -
     * which is wanted here rather than tolerated: re-opening lyrics for the track still playing
     * should paint instantly instead of spinning through another round trip. The response carries
     * the track it is for, so the consumer discards one belonging to a song already skipped past
     * (see LyricsViewModel) - the replay can never show the wrong words.
     */
    val lyrics = MutableLiveData<LyricsResponse>()

    /**
     * The phone's most recent metadata answer.
     *
     * A plain [MutableLiveData] for the same reason [lyrics] is one: the last answer *is* replayed
     * to a screen that opens later, which is wanted rather than tolerated. The payload carries the
     * track it describes, so the consumer discards one belonging to a song already skipped past
     * (see MetadataFeed) - the replay can never show the wrong table.
     */
    val trackMetadata = MutableLiveData<TrackMetadata>()

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

    /**
     * This device's monotonic clock reading when the current [musicState] actually arrived.
     *
     * The anchor every position prediction on the watch counts from. It has to live here rather
     * than be stamped by each consumer, because LiveData hands a late observer the value that
     * arrived *before* it subscribed - a consumer stamping "now" as it stores that replayed state
     * would restart the clock on a sample that is already old, and the lyric would sit behind by
     * however long the screen took to open.
     *
     * Read it in the same callback that stores the state; it then refers to that state.
     *
     * elapsedRealtime, not currentTimeMillis: this measures a duration, and a wall clock can be
     * stepped by an NTP correction mid-track. See [PlaybackPositionEstimate].
     */
    // Seeded rather than left at zero: a consumer reading it before any state has been delivered
    // would otherwise measure its "time held" from the epoch and pin every track to its end.
    @Volatile
    var musicStateArrivalRealtimeMs: Long = SystemClock.elapsedRealtime()
        private set

    /**
     * Where the song is, for everything on this device that needs to know.
     *
     * Owned here rather than by a screen because the correction it runs has to outlive any one of
     * them: the lyrics screen used to appear to synchronise only when it was opened, and that was
     * literally true - opening it created a fresh observer, which replayed the last state and
     * re-anchored on it. Nothing else ever did. Living on the connection means the estimate is kept
     * honest for as long as the watch app is talking to the phone at all, so a screen that opens
     * finds it already correct instead of starting the process over.
     */
    val playbackClock = PlaybackClock()

    /** The resync loop, cancelled and relaunched rather than left to notice a new cadence on its
     *  own iteration - see [scheduleNextPlaybackSync]. */
    private var playbackSyncJob: Job? = null

    /** This device's monotonic reading when the outstanding sync request went out, or null when
     *  none is in flight. Doubles as the token echoed back by the phone. */
    private var playbackSyncSentAtMs: Long? = null

    // Highest MusicState.seq applied so far. The phone stamps a wall-clock-monotonic seq on every
    // DATA_MUSIC_STATE put, so when Play Services replays the buffered revisions of a watch that
    // was unreachable (skip-skip-skip while asleep), the older ones can be discarded instead of
    // marching through each stale track one by one. Process-lifetime (this is a @Singleton) and
    // in-memory only: the phone's seq out-numbers any stored revision across a phone restart, and
    // a watch restart resets to 0 and re-seeds from loadCurrentMusicState.
    private var lastAppliedMusicSeq: Long = 0L

    // Data Layer asset id (content-derived) of the currently decoded album art - lets an
    // unchanged cover riding along on every state put be skipped without decoding it again.
    private var lastAlbumArtAssetId: String? = null
    private var lastSourceIconAssetId: String? = null

    private var sendingVolume = false
    private var nextVolume = -1f

    private var sendingSeek = false
    private var nextSeekPositionMs = -1L

    private var running = AtomicBoolean(false)

    init {
        lifecycleObserver.addLiveData(musicState)
        lifecycleObserver.addLiveData(albumArt)
        lifecycleObserver.addLiveData(sourceIcon)
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
            // The only phone -> watch *message* this object consumes. Everything else it receives
            // arrives as a DataItem, and the manifest listeners cover the paths that must land
            // while no UI is running - see CommPaths.MESSAGE_LYRICS_RESULT for why lyrics are
            // deliberately not one of those.
            messageClient.addListener(this)

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

            // Independent of any screen: the correction has to be running before a lyrics surface
            // opens, or opening one is once again the only thing that ever synchronises it.
            scheduleNextPlaybackSync()
        }
    }

    private fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        playbackSyncJob?.cancel()
        playbackSyncJob = null
        playbackSyncSentAtMs = null

        scope?.launchWithErrorHandling(context, musicState) {
            try {
                dataClient.removeListener(this)
                // start() registers both listeners; stop() only ever removed the data one, so the
                // capability listener leaked (and kept firing onCapabilityChanged after close).
                capabilityClient.removeListener(this)
                messageClient.removeListener(this)

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
            // The connection just came (back) up. Whatever the estimate has been doing while the
            // phone was unreachable, it has had nothing to check itself against - so verify now
            // rather than waiting out a backoff that grew while nobody was listening.
            requestPlaybackResync()
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

    /**
     * Tells the phone the user picked [entryId] out of [listId].
     *
     * Uncancellable for the same reason [closeManually] is: every caller closes its screen in the
     * same gesture that selects, and the ViewModel scope the send was launched from dies with it.
     * The send is not one call but a node lookup followed by a message, so a cancel landing between
     * the two dropped the selection entirely - the queue tap that appeared to do nothing.
     */
    suspend fun executeCustomMenuAction(listId: String, entryId: String) {
        withContext(NonCancellable) {
            sendToPhone(
                    CommPaths.MESSAGE_CUSTOM_LIST_ITEM_SELECTED,
                    CustomListItemAction.newBuilder()
                            .setListId(listId)
                            .setEntryId(entryId)
                            .build()
                            .toByteArray()
            )
        }
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
            // A reconnecting watch can receive several TYPE_CHANGED revisions of the same path
            // (e.g. a handful of MusicState puts queued while it was unreachable/asleep) - Play
            // Services does not collapse those before delivery. Keeping every one and applying
            // them in order replayed each queued track change/asset in sequence instead of jumping
            // straight to the latest. Collapsing to the newest event per path handles the case
            // where they all land in one buffer; the real flood usually spans several onDataChanged
            // callbacks, which the per-path monotonic guards below (MusicState.seq here,
            // SYNC_REVISION_KEY for prefs, revision-agnostic latest-wins for configs) also cover.
            val latestByPath = LinkedHashMap<String?, DataItem>()
            frozenData.filter { it.type == DataEvent.TYPE_CHANGED }
                    .forEach { event ->
                        val item = event.dataItem
                        val path = item.uri.path
                        val existing = latestByPath[path]
                        // Buffer order isn't guaranteed, so for music state keep the higher seq
                        // rather than the last-iterated item.
                        if (existing != null && path == CommPaths.DATA_MUSIC_STATE &&
                                musicSeqOf(item) < musicSeqOf(existing)) {
                            return@forEach
                        }
                        latestByPath[path] = item
                    }
            latestByPath.values.forEach {
                when (it.uri.path) {
                    CommPaths.DATA_MUSIC_STATE -> {
                        val dataItem = it.freeze()

                        val receivedMusicState = MusicState.parseFrom(dataItem.data)

                        // Assets are only worth decoding for a state that was actually applied -
                        // a revision the seq gate dropped, or an error, has nothing to attach.
                        if (applyMusicState(receivedMusicState)) {
                            sendAck()

                            deliverAlbumArt(dataItem, receivedMusicState)
                            deliverSourceIcon(dataItem, receivedMusicState)
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
     * Posts [state] unless it matches the last delivered one modulo the albumArtPending flag and
     * the seq - skipping the second put of the two-put track change (see [lastDeliveredMusicState]).
     * Must be called without suspending after parsing, so the tracker is updated before the
     * next queued put's coroutine gets a chance to run.
     */
    /**
     * Applies a music state arriving over either transport - the [CommPaths.DATA_MUSIC_STATE]
     * DataItem or the [CommPaths.MESSAGE_MUSIC_STATE] message that races it.
     *
     * Synchronized because those two arrive on different threads and both touch the seq guard and
     * the delivery dedupe. They also carry the *same* state by design, so whichever loses the race
     * must be dropped rather than re-run: without the dedupe every observer would do its full
     * update pass twice per change.
     *
     * @return whether this state was applied, i.e. whether any assets attached to it are worth
     *   decoding. False for a stale revision and for an error state.
     */
    @Synchronized
    private fun applyMusicState(state: MusicState): Boolean {
        // Drop a stale revision replayed after a newer one already applied. seq is wall-clock
        // monotonic per put; 0 = a pre-seq phone build, never gated.
        if (state.seq != 0L && state.seq < lastAppliedMusicSeq) {
            return false
        }

        if (state.seq > lastAppliedMusicSeq) {
            lastAppliedMusicSeq = state.seq
        }

        if (state.error) {
            lastDeliveredMusicState = null
            musicState.postValue(Resource.error(state.title, null))
            return false
        }

        deliverMusicState(state)
        return true
    }

    @Synchronized
    private fun deliverMusicState(state: MusicState) {
        // Clear seq too: the two puts of one track change differ only in albumArtPending and seq,
        // and comparing seq would defeat the dedupe and re-run every observer twice per change.
        val comparable = state.toBuilder().setAlbumArtPending(false).clearSeq().build()
        if (comparable == lastDeliveredMusicState) {
            return
        }
        lastDeliveredMusicState = comparable
        // Stamped here, at real arrival, and only when the state is actually delivered - a
        // duplicate that the check above drops carries the same position sample, so restarting
        // the anchor for it would silently rewind the prediction.
        musicStateArrivalRealtimeMs = SystemClock.elapsedRealtime()
        // Recorded before the state is posted, so every observer that wakes on it reads a clock
        // that already describes this sample rather than the previous one.
        val trackChanged = playbackClock.onMusicState(state, musicStateArrivalRealtimeMs)
        musicState.postValue(Resource.success(state))
        if (trackChanged) {
            // A new track is the moment the estimate is least trustworthy - the player is still
            // settling, and the sample that came with the change may predate it. Verify soon.
            requestPlaybackResync()
        }
    }

    /**
     * Asks the phone where playback actually is, and corrects the local estimate by the answer.
     *
     * The request carries this device's monotonic reading as an opaque token; the phone echoes it
     * back untouched, so the return leg can be measured here without either side reading the
     * other's clock. See [CommPaths.MESSAGE_REQUEST_PLAYBACK_SYNC] and [PlaybackSyncPolicy].
     */
    private suspend fun sendPlaybackSyncRequest() {
        val token = SystemClock.elapsedRealtime()
        playbackSyncSentAtMs = token
        sendToPhone(
                CommPaths.MESSAGE_REQUEST_PLAYBACK_SYNC,
                ByteBuffer.allocate(8).putLong(token).array())
    }

    /**
     * Runs the periodic verification for as long as the connection is up.
     *
     * Relaunched rather than signalled, because `delay` fixes its duration when the wait *begins*:
     * a loop that had already backed off to a minute would sit out that minute no matter what
     * happened next, which is precisely wrong for the events - a seek, a skip, a reconnect - that
     * most need checking. Cancelling makes the new cadence take effect now. The same reasoning is
     * written up on `LyricsViewModel.restartTicker`.
     *
     * Nothing is sent while playback is paused: a stopped position cannot drift, so a check would
     * spend Bluetooth to confirm a number that cannot have changed.
     */
    private fun scheduleNextPlaybackSync(initialDelayMs: Long? = null) {
        playbackSyncJob?.cancel()
        val scope = scope ?: return
        // A plain launch, deliberately not launchWithErrorHandling: that helper posts
        // Resource.error to musicState, so a sync ping failing because the phone stepped out of
        // range for a moment would replace a perfectly good now-playing screen with an error. This
        // is background upkeep - it fails quietly and the next check retries, the same stance
        // MusicViewModel.refreshPlaybackQueueSilently takes.
        playbackSyncJob = scope.launch {
            var wait = initialDelayMs ?: playbackClock.syncIntervalMs
            while (isActive) {
                delay(wait)
                if (playbackClock.isPlaying()) {
                    if (playbackSyncSentAtMs != null) {
                        // The previous request was never answered - the phone is out of range, or
                        // its MusicService was not up to hear it. Treat that exactly as a quiet
                        // check, or an unreachable phone would be polled at the floor cadence for
                        // as long as the clock still believed something was playing.
                        playbackClock.backOffUnanswered()
                    }
                    try {
                        sendPlaybackSyncRequest()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.v(e, "Could not ask the phone for a playback sync")
                    }
                }
                wait = playbackClock.syncIntervalMs
            }
        }
    }

    /**
     * Forces a verification shortly from now and puts the cadence back at its floor.
     *
     * Called for everything that invalidates the estimate without replacing it: play, pause, skip,
     * seek, a predicted track advance, a reconnect, a lyrics surface opening. Deliberately *not*
     * immediate - [PlaybackSyncPolicy.COMMAND_SETTLE_MS] gives the phone time to receive the
     * command, hand it to the player and let the player act, since sampling before the command took
     * effect would correct the estimate to the state it was trying to leave.
     */
    fun requestPlaybackResync() {
        playbackClock.resetBackoff()
        scheduleNextPlaybackSync(PlaybackSyncPolicy.COMMAND_SETTLE_MS)
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

    /** Decodes the optional source-app icon asset. It only changes when the playing app changes
     *  (the phone dedupes it), so track by asset id and keep the cached bitmap otherwise. */
    private suspend fun deliverSourceIcon(dataItem: DataItem, state: MusicState) {
        val asset = dataItem.assets[CommPaths.ASSET_SOURCE_ICON]
        if (asset == null) {
            // An albumArtPending state is the interim put the phone ships on every track change so
            // the title/artist do not wait behind the cover transfer - it carries *no* assets at
            // all, this icon included. Clearing here made the source icon blink out on every track
            // change until the second put landed. Only a settled state genuinely means "no icon".
            // deliverAlbumArt guards on the same flag for the same reason.
            if (state.albumArtPending) return
            lastSourceIconAssetId = null
            sourceIcon.postValue(null)
            return
        }
        if (asset.id == lastSourceIconAssetId && sourceIcon.value != null) return
        val bytes = dataClient.getByteArrayAsset(asset)
        val bitmap = bytes?.let { withContext(Dispatchers.Default) { BitmapUtils.deserialize(it) } }
        lastSourceIconAssetId = asset.id
        sourceIcon.postValue(bitmap)
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
        // Seed the seq guard from the store's current (latest) revision so the buffered older
        // puts that onDataChanged is about to replay on reconnect are gated as stale from the off.
        if (storedState.seq > lastAppliedMusicSeq) {
            lastAppliedMusicSeq = storedState.seq
        }
        if (storedState.error) {
            // A stored error ("nothing playing" etc.) may predate whatever the phone is about to
            // send in response to MESSAGE_WATCH_OPENED - keep showing loading instead.
            return
        }

        applyMusicState(storedState)
        deliverAlbumArt(dataItem, storedState)
    }

    /** Parses just the transport seq off a music-state DataItem for the in-buffer collapse; a
     *  malformed or pre-seq item reads as 0 (never gated). */
    private fun musicSeqOf(dataItem: DataItem): Long =
            try {
                MusicState.parseFrom(dataItem.data).seq
            } catch (e: Exception) {
                0L
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
            //
            // Trimmed here rather than only on the phone because this is the one point every cover
            // the watch draws passes through, whatever produced it - a resolver step, a reused
            // now-playing bitmap, or an older phone build that never trimmed at all. Without it a
            // YouTube Music "art track" thumbnail keeps its letterbox bars, and the row renders as
            // a small cover inside a flat rectangle instead of filling its slot.
            val picture = pictureData?.let { bytes ->
                withContext(Dispatchers.Default) {
                    BitmapUtils.deserialize(bytes)?.let(BitmapBorderTrim::trim)
                }
            }
            CustomListItemWithIcon(rawEntry, picture)
        }
        return CustomListWithBitmaps(
                received.listTimestamp,
                received.listId,
                listItems,
                received.activeEntryId.takeIf { it.isNotEmpty() },
                // A phone that predates paging reports no total; what arrived is then all there is.
                if (received.hasTotalEntryCount()) received.totalEntryCount else listItems.size
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

    /**
     * Whether a phone node is currently known.
     *
     * Best-effort and deliberately not authoritative: it reflects the cached node from the last
     * capability update, so it can be stale in either direction. Used only to warn in the face
     * picker that a choice may not stick, never to decide whether to attempt a send.
     */
    fun isPhoneConnected(): Boolean = phoneNodeId != null

    /**
     * Tells the phone the user picked [face] in the on-watch picker.
     *
     * Uncancellable for the same reason [executeCustomMenuAction] is: the picker closes its own
     * screen in the same gesture, and the send is a node lookup followed by a message.
     */
    suspend fun setScreenFace(face: String) {
        withContext(NonCancellable) {
            sendToPhone(CommPaths.MESSAGE_SET_SCREEN_FACE, face.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Asks the phone to publish the playback queue, capped at [entryLimit] entries.
     *
     * The cap travels with the request because the phone replaces the queue DataItem wholesale
     * rather than appending to it - "load more" is the same request asking for a bigger page. Play
     * Services addresses assets by content hash, so the covers already on the watch are not
     * re-transferred, only the newly-included ones.
     */
    suspend fun openPlaybackQueue(entryLimit: Int = QueuePaging.PAGE_SIZE) {
        sendToPhone(
                CommPaths.MESSAGE_OPEN_PLAYBACK_QUEUE,
                ByteBuffer.allocate(4).putInt(entryLimit).array())
    }

    /**
     * Asks the phone to look the lyrics for this track up and send them back.
     *
     * The track travels with the request rather than the phone reading its own session: the two
     * sides are a track apart whenever a skip is in flight, and lyrics for the previous song are
     * worse than none at all because nothing about them looks wrong. The phone echoes these fields
     * into its answer so [lyrics] can be matched against what is actually on screen.
     */
    suspend fun requestLyrics(title: String?, artist: String?, durationMs: Long) {
        val request = LyricsRequest.newBuilder()
                .setTitle(title.orEmpty())
                .setArtist(artist.orEmpty())
                .setDurationMs(durationMs)
                .build()
        sendToPhone(CommPaths.MESSAGE_REQUEST_LYRICS, request.toByteArray())
    }

    /**
     * Asks the phone for everything it knows about this track.
     *
     * The track travels with the request rather than the phone reading its own session, for the
     * reason [requestLyrics] documents: the two sides are a track apart whenever a skip is in
     * flight. The phone echoes these fields into its answer so [trackMetadata] can be matched
     * against what is actually on screen.
     */
    suspend fun requestTrackMetadata(title: String?, artist: String?) {
        val request = TrackMetadata.newBuilder()
                .setTitle(title.orEmpty())
                .setArtist(artist.orEmpty())
                .build()
        sendToPhone(CommPaths.MESSAGE_REQUEST_TRACK_METADATA, request.toByteArray())
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            CommPaths.MESSAGE_LYRICS_RESULT -> try {
                lyrics.postValue(LyricsResponse.parseFrom(event.data))
            } catch (e: Exception) {
                // A malformed payload can only come from a phone build that disagrees with this
                // one about the schema. Dropping it leaves the screen on its "still loading"
                // state, which its own timeout resolves - far better than taking the process down
                // over a lyric.
                Timber.w(e, "Could not parse the lyrics response")
            }

            CommPaths.MESSAGE_TRACK_METADATA -> try {
                trackMetadata.postValue(TrackMetadata.parseFrom(event.data))
            } catch (e: Exception) {
                // A payload from a phone build that disagrees about the schema. Dropping it leaves
                // the face on its empty state rather than taking the process down over a table.
                Timber.w(e, "Could not parse the track metadata")
            }

            CommPaths.MESSAGE_PLAYBACK_SYNC -> try {
                // Ignored when nothing is outstanding: a duplicate reply, or one arriving after the
                // request it answers was superseded, has no round trip to be measured against.
                val sentAt = playbackSyncSentAtMs
                val sync = PlaybackSync.parseFrom(event.data)
                if (sentAt != null && sync.token == sentAt) {
                    playbackSyncSentAtMs = null
                    if (playbackClock.onSyncReply(sync, sentAt)) {
                        // The backoff has already been reset by the correction; relaunch so the
                        // loop picks the new cadence up now rather than after its current wait.
                        scheduleNextPlaybackSync()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not parse the playback sync reply")
            }

            CommPaths.MESSAGE_MUSIC_STATE -> try {
                // The low-latency copy of the DataItem below. It carries no assets, so the cover
                // keeps arriving on the slower path and crossfades in behind this - which is the
                // same two-phase behaviour a track change already had, just with the text half no
                // longer waiting on the replication layer.
                applyMusicState(MusicState.parseFrom(event.data))
            } catch (e: Exception) {
                Timber.w(e, "Could not parse the music state message")
            }
        }
    }

    private class ConnectionCloseHandler(val phoneConnection: WeakReference<PhoneConnection>) : Handler(Looper.getMainLooper()) {
        override fun dispatchMessage(msg: android.os.Message) {
            if (msg.what == MESSAGE_CLOSE_CONNECTION) {
                phoneConnection.get()?.stop()
            }
        }
    }
}
