package com.svartifoss.snfell.view.watchface.theme

import com.svartifoss.snfell.common.FaceScopedPreferences
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the data-only contract shared with the trusted Node publisher. */
class CommunityThemeConstraintsTest {

    @Test
    fun `only canonical public values satisfy semantic rules`() {
        assertTrue(constraints.accepts("wear_normal_color", WatchThemeValue.Text("#A1B2C3")))
        assertFalse(constraints.accepts("wear_normal_color", WatchThemeValue.Text("#a1b2c3")))
        assertFalse(constraints.accepts("wear_font", WatchThemeValue.Text("typewriter")))
        assertTrue(constraints.accepts("wear_font", WatchThemeValue.Text("atkinson_hyperlegible")))
        assertTrue(constraints.accepts("wear_title_font", WatchThemeValue.Text("dancing_script")))
        assertTrue(constraints.accepts(
                "wear_overlay_backdrop_style", WatchThemeValue.Text("transparent")))
        assertFalse(constraints.accepts("wear_overlay_backdrop_style", WatchThemeValue.Text("liquid_glass")))
        assertFalse(constraints.accepts("screen_buttons_bg_style", WatchThemeValue.Text("solid_theme")))
        assertTrue(constraints.accepts("wear_seek_style", WatchThemeValue.Text("solid_theme")))
        assertFalse(constraints.accepts("album_art_blur_radius", WatchThemeValue.Number(121)))
        assertTrue(constraints.accepts("album_art_blur_radius", WatchThemeValue.Number(120)))
    }

    @Test
    fun `legacy cinema is isolated from normal public candidates`() {
        val cinema = WatchThemeValue.Text("cinema")
        assertFalse(constraints.accepts("wear_screen_theme", cinema))
        assertTrue(constraints.accepts("wear_screen_theme", cinema, allowLegacyReadOnly = true))
    }

    @Test
    fun `applicability includes only visible face and state controls`() {
        val poster = settingsFor("poster").toMutableMap()
        assertFalse(constraints.isOriginalityApplicable(
                "wear_carousel_card_shape", poster, "poster"))
        assertFalse(constraints.isOriginalityApplicable(
                "wear_split_panel", poster, "poster"))
        assertFalse(constraints.isOriginalityApplicable(
                "wear_clock_font_weight", poster, "poster"))

        poster["always_show_time"] = WatchThemeValue.Flag(true)
        assertTrue(constraints.isOriginalityApplicable(
                "wear_clock_font_weight", poster, "poster"))

        assertFalse(constraints.isOriginalityApplicable(
                "screen_buttons_shape", settingsFor("chat"), "chat"))
        assertTrue(constraints.isOriginalityApplicable(
                "screen_buttons_shape", poster, "poster"))
    }

    private fun settingsFor(face: String): Map<String, WatchThemeValue> =
            FaceScopedPreferences.SCOPED_DEFINITIONS.associate { definition ->
                val faceValue = FaceScopedPreferences.perFaceDefault(face, definition.key)
                definition.key to when (val default = definition.defaultValue) {
                    is String -> WatchThemeValue.Text(faceValue ?: default)
                    is Boolean -> WatchThemeValue.Flag(faceValue?.toBooleanStrictOrNull() ?: default)
                    is Int -> WatchThemeValue.Number(faceValue?.toIntOrNull() ?: default)
                    else -> error("Unexpected preference type")
                }
            }

    private val constraints: CommunityThemeConstraints by lazy {
        val file = listOf(
                File("../common/src/main/assets/community-theme-constraints.json"),
                File("common/src/main/assets/community-theme-constraints.json"))
                .firstOrNull(File::isFile)
                ?: error("Could not locate community-theme constraints asset")
        requireNotNull(CommunityThemeConstraints.fromJson(JSONObject(file.readText())))
    }
}
