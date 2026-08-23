package com.svartifoss.snfell.watch.view.facepicker

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceRecencyTest {

    private fun option(key: String) = WatchFaceOption(key, labelRes = 0, baseFace = key)

    private val registryOrder = listOf("classic", "expressive", "vinyl", "poster", "studio")
            .map(::option)

    private fun keysOf(options: List<WatchFaceOption>) = options.map { it.key }

    @Test
    fun `nothing used keeps the registry order exactly`() {
        assertEquals(
                keysOf(registryOrder),
                keysOf(FaceRecency.ordered(registryOrder, emptyMap())))
    }

    @Test
    fun `the most recently used comes first`() {
        val ordered = FaceRecency.ordered(
                registryOrder,
                mapOf("studio" to 300L, "vinyl" to 100L, "poster" to 200L))
        assertEquals(
                listOf("studio", "poster", "vinyl", "classic", "expressive"),
                keysOf(ordered))
    }

    /**
     * The untouched faces keep the curated order they were designed in. Sorting them too would
     * reshuffle most of the list every time one face was picked, which is unlearnable.
     */
    @Test
    fun `never-used faces keep their registry order behind the used ones`() {
        val ordered = FaceRecency.ordered(registryOrder, mapOf("studio" to 1L))
        assertEquals(
                listOf("studio", "classic", "expressive", "vinyl", "poster"),
                keysOf(ordered))
    }

    @Test
    fun `a timestamp for a face that is not offered is ignored`() {
        val ordered = FaceRecency.ordered(registryOrder, mapOf("archived-face" to 999L))
        assertEquals(keysOf(registryOrder), keysOf(ordered))
    }

    @Test
    fun `ordering never drops or duplicates an option`() {
        val ordered = FaceRecency.ordered(
                registryOrder, mapOf("poster" to 5L, "classic" to 9L))
        assertEquals(registryOrder.size, ordered.size)
        assertEquals(keysOf(registryOrder).toSet(), keysOf(ordered).toSet())
    }

    /**
     * The two sections are ordered independently, so a saved theme can never be placed among the
     * built-in faces on the strength of a timestamp - which is the whole reason the picker has
     * sections.
     */
    @Test
    fun `sections order independently of each other`() {
        val custom = listOf(option("custom:a"), option("custom:b"))
        val stamps = mapOf("custom:b" to 500L, "classic" to 400L)

        assertEquals(
                listOf("custom:b", "custom:a"),
                keysOf(FaceRecency.ordered(custom, stamps)))
        assertEquals(
                listOf("classic", "expressive", "vinyl", "poster", "studio"),
                keysOf(FaceRecency.ordered(registryOrder, stamps)))
    }
}
