package com.svartifoss.snfell.view.watchface.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchTabNavigationTest {

    private val sections = listOf("style", "background", "colors", "typography", "aod", "panels", "miniButtons")

    private fun resolve(
            restored: WatchTabPlace? = null,
            search: String? = null,
            last: WatchTabPlace? = null
    ) = WatchTabNavigation.resolve(restored, search, last, sections)

    /** A rotation brings back what the person was looking at a moment ago, whatever else says. */
    @Test
    fun `restored state wins over a search target and the last place`() {
        assertEquals(
                WatchTabPlace.Area("colors"),
                resolve(
                        restored = WatchTabPlace.Area("colors"),
                        search = "aod",
                        last = WatchTabPlace.Area("panels")))
        assertEquals(
                WatchTabPlace.Home,
                resolve(restored = WatchTabPlace.Home, search = "aod", last = WatchTabPlace.Area("panels")))
    }

    /** A result is a row on a page, so search always opens an area - never Home. */
    @Test
    fun `a search target opens its area`() {
        assertEquals(WatchTabPlace.Area("aod"), resolve(search = "aod", last = WatchTabPlace.Home))
        assertEquals(WatchTabPlace.Area("aod"), resolve(search = "aod", last = WatchTabPlace.Area("panels")))
    }

    @Test
    fun `the tab reopens where this process left it`() {
        assertEquals(WatchTabPlace.Area("panels"), resolve(last = WatchTabPlace.Area("panels")))
        assertEquals(WatchTabPlace.Home, resolve(last = WatchTabPlace.Home))
    }

    @Test
    fun `a fresh process opens Home`() {
        assertEquals(WatchTabPlace.Home, resolve())
    }

    /** What a page removed by an update would otherwise do: open somewhere arbitrary. */
    @Test
    fun `an unknown section falls back to Home`() {
        assertEquals(WatchTabPlace.Home, resolve(restored = WatchTabPlace.Area("gone")))
        assertEquals(WatchTabPlace.Home, resolve(search = "gone"))
        assertEquals(WatchTabPlace.Home, resolve(last = WatchTabPlace.Area("gone")))
        assertEquals(
                WatchTabPlace.Area("colors"),
                resolve(search = "gone", last = WatchTabPlace.Area("colors")))
    }

    @Test
    fun `a place survives the saved-state bundle`() {
        listOf(WatchTabPlace.Home, WatchTabPlace.Area("typography")).forEach { place ->
            assertEquals(place, WatchTabNavigation.decode(WatchTabNavigation.encode(place)))
        }
        assertNull(WatchTabNavigation.decode(null))
        assertNull(WatchTabNavigation.decode(""))
    }

    /** Home is saved under the same word the preference page uses for its section. */
    @Test
    fun `home is saved as the home section key`() {
        assertEquals(
                com.svartifoss.snfell.view.watchface.WatchFacePrefsFragment.SECTION_HOME,
                WatchTabNavigation.encode(WatchTabPlace.Home))
    }
}
