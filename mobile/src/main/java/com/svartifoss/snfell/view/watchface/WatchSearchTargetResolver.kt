package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.MiniButtonPlacement
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.WatchTypography

/**
 * Resolves a searchable Watch appearance row to something currently visible and actionable.
 *
 * Conditional preferences stay in the search index for discoverability. When their prerequisite
 * is not active, navigation focuses that prerequisite instead of opening a page whose requested
 * row is hidden or disabled. Reads are injected so this policy remains independent of Android and
 * face-scoped storage while callers still use the correct persisted scope.
 */
internal object WatchSearchTargetResolver {

    data class Target(
            val section: String,
            val key: String,
            val redirected: Boolean)

    private val titleDependentRows = setOf(
            "wear_title_text_mode",
            "wear_title_font_weight",
            "wear_title_font_italic",
            "wear_title_font_scale",
            "wear_title_font_opacity",
            "wear_title_font_tracking",
            "wear_title_color_mode",
            "wear_title_custom_color",
            "wear_title_adaptive_contrast")

    private val artistDependentRows = setOf(
            "wear_artist_font_weight",
            "wear_artist_font_italic",
            "wear_artist_font_scale",
            "wear_artist_font_opacity",
            "wear_artist_font_tracking",
            "wear_artist_color_mode",
            "wear_artist_custom_color",
            "wear_artist_adaptive_contrast")

    private val dimDependentRows = setOf(
            "wear_player_shading_style",
            "album_art_dim_strength",
            "wear_shading_color_mode",
            "wear_shading_custom_color")

    private val internalProgressFaces = setOf(
            "vinyl", "poster", "studio", "halo", "aurora", "eclipse", "spectrum",
            "depth", "verse")

    private val visualAodKeys = setOf(
            "wear_aod_show_transport", "wear_aod_show_progress", "wear_aod_show_pills")

    // Must match WatchFacePrefsFragment.updateAodDetailVisibility. An explicit allow-list also
    // keeps removed/unknown persisted styles (notably legacy "minimal") on the safe Classic path.
    private val visualAodStyles = setOf(
            "expressive", "vinyl", "poster", "studio", "halo", "aurora", "eclipse",
            "spectrum", "material", "immersive", "depth", "carousel", "chat", "split",
            "note", "verse", "metadata")

    private val artworkAodKeys = setOf(
            "wear_aod_show_art", "wear_aod_art_treatment", "ambient_album_art_opacity")

    private val modePrerequisites = mapOf(
            "wear_aod_custom_color" to ModePrerequisite(
                    "wear_aod_color_mode", setOf("custom"), WatchFacePrefsFragment.SECTION_AOD),
            "wear_clock_custom_color" to ModePrerequisite(
                    "wear_clock_color_mode", setOf("custom"), WatchFacePrefsFragment.SECTION_COLORS),
            "wear_title_custom_color" to ModePrerequisite(
                    "wear_title_color_mode", setOf("normal", "custom"),
                    WatchFacePrefsFragment.SECTION_COLORS),
            "wear_artist_custom_color" to ModePrerequisite(
                    "wear_artist_color_mode", setOf("normal", "custom"),
                    WatchFacePrefsFragment.SECTION_COLORS),
            "wear_progress_custom_color" to ModePrerequisite(
                    "wear_progress_color_mode", setOf("normal", "custom"),
                    WatchFacePrefsFragment.SECTION_COLORS),
            "wear_volume_custom_color" to ModePrerequisite(
                    "wear_volume_color_mode", setOf("normal", "custom"),
                    WatchFacePrefsFragment.SECTION_COLORS),
            "wear_quick_panel_custom_color" to ModePrerequisite(
                    "wear_quick_panel_color_mode", setOf("normal", "custom"),
                    WatchFacePrefsFragment.SECTION_COLORS),
            "wear_shading_custom_color" to ModePrerequisite(
                    "wear_shading_color_mode", setOf("custom"),
                    WatchFacePrefsFragment.SECTION_BACKGROUND))

