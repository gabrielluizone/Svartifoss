package com.svartifoss.snfell.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a reported list of active sessions is worth acting on. Strings stand in for session tokens,
 * which compare the same way (by identity of the session).
 */
class SessionListChangeTest {

    @Test
    fun theSameSessionsInTheSameOrderAreNotNews() {
        // The flood that prompted this: every event another app's session caused arrived with the
        // one active session, YouTube Music, again.
        assertFalse(SessionListChange.isNew(listOf("ytmusic"), listOf("ytmusic")))
        assertFalse(SessionListChange.isNew(emptyList<String>(), emptyList<String>()))
    }

    @Test
    fun aSessionAppearingOrGoingIsNews() {
        assertTrue(SessionListChange.isNew(listOf("ytmusic"), listOf("ytmusic", "spotify")))
        assertTrue(SessionListChange.isNew(listOf("ytmusic", "spotify"), listOf("spotify")))
        assertTrue(SessionListChange.isNew(listOf("ytmusic"), emptyList<String>()))
    }

    @Test
    fun anotherOrderIsNewsBecauseItSaysWhichWasActiveLast() {
        assertTrue(SessionListChange.isNew(listOf("ytmusic", "spotify"), listOf("spotify", "ytmusic")))
    }

    @Test
    fun theFirstListAfterListeningStartsIsAlwaysNews() {
        assertTrue(SessionListChange.isNew(null, emptyList<String>()))
        assertTrue(SessionListChange.isNew(null, listOf("ytmusic")))
    }
}
