package com.svartifoss.snfell.view.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the ranking half of the settings search - the part where the judgement calls live, kept free
 * of Android types precisely so it can be tested directly rather than through the Activity.
 */
class SettingsSearchRankingTest {

    private fun entry(
            title: String,
            summary: String = "",
            category: String = "Appearance",
            key: String = title.lowercase().replace(' ', '_')
    ) = SettingsSearchEntry(
            key = key,
            title = title,
            summary = summary,
            categoryTitle = category,
            destination = SettingsSearchDestination.Settings("general"))

    @Test
    fun `empty query returns nothing rather than everything`() {
        val entries = listOf(entry("Album art style"), entry("Accent colour"))

        assertTrue(SettingsSearchIndex.rank(entries, "").isEmpty())
        assertTrue(SettingsSearchIndex.rank(entries, "   ").isEmpty())
    }

    @Test
    fun `title matches outrank category and summary matches`() {
        val titleHit = entry("Volume step")
        val categoryHit = entry("Something else", category = "Volume")
        val summaryHit = entry("Another thing", summary = "Controls the volume on the watch")

        val results = SettingsSearchIndex.rank(listOf(summaryHit, categoryHit, titleHit), "volume")

        assertEquals(listOf(titleHit, categoryHit, summaryHit), results)
    }

    @Test
    fun `a title the query starts outranks one it merely appears in`() {
        val startsWith = entry("Clock colour")
        val contains = entry("Show clock")

        val results = SettingsSearchIndex.rank(listOf(contains, startsWith), "clock")

        assertEquals(listOf(startsWith, contains), results)
    }

    /** Extra words have to narrow the result set. Widening would make a longer, more specific
     *  query return *more* rows, which is the opposite of what typing more is for. */
    @Test
    fun `every term must match, so more words narrow the results`() {
        val both = entry("Clock colour", summary = "Colour of the clock")
        val onlyClock = entry("Clock size")

        val results = SettingsSearchIndex.rank(listOf(both, onlyClock), "clock colour")

        assertEquals(listOf(both), results)
    }

    /**
     * The app ships 13 languages; requiring diacritics to be typed exactly would make search close
     * to unusable in most of them.
     */
    @Test
    fun `matching ignores case and diacritics in both directions`() {
        val accented = entry("Reprodução", summary = "Opções de reprodução")

        assertEquals(listOf(accented), SettingsSearchIndex.rank(listOf(accented), "reproducao"))
        assertEquals(listOf(accented), SettingsSearchIndex.rank(listOf(accented), "REPRODUÇÃO"))

        val plain = entry("Reproducao")
        assertEquals(listOf(plain), SettingsSearchIndex.rank(listOf(plain), "reprodução"))
    }

    @Test
    fun `a query matching nothing returns nothing`() {
        val entries = listOf(entry("Album art style"), entry("Accent colour"))

        assertTrue(SettingsSearchIndex.rank(entries, "bluetooth").isEmpty())
    }

    /** Equal scores sort by title, so results do not reshuffle when a preference is moved in the
     *  XML - the index is built in file order, which is not a meaningful ranking. */
    @Test
    fun `ties are broken by title rather than by index order`() {
        val b = entry("Beta rows")
        val a = entry("Alpha rows")

        assertEquals(listOf(a, b), SettingsSearchIndex.rank(listOf(b, a), "rows"))
    }

    @Test
    fun `normalize strips accents and lowercases`() {
        assertEquals("reproducao", SettingsSearchIndex.normalize("Reprodução"))
        assertEquals("aeiou", SettingsSearchIndex.normalize("áéíóu"))
    }
}
