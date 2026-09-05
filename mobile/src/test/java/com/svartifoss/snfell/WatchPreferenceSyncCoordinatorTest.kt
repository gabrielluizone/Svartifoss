package com.svartifoss.snfell

import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPreferenceSyncCoordinatorTest {

    @Test
    fun `syncs global and face scoped watch settings`() {
        assertTrue(shouldSyncWatchPreference("rotary_seek"))
        assertTrue(shouldSyncWatchPreference("wear_screen_face"))
        assertTrue(shouldSyncWatchPreference("wear_queue_style@material"))
        assertTrue(shouldSyncWatchPreference("album_art_style@custom_active"))
        assertTrue(shouldSyncWatchPreference("album_art_filter@custom_active"))
        assertTrue(shouldSyncWatchPreference("wear_dev_show_layout_bounds"))
        assertTrue(shouldSyncWatchPreference("wear_dev_show_player_info"))
    }

    @Test
    fun `ignores phone local and transient preferences`() {
        assertFalse(shouldSyncWatchPreference(null))
        assertFalse(shouldSyncWatchPreference("app_theme"))
        assertFalse(shouldSyncWatchPreference("last_update_check"))
    }

    /**
     * The three personal-data storages live in the phone's *default* preference file, which the
     * DataItem push used to ship to the watch wholesale - the watch reads none of them, and they
     * spent the same 100 KB item budget the watch-facing keys need. Both transports now filter on
     * this predicate, so these must stay out of it.
     */
    @Test
    fun `ignores the phone's own history and shortcut storages`() {
        assertFalse(shouldSyncWatchPreference("track_history"))
        assertFalse(shouldSyncWatchPreference("search_history"))
        assertFalse(shouldSyncWatchPreference("playlist_shortcuts"))
    }

    @Test
    fun `snapshot budget measures utf8 rather than unicode code units`() {
        // `é` occupies two UTF-8 bytes and the musical-symbol emoji four. Public theme text must
        // be counted this way; String.length() would understate both values.
        assertEquals(
                20,
                estimateWatchPreferenceSnapshotBytes(mapOf("é" to "🎵")))
    }

    /**
     * The auto-start blacklist is the one [Set] in the exportable registry, and it grows with the
     * number of apps the user picked. Counting it as zero understated exactly the entry a heavy
     * user is most likely to have made large.
     */
    @Test
    fun `snapshot budget measures string set members`() {
        val empty = estimateWatchPreferenceSnapshotBytes(mapOf("k" to emptySet<String>()))
        val two = estimateWatchPreferenceSnapshotBytes(
                mapOf("k" to setOf("com.example.one", "com.example.two")))
        assertTrue(two > empty)
    }

    /**
     * The active scope is the only one the watch can read, so it and the unscoped behaviour keys
     * are never measured against the packing budget. Shipping a theme used to be refused because
     * the *other* twenty scopes filled the payload - settings nothing on the wrist can reach.
     */
    @Test
    fun `the active scope is always transmitted whole however large the rest is`() {
        val prefs = mutableMapOf<String, Any?>("app_language" to "pt-BR")
        prefs += scopeEntries("custom_active", 179)
        ThemeAppearance.ALLOWED_BASE_FACES.forEach { prefs += scopeEntries(it, 179) }

        val selected = selectWatchPreferenceSnapshot(prefs, "custom_active").values

        assertEquals("pt-BR", selected["app_language"])
        scopeEntries("custom_active", 179).forEach { (key, value) ->
            assertEquals("missing $key", value, selected[key])
        }
        assertTrue(
                estimateWatchPreferenceSnapshotBytes(selected) < WATCH_SNAPSHOT_GUARD_BYTES)
    }

    /** A library no watch could ever have received now transmits, which is the whole point. */
    @Test
    fun `a fully customised library stays inside the transport budget`() {
        val prefs = mutableMapOf<String, Any?>()
        prefs += scopeEntries("custom_active", 179)
        ThemeAppearance.ALLOWED_BASE_FACES.forEach { prefs += scopeEntries(it, 179) }

        val unfiltered = estimateWatchPreferenceSnapshotBytes(prefs)
        val selected = selectWatchPreferenceSnapshot(prefs, "classic")

        assertTrue("unfiltered snapshot should exceed the guard", unfiltered > WATCH_SNAPSHOT_GUARD_BYTES)
        assertTrue(
                estimateWatchPreferenceSnapshotBytes(selected.values) < WATCH_SNAPSHOT_GUARD_BYTES)
        assertTrue(selected.droppedScopes.isNotEmpty())
        assertFalse("the active face is never dropped", selected.droppedScopes.contains("classic"))
    }

    /**
     * An ordinary library changes not at all: every scope still travels, so a face changed from the
     * wrist keeps rendering its own settings with no round trip.
     */
    @Test
    fun `a small library keeps every scope`() {
        val prefs = mutableMapOf<String, Any?>("app_language" to "en")
        prefs += scopeEntries("classic", 20)
        prefs += scopeEntries("verse", 20)
        prefs += scopeEntries("chat", 20)

        val selected = selectWatchPreferenceSnapshot(prefs, "classic")

        assertTrue(selected.droppedScopes.isEmpty())
        assertEquals(prefs.keys, selected.values.keys)
    }

    /**
     * Once a theme is not active its snapshot is the one group nothing can read, so it is the first
     * thing to give up - never a built-in face the picker still offers.
     */
    @Test
    fun `a stale custom theme snapshot is dropped before a built-in face`() {
        val prefs = mutableMapOf<String, Any?>()
        prefs += scopeEntries("classic", 179)
        prefs += scopeEntries("custom_active", 179)
        ThemeAppearance.ALLOWED_BASE_FACES.forEach { prefs += scopeEntries(it, 179) }

        val dropped = selectWatchPreferenceSnapshot(prefs, "classic").droppedScopes

        assertTrue(dropped.contains("custom_active"))
        assertEquals("custom_active", dropped.last())
    }

    /** Packing order is fixed, so two pushes of one unchanged preference file agree. */
    @Test
    fun `selection is deterministic for the same preferences`() {
        val prefs = mutableMapOf<String, Any?>()
        ThemeAppearance.ALLOWED_BASE_FACES.forEach { prefs += scopeEntries(it, 179) }

        val first = selectWatchPreferenceSnapshot(prefs, "classic")
        val second = selectWatchPreferenceSnapshot(prefs.toMap(), "classic")

        assertEquals(first.values.keys, second.values.keys)
        assertEquals(first.droppedScopes, second.droppedScopes)
    }

    @Test
    fun `phone-local keys never reach the watch whatever their scope`() {
        val prefs = mapOf<String, Any?>(
                "track_history" to "[]",
                "app_theme" to "dark",
                "wear_queue_style@classic" to "tonal")

        val selected = selectWatchPreferenceSnapshot(prefs, "classic").values

        assertEquals(setOf("wear_queue_style@classic"), selected.keys)
    }

    /**
     * An active custom theme makes its snapshot the scope, and a built-in face makes itself the
     * scope. Getting this backwards would transmit the face the user just switched *away* from.
     */
    @Test
    fun `the active scope follows the theme metadata in the same snapshot`() {
        val builtIn = mapOf<String, Any?>("wear_screen_face" to "verse")
        assertEquals("verse", activeWatchAppearanceScope(builtIn))

        val themed = mapOf<String, Any?>(
                "wear_screen_face" to "verse",
                "wear_active_custom_theme_id" to "a-theme",
                "wear_custom_theme_complete" to true,
                "wear_custom_theme_schema" to ThemeAppearance.CURRENT_SCHEMA.toString())
        assertEquals("custom_active", activeWatchAppearanceScope(themed))
    }

    /**
     * An incomplete or schema-mismatched theme resolves to its base face, exactly as the watch
     * resolves it - so the scope sent is the one the watch is about to read.
     */
    @Test
    fun `an unusable theme falls back to the base face scope`() {
        val incomplete = mapOf<String, Any?>(
                "wear_screen_face" to "chat",
                "wear_active_custom_theme_id" to "a-theme",
                "wear_custom_theme_complete" to false,
                "wear_custom_theme_schema" to ThemeAppearance.CURRENT_SCHEMA.toString())
        assertEquals("chat", activeWatchAppearanceScope(incomplete))

        // Empty preferences must still name a real scope rather than nothing at all.
        assertEquals(ThemeAppearance.DEFAULT_FACE, activeWatchAppearanceScope(emptyMap()))
    }

    /** One representative scoped key per synthetic scope, sized like the real registry. */
    private fun scopeEntries(scope: String, count: Int): Map<String, Any?> =
            SAMPLE_SCOPED_KEYS.take(count).associate { "$it@$scope" to "sample_value" }

    private companion object {
        /** The real registry, so a scope in these tests costs what a scope costs on a phone. */
        val SAMPLE_SCOPED_KEYS: List<String> = FaceScopedPreferences.SCOPED_KEYS.toList()
    }
}
