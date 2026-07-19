package com.svartifoss.snfell.common

import android.content.SharedPreferences
import com.matejdro.wearutils.preferences.definition.SimplePreferenceDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAppearanceTest {

    @Test
    fun `allowlist contains every renderer and no custom scope`() {
        assertEquals(
                setOf(
                        "classic", "expressive", "vinyl", "poster", "studio",
                        "halo", "aurora", "eclipse", "spectrum", "material"),
                ThemeAppearance.ALLOWED_BASE_FACES)
        assertFalse(ThemeAppearance.CUSTOM_SCOPE in ThemeAppearance.ALLOWED_BASE_FACES)
    }

    @Test
    fun `complete current custom snapshot resolves to custom context`() {
        assertEquals(
                AppearanceContext.Custom(
                        themeId = "profile-id",
                        baseFace = "poster",
                        schema = ThemeAppearance.CURRENT_SCHEMA,
                        revision = 7),
                ThemeAppearance.resolve(
                        baseFace = "poster",
                        customThemeId = " profile-id ",
                        customComplete = true,
                        customSchema = ThemeAppearance.CURRENT_SCHEMA,
                        customRevision = 7))
    }

    @Test
    fun `custom snapshot requires id complete marker current schema and valid face`() {
        val candidates = listOf(
                ThemeAppearance.resolve("poster", "", true, 1),
                ThemeAppearance.resolve("poster", "id", false, 1),
                ThemeAppearance.resolve("poster", "id", true, 0),
                ThemeAppearance.resolve("not-a-face", "id", true, 1))

        assertTrue(candidates.all { it is AppearanceContext.BuiltIn })
        assertEquals(
                AppearanceContext.BuiltIn("classic"),
                candidates.last())
    }

    @Test
    fun `invalid built-in face always normalizes to classic`() {
        assertEquals("classic", ThemeAppearance.normalizeBaseFace(null))
        assertEquals("classic", ThemeAppearance.normalizeBaseFace(""))
        assertEquals("classic", ThemeAppearance.normalizeBaseFace("custom_active"))
        assertEquals("material", ThemeAppearance.normalizeBaseFace("material"))
    }

    @Test
    fun `negative custom revision is normalized without rejecting valid snapshot`() {
        val resolved = ThemeAppearance.resolve("studio", "id", true, 1, -10)
        assertEquals(0, (resolved as AppearanceContext.Custom).revision)
    }

    @Test
    fun `scoped registry is derived from exportable appearance definitions`() {
        assertTrue(FaceScopedPreferences.SCOPED_DEFINITIONS.isNotEmpty())
        assertEquals(
                FaceScopedPreferences.SCOPED_DEFINITIONS.size,
                FaceScopedPreferences.SCOPED_DEFINITIONS_BY_KEY.size)
        assertEquals(
                FaceScopedPreferences.SCOPED_KEYS,
                FaceScopedPreferences.SCOPED_DEFINITIONS_BY_KEY.keys)
        assertTrue(FaceScopedPreferences.SCOPED_DEFINITIONS.all {
            it.key in FaceScopedPreferences.SCOPED_KEYS && it in MiscPreferences.EXPORTABLE
        })
        assertFalse(MiscPreferences.WEAR_SCREEN_FACE.key in
                FaceScopedPreferences.SCOPED_DEFINITIONS_BY_KEY)
    }

    @Test
    fun `custom reads are isolated from global and base-face values`() {
        val definition = SimplePreferenceDefinition("wear_font", "google_sans")
        val prefs = MapPreferences(mapOf(
                "wear_font" to "serif",
                "wear_font@poster" to "cursive",
                "wear_font@custom_active" to "rounded"))

        assertEquals(
                "cursive",
                FaceScopedPreferences.getString(
                        prefs, definition, AppearanceContext.BuiltIn("poster")))
        assertEquals(
                "rounded",
                FaceScopedPreferences.getString(
                        prefs, definition,
                        AppearanceContext.Custom("id", "poster", 1, 1)))

        val withoutCustomValue = MapPreferences(mapOf(
                "wear_font" to "serif",
                "wear_font@poster" to "cursive"))
        assertEquals(
                "google_sans",
                FaceScopedPreferences.getString(
                        withoutCustomValue, definition,
                        AppearanceContext.Custom("id", "poster", 1, 1)))
    }

    @Test
    fun `custom reads reject mismatched types and retain int compatibility`() {
        val prefs = MapPreferences(mapOf(
                "string@custom_active" to 12,
                "boolean@custom_active" to "true",
                "intString@custom_active" to "42",
                "intRaw@custom_active" to 24))
        val context = AppearanceContext.Custom("id", "classic", 1, 1)

        assertEquals("fallback", FaceScopedPreferences.getString(
                prefs, SimplePreferenceDefinition("string", "fallback"), context))
        assertFalse(FaceScopedPreferences.getBoolean(
                prefs, SimplePreferenceDefinition("boolean", false), context))
        assertEquals(42, FaceScopedPreferences.getInt(
                prefs, SimplePreferenceDefinition("intString", 0), context))
        assertEquals(24, FaceScopedPreferences.getInt(
                prefs, SimplePreferenceDefinition("intRaw", 0), context))
    }

    private class MapPreferences(values: Map<String, Any>) : SharedPreferences {
        private val data = values.toMap()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun getString(key: String?, defValue: String?): String? {
            val value = data[key] ?: return defValue
            if (value !is String) throw ClassCastException()
            return value
        }
        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            val value = data[key] ?: return defValue
            if (value !is Boolean) throw ClassCastException()
            return value
        }
        override fun getInt(key: String?, defValue: Int): Int {
            val value = data[key] ?: return defValue
            if (value !is Int) throw ClassCastException()
            return value
        }
        override fun getLong(key: String?, defValue: Long): Long =
                (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float =
                (data[key] as? Float) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
                (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun registerOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
}
