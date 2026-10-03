package com.svartifoss.snfell.config

import com.svartifoss.snfell.common.actions.StandardActions

/**
 * The action keys whose entries carry [com.svartifoss.snfell.actions.PhoneAction.seekOffsetMs].
 *
 * Only consulted to recognise a config the watch already holds from a phone build that did not
 * send the offset yet - see [lacksSeekOffset]. What is *sent* is decided by the action itself.
 */
private val SEEK_OFFSET_ACTION_KEYS = setOf(
        StandardActions.ACTION_SKIP_30_SECONDS,
        StandardActions.ACTION_REVERSE_30_SECONDS)

/**
 * Whether an entry on the watch predates the seek offset and has to be sent again.
 *
 * Without this, the watch would go on drawing a skip-by-seconds press only after the round trip
 * until the user happened to edit that config - the payload is pushed on edits, not on upgrades.
 * The same reason the transmitters already re-send a streaming shortcut that arrived without its
 * link.
 */
fun lacksSeekOffset(actionKey: String, hasSeekOffset: Boolean): Boolean =
        !hasSeekOffset && actionKey in SEEK_OFFSET_ACTION_KEYS
