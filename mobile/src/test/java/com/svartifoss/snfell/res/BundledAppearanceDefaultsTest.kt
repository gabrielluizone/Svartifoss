package com.svartifoss.snfell.res

import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Reset this layout" restores from `res/raw/default_config.json`, so that file is no longer only
 * a first-run seed - it is the definition of what every reset returns to.
 *
 * The failure it replaced was silent in the worst way: reset *deleted* a face's scoped values and
 * the app fell through to definition defaults, a look that has never shipped, with no route back.
 * If a future defaults export were to drop a face's scope, reset would quietly regress to exactly
 * that for the face, so the coverage is pinned here rather than left to be noticed on a device.
 */
class BundledAppearanceDefaultsTest {

    private val preferences: JSONObject by lazy {
        val file = listOf(
                File("src/main/res/raw/default_config.json"),
                File("mobile/src/main/res/raw/default_config.json")
        ).first { it.exists() }
        JSONObject(file.readText()).getJSONObject("preferences")
    }

    private fun scopedKeys(): List<Pair<String, String>> =
            preferences.keys().asSequence()
                    .filter { it.contains(FaceScopedPreferences.SCOPE_SEPARATOR) }
                    .map { key ->
                        val at = key.indexOf(FaceScopedPreferences.SCOPE_SEPARATOR)
                        key.substring(0, at) to key.substring(at + 1)
                    }
                    .toList()

    @Test
    fun `every face has shipped appearance defaults to reset to`() {
        val scopes = scopedKeys().map { it.second }.toSet()
        val missing = ThemeAppearance.ALLOWED_BASE_FACES.filterNot { it in scopes }
        assertEquals(
                "Faces with no scope in res/raw/default_config.json - \"Reset this layout\" would " +
                        "drop them to definition defaults, a look the app has never shipped. " +
                        "Re-export the app defaults (Settings -> Developer -> Export as app " +
                        "defaults) with every face configured.",
                emptyList<String>(),
                missing)
    }

    @Test
    fun `the shipped scopes name real faces`() {
        val known = ThemeAppearance.ALLOWED_BASE_FACES + ThemeAppearance.CUSTOM_SCOPE
        val unknown = scopedKeys().map { it.second }.toSet().filterNot { it in known }
        assertEquals(
                "Scopes in default_config.json that are not faces - a reset can never reach them, " +
                        "so they are dead weight in the seeded install and in the watch sync.",
                emptyList<String>(),
                unknown)
    }

    @Test
    fun `every shipped scoped value belongs to a scoped appearance key`() {
        val unknown = scopedKeys().map { it.first }.toSet()
                .filterNot { it in FaceScopedPreferences.SCOPED_KEYS }
        assertEquals(
                "Scoped keys in default_config.json that are not in " +
                        "FaceScopedPreferences.SCOPED_KEYS - a reset ignores them, so they would " +
                        "survive it and keep overriding the shipped look.",
                emptyList<String>(),
                unknown)
    }

    @Test
    fun `a shipped face carries enough of the appearance to be a real default`() {
        // Not a per-key requirement: the export only records what was explicitly set, and a key
        // the author left alone correctly resets to its per-face default. But a scope holding only
        // a handful of keys means the export ran before that face was configured, which is the
        // state this test exists to catch early.
        val perFace = scopedKeys().groupingBy { it.second }.eachCount()
        val thin = ThemeAppearance.ALLOWED_BASE_FACES
                .filter { (perFace[it] ?: 0) < 100 }
        assertEquals(
                "Faces whose shipped scope holds under 100 values - too thin to be the default " +
                        "look, so resetting them mostly falls through to definition defaults: $perFace",
                emptyList<String>(),
                thin)
    }

    @Test
    fun `archived faces still ship defaults, since they can still be selected`() {
        val scopes = scopedKeys().map { it.second }.toSet()
        ArchivedFaces.KEYS.forEach {
            assertTrue(
                    "Archived face '$it' has no shipped defaults. Archival hides a face from the " +
                            "pickers; anyone already on it can still reset it.",
                    it in scopes)
        }
    }

    @Test
    fun `every shipped value is a typed envelope a reset can write back`() {
        preferences.keys().forEach { key ->
            val entry = preferences.optJSONObject(key)
            assertTrue("Preference '$key' is not a typed envelope", entry != null)
            assertTrue(
                    "Preference '$key' has no type - ConfigBackup.putTypedPreference would throw " +
                            "and the reset would fall back to removing it",
                    entry!!.optString("type").isNotEmpty())
        }
    }
}
