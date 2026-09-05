package com.svartifoss.snfell.watch.view

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.watch.view.lyrics.LyricsFeed
import com.svartifoss.snfell.watch.view.metadata.MetadataFeed
import com.svartifoss.snfell.watch.view.lyrics.LyricsUiState
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.actions.StandardActions
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.SpecialButtonCodes
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.proto.TrackMetadata
import com.svartifoss.snfell.watch.communication.CustomListWithBitmaps
import com.svartifoss.snfell.watch.communication.PhoneConnection
import com.svartifoss.snfell.watch.communication.PhoneUriOpener
import com.svartifoss.snfell.watch.communication.WatchInfoSender
import com.svartifoss.snfell.watch.config.ButtonAction
import com.svartifoss.snfell.watch.config.PreferencesBus
import com.svartifoss.snfell.watch.config.WatchActionConfigProvider
import com.svartifoss.snfell.watch.config.WatchActionMenuProvider
import com.svartifoss.snfell.watch.model.Notification
import com.svartifoss.snfell.watch.util.launchWithErrorHandling
import com.matejdro.wearutils.lifecycle.Resource
import com.matejdro.wearutils.lifecycle.SingleLiveEvent
import com.matejdro.wearutils.preferences.definition.Preferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

data class PlaybackPosition(val positionMs: Long, val durationMs: Long, val seekable: Boolean)

private const val POSITION_TICK_INTERVAL_MS = 500L

/** See [MusicViewModel.expirePrediction]. */
private const val PREDICTION_MAX_MS = 5_000L

/** How long the app may sit on the idle "Nothing playing" screen before it closes itself
 *  (when [MiscPreferences.WEAR_CLOSE_ON_IDLE] is on). */
private const val IDLE_CLOSE_SECONDS = 60

