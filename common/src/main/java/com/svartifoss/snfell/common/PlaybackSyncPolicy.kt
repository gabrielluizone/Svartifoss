package com.svartifoss.snfell.common

import kotlin.math.abs

/**
 * The rules governing how the watch corrects its predicted playback position against the phone.
 *
 * [PlaybackPositionEstimate] answers "where is playback, given a sample". This answers the question
 * that comes after it: **that sample was taken somewhere else, some time ago, and it is the only
 * one there will be for a while - so how wrong is it, and what should be done about it?**
 *
 * Three separate problems, each with its own rule below.
 *
 * **1. The sample arrives late.** `positionAgeMs` measures staleness at the sender, so the one leg
 * it cannot describe is the trip between the two devices - and that leg is not small on Bluetooth,
 * nor is it constant. The fix is to measure it: the watch stamps a request with its own monotonic
 * clock, the phone echoes that stamp back untouched, and the watch subtracts on return. That yields
 * a round trip; half of it is the inbound leg, on the usual assumption that the two directions cost
 * about the same. See [oneWayDelayMs] - and [isUsableRoundTrip], which throws the sample away when
 * the round trip is long enough that the symmetry assumption stops being credible.
 *
 * **2. Correcting is itself visible.** A lyrics screen that snaps its highlighted line every few
 * seconds is worse than one that runs a fraction of a second behind: the first looks broken, the
 * second looks like a lyric. So a correction is not applied merely because there is one. Below
 * [IGNORE_THRESHOLD_MS] nothing happens at all; between there and [STEP_THRESHOLD_MS] only a
 * fraction is applied, so successive checks converge without any single one being noticeable; past
 * [STEP_THRESHOLD_MS] the estimate is wrong in a way the user can already see, and easing towards
 * the truth would only prolong it, so it snaps. See [correctionMs].
 *
 * **3. Checking costs battery.** A fixed interval is either wasteful when the estimate is tracking
 * well or too slow when it is not. [nextIntervalMs] doubles the wait each time a check finds
 * nothing to fix and drops back to the floor the moment one does, so a steady track settles into
 * roughly one message a minute while a player that drifts is watched closely.
 *
 * Pure and free of `android.*`, so all of it is pinned by a plain JVM test.
 */
object PlaybackSyncPolicy {

    /**
     * Below this, an error is not worth touching.
     *
     * Roughly the point at which a shifted lyric line stops being perceptible, and comfortably
     * above the noise floor of the measurement itself - the round-trip halving is an estimate, not
     * a measurement, so small corrections would otherwise chase their own error indefinitely.
     */
    const val IGNORE_THRESHOLD_MS = 150L

    /**
     * At or beyond this, correct in full.
     *
     * A third of a second is already a visibly wrong lyric line, and easing into the fix from here
     * would leave it wrong for several more checks. Note this is deliberately far below the
     * phone's own `SEEK_DETECTION_THRESHOLD_MS` (1.5 s), which decides whether a *retransmission*
     * is worth the Bluetooth - a different question with a much higher bar.
     */
    const val STEP_THRESHOLD_MS = 350L

    /**
     * How much of a middling error to absorb per check.
     *
     * Chosen so two or three checks close the gap. Higher would be a visible step; much lower
     * would take longer to converge than the interval backoff allows before the error stops being
     * measured often enough to matter.
     */
    const val PARTIAL_CORRECTION_FACTOR = 0.5f

    /**
     * Longest round trip still treated as a usable sample.
     *
     * Past this the halving assumption is the problem: a reply delayed by a congested Bluetooth
     * link, a dozing phone or a `MusicService` that had to be started to answer did not spend its
     * time symmetrically, so half of it is not the inbound leg and "correcting" by it would inject
     * an error rather than remove one. Discarding costs one wasted message; the next check is
     * moments away.
     */
    const val MAX_USABLE_ROUND_TRIP_MS = 2_000L

    /** Floor for [nextIntervalMs] - the cadence right after a correction, a track change or any of
     *  the events that invalidate the estimate outright. */
    const val MIN_INTERVAL_MS = 8_000L

    /** Ceiling for [nextIntervalMs] - where a well-behaved track settles. About one message a
     *  minute, which is far below the traffic the app already generates for playback state. */
    const val MAX_INTERVAL_MS = 64_000L

    /**
     * How long to wait before verifying a change the watch just asked the phone to make.
     *
     * Play, pause, skip and seek are all requests: the phone has to receive one, hand it to the
     * player, and the player has to act. Checking immediately would sample the state *before* the
     * command took effect and then "correct" the estimate to it - the one way this mechanism could
     * make the very events it exists to handle worse.
     */
    const val COMMAND_SETTLE_MS = 900L

    /** Whether a reply that took [roundTripMs] to come back can be trusted to describe its own
     *  delay. See [MAX_USABLE_ROUND_TRIP_MS]. */
    fun isUsableRoundTrip(roundTripMs: Long): Boolean =
            roundTripMs >= 0L && roundTripMs <= MAX_USABLE_ROUND_TRIP_MS

    /**
     * The inbound leg of [roundTripMs] - what has to be added to the phone's own staleness figure
     * to get the sample's true age on arrival.
     *
     * Never negative: a monotonic clock cannot run backwards, but a caller passing a token from
     * before a process restart can still produce nonsense, and adding a negative age would run the
     * lyric ahead of the song.
     */
    fun oneWayDelayMs(roundTripMs: Long): Long = (roundTripMs / 2).coerceAtLeast(0L)

    /**
     * How much of [driftMs] to apply, where drift is *estimated minus confirmed* - positive when
     * the watch is running ahead of the phone.
     *
     * Returns zero for anything inside the tolerance, which is the whole point: most checks should
     * find nothing and change nothing.
     */
    fun correctionMs(driftMs: Long): Long = when {
        abs(driftMs) < IGNORE_THRESHOLD_MS -> 0L
        abs(driftMs) >= STEP_THRESHOLD_MS -> driftMs
        else -> (driftMs * PARTIAL_CORRECTION_FACTOR).toLong()
    }

    /** Whether [driftMs] was large enough to have been acted on - i.e. whether this check found
     *  something. Kept beside [correctionMs] so the two can never disagree about what "within
     *  tolerance" means. */
    fun needsCorrection(driftMs: Long): Boolean = correctionMs(driftMs) != 0L

    /**
     * How long to wait before the next check, given how long the last wait was and whether that
     * check had to correct anything.
     *
     * Doubling rather than a fixed schedule because the two failure modes pull in opposite
     * directions: a player whose position drifts needs watching, and one that does not needs
     * leaving alone. Starting over at the floor after every correction means an unsettled track is
     * checked often for as long as it stays unsettled, and not one message longer.
     */
    fun nextIntervalMs(currentIntervalMs: Long, corrected: Boolean): Long = if (corrected) {
        MIN_INTERVAL_MS
    } else {
        (currentIntervalMs * 2).coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    }
}
