package com.svartifoss.snfell.watch.view

import android.app.Application
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
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.actions.StandardActions
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.SpecialButtonCodes
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.communication.CustomListWithBitmaps
import com.svartifoss.snfell.watch.communication.PhoneConnection
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

    val notification: LiveData<Notification> = phoneConnection.notification

    val volume = MutableLiveData<Float>()
    val playbackPosition = MutableLiveData<PlaybackPosition>()
    private var latestMusicState: MusicState? = null
    private var continuousTickingEnabled = true
    private val positionTickRunnable = Runnable { tickPlaybackPosition() }

    /**
     * In ambient mode the screen should repaint as little as possible to save battery and avoid
     * burn-in, so the 500ms ticker is paused entirely (the position isn't even shown there).
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
    val openPlaybackQueueScreen = SingleLiveEvent<Unit>()
    val openStreamingShortcutsMenu = SingleLiveEvent<Unit>()
    val openUriOnPhone = SingleLiveEvent<String>()
    val openVoiceSearch = SingleLiveEvent<Unit>()
    val closeApp = SingleLiveEvent<Unit>()

    val albumArt
        get() = phoneConnection.albumArt

    val sourceIcon
        get() = phoneConnection.sourceIcon

    fun updateTimers() {
        if (closeDeadline < System.currentTimeMillis()) {
            closeApp.call()
        }
    }

    fun executeActionFromMenu(index: Int) {
        val action = actionsMenuConfig.config.value?.get(index) ?: return

        closeActionsMenu.postValue(Unit)

        action.remoteUri?.takeIf(String::isNotBlank)?.let {
            openUriOnPhone.value = it
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
            openUriOnPhone.value = itemId
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
            openUriOnPhone.value = it
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
            StandardActions.ACTION_SEARCH -> {
                openVoiceSearch.call()
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
                .build()

        playbackPosition.value = PlaybackPosition(positionMs, state.durationMs, state.seekable)
        phoneConnection.sendSeek(positionMs)
    }

    private fun tickPlaybackPosition() {
        val state = latestMusicState
        if (state != null) {
            val elapsedWhilePlaying = if (state.playing) {
                ((System.currentTimeMillis() - state.positionUpdateTime) * state.playbackSpeed).toLong()
            } else {
                0L
            }

            val maxPosition = if (state.durationMs > 0) state.durationMs else Long.MAX_VALUE
            val interpolatedPosition = (state.positionMs + elapsedWhilePlaying).coerceIn(0L, maxPosition)

            playbackPosition.value = PlaybackPosition(interpolatedPosition, state.durationMs, state.seekable)
        }

        if (state?.playing == true && continuousTickingEnabled) {
            handler.postDelayed(positionTickRunnable, POSITION_TICK_INTERVAL_MS)
        }
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
        }
    }

    private fun applyOptimisticPlayingState(nowPlaying: Boolean) {
        val state = latestMusicState ?: return
        if (state.playing == nowPlaying) {
            return
        }

        // Re-anchor the position so the progress display neither keeps advancing after an
        // optimistic pause nor extrapolates from a stale base after an optimistic resume -
        // same trick seekTo() uses.
        val elapsedWhilePlaying = if (state.playing) {
            ((System.currentTimeMillis() - state.positionUpdateTime) * state.playbackSpeed).toLong()
        } else {
            0L
        }
        val maxPosition = if (state.durationMs > 0) state.durationMs else Long.MAX_VALUE
        val position = (state.positionMs + elapsedWhilePlaying).coerceIn(0L, maxPosition)

        val optimisticState = state.toBuilder()
                .setPlaying(nowPlaying)
                .setPositionMs(position)
                .setPositionUpdateTime(System.currentTimeMillis())
                .build()

        // Route through the regular listener so every side effect (config swap, position
        // ticker, close timeout) behaves exactly as it will when the phone confirms.
        musicStateListener.onChanged(Resource.success(optimisticState))
    }

    /** Skips to the next track directly, independent of the quadrant/button config - used by
     *  the expressive face's transport buttons, over the same Data Layer path
     *  WatchMediaSession's transport controls use. */
    fun skipNext() {
        viewModelScope.launchWithErrorHandling(application, musicState) {
            phoneConnection.sendSkipNext()
        }
    }

    /** Skips to the previous track directly - see [skipNext]. */
    fun skipPrevious() {
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

        musicState.addSource(phoneConnection.musicState, musicStateListener)
        musicState.addSource(phoneConnection.customList) { customList.value = it }
        swapConfig(stoppedConfig)


        volume.value = 0.5f
    }

    private val closeRunnable = Runnable {
        closeApp.call()
    }

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacks(positionTickRunnable)
        // Detach the observeForever hooks these providers hold on PhoneConnection's (@Singleton)
        // LiveData, otherwise each recreated MusicViewModel leaks its three config providers.
        playbackConfig.destroy()
        stoppedConfig.destroy()
        actionsMenuConfig.destroy()
    }
}
