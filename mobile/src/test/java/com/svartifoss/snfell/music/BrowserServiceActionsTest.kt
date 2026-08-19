package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the service actions [MediaBrowserSearch.findBrowserService] looks for.
 *
 * The bug behind it: the lookup queried only the legacy `MediaBrowserService` action, but a Media3
 * app is not obliged to declare that one and current apps don't - SoundCloud's service advertises
 * `MediaLibraryService`/`MediaSessionService` and nothing else. The query therefore came back empty
 * and search, library browsing and both background-playback routes all concluded the app had no
 * library, in an app that implements all of them.
 *
 * This is a plain list rather than anything clever precisely so a future edit that drops an entry
 * fails here instead of silently switching four features off for a whole generation of apps.
 */
class BrowserServiceActionsTest {

    @Test
    fun legacyActionIsTriedFirstSoExistingAppsResolveUnchanged() {
        // Any app that already published the legacy action must keep resolving to the same
        // component it always did; the Media3 entries are only ever a fallback.
        assertEquals(
                "android.media.browse.MediaBrowserService",
                MediaBrowserSearch.browserServiceActions().first())
    }

    @Test
    fun media3ActionsAreQueriedToo() {
        val actions = MediaBrowserSearch.browserServiceActions()
        assertTrue(
                "MediaLibraryService must be queried - it is the browsable Media3 service",
                "androidx.media3.session.MediaLibraryService" in actions)
        assertTrue(
                "MediaSessionService must be queried - connecting still wakes the app for playback",
                "androidx.media3.session.MediaSessionService" in actions)
    }

    @Test
    fun theBrowsableMedia3ServiceIsPreferredOverTheSessionOnlyOne() {
        // MediaLibraryService carries a library; MediaSessionService does not. An app declaring
        // both must resolve to the one that can actually answer a browse.
        val actions = MediaBrowserSearch.browserServiceActions()
        assertTrue(
                actions.indexOf("androidx.media3.session.MediaLibraryService") <
                        actions.indexOf("androidx.media3.session.MediaSessionService"))
    }
}
