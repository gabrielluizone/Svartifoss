package com.svartifoss.snfell.watch.view.queue

import kotlin.math.abs

/**
 * Where the queue should sit when it opens, and how it should get there.
 *
 * The queue used to open wherever the list happened to start - the top - and never move. On a
 * two-track queue that is invisible; on a long playlist it means the song you are listening to is
 * somewhere below, and finding it is a scrolling job the screen could have done for you.
 *
 * Getting there is the part with a decision in it. Animating is right when the song is a row or two
 * away: the movement shows *which* way the list went, which a jump cannot. It is wrong when the song
 * is eighty rows away, because then it is several seconds of watching tracks fly past to arrive
 * somewhere the user could have been shown immediately. So distance picks the method - see
 * [resolve].
 *
 * Pure and free of `android.*` so both halves are pinned by a plain JVM test, the same shape as
 * `PredictedTrackAdvance` and `QueuePaging`.
 */
object QueueScrollPolicy {

    /**
     * How far the playing row may be from what is on screen and still be animated to, in rows.
     *
     * The queue shows roughly three rows at a time, so this is about two screens' worth - far
     * enough that the common cases (the next track, a track you just scrolled past) glide, close
     * enough that the animation is over in a moment. Beyond it, animating stops communicating
     * direction and starts costing time.
     */
    const val ANIMATE_WITHIN_ROWS = 6

    enum class Move {
        /** Already there, or there is nothing to go to. */
        NONE,

        /** Close enough that the movement itself is worth seeing. */
        ANIMATE,

        /** Far enough that the movement is only a delay - reposition outright. */
        JUMP
    }

    /**
     * How to move from [centerIndex] to [targetIndex]. Both are **list** indices, counting whatever
     * header rows sit above the queue itself, so the caller offsets once and this never has to know
     * about them.
     *
     * A negative [targetIndex] means the playing row was not found at all (see [activeRowIndex]),
     * which is reported as [Move.NONE] rather than as position zero: scrolling to the top because
     * the song could not be located would be a confident wrong answer.
     *
     * Being already centred is also [Move.NONE], and that one matters more than it looks: the phone
     * republishes the queue on every track change and the watch re-reads it, so a policy that
     * scrolled whenever it was asked would re-run an animation over a list that is already in the
     * right place.
     */
    fun resolve(
            centerIndex: Int,
            targetIndex: Int,
            animateWithin: Int = ANIMATE_WITHIN_ROWS,
    ): Move = when {
        targetIndex < 0 -> Move.NONE
        targetIndex == centerIndex -> Move.NONE
        abs(targetIndex - centerIndex) <= animateWithin -> Move.ANIMATE
        else -> Move.JUMP
    }

    /**
     * Which row is the song currently playing, or -1 when it cannot be found.
     *
     * [playing] is the per-row flag the list already draws its highlight from, and it is consulted
     * **first** on purpose. The phone-side `PredictedTrackAdvance.activeIndex` resolves the same
     * question in the opposite order - title first - because there a controller's metadata runs
     * ahead of its own queue position. Here the flag *is* the highlight, so preferring anything
     * else would let the screen scroll to one row and light up another, which reads as a bug in
     * both of them.
     *
     * The title is the fallback rather than the primary, for the case that has no flag to prefer:
     * plenty of players never publish `activeQueueItemId` at all, and without this the queue would
     * simply not find the song on those. Matched on the trimmed, case-insensitive title, which is
     * the only field the queue and the now-playing header are guaranteed to express the same way.
     *
     * Duplicate titles resolve to the first occurrence, which is why this is the fallback and not
     * the rule: a queue holding the same song twice has no way to tell them apart by name.
     */
    fun activeRowIndex(
            playing: List<Boolean>,
            titles: List<String>,
            nowPlayingTitle: String?,
    ): Int {
        val flagged = playing.indexOfFirst { it }
        if (flagged >= 0) {
            return flagged
        }
        val wanted = nowPlayingTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return -1
        return titles.indexOfFirst { it.trim().equals(wanted, ignoreCase = true) }
    }
}
