package com.svartifoss.snfell.watch.view

/**
 * Decides when the watch may show the *next* track before the phone has said so, and which track
 * that is.
 *
 * Everything the now-playing screen shows about the current track - title, artist, cover, duration,
 * position - changes only when a `MusicState` arrives from the phone. That is the right ownership
 * (the phone reads the real `MediaSession`; the watch has no access to it at all), but it makes the
 * one moment users notice most, the track boundary, the slowest: the phone only learns about the
 * new track when the player publishes `onMetadataChanged`, and at a track boundary that callback is
 * routinely late - several players drop out of the playing state for a moment in between, and
 * `ActiveMediaSessionProvider` observes metadata only while a session is playing. The cover then
 * travels as a Data Layer asset, the slowest leg of all.
 *
 * When the watch already holds the queue, it knows something the delay cannot take away: a track
 * that has just run its full duration is followed by the entry after it. Acting on that is worth a
 * lot of perceived latency and costs nothing that is not immediately correctable.
 *
 * **This is a prediction, never a second source of truth.** The phone stays the authority: the
 * moment a real state arrives it is applied verbatim, whether it agrees with the guess or not (see
 * `MusicViewModel.resolvePrediction`). The rules below are therefore written to be *conservative* -
 * every case where the queue's order is not the play order refuses to predict rather than guessing
 * - because a refused prediction costs only the latency that exists today, while a wrong one shows
 * a track that is not playing.
 *
 * Pure and free of `android.*` so the decisions can be pinned by a plain JVM test, following the
 * same convention as [com.svartifoss.snfell.common.PlaybackPositionEstimate] and
 * `resolveServiceHold` - the fallbacks are where the bugs would be.
 */
object PredictedTrackAdvance {

    /**
     * How close to the end of the track counts as "it has run out".
     *
     * The position the watch shows is extrapolated and clamped to the duration, so it settles
     * exactly *at* `durationMs` and this could in principle be zero. It is not, because the
     * extrapolation is sampled by a 500 ms ticker and the last sample before the end can land
     * slightly short - waiting for another whole tick would hand back a chunk of the latency this
     * exists to remove.
     */
    const val END_TOLERANCE_MS = 400L

    /**
     * Below this, a "duration" is not one.
     *
     * Players publish a duration of 0 while metadata is still settling, and some publish small
     * placeholder values. Extrapolating against those reaches the end almost immediately and would
     * fire a prediction on a track that has barely started.
     */
    const val MIN_PREDICTABLE_DURATION_MS = 5_000L

    /** `MusicState.repeatMode` for repeat-one - the same track plays again, so there is no advance. */
    private const val REPEAT_ONE = 2

    /**
     * Whether the current track has run out and the queue order can be trusted to say what follows.
     *
     * The two refusals are deliberate and are the whole reason this is a function rather than a
     * comparison at the call site:
     *
     *  - **repeat-one** replays the same track, so "the entry after this one" is the wrong answer
     *    by construction.
     *  - **shuffle** means the queue the phone published is not necessarily the order playback will
     *    follow. Some players publish the shuffled order and would predict correctly; others
     *    publish the original order and would be wrong on every single track. There is no way to
     *    tell them apart from here, and being consistently wrong is far worse than being merely
     *    late, so this refuses for all of them.
     */
    fun canPredict(
            playing: Boolean,
            positionMs: Long,
            durationMs: Long,
            shuffleEnabled: Boolean,
            repeatMode: Int,
    ): Boolean = playing &&
            !shuffleEnabled &&
            repeatMode != REPEAT_ONE &&
            durationMs >= MIN_PREDICTABLE_DURATION_MS &&
            positionMs >= durationMs - END_TOLERANCE_MS

    /**
     * Which queue row is the track currently playing, or -1 when it cannot be identified.
     *
     * Title first, `activeEntryId` second - the same order, and for the same reason, as the Up Next
     * pill in `MainActivity.updateUpNextPreview`: a controller routinely advances its metadata
     * before it advances `activeQueueItemId`, so on the pass that matters the id still points at the
     * track that just ended while the title is already the new one.
     */
    fun activeIndex(
            entryIds: List<String>,
            titles: List<String>,
            activeEntryId: String?,
            currentTitle: String?,
    ): Int {
        val byTitle = if (currentTitle.isNullOrBlank()) {
            -1
        } else {
            titles.indexOfFirst { it.equals(currentTitle, ignoreCase = true) }
        }
        if (byTitle >= 0) {
            return byTitle
        }
        if (activeEntryId.isNullOrEmpty()) {
            return -1
        }
        return entryIds.indexOf(activeEntryId)
    }

    /**
     * The row that follows the playing one, or -1 when there is nothing to predict.
     *
     * Never wraps around to the start: repeat-all does return to the top, but the queue the watch
     * holds is a *page* (see `QueuePaging`), so the first row is only the start of the playlist when
     * the whole thing happens to fit. Guessing that a queue's last loaded row is followed by its
     * first would be wrong for every playlist longer than one page.
     */
    fun nextIndex(
            entryIds: List<String>,
            titles: List<String>,
            activeEntryId: String?,
            currentTitle: String?,
    ): Int {
        val active = activeIndex(entryIds, titles, activeEntryId, currentTitle)
        if (active < 0 || active >= entryIds.size - 1) {
            return -1
        }
        return active + 1
    }

    /**
     * Whether the phone's answer is the track that was predicted.
     *
     * Compared by title, because that is the only field the queue and the media session are
     * guaranteed to express the same way - a queue row carries no duration, and its `entryId`
     * encodes a queue position that the metadata does not report at all. Blank on either side is
     * never a match: an empty title is the absence of an answer, not an answer that happens to
     * agree.
     */
    fun isSameTrack(predictedTitle: String?, actualTitle: String?): Boolean =
            !predictedTitle.isNullOrBlank() &&
                    !actualTitle.isNullOrBlank() &&
                    predictedTitle.trim().equals(actualTitle.trim(), ignoreCase = true)
}