@HiltViewModel
class MusicViewModel @Inject constructor(
        private val application: Application,
        private val phoneConnection: PhoneConnection
) : ViewModel() {

    private val playbackConfig = WatchActionConfigProvider(application, viewModelScope, phoneConnection.rawPlaybackConfig)
    private val stoppedConfig = WatchActionConfigProvider(application, viewModelScope, phoneConnection.rawStoppedConfig)

    private val handler = Handler(Looper.getMainLooper())
    private var closeDeadline = Long.MAX_VALUE

    val currentButtonConfig = MediatorLiveData<WatchActionConfigProvider>()
    val musicState = MediatorLiveData<Resource<MusicState>>()
    val customList = MediatorLiveData<CustomListWithBitmaps>()
    val actionsMenuConfig = WatchActionMenuProvider(application, viewModelScope, phoneConnection.rawActionMenuConfig)
    val preferences = PreferencesBus as LiveData<SharedPreferences>

    /**
     * Phone notifications to pop up, once each, for *this* Activity.
     *
     * Owned here rather than by [PhoneConnection] because the ViewModel dies with the screen while
     * that connection does not - see the note on `PhoneConnection.notification`. Replays are
     * dropped by [Notification.time], so an Activity recreated after one arrived does not show it
     * a second time, which is the guarantee SingleLiveEvent used to provide.
     */
    val notification = SingleLiveEvent<Notification>()

    private var lastForwardedNotificationTime: Long? = null
    private val notificationRelay = Observer<Notification?> { incoming ->
        if (incoming != null && incoming.time != lastForwardedNotificationTime) {
            lastForwardedNotificationTime = incoming.time
            notification.value = incoming
        }
    }

    val volume = MutableLiveData<Float>()
    val playbackPosition = MutableLiveData<PlaybackPosition>()
    private var latestMusicState: MusicState? = null
    private var continuousTickingEnabled = true
    private val positionTickRunnable = Runnable { tickPlaybackPosition() }

    /**
     * In ambient mode the screen should repaint as little as possible, so the 500ms ticker is
     * paused entirely.
     *
     * It was written for this and then never called, so the ticker kept running with the wrist
     * down: 120 position updates a minute, each one pushing new face state and recomposing the
     * ambient face, on a panel the system physically redraws about **once** in that minute. All of
     * it invisible, all of it billed to the battery.
     *
     * The original note said the position "isn't even shown there", which was true when only the
     * classic AOD existed. It is not any more - ambient progress, the ambient track time and the
     * Verse face's ambient lyric line all read it - so the position is refreshed once per ambient
     * update instead (see [refreshPositionOnce]), which is the platform's own cadence and costs no
     * extra wakeups.
     */
    fun setContinuousPositionTicking(enabled: Boolean) {
        continuousTickingEnabled = enabled

        if (!enabled) {
            handler.removeCallbacks(positionTickRunnable)
        } else if (latestMusicState?.playing == true) {
            tickPlaybackPosition()
        }
    }

    val popupVolumeBar = SingleLiveEvent<Unit>()
    val closeActionsMenu = SingleLiveEvent<Unit>()
    val openActionsMenu = SingleLiveEvent<Unit>()
    val openQuickActionsPanel = SingleLiveEvent<Unit>()
    val openPlaybackQueueScreen = SingleLiveEvent<Unit>()
    val openStreamingShortcutsMenu = SingleLiveEvent<Unit>()
    val openVoiceSearch = SingleLiveEvent<Unit>()

    /** Opens the synced-lyrics screen. Purely local - nothing is asked of the phone until that
     *  screen itself requests the lyric text. */
    val openLyricsScreen = SingleLiveEvent<Unit>()

    /** Opens the dedicated volume/progress screens - fully local, like [openLyricsScreen]: both
     *  read what they need from the MusicState PhoneConnection already holds. */
    val openVolumeScreen = SingleLiveEvent<Unit>()
    val openProgressScreen = SingleLiveEvent<Unit>()
    val openFacePicker = SingleLiveEvent<Unit>()

    /**
     * Lyrics for the current track, for the Verse face.
     *
     * Off until [setLyricsEnabled] turns it on, which the host does only while that face is the
     * selected one: the lookup is a network call on the phone, and nobody who has not chosen a
     * lyrics surface should be paying for one on every track change.
     */
    private val lyricsFeed = LyricsFeed(phoneConnection, viewModelScope)
    val lyricsState: LiveData<LyricsUiState> = lyricsFeed.state

    fun setLyricsEnabled(enabled: Boolean) = lyricsFeed.setEnabled(enabled)

    /**
     * The playing track's full metadata, for the Metadata face.
     *
     * Off until [setMetadataEnabled] turns it on, which the host does only while that face is
     * selected - answering it costs the phone a file read, and an online lookup on top when the
     * user has switched that on. Same gating, and same reasoning, as [lyricsFeed].
     */
    private val metadataFeed = MetadataFeed(phoneConnection, viewModelScope)
    val trackMetadata: LiveData<TrackMetadata?> = metadataFeed.state

    fun setMetadataEnabled(enabled: Boolean) = metadataFeed.setEnabled(enabled)
    val closeApp = SingleLiveEvent<Unit>()

    /**
     * The cover for the now-playing screen: the phone's, except while a predicted track advance is
     * standing in for one that has not crossed Bluetooth yet (see [tryPredictNextTrack]).
     *
     * A mediator rather than the plain passthrough this used to be, because the prediction needs
     * somewhere to put a stand-in cover that the phone's own delivery then overrides. Deliberately
     * scoped to this ViewModel and no further: [PhoneConnection.albumArt] stays the untouched
     * phone truth, so the proxy `WatchMediaSession`, the queue, the lyrics screen and the face
     * picker keep reading it directly and never see a guess. The prediction is a latency device for
     * one screen, not a new source of truth.
     *
     * Sourcing it here preserves the connection lifecycle exactly as the passthrough did: a
     * MediatorLiveData subscribes to its sources only while it is itself active, so observing this
     * still holds [PhoneConnection] open through its `ListenableLiveData`.
     */
    private val _albumArt = MediatorLiveData<Bitmap?>()
    val albumArt: LiveData<Bitmap?> = _albumArt

    val sourceIcon
        get() = phoneConnection.sourceIcon

    /**
     * One position update, with no follow-up scheduled.
     *
     * Relies on [setContinuousPositionTicking] having been turned off first: the tick reschedules
     * itself only while continuous ticking is enabled, so with it disabled this is a genuine
     * one-shot. Called from the ambient update callback so whatever reads the position - the
     * ambient progress, the track time, the Verse face's lyric line - is correct at the moment the
     * panel actually redraws, rather than showing a value frozen when the wrist went down.
     */
    fun refreshPositionOnce() = tickPlaybackPosition()

    fun updateTimers() {
        if (closeDeadline < System.currentTimeMillis()) {
            closeApp.call()
        }
    }

    fun executeActionFromMenu(index: Int) {
        val action = actionsMenuConfig.config.value?.get(index) ?: return

        closeActionsMenu.postValue(Unit)

        action.remoteUri?.takeIf(String::isNotBlank)?.let {
            // Registered, not opened. The phone reports back once its silent routes have
            // had their turn; opening here would foreground the app before they even ran.
            PhoneUriOpener.requestOpenAfterPhoneTries(application, it)
            viewModelScope.launchWithErrorHandling(application, musicState) {
                phoneConnection.executeMenuAction(index)
            }
            return
        }

        if (action.key == StandardActions.ACTION_OPEN_STREAMING_SHORTCUTS) {
            // Render the dedicated DataItem cache immediately. The phone command only refreshes
            // its contents; it is no longer on the critical path to opening the screen.
            openStreamingShortcutsMenu.call()
            viewModelScope.launchWithErrorHandling(application, musicState) {
                phoneConnection.executeMenuAction(index)
            }
            return
        }

        if (executeActionOnWatch(action, 1f)) {
            return
        }

        applyOptimisticFeedback(action)
        viewModelScope.launchWithErrorHandling(application, musicState) {
            phoneConnection.executeMenuAction(index)
        }
    }

    fun executeItemFromCustomMenu(listId: String, itemId: String) {
        closeActionsMenu.postValue(Unit)

        if (listId == CustomLists.PLAYLIST_SHORTCUTS) {
            // Registered, not opened. The phone reports back once its silent routes have
            // had their turn; opening here would foreground the app before they even ran.
            PhoneUriOpener.requestOpenAfterPhoneTries(application, itemId)
            viewModelScope.launchWithErrorHandling(application, musicState) {
                phoneConnection.executeCustomMenuAction(listId, itemId)
            }
            return
        }

        viewModelScope.launchWithErrorHandling(application, musicState) {
            phoneConnection.executeCustomMenuAction(listId, itemId)
        }
    }

    fun executeAction(buttonInfo: ButtonInfo): Boolean {
        val action = currentButtonConfig.value?.getAction(buttonInfo) ?: return false

        action.remoteUri?.takeIf(String::isNotBlank)?.let {
            // Registered, not opened. The phone reports back once its silent routes have
            // had their turn; opening here would foreground the app before they even ran.
            PhoneUriOpener.requestOpenAfterPhoneTries(application, it)
            viewModelScope.launchWithErrorHandling(application, musicState) {
                phoneConnection.executeButtonAction(buttonInfo)
            }
            return true
        }

        if (action.key == StandardActions.ACTION_OPEN_STREAMING_SHORTCUTS) {
            openStreamingShortcutsMenu.call()
            viewModelScope.launchWithErrorHandling(application, musicState) {
                phoneConnection.executeButtonAction(buttonInfo)
            }
            return true
        }

        val multiplier = if (buttonInfo.buttonCode == SpecialButtonCodes.TURN_ROTARY_CW ||
                buttonInfo.buttonCode == SpecialButtonCodes.TURN_ROTARY_CCW) {
            Preferences.getInt(preferences.value!!, MiscPreferences.ROTATING_CROWN_SENSITIVITY) / 100f
        } else {
            1f
        }

        if (!executeActionOnWatch(action, multiplier)) {
            applyOptimisticFeedback(action)
            viewModelScope.launchWithErrorHandling(application, musicState) {
                phoneConnection.executeButtonAction(buttonInfo)
            }
        }

        return true
    }

    private fun executeActionOnWatch(action: ButtonAction, multiplier: Float): Boolean {
        return when (action.key) {
            StandardActions.ACTION_VOLUME_UP -> {
                val volumeStep = (currentButtonConfig.value?.volumeStep ?: 0.1f) * multiplier
                updateVolume(min(1f, volume.value!! + volumeStep))
                popupVolumeBar.call()
                true
            }
            StandardActions.ACTION_VOLUME_DOWN -> {
                val volumeStep = (currentButtonConfig.value?.volumeStep ?: 0.1f) * multiplier

                updateVolume(max(0f, volume.value!! - volumeStep))
                popupVolumeBar.call()
                true
            }
            StandardActions.ACTION_OPEN_MENU -> {
                openActionsMenu.call()
                true
            }
            StandardActions.ACTION_OPEN_QUICK_ACTIONS_PANEL -> {
                openQuickActionsPanel.call()
                true
            }
            StandardActions.ACTION_SEARCH -> {
                openVoiceSearch.call()
                true
            }
            StandardActions.ACTION_OPEN_LYRICS -> {
                // Fully watch-local, unlike the queue below: there is no state for the phone to
                // publish up front, so nothing is sent here at all. LyricsActivity asks for the
                // text itself once it knows which track it is showing.
                openLyricsScreen.call()
                true
            }
            StandardActions.ACTION_OPEN_PLAYLIST_MENU -> {
                // Same phone-side request QueueActivity itself makes (asks the phone to run
                // OpenPlaylistAction and push fresh queue data) - just also opens the screen
                // locally instead of only reacting to whatever list shows up.
                openPlaybackQueueScreen.call()
                openPlaybackQueue()
                true
            }
            StandardActions.ACTION_OPEN_VOLUME_SCREEN -> {
                openVolumeScreen.call()
                true
            }
            StandardActions.ACTION_OPEN_PROGRESS_SCREEN -> {
                openProgressScreen.call()
                true
            }
            StandardActions.ACTION_OPEN_FACE_PICKER -> {
                openFacePicker.call()
                true
            }
            StandardActions.ACTION_CLOSE_WATCH_APP -> {
                closeApp.call()
                true
            }
            else -> false
        }
    }

    fun playFromSearch(query: String) {
        viewModelScope.launchWithErrorHandling(application, musicState) {
            phoneConnection.sendPlayFromSearch(query)
        }
    }

    fun updateVolume(newVolume: Float) {
        volume.value = newVolume
        phoneConnection.sendVolume(newVolume)
    }

    /** Seeks to [fraction] (0f..1f) of the current track's duration. No-op if not seekable. */
    fun seekTo(fraction: Float) {
        val state = latestMusicState ?: return
        if (!state.seekable || state.durationMs <= 0) {
            return
        }

        val positionMs = (fraction * state.durationMs).toLong()

        // Update our local snapshot too (not just the LiveData), otherwise the next scheduled
        // tick - still running every POSITION_TICK_INTERVAL_MS off the pre-seek snapshot - would
        // immediately overwrite this optimistic value with a position extrapolated from stale
        // data, making the seek look like it "snaps back" until the phone confirms the real one.
        latestMusicState = state.toBuilder()
                .setPositionMs(positionMs)
                .setPositionUpdateTime(System.currentTimeMillis())
                .setPositionAgeMs(0L)
                .build()
        anchorPositionNow(positionMs, state.playing)

        playbackPosition.value = PlaybackPosition(positionMs, state.durationMs, state.seekable)
        phoneConnection.sendSeek(positionMs)
    }

    /**
     * Marks the one state about to be pushed through [musicStateListener] from here rather than
     * from the phone.
     *
     * Only [resolvePrediction] still reads it: a locally built state must not be mistaken for the
     * phone confirming a guess, since it is this device answering itself.
     */
    private var nextStateIsLocallyAnchored = false

    /**
     * Records a position worked out here, this instant, and asks the phone to verify it shortly.
     *
     * Both halves matter. The local anchor is what makes a seek or an optimistic pause show
     * immediately instead of after a round trip; the verification is what stops that optimism from
     * becoming a lie when the command did not take effect the way this device assumed - the phone
     * is the only thing that knows whether the player actually did it.
     */
    private fun anchorPositionNow(positionMs: Long, playing: Boolean) {
        phoneConnection.playbackClock.anchorLocally(positionMs, playing)
        phoneConnection.requestPlaybackResync()
    }

    private fun tickPlaybackPosition() {
        val state = latestMusicState
        if (state != null) {
            // From the shared clock, not from a local anchor: it is the one that gets verified
            // against the phone, so the progress ring, the track time and the Verse face's lyric
            // line all move on the same corrected position instead of three drifting copies.
            val interpolatedPosition = phoneConnection.playbackClock.positionNowMs()

            playbackPosition.value = PlaybackPosition(interpolatedPosition, state.durationMs, state.seekable)

            expirePrediction()
            // After the position has been published, not before: the prediction replaces
            // latestMusicState, and this tick's job is to report where the track that is ending
            // actually got to.
            //
            // Returning when it fires is not optional. A prediction is applied through
            // musicStateListener, which re-runs this function for the new state and schedules the
            // next tick itself - falling through to the scheduling below as well would leave two
            // ticker chains posting for the rest of the session.
            if (tryPredictNextTrack(state, interpolatedPosition)) {
                return
            }
        }

        if (state?.playing == true && continuousTickingEnabled) {
            handler.postDelayed(positionTickRunnable, POSITION_TICK_INTERVAL_MS)
        }
    }

    /**
     * Title of the track this device has moved to on its own, ahead of the phone confirming it, or
     * null when no prediction is outstanding.
     *
     * Doubles as the "one at a time" guard: a prediction stands until a state arrives from the
     * phone, so a track whose successor is never confirmed cannot keep predicting its way down the
     * queue.
     */
    private var predictedTitle: String? = null

    /** Set while [_albumArt] is showing a queue thumbnail in place of the phone's cover. */
    private var predictedArt: Bitmap? = null

    /** When the outstanding prediction was made, on this device's monotonic clock. Bounds both
     *  halves of it - see [expirePrediction]. */
    private var predictedAtRealtimeMs = 0L

    /**
     * Moves the screen to the next queue entry the moment the current track runs out, instead of
     * waiting to be told.
     *
     * The phone remains the authority - see [resolvePrediction], which applies whatever it says the
     * moment it says it. What this buys is the gap in between, which at a track boundary is the
     * worst one there is: the player has to publish new metadata, `ActiveMediaSessionProvider` has
     * to be listening at that moment, the state has to cross Bluetooth, and the cover has to follow
     * it as a Data Layer asset.
     *
     * Only the fields that follow from "this track ended and the queue says what is next" are
     * touched:
     *
     *  - **title/artist** come from the queue row, which is the phone's own data, round-tripped.
     *  - **position** goes to zero, anchored to now - the certain part, and the one the user
     *    notices, since the old behaviour kept extrapolating the finished track's clock against the
     *    new track's length.
     *  - **liked** is cleared, because it belongs to the track that just ended and a stale heart
     *    reads as a statement about the new one.
     *  - **duration** is deliberately *kept*. The queue carries none, and showing `0:00 / 0:00`
     *    with a dead ring for the moment before the phone answers is a worse lie than a ring that
     *    restarts against a length that is about to be corrected. This is the same trade
     *    [applyOptimisticTrackStart] already makes for a manual skip.
     *  - **mediaActions** are kept: they describe the playing app's notification buttons, not the
     *    track, so dropping them would flicker the quick panel for no gain.
     */
    private fun tryPredictNextTrack(state: MusicState, positionMs: Long): Boolean {
        if (predictedTitle != null) {
            return false
        }
        if (!PredictedTrackAdvance.canPredict(
                        playing = state.playing,
                        positionMs = positionMs,
                        durationMs = state.durationMs,
                        shuffleEnabled = state.shuffleEnabled,
                        repeatMode = state.repeatMode)) {
            return false
        }

        val queue = customList.value ?: return false
        // History is backward-looking - it is what the watch falls back to when the player exposes
        // no queue at all - so it cannot say what comes next. Search results and shortcut lists
        // share the same DataItem and are not a play order either.
        if (queue.listId != CustomLists.PLAYLIST) {
            return false
        }
        val items = queue.items.filter { it.listItem.entryId != CustomLists.SPECIAL_ITEM_ERROR }
        val nextIndex = PredictedTrackAdvance.nextIndex(
                entryIds = items.map { it.listItem.entryId },
                titles = items.map { it.listItem.entryTitle },
                activeEntryId = queue.activeEntryId,
                currentTitle = state.title)
        if (nextIndex < 0) {
            return false
        }
        val next = items[nextIndex]
        val nextTitle = next.listItem.entryTitle.takeIf { it.isNotBlank() } ?: return false

        Timber.d("Predicting the track change to %s from the queue", nextTitle)
        predictedTitle = nextTitle
        predictedAtRealtimeMs = SystemClock.elapsedRealtime()

        next.icon?.let { thumbnail ->
            predictedArt = thumbnail
            _albumArt.value = thumbnail
        }

        val predicted = state.toBuilder()
                .setTitle(nextTitle)
                .setArtist(next.listItem.entrySubtitle.orEmpty())
                .setPositionMs(0L)
                .setPositionUpdateTime(System.currentTimeMillis())
                // Worked out here, right now, so it is not stale at all.
                .setPositionAgeMs(0L)
                .setLiked(false)
                .build()

        anchorPositionNow(0L, playing = true)
        // Through the regular listener, exactly as the optimistic play/pause and skip paths do, so
        // every side effect (config swap, close timeout, position anchoring) behaves the same way
        // it will when the phone confirms.
        nextStateIsLocallyAnchored = true
        musicStateListener.onChanged(Resource.success(predicted))
        return true
    }

    /**
     * Settles an outstanding prediction against what the phone actually says.
     *
     * Called for every state that did *not* originate here. The phone's state is applied either
     * way - this only decides what happens to the stand-in cover, since the stand-in is the one
     * piece the incoming state does not itself replace.
     */
    private fun resolvePrediction(confirmed: MusicState?) {
        val predicted = predictedTitle ?: return
        predictedTitle = null

        if (!PredictedTrackAdvance.isSameTrack(predicted, confirmed?.title)) {
            // The guess was wrong (or the phone went to the idle/paused state instead). Drop the
            // stand-in at once rather than leaving a cover belonging to a track that is not
            // playing; the phone's own cover is whatever is currently decoded.
            clearPredictedArt()
        }
        // Right guess: keep the stand-in. The confirmation arrives over MESSAGE_MUSIC_STATE, which
        // carries no assets by design, so the real cover is still in flight at this point -
        // clearing here would flip the screen back to the previous track's cover for the length of
        // the asset transfer. The phone's delivery clears it instead (see the mediator source in
        // init), with expirePrediction as the backstop.
    }

    /**
     * Drops a prediction the phone has not answered, and a stand-in cover it has not replaced.
     *
     * Both halves need a bound, for different reasons.
     *
     * The **cover** has one specific case that nothing else would ever resolve:
     * `PhoneConnection.deliverAlbumArt` skips delivery outright when the incoming asset id matches
     * what is already decoded, so a next track from the *same album* produces no emission at all.
     * The stand-in is then a copy of the correct cover at queue-thumbnail resolution
     * (`OpenPlaylistAction` sends 96 px, or 320 px for the Cover queue style), which on a
     * full-screen face reads as soft. That is the price, and it is worth paying: the alternative
     * everywhere else is showing the *previous* track's cover for the length of the asset transfer,
     * and a soft copy of the right artwork beats a sharp copy of the wrong artwork.
     *
     * The **prediction itself** needs one because [resolvePrediction] runs only when a state
     * arrives from the phone, and a phone that has gone out of range sends nothing. Without this,
     * one unanswered guess would leave [predictedTitle] set for the rest of the session and block
     * every later track boundary from predicting - degrading silently to the old behaviour, which
     * is exactly the kind of failure that never gets reported.
     */
    private fun expirePrediction() {
        if (predictedTitle == null && predictedArt == null) {
            return
        }
        if (SystemClock.elapsedRealtime() - predictedAtRealtimeMs < PREDICTION_MAX_MS) {
            return
        }
        predictedTitle = null
        clearPredictedArt()
    }

    private fun clearPredictedArt() {
        if (predictedArt == null) {
            return
        }
        predictedArt = null
        _albumArt.value = phoneConnection.albumArt.value
    }

    fun sendManualCloseMessage() {
        viewModelScope.launchWithErrorHandling(application, musicState) {
            phoneConnection.sendManualCloseMessage()
        }
    }

    fun openPlaybackQueue() {
        viewModelScope.launchWithErrorHandling(application, musicState) {
            phoneConnection.openPlaybackQueue()
        }
    }

    /** Ambient Up Next is optional decoration. A disconnected phone must not replace otherwise
     * valid playback metadata with an error merely because this background refresh failed. */
    fun refreshPlaybackQueueSilently() {
        viewModelScope.launch {
            try {
                phoneConnection.openPlaybackQueue()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not refresh ambient Up Next preview")
            }
        }
    }

    /** Toggles play/pause directly, independent of however the four quadrants are configured. */
    fun togglePlayPause() {
        latestMusicState?.let { applyOptimisticPlayingState(!it.playing) }
        viewModelScope.launchWithErrorHandling(application, musicState) {
            phoneConnection.togglePlayPause()
        }
    }

    /**
     * Immediately reflects a play/pause-type command in the local UI instead of waiting for the
     * phone to execute it and transmit the resulting state back - that full Bluetooth round trip
     * plus the player app's own reaction time is what made the button feel laggy. The phone's
     * real state overwrites this within a moment; if the command didn't take, the UI snaps back.
     */
    private fun applyOptimisticFeedback(action: ButtonAction) {
        when (action.key) {
            StandardActions.ACTION_PLAY_PAUSE ->
                latestMusicState?.let { applyOptimisticPlayingState(!it.playing) }
            StandardActions.ACTION_PLAY -> applyOptimisticPlayingState(true)
            StandardActions.ACTION_PAUSE, StandardActions.ACTION_STOP ->
                applyOptimisticPlayingState(false)
            StandardActions.ACTION_SKIP_TO_NEXT,
            StandardActions.ACTION_SKIP_TO_PREV,
            StandardActions.ACTION_RESTART -> applyOptimisticTrackStart()
        }
    }

    /**
     * Snaps the displayed position to the start of a track, without waiting to be told.
     *
     * Asking for the next track and then asking the phone where playback is answers a question
     * whose answer is already known: it is at the beginning. Until the phone's state came back the
     * watch kept extrapolating the *previous* track's position, so the clock carried on climbing
     * through the track the user had just left - and against the new track's length it kept
     * climbing rather than stopping at the old one's end.
     *
     * "Previous" resets to zero as well, and correctly: players almost universally treat it as
     * "restart this track unless you are within a few seconds of the start", so both outcomes begin
     * at zero.
     *
     * The title and artist are deliberately left alone - the next track's name is genuinely not
     * known here, and guessing it is what an optimistic update must not do. Only the part that is
     * certain moves.
     */
    private fun applyOptimisticTrackStart() {
        val state = latestMusicState ?: return

        val optimisticState = state.toBuilder()
                .setPositionMs(0L)
                .setPositionUpdateTime(System.currentTimeMillis())
                // Worked out here, right now, so it is not stale at all.
                .setPositionAgeMs(0L)
                .build()

        anchorPositionNow(0L, state.playing)
        nextStateIsLocallyAnchored = true
        musicStateListener.onChanged(Resource.success(optimisticState))
    }

    private fun applyOptimisticPlayingState(nowPlaying: Boolean) {
        val state = latestMusicState ?: return
        if (state.playing == nowPlaying) {
            return
        }

        // Where playback has actually reached, taken before the anchor moves: pausing must freeze
        // the display where the song is, not where its last sample was, and resuming must carry on
        // from there rather than from a stale base. Same trick seekTo() uses.
        val position = phoneConnection.playbackClock.positionNowMs()

        val optimisticState = state.toBuilder()
                .setPlaying(nowPlaying)
                .setPositionMs(position)
                .setPositionUpdateTime(System.currentTimeMillis())
                // Worked out here, right now, so it is not stale at all.
                .setPositionAgeMs(0L)
                .build()

        anchorPositionNow(position, nowPlaying)
        // Route through the regular listener so every side effect (config swap, position
        // ticker, close timeout) behaves exactly as it will when the phone confirms. The flag
        // tells it this state was built here, so resolvePrediction does not read it as the phone
        // confirming a guess.
        nextStateIsLocallyAnchored = true
        musicStateListener.onChanged(Resource.success(optimisticState))
    }

    /** Skips to the next track directly, independent of the quadrant/button config - used by
     *  the expressive face's transport buttons, over the same Data Layer path
     *  WatchMediaSession's transport controls use. */
    fun skipNext() {
        applyOptimisticTrackStart()
        viewModelScope.launchWithErrorHandling(application, musicState) {
            phoneConnection.sendSkipNext()
        }
    }

    /** Skips to the previous track directly - see [skipNext]. */
    fun skipPrevious() {
        applyOptimisticTrackStart()
        viewModelScope.launchWithErrorHandling(application, musicState) {
            phoneConnection.sendSkipPrevious()
        }
    }

    /** Triggers like/shuffle/repeat directly from the quick-actions panel, regardless of how
     *  (or whether) the four quadrants are configured. [name] is "like", "shuffle" or "repeat". */
    fun sendQuickAction(name: String) {
        viewModelScope.launchWithErrorHandling(application, musicState) {
            phoneConnection.sendQuickAction(name)
        }
    }

    private val configChangeListener = Observer<WatchActionConfigProvider?> {
        currentButtonConfig.value = it
    }

    private val musicStateListener = Observer<Resource<MusicState>?> {
        Timber.d("Received MusicState %s", it?.data)

        // A state that was not built here came from the phone, which is the authority: it settles
        // whatever the heuristic guessed before it is applied below. Read the flag rather than
        // consuming it - it is cleared further down, after every consumer has had it.
        if (!nextStateIsLocallyAnchored) {
            resolvePrediction(it?.data)
        }

        val playing = it?.data?.playing == true

        closeDeadline = Long.MAX_VALUE
        handler.removeCallbacks(closeRunnable)
        if (!playing) {
            val prefs = preferences.value!!
            val configuredTimeout = Preferences.getInt(prefs, MiscPreferences.CLOSE_TIMEOUT)
            // Truly idle = nothing playing at all (the "Nothing playing" screen), as opposed to a
            // paused track that still has metadata. Idle auto-closes on its own short timer so the
            // app never lingers there; the paused case stays governed by the user's Close timeout.
            val idle = it?.data == null ||
                    (it.data?.playing != true && it.data?.title.isNullOrBlank() == true)
            val idleTimeout = if (idle &&
                    Preferences.getBoolean(prefs, MiscPreferences.WEAR_CLOSE_ON_IDLE)) {
                IDLE_CLOSE_SECONDS
            } else {
                0
            }
            // Fire on whichever applicable timeout is sooner.
            val effectiveSeconds = listOf(configuredTimeout, idleTimeout)
                    .filter { seconds -> seconds > 0 }
                    .minOrNull() ?: 0
            if (effectiveSeconds > 0) {
                val timeoutMs = effectiveSeconds * 1000L
                closeDeadline = System.currentTimeMillis() + timeoutMs
                handler.postDelayed(closeRunnable, timeoutMs)
            }
        }

        val newMusicState = it?.data
        if (it?.status == Resource.Status.SUCCESS && newMusicState != null) {
            if (volume.value != newMusicState.volume) {
                volume.value = newMusicState.volume
            }
        }

        val newConfig = if (playing) playbackConfig else stoppedConfig
        swapConfig(newConfig)

        musicState.value = it

        latestMusicState = newMusicState
        // The anchor itself is no longer kept here. A state from the phone was recorded into
        // PhoneConnection.playbackClock as it arrived (before this observer ran), and a state built
        // here anchored the clock at the point it was built - so by now it is already correct
        // either way. The flag survives only to tell resolvePrediction which of those happened.
        nextStateIsLocallyAnchored = false
        handler.removeCallbacks(positionTickRunnable)
        if (newMusicState != null) {
            tickPlaybackPosition()
        }
    }

    private fun swapConfig(newConfig: WatchActionConfigProvider) {
        if (newConfig === currentButtonConfig.value) {
            return
        }


        currentButtonConfig.value?.updateListener?.let { currentButtonConfig.removeSource(it) }
        currentButtonConfig.addSource(newConfig.updateListener, configChangeListener)
    }

    init {
        viewModelScope.launchWithErrorHandling(application, musicState) {
            WatchInfoSender(application, true).sendWatchInfoToPhone()
        }

        // observeForever, not addSource: the relay must feed the SingleLiveEvent above, and it is
        // removed in onCleared so it dies with this ViewModel rather than with the connection.
        phoneConnection.notification.observeForever(notificationRelay)

        musicState.addSource(phoneConnection.musicState, musicStateListener)
        musicState.addSource(phoneConnection.customList) { customList.value = it }

        // The phone's cover always wins, and its arrival is exactly the moment a predicted
        // stand-in has done its job.
        _albumArt.addSource(phoneConnection.albumArt) { art ->
            predictedArt = null
            _albumArt.value = art
        }

        swapConfig(stoppedConfig)


        volume.value = 0.5f
    }

    private val closeRunnable = Runnable {
        closeApp.call()
    }

    override fun onCleared() {
        phoneConnection.notification.removeObserver(notificationRelay)
        metadataFeed.release()
        super.onCleared()
        handler.removeCallbacks(positionTickRunnable)
        // Detach the observeForever hooks these providers hold on PhoneConnection's (@Singleton)
        // LiveData, otherwise each recreated MusicViewModel leaks its three config providers.
        playbackConfig.destroy()
        stoppedConfig.destroy()
        actionsMenuConfig.destroy()
    }
}
