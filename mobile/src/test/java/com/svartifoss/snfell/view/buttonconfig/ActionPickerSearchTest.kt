package com.svartifoss.snfell.view.buttonconfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPickerSearchTest {
    private val candidates = listOf(
            candidate("pause", "Pause", "Playback controls", 0),
            candidate("volume", "Set volume to 50%", "Volume controls › Volume levels", 1),
            candidate("spotify", "Play liked songs (Spotify)", "Streaming shortcuts", 2),
            candidate("search", "Search music", "Find & play", 3),
            candidate("faces", "Open face picker", "Watch screens", 4))

    @Test
    fun titleMatchRanksAheadOfCategoryMatch() {
        val ranked = ActionPickerSearch.rank(
                listOf(
                        candidate("category", "Pause", "Volume controls", 0),
                        candidate("title", "Set volume to 25%", "Audio", 1)),
                "volume")

        assertEquals(listOf("title", "category"), ranked.map { it.value })
    }

    @Test
    fun searchesNestedActionsFromTheirBreadcrumb() {
        val ranked = ActionPickerSearch.rank(candidates, "volume levels")

        assertEquals(listOf("volume"), ranked.map { it.value })
    }

    @Test
    fun everyQueryTermMustMatch() {
        val ranked = ActionPickerSearch.rank(candidates, "spotify liked")

        assertEquals(listOf("spotify"), ranked.map { it.value })
        assertTrue(ActionPickerSearch.rank(candidates, "spotify volume").isEmpty())
    }

    @Test
    fun searchIsCaseAndAccentInsensitive() {
        val accented = listOf(candidate("action", "Abrir ações", "Telas do relógio", 0))

        assertEquals("action", ActionPickerSearch.rank(accented, "ACOES").single().value)
        assertEquals("action", ActionPickerSearch.rank(accented, "relogio").single().value)
    }

    @Test
    fun tiesKeepCatalogOrder() {
        val ranked = ActionPickerSearch.rank(
                listOf(
                        candidate("second-source", "Repeat all", "Playback", 9),
                        candidate("first-source", "Repeat one", "Playback", 2)),
                "repeat")

        assertEquals(listOf("first-source", "second-source"), ranked.map { it.value })
    }

    private fun candidate(value: String, title: String, breadcrumb: String, order: Int) =
            ActionSearchCandidate(value, title, breadcrumb, order)
}
