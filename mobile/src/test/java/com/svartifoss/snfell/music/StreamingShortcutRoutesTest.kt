package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingShortcutRoutesTest {
    @Test
    fun unavailableClientsLeaveAnUntargetedWebLink() {
        val service = StreamingService.SPOTIFY
        val target = StreamingShortcutRoutes.resolvePackage(service,
                StreamingShortcutLinks.OPEN_MODE_APP, "removed.client", { false })
        assertNull(target)
        assertEquals("https://open.spotify.com/collection/tracks",
                StreamingShortcutRoutes.linkForTarget("spotify:collection:tracks", service, target))
    }

    @Test
    fun selectedAlternativeWinsForItsOwnService() {
        val target = StreamingShortcutRoutes.resolvePackage(
                StreamingService.YOUTUBE_MUSIC,
                StreamingShortcutLinks.OPEN_MODE_APP,
                "app.morphe",
                setOf("app.morphe", "com.google.android.apps.youtube.music")::contains
        )

        assertEquals("app.morphe", target)
    }

    @Test
    fun missingAlternativeFallsBackToOfficialServiceApp() {
        val target = StreamingShortcutRoutes.resolvePackage(
                StreamingService.SPOTIFY,
                StreamingShortcutLinks.OPEN_MODE_APP,
                "example.spotify.client",
                setOf("com.spotify.music")::contains
        )

        assertEquals("com.spotify.music", target)
    }

    @Test
    fun androidDefaultAndChooserDoNotForceSelectedApp() {
        listOf(
                StreamingShortcutLinks.OPEN_MODE_DEFAULT,
                StreamingShortcutLinks.OPEN_MODE_CHOOSER
        ).forEach { mode ->
            assertNull(StreamingShortcutRoutes.resolvePackage(
                    StreamingService.YOUTUBE_MUSIC,
                    mode,
                    "app.morphe",
                    { true }
            ))
        }
    }

    @Test
    fun alternativeAppsReceiveWebLinksWhileOfficialSpotifyUsesAppUri() {
        val link = "https://open.spotify.com/playlist/abc123"

        assertEquals(
                link,
                StreamingShortcutRoutes.linkForTarget(link, StreamingService.SPOTIFY, "example.client")
        )
        assertEquals(
                "spotify:playlist:abc123",
                StreamingShortcutRoutes.linkForTarget(
                        link, StreamingService.SPOTIFY, "com.spotify.music")
        )
    }
}
