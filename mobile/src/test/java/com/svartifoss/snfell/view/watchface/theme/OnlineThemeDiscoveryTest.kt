package com.svartifoss.snfell.view.watchface.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the gallery's local discovery rules independently of its Activity and RecyclerView. */
class OnlineThemeDiscoveryTest {

    @Test
    fun `search normalizes name author and base face`() {
        val match = theme(
                id = "b",
                name = "Cora\u00e7\u00e3o / Azul",
                author = "L\u00facia",
                baseFace = "immersive")
        val other = theme(id = "a", name = "Signal Noise", author = "Mina", baseFace = "poster")

        val results = OnlineThemeDiscovery.discover(
                listOf(other, match),
                OnlineThemeDiscoveryRequest(query = "CORACAO lucia immersive"))

        assertEquals(listOf(match), results)
        assertEquals("coracao azul", OnlineThemeDiscovery.normalize("Cora\u00e7\u00e3o / Azul"))
    }

    @Test
    fun `all face filter keeps every matching theme while a face filter is exact`() {
        val poster = theme(id = "poster", baseFace = "poster")
        val immersive = theme(id = "immersive", baseFace = "immersive")
        val themes = listOf(poster, immersive)

        val all = OnlineThemeDiscovery.discover(
                themes,
                OnlineThemeDiscoveryRequest(baseFace = OnlineThemeBaseFaceFilter.All))
        val onlyPoster = OnlineThemeDiscovery.discover(
                themes,
                OnlineThemeDiscoveryRequest(
                        baseFace = OnlineThemeBaseFaceFilter.BaseFace("POSTER")))

        assertEquals(listOf(immersive, poster), all)
        assertEquals(listOf(poster), onlyPoster)
    }

    @Test
    fun `newest sorts by publication time then id rather than input order`() {
        val old = theme(id = "old", publishedAt = "2026-08-20T00:00:00Z")
        val sameMomentZ = theme(id = "z", publishedAt = "2026-08-24T12:00:00Z")
        val sameMomentA = theme(id = "a", publishedAt = "2026-08-24T12:00:00Z")

        val results = OnlineThemeDiscovery.discover(
                listOf(sameMomentZ, old, sameMomentA),
                OnlineThemeDiscoveryRequest(sort = OnlineThemeSort.NEWEST))

        assertEquals(listOf(sameMomentA, sameMomentZ, old), results)
    }

    @Test
    fun `most liked breaks ties by newest then id`() {
        val fewerLikes = theme(id = "fewer", likes = 4, publishedAt = "2026-08-30T00:00:00Z")
        val olderPopular = theme(id = "older", likes = 8, publishedAt = "2026-08-20T00:00:00Z")
        val sameMomentZ = theme(id = "z", likes = 8, publishedAt = "2026-08-24T12:00:00Z")
        val sameMomentA = theme(id = "a", likes = 8, publishedAt = "2026-08-24T12:00:00Z")

        val results = OnlineThemeDiscovery.discover(
                listOf(fewerLikes, sameMomentZ, olderPopular, sameMomentA),
                OnlineThemeDiscoveryRequest(sort = OnlineThemeSort.MOST_LIKED))

        assertEquals(listOf(sameMomentA, sameMomentZ, olderPopular, fewerLikes), results)
    }

    @Test
    fun `query terms narrow results and an unmatched query returns no themes`() {
        val blue = theme(id = "blue", name = "Blue Orbit", author = "L\u00facia", baseFace = "poster")
        val green = theme(id = "green", name = "Green Orbit", author = "Mina", baseFace = "poster")

        val narrow = OnlineThemeDiscovery.discover(
                listOf(blue, green),
                OnlineThemeDiscoveryRequest(query = "orbit lucia"))
        val missing = OnlineThemeDiscovery.discover(
                listOf(blue, green),
                OnlineThemeDiscoveryRequest(query = "metadata"))

        assertEquals(listOf(blue), narrow)
        assertTrue(missing.isEmpty())
    }

    @Test
    fun `liked filter composes with search face filtering and ordering`() {
        val newestLiked = theme(
                id = "newest-liked",
                name = "Night Bloom",
                baseFace = "poster",
                likes = 2,
                publishedAt = "2026-08-25T00:00:00Z")
        val olderLiked = theme(
                id = "older-liked",
                name = "Night Tide",
                baseFace = "poster",
                likes = 9,
                publishedAt = "2026-08-20T00:00:00Z")
        val unliked = theme(
                id = "unliked",
                name = "Night Noise",
                baseFace = "poster",
                likes = 100,
                publishedAt = "2026-08-30T00:00:00Z")

        val results = OnlineThemeDiscovery.discover(
                themes = listOf(unliked, olderLiked, newestLiked),
                request = OnlineThemeDiscoveryRequest(
                        query = "night",
                        baseFace = OnlineThemeBaseFaceFilter.BaseFace("poster"),
                        sort = OnlineThemeSort.NEWEST,
                        likedOnly = true),
                likedThemeIds = setOf(olderLiked.id, newestLiked.id))

        assertEquals(listOf(newestLiked, olderLiked), results)
    }

    private fun theme(
            id: String,
            name: String = "Theme $id",
            author: String = "Author $id",
            baseFace: String = "classic",
            likes: Int = 0,
            publishedAt: String = "2026-08-24T00:00:00Z"
    ) = OnlineThemeSummary(
            id = id,
            name = name,
            author = author,
            baseFace = baseFace,
            revision = 1,
            schemaVersion = 1,
            minimumAppVersion = "3.3",
            publishedAt = publishedAt,
            likes = likes)
}
