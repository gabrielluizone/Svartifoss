package com.svartifoss.snfell.res

import com.svartifoss.snfell.common.FaceScopedPreferences
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Every control on the Watch face screen must be per-face, or changing it on one face silently
 * changes it on all of them.
 *
 * The screen *is* the appearance surface, so "declared here" is the working definition of "this is
 * appearance". A key that lands in `watch_face_settings.xml` without also being in
 * [FaceScopedPreferences.SCOPED_KEYS] is written unscoped by the data store, and the user only
 * finds out by switching faces and watching a setting follow them - which is how the mini-button
 * row offset stayed global while its four siblings were scoped.
 *
 * [DELIBERATELY_GLOBAL] is the escape hatch, and every entry in it has to earn its place: these are
 * screens that are not faces, phone-side bindings, or the face selector itself. Adding to that set
 * is the moment to think, which is the point of listing them here rather than in a comment.
 */
class AppearancePreferenceScopingTest {

    private companion object {
        val DELIBERATELY_GLOBAL = setOf(
                // The face selector - scoping it by face would be circular.
                "wear_screen_face",
                // Drives a phone-side binding (whether notification media actions are mirrored),
                // which has no per-face notion.
                "wear_quick_panel_source",
                // A network toggle, and the one row deliberately declared on two screens.
                "queue_remote_artwork"
        )

        /** Rows that are buttons, links or explanatory text rather than stored settings. */
        val NON_PREFERENCE_ROWS = setOf(
                "apply_appearance_to_all_faces",
                "reset_appearance",
                "reset_all_faces",
                "watch_streaming_shortcuts",
                "quick_panel_open_note",
                "screen_buttons_hint",
                "wear_flex_axes_hint"
        )
    }

    @Test
    fun everyWatchFaceSettingIsPerFace() {
        val keys = declaredKeys()
        assertTrue("watch_face_settings.xml should declare keys", keys.size > 50)

        val leaking = keys
                .filterNot { it.startsWith("cat_") }
                .filterNot { it in NON_PREFERENCE_ROWS }
                .filterNot { it in DELIBERATELY_GLOBAL }
                .filterNot { FaceScopedPreferences.isScoped(it) }

        if (leaking.isNotEmpty()) {
            fail("Appearance settings that are NOT per-face - changing one of these on a face " +
                    "changes it on every face:\n  " + leaking.sorted().joinToString("\n  ") +
                    "\nAdd them to FaceScopedPreferences.SCOPED_KEYS, or to this test's " +
                    "DELIBERATELY_GLOBAL set with a reason.")
        }
    }

    /**
     * Keys that style one visual element must all agree on being per-face.
     *
     * This is the rule the screen-based test above cannot enforce, because an appearance key does
     * not have to live on the Watch face screen: `screen_buttons_bottom_offset` sits in developer
     * settings, so it was invisible to that check while its four siblings
     * (`screen_buttons_bg_style` / `_curve_style` / `_opacity` / `_shape`) were all scoped. One
     * family, split down the middle - which is exactly how a setting ends up following the user
     * from face to face while the ones next to it do not.
     */
    @Test
    fun eachAppearanceFamilyIsScopedConsistently() {
        val families = listOf(
                "screen_buttons_",
                "wear_clock_",
                "wear_title_font_",
                "wear_artist_font_",
                "wear_source_icon_",
                "wear_aod_",
                "wear_font_flex_"
        )
        val problems = mutableListOf<String>()
        families.forEach { prefix ->
            val members = FaceScopedPreferences.SCOPED_KEYS.filter { it.startsWith(prefix) } +
                    knownKeys().filter { it.startsWith(prefix) }
            // Hint/button rows carry a key but store nothing, so they are not part of the family.
            val distinct = members.distinct()
                    .filterNot { it in NON_PREFERENCE_ROWS || it in DELIBERATELY_GLOBAL }
            val unscoped = distinct.filterNot { FaceScopedPreferences.isScoped(it) }
            if (unscoped.isNotEmpty() && distinct.size != unscoped.size) {
                problems += "$prefix*: ${unscoped.sorted()} are global while the rest of the " +
                        "family is per-face"
            }
        }
        if (problems.isNotEmpty()) {
            fail("Appearance families split between per-face and global:\n  " +
                    problems.joinToString("\n  "))
        }
    }

    /**
     * Every preference key the app defines, read from MiscPreferences rather than from the settings
     * XML.
     *
     * MiscPreferences is the only complete source: `screen_buttons_bottom_offset` - the key this
     * family check exists for - has no row in either settings screen at all (it is set from the
     * developer menu), so an XML-based sweep could never see it.
     */
    private fun knownKeys(): Set<String> {
        val file = listOf(
                File("../common/src/main/java/com/svartifoss/snfell/common/MiscPreferences.kt"),
                File("common/src/main/java/com/svartifoss/snfell/common/MiscPreferences.kt")
        ).first { it.exists() }
        return Regex("""PreferenceDefinition\("([a-z_0-9]+)"""")
                .findAll(file.readText())
                .map { it.groupValues[1] }
                .toSet()
    }

    /** Keeps the escape hatch honest: a stale entry here would hide a real leak later. */
    @Test
    fun everyDeliberatelyGlobalKeyStillExistsAndIsStillUnscoped() {
        val keys = declaredKeys()
        DELIBERATELY_GLOBAL.forEach { key ->
            assertTrue(
                    "$key is listed as deliberately global but no longer appears on the screen",
                    key in keys)
            assertTrue(
                    "$key is listed as deliberately global but is now scoped - drop it from the set",
                    !FaceScopedPreferences.isScoped(key))
        }
    }

    private fun declaredKeys(): Set<String> {
        val file = listOf(
                File("src/main/res/xml/watch_face_settings.xml"),
                File("mobile/src/main/res/xml/watch_face_settings.xml")
        ).first { it.exists() }
        return Regex("""android:key="([a-z_0-9]+)"""")
                .findAll(file.readText())
                .map { it.groupValues[1] }
                .toSet()
    }
}
