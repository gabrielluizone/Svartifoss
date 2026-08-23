package com.svartifoss.snfell.watch.view.lyrics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.svartifoss.snfell.common.LyricsParser
import com.svartifoss.snfell.common.LyricsStatus
import com.svartifoss.snfell.proto.LyricsResponse
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.communication.PhoneConnection
import com.matejdro.wearutils.lifecycle.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps [state] holding the lyrics for whatever the phone is currently playing.
 *
 * Extracted from [LyricsViewModel] once the Verse face needed the same thing on the main screen:
 * the request/discard/retry rules here are subtle enough that a second copy would drift, and the
 * two consumers want different things around them - the lyrics screen owns its own position ticker
 * and accent, while the face reads position from the player it lives in.
 *
 * **Nothing is requested until [setEnabled] is called with true.** The fetch happens on the phone
 * and costs a network round trip per track, so a user who has neither opened the lyrics screen nor
 * chosen the Verse face never causes one.
 */
class LyricsFeed(
        private val phoneConnection: PhoneConnection,
        private val scope: CoroutineScope,
) {

    private val _state = MutableLiveData<LyricsUiState>(LyricsUiState.Loading)
    val state: LiveData<LyricsUiState> = _state

    private var enabled = false

    /** Identity of the track the outstanding request belongs to; null before the first one. */
    private var requestedKey: String? = null
    private var latestTrack: MusicState? = null
    private var timeoutJob: Job? = null

    private val stateObserver = Observer<Resource<MusicState>?> { resource ->
        onMusicState(resource?.data)
    }

    private val lyricsObserver = Observer<LyricsResponse?> { response ->
        if (response != null) onLyricsResponse(response)
    }

    /**
     * Starts or stops following the phone's track.
     *
     * Disabling clears the outstanding request rather than only unsubscribing, so re-enabling on
     * the same track fetches again instead of waiting for a track change that may never come.
     */
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value

        if (value) {
            phoneConnection.musicState.observeForever(stateObserver)
            phoneConnection.lyrics.observeForever(lyricsObserver)
        } else {
            phoneConnection.musicState.removeObserver(stateObserver)
            phoneConnection.lyrics.removeObserver(lyricsObserver)
            timeoutJob?.cancel()
            requestedKey = null
            latestTrack = null
            _state.value = LyricsUiState.Loading
        }
    }

    fun release() = setEnabled(false)

    private fun onMusicState(state: MusicState?) {
        if (state == null || (state.title.isNullOrBlank() && state.artist.isNullOrBlank())) {
            latestTrack = null
            requestedKey = null
            _state.value = LyricsUiState.NoTrack
            return
        }

        latestTrack = state

        // MusicState arrives on every position update, not only on a track change. Re-requesting
        // per update would mean a request every couple of seconds against a rate-limited service
        // for an answer that cannot have changed - so key on the track's identity, rounding the
        // duration to seconds because the reported length wobbles by a few milliseconds between
        // updates on some players.
        val key = trackKey(state.title, state.artist, state.durationMs)
        if (key != requestedKey) {
            requestedKey = key
            _state.value = LyricsUiState.Loading
            request(state)
        }
    }

    private fun request(state: MusicState) {
        val requestedFor = requestedKey
        scope.launch {
            try {
                phoneConnection.requestLyrics(state.title, state.artist, state.durationMs)
            } catch (e: Exception) {
                // Phone out of range, Play Services down. Nothing will answer, so say so now
                // instead of leaving the screen waiting until the timeout.
                if (requestedKey == requestedFor) _state.value = LyricsUiState.Failed
            }
        }

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            // One retry, because there is a real case where the first request is simply lost: a
            // message arriving while the phone's MusicService is stopped reaches the manifest
            // listener, which *starts* that service but cannot hand it the payload - the service's
            // own listener is not registered yet. By the time this fires it is.
            delay(RETRY_MS)
            if (requestedKey != requestedFor || _state.value !is LyricsUiState.Loading) {
                return@launch
            }
            try {
                phoneConnection.requestLyrics(state.title, state.artist, state.durationMs)
            } catch (e: Exception) {
                _state.value = LyricsUiState.Failed
                return@launch
            }

            delay(RESPONSE_TIMEOUT_MS - RETRY_MS)
            if (requestedKey == requestedFor && _state.value is LyricsUiState.Loading) {
                _state.value = LyricsUiState.Failed
            }
        }
    }

    private fun onLyricsResponse(response: LyricsResponse) {
        // Discard an answer for a track already skipped past. The phone echoes the request's fields
        // precisely so this comparison is possible - without it, a slow lookup landing after two
        // skips would put the wrong song's words on screen, and nothing about them would look wrong.
        if (trackKey(response.title, response.artist, response.durationMs) != requestedKey) {
            return
        }

        timeoutJob?.cancel()

        _state.value = when (response.status) {
            LyricsStatus.SYNCED -> {
                val lines = LyricsParser.parseSynced(response.lrc.orEmpty())
                // The phone already checks the LRC parses before calling it synced, so this only
                // catches a disagreement between the two builds' parsers.
                if (lines.isNotEmpty()) {
                    LyricsUiState.Synced(lines)
                } else {
                    LyricsUiState.Plain(response.lrc.orEmpty())
                }
            }
            LyricsStatus.PLAIN -> LyricsUiState.Plain(response.plain.orEmpty())
            LyricsStatus.NONE -> LyricsUiState.None
            LyricsStatus.DISABLED -> LyricsUiState.Disabled
            LyricsStatus.FAILED -> LyricsUiState.Failed
            // A status from a newer phone build. "Could not complete" is the honest reading of a
            // result this build cannot interpret, and the one the user can act on.
            else -> LyricsUiState.Failed
        }
    }

    private fun trackKey(title: String?, artist: String?, durationMs: Long): String =
            "${title.orEmpty()}|${artist.orEmpty()}|${durationMs / 1000}"

    private companion object {
        const val RETRY_MS = 7_000L
        const val RESPONSE_TIMEOUT_MS = 25_000L
    }
}