    fun resolve(
            section: String,
            key: String,
            readString: (key: String, default: String) -> String,
            readBoolean: (key: String, default: Boolean) -> Boolean
    ): Target {
        val face = readString("wear_screen_face", "classic")

        fun redirect(targetSection: String, targetKey: String) =
                Target(targetSection, targetKey, redirected = true)

        if (key in titleDependentRows && !readBoolean("wear_show_track_title", true)) {
            return redirect(WatchFacePrefsFragment.SECTION_TYPOGRAPHY, "wear_show_track_title")
        }
        if (key in artistDependentRows && !readBoolean("wear_show_track_artist", true)) {
            return redirect(WatchFacePrefsFragment.SECTION_TYPOGRAPHY, "wear_show_track_artist")
        }
        if (key in setOf("wear_source_icon_scale", "wear_source_icon_opacity") &&
                !readBoolean("wear_show_source_icon", true)) {
            return redirect(WatchFacePrefsFragment.SECTION_STYLE, "wear_show_source_icon")
        }
        if (key in dimDependentRows && !readBoolean("dim_album_art", true)) {
            return redirect(WatchFacePrefsFragment.SECTION_BACKGROUND, "dim_album_art")
        }

        if (key.startsWith("wear_metadata_") && face != "metadata") {
            return redirect(WatchFacePrefsFragment.SECTION_STYLE, "wear_screen_face")
        }
        if (key == "wear_expressive_seek_mode" && face != "expressive" ||
                key == "wear_carousel_card_shape" && face != "carousel" ||
                key == "wear_split_panel" && face != "split" ||
                key == "wear_quadrant_tap_flash" && face != "classic" ||
                key == "wear_classic_icons_visible" && face in setOf("expressive", "material") ||
                key == "wear_internal_progress_visible" && face !in internalProgressFaces ||
                key in setOf("screen_buttons_curve_style", "screen_buttons_shape") &&
                    MiniButtonPlacement.isHostedByFace(face)) {
            return redirect(WatchFacePrefsFragment.SECTION_STYLE, "wear_screen_face")
        }

        if (key.startsWith("wear_font_flex_") || key == "wear_flex_axes_hint") {
            if (!WatchTypography.isFlexFont(readString("wear_font", "google_sans"))) {
                return redirect(WatchFacePrefsFragment.SECTION_TYPOGRAPHY, "wear_font")
            }
        }

        if (key == "album_art_blur_radius" &&
                !PlayerBackgroundStyle.fromPreference(
                        readString("album_art_style", "cover")).usesBlurRadius) {
            return redirect(WatchFacePrefsFragment.SECTION_BACKGROUND, "album_art_style")
        }

        val edgeProgressAvailable =
                readBoolean("wear_edge_progress_visible", true) ||
                        readBoolean("wear_edge_seek_enabled", true)
        if (key in setOf("wear_progress_style", "wear_progress_gradient") &&
                !edgeProgressAvailable) {
            return redirect(WatchFacePrefsFragment.SECTION_STYLE, "wear_edge_progress_visible")
        }
        if (key == "wear_progress_gradient" &&
                readString("wear_progress_style", "solid") != "solid") {
            return redirect(WatchFacePrefsFragment.SECTION_PANELS, "wear_progress_style")
        }

        if (key == "wear_normal_color" &&
                readString("wear_color_treatment", "expressive") != "normal") {
            return redirect(WatchFacePrefsFragment.SECTION_COLORS, "wear_color_treatment")
        }

        if (key == "wear_title_adaptive_contrast" &&
                readString(
                        "wear_title_color_mode",
                        MiscPreferences.TITLE_COLOR_FACE_DEFAULT
                ) == MiscPreferences.TITLE_COLOR_FACE_DEFAULT) {
            return redirect(WatchFacePrefsFragment.SECTION_COLORS, "wear_title_color_mode")
        }

        val aodStyle = readString("wear_aod_style", "follow")
        val effectiveAod = if (aodStyle == "follow") face else aodStyle
        val visualAod = effectiveAod in visualAodStyles
        if (key in visualAodKeys && !visualAod ||
                key in artworkAodKeys && effectiveAod in setOf("chrono", "eclipse")) {
            return redirect(WatchFacePrefsFragment.SECTION_AOD, "wear_aod_style")
        }
        if (key == "wear_aod_show_progress" &&
                !readBoolean("wear_aod_show_transport", true)) {
            return redirect(WatchFacePrefsFragment.SECTION_AOD, "wear_aod_show_transport")
        }
        if (key in setOf("wear_aod_art_treatment", "ambient_album_art_opacity") &&
                !readBoolean("wear_aod_show_art", true)) {
            return redirect(WatchFacePrefsFragment.SECTION_AOD, "wear_aod_show_art")
        }

        modePrerequisites[key]?.let { prerequisite ->
            if (readString(prerequisite.key, "") !in prerequisite.visibleModes) {
                return redirect(prerequisite.section, prerequisite.key)
            }
        }

        return Target(section, key, redirected = false)
    }

    private data class ModePrerequisite(
            val key: String,
            val visibleModes: Set<String>,
            val section: String)
}
