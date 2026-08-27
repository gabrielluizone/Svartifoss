package com.svartifoss.snfell.view.watchface.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommunityThemeInstalledStateTest {

    @Test
    fun `published id finds the installed local copy regardless of revision`() {
        val installed = profile(
                localId = "local-copy",
                publishedTheme = PublishedThemeSource("public-theme", revision = 2))

        assertEquals(
                installed,
                installedCommunityTheme(
                        profiles = listOf(installed),
                        publishedId = "public-theme"))
    }

    @Test
    fun `another publication and a user owned fork are not installed matches`() {
        val anotherPublication = profile(
                localId = "another-local-copy",
                publishedTheme = PublishedThemeSource("another-public-theme", revision = 1))
        val userOwnedFork = profile(localId = "public-theme", publishedTheme = null)

        assertNull(installedCommunityTheme(
                profiles = listOf(anotherPublication, userOwnedFork),
                publishedId = "public-theme"))
    }

    private fun profile(
            localId: String,
            publishedTheme: PublishedThemeSource?
    ): WatchThemeProfile = WatchThemeProfile(
            id = localId,
            name = "Theme",
            baseFace = "classic",
            createdAt = 1L,
            updatedAt = 1L,
            revision = 1,
            settings = emptyMap(),
            publishedTheme = publishedTheme)
}
