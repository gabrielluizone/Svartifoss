package com.svartifoss.snfell.actions.playback

import android.support.v4.media.session.PlaybackStateCompat

/**
 * What the repeat and shuffle buttons do next, and how the current mode is reported to the watch.
 *
 * Shuffle and repeat exist only on the AndroidX media-compat layer, never on the framework
 * `MediaController`, and that layer reads them over an "extra binder" the session hands out on
 * request. The request is asynchronous, so until it lands both `getRepeatMode()` and
 * `getShuffleMode()` answer **-1** - `REPEAT_MODE_INVALID` / `SHUFFLE_MODE_INVALID`. A session
 * that was never built on `MediaSessionCompat` answers -1 forever, because the binder never
 * arrives at all.
 *
 * So -1 is a routine answer, not an exotic one, and every one of these functions has to treat it
 * as *the mode is unknown* rather than as a mode. Both buttons used to fall through to their
 * "everything off" branch on it, which is precisely what made them look broken: the repeat button
 * switched repeat off on every press instead of cycling, and shuffle read -1 as "not NONE", took
 * that to mean shuffle was already on, and switched it off on every press too.
 *
 * Pure and parameterless by design - the fallbacks are the whole content here, and they are what
 * [PlaybackModeCycleTest] pins.
 */

/**
 * The mode a press of the cycling repeat button should ask for.
 *
 * Unknown behaves like off, so a press turns repeat **on**. That direction is the point: a button
 * pressed when nothing is known about the current mode should do the thing its icon suggests, and
 * "off -> off" is indistinguishable from a button that does not work.
 */
internal fun nextRepeatMode(current: Int): Int = when (current) {
    // GROUP is ALL under another name - some players (Retro Music among them) report it instead,
    // and the cycle has to keep moving on to ONE rather than starting over.
    PlaybackStateCompat.REPEAT_MODE_ALL,
    PlaybackStateCompat.REPEAT_MODE_GROUP -> PlaybackStateCompat.REPEAT_MODE_ONE
    PlaybackStateCompat.REPEAT_MODE_ONE -> PlaybackStateCompat.REPEAT_MODE_NONE
    else -> PlaybackStateCompat.REPEAT_MODE_ALL
}

/** The mode a press of the repeat-one toggle should ask for: off while it is on, on otherwise. */
internal fun toggledRepeatOneMode(current: Int): Int =
        if (current == PlaybackStateCompat.REPEAT_MODE_ONE) {
            PlaybackStateCompat.REPEAT_MODE_NONE
        } else {
            PlaybackStateCompat.REPEAT_MODE_ONE
        }

/** The mode a press of the shuffle toggle should ask for. */
internal fun nextShuffleMode(current: Int): Int =
        if (isShuffleOn(current)) {
            PlaybackStateCompat.SHUFFLE_MODE_NONE
        } else {
            PlaybackStateCompat.SHUFFLE_MODE_ALL
        }

/**
 * Whether shuffle counts as on.
 *
 * Only an explicit ALL or GROUP does. Asking `!= SHUFFLE_MODE_NONE` instead reads unknown as on,
 * which showed the watch a permanently lit shuffle button and made the toggle a one-way switch.
 */
internal fun isShuffleOn(mode: Int): Boolean =
        mode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
                mode == PlaybackStateCompat.SHUFFLE_MODE_GROUP

/**
 * The repeat mode as `music.proto` carries it: 0 none, 1 all, 2 one.
 *
 * Unknown is reported as none because the wire format has no third answer, and none is the state
 * whose icon claims the least.
 */
internal fun repeatModeCode(mode: Int): Int = when (mode) {
    PlaybackStateCompat.REPEAT_MODE_ALL,
    PlaybackStateCompat.REPEAT_MODE_GROUP -> 1
    PlaybackStateCompat.REPEAT_MODE_ONE -> 2
    else -> 0
}
