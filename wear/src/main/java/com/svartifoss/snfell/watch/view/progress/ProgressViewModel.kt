package com.svartifoss.snfell.watch.view.progress

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svartifoss.snfell.watch.communication.PhoneConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ProgressUiState(
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val playing: Boolean = false,
        val speed: Float = 1f
)

private const val PROGRESS_SPEED_STEP = 0.25f
private const val PROGRESS_MIN_SPEED = 0.5f
private const val PROGRESS_MAX_SPEED = 2.0f

/** Computes the watch's immediate visual destination for a signed relative seek. */
internal fun relativeSeekTarget(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
    val unbounded = currentMs + deltaMs
    return if (durationMs > 0L) {
        unbounded.coerceIn(0L, durationMs)
    } else {
        unbounded.coerceAtLeast(0L)
    }
}

/**
 * Drives [ProgressActivity]. Reads position, duration, playback state and speed from
 * [PhoneConnection], the same source `MusicViewModel` reads for the primary player, and issues
 * seek/speed commands over direct message paths rather than the generic `ButtonAction` round trip.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
        private val phoneConnection: PhoneConnection
) : ViewModel() {

    /** See `VolumeViewModel.albumArt`. */
    val albumArt get() = phoneConnection.albumArt

    val state = MediatorLiveData<ProgressUiState>().apply {
        addSource(phoneConnection.musicState) { resource ->
            val data = resource?.data ?: return@addSource
            value = ProgressUiState(
                    positionMs = data.positionMs,
                    durationMs = data.durationMs,
                    playing = data.playing,
                    speed = data.playbackSpeed)
        }
    }

    /** The live, extrapolated position - see `PlaybackClock`, the single shared anchor every
     *  other position readout in the app extrapolates from rather than each keeping its own. */
    fun livePositionMs(): Long = phoneConnection.playbackClock.positionNowMs()

    /**
     * Moves the shared watch clock before starting the Bluetooth round trip. The dedicated screen,
     * main player, lyrics and system media surface therefore all see the same new position on the
     * tap's own frame; the delayed resync remains responsible for correcting a player that clamps
     * or ignores the command.
     */
    fun skipBy(deltaMs: Long) {
        val currentState = state.value ?: return
        val duration = currentState.durationMs
        val target = relativeSeekTarget(
                phoneConnection.playbackClock.positionNowMs(), deltaMs, duration)
        anchorPositionNow(target, currentState)
        launchSilently { phoneConnection.sendSeekRelative(deltaMs) }
    }

    /** Mirrors the primary player's immediate play/pause feedback on the shared playback clock. */
    fun togglePlayPause() {
        val currentState = state.value ?: return
        val positionNow = phoneConnection.playbackClock.positionNowMs()
        val toggled = currentState.copy(positionMs = positionNow, playing = !currentState.playing)
        anchorPositionNow(positionNow, toggled)
        launchSilently { phoneConnection.togglePlayPause() }
    }

    /** Keeps playback speed available without competing with the six primary seek buttons. */
    fun cycleSpeed() {
        val current = state.value?.speed ?: 1f
        val next = (current + PROGRESS_SPEED_STEP).let {
            if (it > PROGRESS_MAX_SPEED + 0.001f) PROGRESS_MIN_SPEED else it
        }.coerceIn(PROGRESS_MIN_SPEED, PROGRESS_MAX_SPEED)
        launchSilently { phoneConnection.sendPlaybackSpeed(next) }
    }

    /** Absolute seek from a ring drag - non-suspend and self-dispatching, same as `sendVolume`. */
    fun seekToFraction(fraction: Float) {
        val currentState = state.value ?: return
        val duration = currentState.durationMs
        if (duration <= 0L) return
        val target = (fraction.coerceIn(0f, 1f) * duration).toLong()
        anchorPositionNow(target, currentState)
        phoneConnection.sendSeek(target)
    }

    private fun anchorPositionNow(targetMs: Long, currentState: ProgressUiState) {
        phoneConnection.playbackClock.anchorLocally(targetMs, currentState.playing)
        state.value = currentState.copy(positionMs = targetMs)
        phoneConnection.requestPlaybackResync()
    }

    private fun launchSilently(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Progress screen command failed")
            }
        }
    }
}
