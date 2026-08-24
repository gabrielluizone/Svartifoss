package com.svartifoss.snfell.view.watchface.theme

import com.svartifoss.snfell.common.FaceScopedPreferences
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityThemeSubmissionPreflightEvaluatorTest {

    @Test
    fun `requires twelve applicable differences from the shipped base face`() {
        val defaults = settingsFor("poster")
        val tooClose = CommunityThemeSubmissionPreflightEvaluator.evaluate(draft(defaults), constraints)
                as CommunityThemeSubmissionPreflight.InsufficientOriginality
        assertEquals(0, tooClose.changedSettings)
        assertEquals(COMMUNITY_THEME_MINIMUM_CHANGED_SETTINGS, tooClose.minimumRequired)

        val changed = defaults.toMutableMap().applyApplicableChanges()
        val accepted = CommunityThemeSubmissionPreflightEvaluator.evaluate(draft(changed), constraints)
                as CommunityThemeSubmissionPreflight.Ready
        assertEquals(COMMUNITY_THEME_MINIMUM_CHANGED_SETTINGS, accepted.changedSettings)
        assertTrue(accepted.settingsDigest.matches(Regex("sha256:[0-9a-f]{64}")))
    }

    @Test
    fun `rejects an incomplete raw draft rather than treating it as a default`() {
        val incomplete = settingsFor("poster").toMutableMap().apply {
            remove(FaceScopedPreferences.SCOPED_DEFINITIONS.first().key)
        }
        assertEquals(
                CommunityThemeSubmissionPreflight.InvalidDraft,
                CommunityThemeSubmissionPreflightEvaluator.evaluate(draft(incomplete), constraints))
    }

    @Test
    fun `irrelevant controls and gated controls do not inflate originality`() {
        val defaults = settingsFor("poster")
        val irrelevant = defaults.toMutableMap().applyIrrelevantChanges()
        val result = CommunityThemeSubmissionPreflightEvaluator.evaluate(draft(irrelevant), constraints)
                as CommunityThemeSubmissionPreflight.InsufficientOriginality
        assertEquals(0, result.changedSettings)

        assertFalse(constraints.isOriginalityApplicable(
                "wear_clock_font_weight", defaults, "poster"))
        val clockVisible = defaults.toMutableMap().apply {
            this["always_show_time"] = WatchThemeValue.Flag(true)
        }
        assertTrue(constraints.isOriginalityApplicable(
                "wear_clock_font_weight", clockVisible, "poster"))
    }

    private fun draft(settings: Map<String, WatchThemeValue>) = CommunityThemeSubmissionDraft(
            id = "22222222-2222-4222-8222-222222222222",
            name = "Poster study",
            baseFace = "poster",
            settings = settings,
            serializedProfile = "{}")

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

    private fun MutableMap<String, WatchThemeValue>.applyApplicableChanges(): MutableMap<String, WatchThemeValue> {
        put("album_art_dim_strength", WatchThemeValue.Number(81))
        put("always_show_time", WatchThemeValue.Flag(true))
        put("dim_album_art", WatchThemeValue.Flag(false))
        put("overlay_blur_radius", WatchThemeValue.Number(36))
        put("screen_buttons_opacity", WatchThemeValue.Number(99))
        put("wear_accent_floor", WatchThemeValue.Text("soft"))
        put("wear_album_accent_source", WatchThemeValue.Text("vibrant"))
        put("wear_album_art_fade", WatchThemeValue.Flag(false))
        put("wear_aod_show_clock", WatchThemeValue.Flag(false))
        put("wear_aod_show_pills", WatchThemeValue.Flag(false))
        put("wear_aod_show_progress", WatchThemeValue.Flag(false))
        put("wear_aod_show_track_info", WatchThemeValue.Flag(false))
        return this
    }

    private fun MutableMap<String, WatchThemeValue>.applyIrrelevantChanges(): MutableMap<String, WatchThemeValue> {
        put("wear_artist_desaturated", WatchThemeValue.Flag(true))
        put("wear_carousel_card_shape", WatchThemeValue.Text("square"))
        put("wear_expressive_seek_mode", WatchThemeValue.Text("edge"))
        put("wear_metadata_show_core", WatchThemeValue.Flag(false))
        put("wear_metadata_show_credits", WatchThemeValue.Flag(false))
        put("wear_metadata_show_identifiers", WatchThemeValue.Flag(true))
        put("wear_metadata_show_playback", WatchThemeValue.Flag(false))
        put("wear_metadata_show_release", WatchThemeValue.Flag(false))
        put("wear_metadata_show_technical", WatchThemeValue.Flag(false))
        put("wear_player_shading_intensity", WatchThemeValue.Text("soft"))
        put("wear_progress_desaturated", WatchThemeValue.Flag(true))
        put("wear_split_panel", WatchThemeValue.Text("solid"))
        put("wear_dynamic_accent", WatchThemeValue.Flag(false))
        put("wear_quadrant_tap_flash", WatchThemeValue.Flag(true))
        return this
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
