package com.svartifoss.snfell.watch.view.shortcut

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.svartifoss.snfell.proto.ShortcutTracks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.UUID

/** One song of a shortcut's playlist as its screen lists it; [entryId] is what plays it. */
@Immutable
data class ShortcutTrackRow(val entryId: String, val title: String, val artist: String?)

/**
 * What the shortcut screen shows about the songs, under "Open on phone".
 *
 * Only YouTube playlists and albums have songs to show (the phone lists them through the YouTube
 * Data API - see the phone's `ShortcutTracksResponder`); for anything else the screen is the one
 * it always was.
 */
sealed interface ShortcutTracksUi {
    /** Nothing: not a playlist that can be listed, or the phone said it cannot list it. */
    object Hidden : ShortcutTracksUi

    /** "See songs": the list needs an online lookup the user agrees to by tapping. */
    object Offer : ShortcutTracksUi

    object Loading : ShortcutTracksUi

    /** The lookup failed, or no answer came: a row that tries again. */
    object Failed : ShortcutTracksUi

    @Immutable
    data class Listed(val rows: List<ShortcutTrackRow>, val total: Int) : ShortcutTracksUi
}

/** The decisions behind the songs section, free of Compose; pinned by `ShortcutTracksPolicyTest`. */
internal object ShortcutTracksPolicy {

    /**
     * How long the screen waits for the phone before offering to try again. Longer than a lookup
     * of four pages takes on a slow connection, shorter than anyone keeps looking at a spinner.
     */
    const val ANSWER_TIMEOUT_MS = 20_000L

    /**
     * Songs seen this recently are shown again without asking: reopening a shortcut is common,
     * and the phone's own copy is a day fresh anyway.
     */
    const val REMEMBERED_MS = 10 * 60_000L

    /** What the section shows before any answer: a remembered list, else Loading - or nothing. */
    fun initialState(link: String, remembered: ShortcutTracksUi.Listed?): ShortcutTracksUi = when {
        !ShortcutDestination.mayListSongs(link) -> ShortcutTracksUi.Hidden
        remembered != null -> remembered
        else -> ShortcutTracksUi.Loading
    }

    /**
     * What [answer] makes of the section for [link], or null when it is not this section's answer:
     * another shortcut's, or one to an older request (the last answer is replayed to a screen that
     * opens later, and must not overwrite what the newest request brings).
     */
    fun stateFor(answer: ShortcutTracks, link: String, requestId: String?): ShortcutTracksUi? {
        if (answer.link != link || requestId == null || answer.requestId != requestId) return null
        return when (answer.status) {
            ShortcutTracks.Status.LISTED -> if (answer.tracksCount == 0) {
                ShortcutTracksUi.Hidden
            } else {
                ShortcutTracksUi.Listed(
                        rows = answer.tracksList.map { track ->
                            ShortcutTrackRow(
                                    entryId = track.entryId,
                                    title = track.title,
                                    artist = track.artist.takeIf { track.hasArtist() && it.isNotBlank() })
                        },
                        total = maxOf(answer.totalCount, answer.tracksCount))
            }
            ShortcutTracks.Status.NEEDS_CONSENT -> ShortcutTracksUi.Offer
            ShortcutTracks.Status.FAILED -> ShortcutTracksUi.Failed
            ShortcutTracks.Status.NOT_LISTABLE, null -> ShortcutTracksUi.Hidden
        }
    }

    /**
     * A refresh that cannot answer leaves the list on screen where it is: a list shown from memory
     * is not taken away because the phone was unreachable this time. Every other answer replaces
     * what was there - including the phone saying the playlist can no longer be listed.
     */
    fun merge(current: ShortcutTracksUi, next: ShortcutTracksUi): ShortcutTracksUi =
            if (current is ShortcutTracksUi.Listed &&
                    (next == ShortcutTracksUi.Failed || next == ShortcutTracksUi.Offer ||
                            next == ShortcutTracksUi.Loading)) current
            else next
}

/**
 * The lists shown lately, so reopening a shortcut shows its songs at once. Process-owned and read
 * on the main thread only; a handful of entries, each a list of short strings.
 */
internal object ShortcutTrackMemory {
    private const val LINKS = 6
    private val lists = LinkedHashMap<String, Pair<Long, ShortcutTracksUi.Listed>>()

    fun recall(link: String): ShortcutTracksUi.Listed? = lists[link]?.second

    fun isRecent(link: String, nowMs: Long): Boolean =
            lists[link]?.let { nowMs - it.first in 0 until ShortcutTracksPolicy.REMEMBERED_MS } == true

    fun keep(link: String, listed: ShortcutTracksUi.Listed, nowMs: Long) {
        lists.remove(link)
        lists[link] = nowMs to listed
        while (lists.size > LINKS) lists.remove(lists.keys.first())
    }
}

/** What a host gives the shortcut screen to ask the phone about a shortcut's songs. */
class ShortcutTracksSource(
        val answers: LiveData<ShortcutTracks>,
        val ask: suspend (link: String, explicit: Boolean, requestId: String) -> Unit
)

/** The songs section of [link]'s screen, and what its "See songs" / "try again" row does. */
class ShortcutTracksHolder(val state: ShortcutTracksUi, val show: () -> Unit)

/**
 * Asks the phone for [link]'s songs when the screen opens - unless they were seen moments ago -
 * and again each time [ShortcutTracksHolder.show] is called, which also counts as the user's
 * consent to a lookup. A null [source] (a host with no phone connection) shows nothing.
 */
@Composable
fun rememberShortcutTracks(source: ShortcutTracksSource?, link: String): ShortcutTracksHolder {
    var state by remember(link) {
        mutableStateOf(if (source == null) ShortcutTracksUi.Hidden
        else ShortcutTracksPolicy.initialState(link, ShortcutTrackMemory.recall(link)))
    }
    var requestId by remember(link) { mutableStateOf<String?>(null) }
    var attempt by remember(link) { mutableIntStateOf(0) }
    var explicit by remember(link) { mutableStateOf(false) }

    LaunchedEffect(link, attempt) {
        if (source == null || !ShortcutDestination.mayListSongs(link)) return@LaunchedEffect
        if (attempt == 0 && ShortcutTrackMemory.isRecent(link, SystemClock.elapsedRealtime())) {
            return@LaunchedEffect
        }
        val id = UUID.randomUUID().toString()
        requestId = id
        if (state !is ShortcutTracksUi.Listed) state = ShortcutTracksUi.Loading
        try {
            source.ask(link, explicit, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No phone in reach: the same "try again" a missing answer ends in, at once.
            Timber.w(e, "Could not ask the phone for a shortcut's songs")
            if (requestId == id) state = ShortcutTracksPolicy.merge(state, ShortcutTracksUi.Failed)
            return@LaunchedEffect
        }
        delay(ShortcutTracksPolicy.ANSWER_TIMEOUT_MS)
        if (requestId == id && state == ShortcutTracksUi.Loading) state = ShortcutTracksUi.Failed
    }

    val answers = source?.answers ?: remember { MutableLiveData<ShortcutTracks>() }
    val answer by answers.observeAsState()
    LaunchedEffect(answer, requestId) {
        val next = answer?.let { ShortcutTracksPolicy.stateFor(it, link, requestId) }
                ?: return@LaunchedEffect
        state = ShortcutTracksPolicy.merge(state, next)
        if (next is ShortcutTracksUi.Listed) {
            ShortcutTrackMemory.keep(link, next, SystemClock.elapsedRealtime())
        }
    }

    return ShortcutTracksHolder(state) {
        explicit = true
        attempt++
    }
}
