package com.svartifoss.snfell.config

import com.svartifoss.snfell.actions.PlayLikedSongsAction
import com.svartifoss.snfell.actions.PlayPlaylistShortcutAction
import com.svartifoss.snfell.actions.PlaySoundCloudLikesAction
import com.svartifoss.snfell.actions.PlaySpotifyLikedSongsAction

/**
 * The action keys whose menu entries carry
 * [com.svartifoss.snfell.actions.PhoneAction.streamingShortcut].
 *
 * Only consulted to recognise an actions menu the watch already holds from a phone build that did
 * not send the description yet - see [lacksShortcutDescription]. What is *sent* is decided by the
 * action itself, the same split [lacksSeekOffset] makes.
 */
private val SHORTCUT_DESCRIPTION_ACTION_KEYS = setOf(
        PlayPlaylistShortcutAction::class.java.canonicalName,
        PlayLikedSongsAction::class.java.canonicalName,
        PlaySpotifyLikedSongsAction::class.java.canonicalName,
        PlaySoundCloudLikesAction::class.java.canonicalName)

/**
 * Whether a menu entry on the watch predates the shortcut description and has to be sent again.
 *
 * The payload is pushed on edits, not on upgrades, so without this an actions menu built before
 * the shortcut screen existed would go on starting its playlists at once until the user happened
 * to edit the menu - while the same playlists in the shortcut list already opened with Play and
 * Shuffle.
 */
fun lacksShortcutDescription(actionKey: String, hasDescription: Boolean): Boolean =
        !hasDescription && actionKey in SHORTCUT_DESCRIPTION_ACTION_KEYS
