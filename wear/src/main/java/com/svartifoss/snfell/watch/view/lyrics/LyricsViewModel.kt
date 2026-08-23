package com.svartifoss.snfell.watch.view.lyrics

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.AdaptiveTextContrast
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.LyricLine
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.SwatchInfo
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.selectPrimaryAccent
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.communication.PhoneConnection
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.matejdro.wearutils.lifecycle.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the lyrics screen is showing. */
sealed interface LyricsUiState {
    /** The request is out and no answer has come back yet. */
    data object Loading : LyricsUiState

    /** Timed lines; the screen follows playback. */
    data class Synced(val lines: List<LyricLine>) : LyricsUiState

    /** Lyrics with no timing anywhere - shown as a plain scrollable block. */
    data class Plain(val text: String) : LyricsUiState

    /** The lookup finished and this track has no lyrics. */
    data object None : LyricsUiState

    /**
     * The lookup could not be completed. Kept apart from [None] all the way to the screen: telling
     * someone their song has no lyrics when the phone was simply offline is a lie they have no way
     * to see through.
     */
    data object Failed : LyricsUiState

    /** Online lookup is switched off in the phone's settings. */
    data object Disabled : LyricsUiState

    /** Nothing is playing, so there is no track to look anything up for. */
    data object NoTrack : LyricsUiState
}

/** The track lyrics are being shown for. */
data class LyricsTrack(val title: String?, val artist: String?, val durationMs: Long)

