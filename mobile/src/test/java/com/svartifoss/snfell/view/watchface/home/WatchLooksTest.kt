package com.svartifoss.snfell.view.watchface.home

import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.view.watchface.theme.WatchThemeProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchLooksTest {

    private val faces = listOf("classic", "expressive", "vinyl", "studio", "verse")
    private val archived = setOf("vinyl")

    private fun profile(id: String, name: String, face: String = "studio") = WatchThemeProfile(
            id = id,
            name = name,
            baseFace = face,
            createdAt = 0L,
            updatedAt = 0L,
            revision = 1,
            settings = emptyMap())

    private val mine = listOf(profile("a", "Night drive"), profile("b", "Gym", face = "classic"))

    private fun build(
            profiles: List<WatchThemeProfile> = mine,
            showArchived: Boolean = false,
            active: AppearanceContext = AppearanceContext.BuiltIn("classic")
    ) = WatchLooks.build(profiles, faces, archived, showArchived, active)

    private fun List<WatchLookTile>.keys() = map { it.look.key }

    @Test
    fun `saved themes lead, then the built-in layouts, then the community tile`() {
        assertEquals(
                listOf("custom:a", "custom:b", "classic", "expressive", "studio", "verse", "community"),
                build().keys())
    }

    @Test
    fun `the community tile is last even with no saved themes`() {
        val keys = build(profiles = emptyList()).keys()
        assertEquals("community", keys.last())
        assertEquals(listOf("classic", "expressive", "studio", "verse"), keys.dropLast(1))
    }

    @Test
    fun `archived layouts are hidden unless revealed or applied`() {
        assertTrue("vinyl" !in build().keys())
        assertTrue("vinyl" in build(showArchived = true).keys())
        assertTrue("vinyl" in build(active = AppearanceContext.BuiltIn("vinyl")).keys())
    }

    @Test
    fun `exactly one tile is applied - a built-in layout`() {
        val tiles = build(active = AppearanceContext.BuiltIn("studio"))
        assertEquals(listOf("studio"), tiles.filter { it.applied }.map { it.look.key })
    }

    /** The saved theme is applied, not the layout it is built on. */
    @Test
    fun `exactly one tile is applied - a saved theme`() {
        val tiles = build(active = AppearanceContext.Custom("a", "studio", schema = 1, revision = 1))
        assertEquals(listOf("custom:a"), tiles.filter { it.applied }.map { it.look.key })
    }

    /** A theme active with no library entry - only a broken restore does that - marks its layout. */
    @Test
    fun `an active theme missing from the library marks its layout`() {
        val tiles = build(active = AppearanceContext.Custom("lost", "verse", schema = 1, revision = 1))
        assertEquals(listOf("verse"), tiles.filter { it.applied }.map { it.look.key })
    }

    @Test
    fun `the community tile is never applied`() {
        build().filter { it.look is WatchLook.Community }.forEach { assertTrue(!it.applied) }
    }
}
