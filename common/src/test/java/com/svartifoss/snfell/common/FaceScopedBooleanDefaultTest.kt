package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the resolution order of the *boolean* face-scoped read against the string one.
 *
 * This has now been the source of the same user-visible bug twice: a per-face default that the
 * phone's settings screen honours and the watch does not, so a switch reads "off" while the watch
 * keeps drawing the element - and it only comes right once the user toggles the switch, because
 * that finally writes an explicit `key@face` entry. Both overloads must walk the same four steps:
 * explicit per-face value → [FaceScopedPreferences.perFaceDefault] → legacy global → definition
 * default.
 *
 * Written against the pure registry rather than SharedPreferences so it runs on the JVM: the part
 * that broke is *which source is consulted and in what order*, and that is expressible here.
 */
class FaceScopedBooleanDefaultTest {

    /**
     * The guard that actually matters. Any face/key pair with a per-face default has to be readable
     * as a boolean through the same lookup the string path uses - if this set is non-empty and the
     * boolean overload ignores [FaceScopedPreferences.perFaceDefault], the bug is back.
     */
    @Test
    fun `every boolean per-face default parses as a boolean`() {
        val booleanDefaults = ThemeAppearance.ALLOWED_BASE_FACES.flatMap { face ->
            FaceScopedPreferences.SCOPED_KEYS.mapNotNull { key ->
                FaceScopedPreferences.perFaceDefault(face, key)?.let { face to (key to it) }
            }
        }.filter { (_, entry) -> entry.second.toBooleanStrictOrNull() != null }

        assertTrue(
                "No boolean per-face default is declared any more. If that is deliberate, delete " +
                        "this test; if not, the registry lost an entry.",
                booleanDefaults.isNotEmpty())
        booleanDefaults.forEach { (face, entry) ->
            assertEquals(
                    "Per-face default for ${entry.first} on $face must be a strict boolean",
                    entry.second,
                    entry.second.toBooleanStrictOrNull().toString())
        }
    }

    /** The faces that compose the whole screen turn the shared chrome off; if these silently
     *  stopped resolving, that chrome would land back on top of their layouts. */
    @Test
    fun `self-composed faces default the edge progress arc off`() {
        listOf("chat", "split").forEach { face ->
            assertEquals(
                    "$face must default the edge progress arc off",
                    "false",
                    FaceScopedPreferences.perFaceDefault(
                            face, MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE.key))
        }
    }

    /**
     * Chat hosts the mini-button row inside its own composition - its round actions *are* the
     * configured buttons - so it must not also declare the row off, which is what it did while it
     * counted as a self-composed face. Getting this wrong is silent: the buttons simply never
     * appear on the one face built to show them, and the user has no way to tell whether they
     * configured them wrongly or the face ignores them.
     */
    @Test
    fun `chat does not default the mini-button row off`() {
        assertNull(
                "Chat draws the mini buttons itself; defaulting them off hides them entirely",
                FaceScopedPreferences.perFaceDefault(
                        "chat", MiscPreferences.WEAR_MINI_BUTTONS_MODE.key))
        // The faces that really do let the shared row float over them still turn it off.
        assertEquals(
                ActivityVisibility.NEVER,
                FaceScopedPreferences.perFaceDefault(
                        "split", MiscPreferences.WEAR_MINI_BUTTONS_MODE.key))
        // ...and Chat keeps its other override, so this is a targeted change rather than the face
        // falling out of the per-face table altogether.
        assertEquals(
                "false",
                FaceScopedPreferences.perFaceDefault(
                        "chat", MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE.key))
    }

    /**
     * Split's badge is the element that makes its composition read as a notification card, so it
     * defaults on for that face even if the user turned the icon off globally. This is the only
     * per-face default that *raises* a value rather than lowering one, which makes it the one most
     * likely to be lost to the resolution bug this file exists to catch.
     */
    @Test
    fun `split defaults the playing-app icon on`() {
        assertEquals(
                "true",
                FaceScopedPreferences.perFaceDefault(
                        "split", MiscPreferences.WEAR_SHOW_SOURCE_ICON.key))
    }

    /** A face with no entry must fall through, or every face would inherit another's defaults. */
    @Test
    fun `a face without a per-face default returns null`() {
        assertEquals(
                null,
                FaceScopedPreferences.perFaceDefault(
                        "classic", MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE.key))
    }
}
