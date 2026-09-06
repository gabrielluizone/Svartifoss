package com.svartifoss.snfell.config

import com.svartifoss.snfell.common.MatejdroArtistAutosizeMigration
import com.svartifoss.snfell.common.MiscPreferences
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what may and may not be compiled into the APK as somebody else's starting point.
 *
 * Every rule here fails silently in the same direction if it breaks: the export still succeeds,
 * the file still imports, and the leak or the broken first run only shows up on a stranger's phone
 * after a release. The section split is [ConfigBackup]'s, so these assert the *policy over* it
 * rather than re-listing keys - a preference added to one of the excluded sections is then covered
 * automatically, which is the whole reason the exporter is written that way.
 */
class DefaultConfigExportTest {

    @Test
    fun `personal, consent and device-local state never become defaults`() {
        assertEquals(
                setOf(
                        ConfigBackupSection.HISTORY,
                        ConfigBackupSection.PRIVACY,
                        ConfigBackupSection.LOCAL_APP_STATE),
                DefaultConfigExport.EXCLUDED_SECTIONS)

        // Stated through the section mapping rather than by naming keys, because that mapping is
        // what the exporter actually consults - listing the keys again would let the two drift.
        listOf("search_history", "track_history",
                MiscPreferences.CRASH_REPORTING_ENABLED.key,
                MiscPreferences.ANNOUNCEMENTS_ENABLED.key,
                "notification_access_prompted", "face_reset_prompt_handled",
                "center_long_press_repaired", "update_last_check_ms",
                MatejdroArtistAutosizeMigration.MARKER_KEY).forEach { key ->
            assertFalse(
                    "$key must not ship as a default",
                    ConfigBackup.sectionForPreference(key) in DefaultConfigExport.INCLUDED_SECTIONS)
        }
    }

    /** The point of the file: the author's setup is what a fresh install should arrive wearing. */
    @Test
    fun `the setup itself does ship`() {
        listOf(
                ConfigBackupSection.BUTTONS,
                ConfigBackupSection.ACTIONS,
                ConfigBackupSection.APP_SETTINGS,
                ConfigBackupSection.WATCH_APPEARANCE,
                ConfigBackupSection.PLAYLIST_SHORTCUTS,
                ConfigBackupSection.ICONS,
                ConfigBackupSection.AUXILIARY_DATA
        ).forEach {
            assertTrue("$it belongs in a defaults snapshot", it in DefaultConfigExport.INCLUDED_SECTIONS)
        }
        assertEquals(
                ConfigBackupSection.ALL,
                DefaultConfigExport.INCLUDED_SECTIONS + DefaultConfigExport.EXCLUDED_SECTIONS)
    }

    /**
     * The language picker overrides the device locale, so a shipped value opens the app in the
     * author's language on everyone's phone. It is the one excluded key that would be a visible
     * fault rather than a leak.
     */
    @Test
    fun `the app language is never pinned for a new install`() {
        assertFalse(DefaultConfigExport.shipsPreference(MiscPreferences.APP_LANGUAGE.key))
        assertTrue(DefaultConfigExport.shipsPreference(MiscPreferences.WEAR_SCREEN_FACE.key))
    }

    /** Face-scoped entries are judged by the setting they hold, not by their decorated name. */
    @Test
    fun `a scoped key is judged by its base setting`() {
        assertFalse(DefaultConfigExport.shipsPreference(
                "${MiscPreferences.APP_LANGUAGE.key}@classic"))
        assertTrue(DefaultConfigExport.shipsPreference("wear_font@poster"))
    }

    /** The icon metadata has to travel with the icon files; the gallery account must not. */
    @Test
    fun `auxiliary stores ship the icons but not the gallery account`() {
        assertTrue(DefaultConfigExport.shipsNamedPreferenceStore("custom_icon_storage"))
        assertFalse(DefaultConfigExport.shipsNamedPreferenceStore("community_theme_submission"))
    }

    @Test
    fun `redaction removes exactly the excluded entries and reports them`() {
        val json = JSONObject()
                .put("preferences", JSONObject()
                        .put(MiscPreferences.APP_LANGUAGE.key, "pt-BR")
                        .put("wear_font", "roboto")
                        .put("wear_font@poster", "serif"))
                .put("namedPreferences", JSONObject()
                        .put("custom_icon_storage", JSONObject())
                        .put("community_theme_submission", JSONObject()))

        val removed = DefaultConfigExport.redact(json)

        val prefs = json.getJSONObject("preferences")
        assertFalse(prefs.has(MiscPreferences.APP_LANGUAGE.key))
        assertTrue(prefs.has("wear_font"))
        assertTrue(prefs.has("wear_font@poster"))

        val stores = json.getJSONObject("namedPreferences")
        assertTrue(stores.has("custom_icon_storage"))
        assertFalse(stores.has("community_theme_submission"))

        assertEquals(
                setOf(
                        MiscPreferences.APP_LANGUAGE.key,
                        "namedPreferences/community_theme_submission"),
                removed.toSet())
    }

    /** A document with neither block is not an error: a snapshot can legitimately carry no
     *  auxiliary stores, and redaction must not invent them. */
    @Test
    fun `redaction tolerates a document missing those blocks`() {
        val json = JSONObject().put("schemaVersion", 7)
        assertTrue(DefaultConfigExport.redact(json).isEmpty())
        assertFalse(json.has("preferences"))
    }
}
