package com.svartifoss.snfell.watch.view.volume

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svartifoss.snfell.watch.communication.PhoneConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** One step of an on-screen button tap: five percentage points. */
const val VOLUME_STEP = 0.05f

/**
 * Drives [VolumeActivity]. Reads the current volume from [PhoneConnection] (already arrives on
 * every `MusicState`, so nothing is requested up front - see `QueueViewModel`'s doc for why a
 * fresh screen still injects [PhoneConnection] directly rather than sharing `MusicViewModel`).
 */
@HiltViewModel
class VolumeViewModel @Inject constructor(
        private val phoneConnection: PhoneConnection
) : ViewModel() {

    /** The current cover, for the panel backdrop styles that are composed over a blurred copy of
     *  it. Read straight from [PhoneConnection] rather than routed through `MusicViewModel`, which
     *  belongs to `MainActivity` and is not alive while this screen is. */
    val albumArt get() = phoneConnection.albumArt

    val volume = MediatorLiveData<Float>().apply {
        addSource(phoneConnection.musicState) { resource ->
            resource?.data?.volume?.let { value = it }
        }
    }

    /** Sets an absolute volume (0f..1f), optimistically reflected before the phone confirms it -
     *  the same shape `MusicViewModel.updateVolume` uses for the primary player. */
    fun setVolume(newVolume: Float) {
        val clamped = newVolume.coerceIn(0f, 1f)
        volume.value = clamped
        phoneConnection.sendVolume(clamped)
    }

    fun step(delta: Float) {
        setVolume((volume.value ?: 0f) + delta)
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            try {
                phoneConnection.togglePlayPause()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Volume screen play/pause command failed")
            }
        }
    }
}
