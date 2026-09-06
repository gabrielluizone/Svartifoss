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
    fun `the author filter is exact where a search term is not`() {
        val theirs = theme(id = "theirs", name = "Midnight", author = "Verse")
        val aboutThem = theme(id = "about", name = "Verse at dusk", author = "Mina")
        val themes = listOf(theirs, aboutThem)

        val filtered = OnlineThemeDiscovery.discover(
                themes,
                OnlineThemeDiscoveryRequest(author = "verse"))
        val searched = OnlineThemeDiscovery.discover(
                themes,
                OnlineThemeDiscoveryRequest(query = "verse"))

        // The whole reason the author is its own field: a search for the name also matches every
        // theme merely *called* that.
        assertEquals(listOf(theirs), filtered)
        assertEquals(listOf(aboutThem, theirs), searched)
    }

    @Test
    fun `the author filter normalizes accents and case, and a blank one filters nothing`() {
        val lucia = theme(id = "lucia", author = "L\u00facia")
        val mina = theme(id = "mina", author = "Mina")
        val themes = listOf(lucia, mina)

        assertEquals(
                listOf(lucia),
                OnlineThemeDiscovery.discover(
                        themes,
                        OnlineThemeDiscoveryRequest(author = "LUCIA")))
        // A blank name arriving from saved state is no filter rather than one nothing satisfies.
        assertEquals(
                listOf(lucia, mina),
                OnlineThemeDiscovery.discover(themes, OnlineThemeDiscoveryRequest(author = "  ")))
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
    fun `most downloaded orders by installs and falls back to likes then newest then id`() {
        // Downloads and likes are deliberately separate orders rather than one "popular": a theme
        // can be widely installed and rarely hearted, so the tie-break chain has to prove the
        // install count wins outright before likes are consulted at all.
        val quietlyPopular = theme(id = "quiet", installs = 90, likes = 1,
                publishedAt = "2026-08-20T00:00:00Z")
        val loved = theme(id = "loved", installs = 12, likes = 400,
                publishedAt = "2026-08-30T00:00:00Z")
        val tiedLessLoved = theme(id = "a", installs = 12, likes = 3,
                publishedAt = "2026-08-31T00:00:00Z")

        val results = OnlineThemeDiscovery.discover(
                listOf(loved, tiedLessLoved, quietlyPopular),
                OnlineThemeDiscoveryRequest(sort = OnlineThemeSort.MOST_DOWNLOADED))

        assertEquals(listOf(quietlyPopular, loved, tiedLessLoved), results)
    }

    @Test
    fun `a catalogue published before install counts existed orders by its remaining signals`() {
        // Every entry reads back as zero installs until the publisher's next run backfills them,
        // and an order that collapsed to catalogue order there would look broken on the one day
        // it matters most.
        val newer = theme(id = "newer", likes = 2, publishedAt = "2026-08-30T00:00:00Z")
        val older = theme(id = "older", likes = 2, publishedAt = "2026-08-20T00:00:00Z")

        val results = OnlineThemeDiscovery.discover(
                listOf(older, newer),
                OnlineThemeDiscoveryRequest(sort = OnlineThemeSort.MOST_DOWNLOADED))

        assertEquals(listOf(newer, older), results)
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

    @Test
    fun `installed themes are hidden by default and returned once the filter is off`() {
        val installed = theme(id = "installed", name = "Already Mine")
        val fresh = theme(id = "fresh", name = "Brand New")

        val hidden = OnlineThemeDiscovery.discover(
                themes = listOf(installed, fresh),
                request = OnlineThemeDiscoveryRequest(),
                installedThemeIds = setOf(installed.id))
        assertEquals(listOf(fresh), hidden)

        val shown = OnlineThemeDiscovery.discover(
                themes = listOf(installed, fresh),
                request = OnlineThemeDiscoveryRequest(hideInstalled = false),
                installedThemeIds = setOf(installed.id))
        assertEquals(setOf(installed, fresh), shown.toSet())
    }

    /**
     * Asking to see what you liked and being shown nothing because you also installed it would
     * read as a broken filter, so Liked wins over the default hide.
     */
    @Test
    fun `liked filter overrides hiding an installed theme`() {
        val installedAndLiked = theme(id = "both", name = "Kept Favourite")

        val results = OnlineThemeDiscovery.discover(
                themes = listOf(installedAndLiked),
                request = OnlineThemeDiscoveryRequest(likedOnly = true),
                likedThemeIds = setOf(installedAndLiked.id),
                installedThemeIds = setOf(installedAndLiked.id))

        assertEquals(listOf(installedAndLiked), results)
    }

    /** An unknown installed id must never remove a catalogue entry it does not name. */
    @Test
    fun `hiding installed themes matches on id alone`() {
        val theme = theme(id = "catalogue-id", name = "Untouched")

        val results = OnlineThemeDiscovery.discover(
                themes = listOf(theme),
                request = OnlineThemeDiscoveryRequest(),
                installedThemeIds = setOf("some-other-local-id"))

        assertEquals(listOf(theme), results)
    }

    private fun theme(
            id: String,
            name: String = "Theme $id",
            author: String = "Author $id",
            baseFace: String = "classic",
            likes: Int = 0,
            installs: Int = 0,
            publishedAt: String = "2026-08-24T00:00:00Z"
    ) = OnlineThemeSummary(
            id = id,
            name = name,
            author = author,
            baseFace = baseFace,
            revision = 1,
            schemaVersion = 1,
            minimumAppVersion = "4.0",
            publishedAt = publishedAt,
            likes = likes,
            installs = installs)
}
