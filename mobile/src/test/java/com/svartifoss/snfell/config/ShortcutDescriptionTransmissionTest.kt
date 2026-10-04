package com.svartifoss.snfell.config

import com.svartifoss.snfell.actions.PlayDeezerFlowAction
import com.svartifoss.snfell.actions.PlayLikedSongsAction
import com.svartifoss.snfell.actions.PlayLikedSongsShuffledAction
import com.svartifoss.snfell.actions.PlayPlaylistShortcutAction
import com.svartifoss.snfell.actions.PlaySoundCloudLikesAction
import com.svartifoss.snfell.actions.PlaySpotifyLikedSongsAction
import com.svartifoss.snfell.common.actions.StandardActions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins which actions-menu entries on the watch are re-sent so they learn they are streaming
 * shortcuts - which is what lets a playlist in the actions menu open its Play and Shuffle screen
 * after an upgrade, without the user editing the menu first.
 */
class ShortcutDescriptionTransmissionTest {

    @Test
    fun `a saved shortcut or a liked-songs entry without its description is sent again`() {
        listOf(
                PlayPlaylistShortcutAction::class.java,
                PlayLikedSongsAction::class.java,
                PlaySpotifyLikedSongsAction::class.java,
                PlaySoundCloudLikesAction::class.java
        ).forEach { type ->
            assertTrue(type.name, lacksShortcutDescription(type.canonicalName!!, hasDescription = false))
            assertFalse(type.name, lacksShortcutDescription(type.canonicalName!!, hasDescription = true))
        }
    }

    @Test
    fun `entries that never carry one do not trigger a resend`() {
        // Re-sending for these would push the whole menu on every start, for a field the phone
        // never fills in for them: the shuffled variant already is the choice, a mix is an endless
        // stream, and the rest are not destinations at all.
        listOf(
                PlayLikedSongsShuffledAction::class.java.canonicalName!!,
                PlayDeezerFlowAction::class.java.canonicalName!!,
                StandardActions.ACTION_PLAY_PAUSE,
                StandardActions.ACTION_SKIP_TO_NEXT
        ).forEach { key ->
            assertFalse(key, lacksShortcutDescription(key, hasDescription = false))
        }
    }
}
