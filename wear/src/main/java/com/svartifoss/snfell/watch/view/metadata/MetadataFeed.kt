package com.svartifoss.snfell.watch.view.metadata

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.proto.TrackMetadata
import com.svartifoss.snfell.watch.communication.PhoneConnection
import com.matejdro.wearutils.lifecycle.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps [state] holding the phone's full metadata for whatever is playing.
 *
 * Deliberately the same shape as `LyricsFeed`, because it has the same job and the same hazards:
 * request once per *track* rather than per state update, discard an answer for a song already
 * skipped past, and cost nothing at all while the surface that wants it is not on screen.
 *
 * **Nothing is requested until [setEnabled] is called with true**, which the host does only while
 * the metadata face is the selected one. That is what keeps the file probe on the phone - and the
 * optional online lookup behind it - unpaid for by everyone else.
 */
class MetadataFeed(
        private val phoneConnection: PhoneConnection,
        private val scope: CoroutineScope,
) {

    private val _state = MutableLiveData<TrackMetadata?>(null)
    val state: LiveData<TrackMetadata?> = _state

    private var enabled = false

    /** Identity of the track the outstanding request belongs to; null before the first one. */
    private var requestedKey: String? = null

    private val stateObserver = Observer<Resource<MusicState>?> { resource ->
        onMusicState(resource?.data)
    }

    private val metadataObserver = Observer<TrackMetadata?> { metadata ->
        if (metadata != null) onMetadata(metadata)
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value

        if (value) {
            phoneConnection.musicState.observeForever(stateObserver)
            phoneConnection.trackMetadata.observeForever(metadataObserver)
        } else {
            phoneConnection.musicState.removeObserver(stateObserver)
            phoneConnection.trackMetadata.removeObserver(metadataObserver)
            // Cleared rather than merely unsubscribed, so re-selecting this face asks again instead
            // of waiting for a track change that may never come - the same reasoning LyricsFeed
            // records for its own release.
            requestedKey = null
            _state.value = null
        }
    }

    fun release() = setEnabled(false)

    private fun onMusicState(state: MusicState?) {
        if (state == null || (state.title.isNullOrBlank() && state.artist.isNullOrBlank())) {
            requestedKey = null
            _state.value = null
            return
        }

        // MusicState arrives on every playback change, not only on a track change. Keyed on the
        // track's identity so a pause does not re-run a file probe on the phone for an answer that
        // cannot have changed.
        val key = trackKey(state.title, state.artist)
        if (key != requestedKey) {
            requestedKey = key
            _state.value = null
            scope.launch {
                try {
                    phoneConnection.requestTrackMetadata(state.title, state.artist)
                } catch (e: Exception) {
                    // Phone out of range. The face shows its "nothing to show" state, which is the
                    // honest reading, and the next track change asks again.
                }
            }
        }
    }

    private fun onMetadata(metadata: TrackMetadata) {
        // Discard an answer for a track already skipped past. The phone echoes the request's fields
        // precisely so this is possible - without it a slow lookup landing after two skips would
        // put a confident, wrong table on screen, and nothing about it would look wrong.
        if (trackKey(metadata.title, metadata.artist) != requestedKey) return

        // A second answer for the same track is the enrichment arriving (see
        // TrackMetadata.enriched); it is a superset of the first, so replacing is right. Guarded
        // anyway so a *plain* re-answer can never strip rows the enriched one had added.
        val held = _state.value
        if (held != null && held.enriched && !metadata.enriched) return
        _state.value = metadata
    }

    private fun trackKey(title: String?, artist: String?): String =
            "${title.orEmpty().trim()}|${artist.orEmpty().trim()}"
}
