package com.svartifoss.snfell.view.watchface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The pickers that open as a grid of pictures are plain list rows on the Watch tab.
 *
 * The grid replaces exactly one thing - `ListPreference`'s own dialog - and reads the same entries
 * the list would show. A key that names a row of another class would either never reach the grid
 * (its preference shows its own dialog) or replace a picker that already draws what it offers.
 */
class VisualChoiceCatalogTest {

    private companion object {
        /** Pickers that already show what they offer, and must keep their own dialogs. */
        val ALREADY_VISUAL = setOf(
                "DescribedListPreference",
                "ColorTreatmentPreference",
                "TransportGlyphPreference",
                "AppIconStylePreference")
    }

    private fun watchSettings(): String =
            (File("src/main/res/xml/watch_face_settings.xml").takeIf { it.exists() }
                    ?: File("mobile/src/main/res/xml/watch_face_settings.xml")).readText()

    /** Every declared row as (tag, key), with the tag's package removed. */
    private fun rows(): Map<String, String> =
            Regex("""<([\w.]+)\s+[^>]*?android:key="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(watchSettings())
                    .associate { it.groupValues[2] to it.groupValues[1].substringAfterLast('.') }

    @Test
    fun `every visual picker is a plain list row`() {
        val rows = rows()
        VisualChoiceCatalog.KEYS.forEach { key ->
            val tag = rows[key]
            assertTrue("$key is not declared in watch_face_settings.xml", tag != null)
            assertEquals("$key must be a plain ListPreference to open as a grid", "ListPreference", tag)
        }
    }

    @Test
    fun `no picker that already shows its choices is replaced`() {
        val rows = rows()
        val replaced = VisualChoiceCatalog.KEYS.filter { rows[it] in ALREADY_VISUAL }
        assertTrue("Already visual, keep their dialogs: $replaced", replaced.isEmpty())
    }

    @Test
    fun `the cover shape pickers keep their own dialog`() {
        // Their dialog starts with "Hidden", which a grid of watches cannot show.
        assertTrue(VisualChoiceCatalog.KEYS.none { it.endsWith("_cover_shape") })
    }
}