@HiltViewModel
class LyricsViewModel @Inject constructor(
        @ApplicationContext private val context: Context,
        private val phoneConnection: PhoneConnection,
) : ViewModel() {

    /** The shared fetch/retry/discard machinery - see [LyricsFeed]. Enabled for as long as this
     *  screen exists, which is exactly when its lyrics are wanted. */
    private val feed = LyricsFeed(phoneConnection, viewModelScope).apply { setEnabled(true) }

    val state: LiveData<LyricsUiState> = feed.state

    private val _track = MutableLiveData<LyricsTrack?>()
    val track: LiveData<LyricsTrack?> = _track

    private val _positionMs = MutableLiveData(0L)
    val positionMs: LiveData<Long> = _positionMs

    private val _accentColor = MutableLiveData(WatchTheme.ACCENT_DEFAULT)
    val accentColor: LiveData<Int> = _accentColor

    private val albumAccentSource: AlbumAccentSource = run {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        AlbumAccentSource.fromPreference(FaceScopedPreferences.getString(
                prefs,
                MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE,
                ThemeAppearance.resolve(prefs)))
    }

    private var latestState: MusicState? = null
    private var ambient = false

    private var tickerJob: Job? = null

    private val stateObserver = Observer<Resource<MusicState>?> { resource ->
        val state = resource?.data
        latestState = state
        // No anchoring here any more. This screen used to keep its own copy of the sample and its
        // own monotonic anchor, which is exactly why it appeared to synchronise only when it was
        // opened: a fresh observer replayed the last state, re-anchored on it, and then nothing
        // touched that anchor for the rest of the track. PhoneConnection.playbackClock holds one
        // anchor for the whole app and keeps it corrected whether this screen exists or not.
        _track.value = state?.let { LyricsTrack(it.title, it.artist, it.durationMs) }
    }

    private val artObserver = Observer<Bitmap?> { bitmap ->
        if (bitmap != null) deriveAccent(bitmap)
    }

    init {
        phoneConnection.musicState.observeForever(stateObserver)
        phoneConnection.albumArt.observeForever(artObserver)

        // Opening the screen is one of the events worth a check - not because the estimate is
        // untrustworthy by then (it is being corrected continuously), but because this is the
        // surface where an error is most visible, and the first line drawn should be the right one.
        phoneConnection.requestPlaybackResync()

        restartTicker()
    }

    /**
     * (Re)starts the position ticker at the cadence the current ambient state calls for.
     *
     * Restarted rather than left to notice on its own iteration, and that distinction is the whole
     * point: `delay` fixes its duration when the wait *begins*, so a loop that entered the ambient
     * cadence sits inside a 30-second wait no matter what happens next. Raising the wrist called
     * `tick()` once and then left the lyric frozen for whatever remained of that wait - up to half
     * a minute of the words simply not moving, and only sometimes, depending on where in the cycle
     * the wrist came up. Cancelling and relaunching makes the new cadence take effect now.
     */
    private fun restartTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(if (ambient) AMBIENT_TICK_MS else TICK_MS)
            }
        }
    }

    /**
     * Ambient state, driven by the Activity.
     *
     * This exists for power, not presentation. The awake ticker runs four times a second so a line
     * lands on the beat; keeping that up in ambient would wake the CPU 240 times a minute to
     * recompute a highlight for a display the system only refreshes **once** in that minute. The
     * screen is designed to be left open for a whole album, so that is not a rounding error.
     */
    fun setAmbient(value: Boolean) {
        if (ambient == value) return
        ambient = value
        // Relaunches at the new cadence and ticks immediately, so the wrist coming up catches the
        // lyric up at once instead of waiting out the ambient delay already in flight.
        restartTicker()
    }

    /** Recomputes the position for an ambient redraw, so the line is right exactly when the pixels
     *  change rather than up to a tick late. Called from the Activity's onUpdateAmbient. */
    fun refreshPosition() = tick()

    /**
     * Where the song has got to, read from the shared clock.
     *
     * The phone does not resend the position while a track plays steadily - it suppresses
     * position-only updates - so for most of a song this is a *prediction*, and this screen is
     * where being a second or two out stops being invisible and starts being obviously wrong.
     * `PhoneConnection.playbackClock` is what keeps that prediction honest: it verifies itself
     * against the phone on a backing-off schedule and corrects by however much it has drifted, so
     * this screen no longer owns any of that reasoning - it just reads the answer, four times a
     * second, so a line lands on the beat.
     */
    private fun tick() {
        if (latestState == null) return
        _positionMs.value = phoneConnection.playbackClock.positionNowMs()
    }

    private fun deriveAccent(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val palette = Palette.from(bitmap).generate()
            val swatches = palette.swatches.map { SwatchInfo(it.rgb, it.population) }
            // The shared selector, honouring the user's album-accent choice, so this screen picks
            // the same colour the player does rather than a fourth opinion of its own.
            val primary = selectPrimaryAccent(
                    palette.vibrantSwatch?.let { SwatchInfo(it.rgb, it.population) },
                    swatches,
                    albumAccentSource)
            // null means the cover carried no colour information; keep whatever is showing.
            if (primary != null) _accentColor.postValue(legibleOnBlack(primary))
        }
    }

    /**
     * Jump playback to where [line] begins.
     *
     * The whole reason to read along is that you can hear where you are; this makes that
     * navigable - tap the line you want to hear again and the song goes there.
     *
     * **Seeks to the line's own `timeMs`, with no lead-in.** A little earlier would arguably be
     * kinder to a listener who wants to catch the run-up, but it would also put the position
     * *before* the line they tapped, and [LyricsParser.indexAt] would then highlight the previous
     * one - so the screen would answer a tap by lighting up a different line. Landing exactly on
     * the timestamp makes the tapped line the current line by construction, which is the only
     * behaviour that cannot look broken. The parser has already folded the file's own `[offset]`
     * into that number, so this is the same instant the highlight uses.
     *
     * **Not gated on the session reporting itself seekable.** That bit is routinely under-reported
     * (see `MediaSessionCapabilities`), and the cost of the two mistakes is not symmetric: issuing
     * a seek a player ignores is a no-op that the clock's own next check quietly undoes, while
     * withholding it because of a wrong bit is the whole feature silently missing. Same rule the
     * queue tap follows.
     */
    fun seekToLine(line: LyricLine) {
        val state = latestState ?: return
        val duration = state.durationMs
        // LRC files routinely carry trailing lines past the end of the audio - a credits block, or
        // timings taken from a longer master. Seeking past the end is undefined per player: some
        // clamp, some skip the track, and skipping is the one outcome a reader tapping a lyric
        // could not have meant.
        val target = if (duration > 0L) {
            line.timeMs.coerceIn(0L, duration)
        } else {
            line.timeMs.coerceAtLeast(0L)
        }

        // Anchored before the message goes out, exactly as the player's own seek does: the phone
        // takes a Bluetooth round trip to confirm, and a lyrics screen that waited for it would sit
        // on the old line for long enough to look like the tap missed.
        phoneConnection.playbackClock.anchorLocally(target, state.playing)
        _positionMs.value = target
        phoneConnection.sendSeek(target)
    }

    /** Seeds the accent from the player so the first frame matches instead of flashing the default. */
    fun seedAccent(color: Int) {
        if (color != 0) _accentColor.value = legibleOnBlack(color)
    }

    /**
     * The accent, lifted until it is readable on this screen's black backdrop.
     *
     * Covers with near-black artwork - a dark sleeve, a monochrome photo, anything shot on black -
     * yield a near-black accent, and this screen paints both the highlighted lyric line and the
     * floor wash in it. Against pure black that is not "subtle", it is invisible: the current line
     * vanishes and the only thing telling you where the song is disappears with it.
     *
     * [AdaptiveTextContrast.adapt] raises the lightness and nothing else, so the line still reads
     * as the album's colour rather than being replaced with white - which is the whole reason to
     * derive a colour from the cover in the first place.
     *
     * Applied unconditionally here, unlike the artist line's version of this, which is a
     * preference. That one guards a colour the user may have tuned against artwork *they* chose;
     * here the background is pure black by construction, so there is no case in which leaving an
     * unreadable colour alone is the right answer.
     */
    private fun legibleOnBlack(color: Int): Int =
            AdaptiveTextContrast.adapt(color, BLACK_LUMINANCE)

    private fun trackKey(title: String?, artist: String?, durationMs: Long): String =
            "${title.orEmpty()}|${artist.orEmpty()}|${durationMs / 1000}"

    override fun onCleared() {
        super.onCleared()
        feed.release()
        phoneConnection.musicState.removeObserver(stateObserver)
        phoneConnection.albumArt.removeObserver(artObserver)
    }

    private companion object {
        /** Fast enough that a line lands on the beat rather than after it, cheap enough to run
         *  while the screen is held on. */
        const val TICK_MS = 250L

        /** Ambient redraws about once a minute, so anything faster than this is invisible work. */
        const val AMBIENT_TICK_MS = 30_000L

        /** Long enough for a cold phone-side lookup (two HTTP round trips plus Bluetooth both
         *  ways) to be under way, short enough that a genuinely lost request is not left sitting. */
        /** This screen's backdrop is pure black, so the luminance the accent is measured against
         *  is a constant rather than something sampled from artwork. */
        const val BLACK_LUMINANCE = 0f

    }
}
