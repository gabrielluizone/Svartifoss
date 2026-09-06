package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.AlbumArtSource

import com.svartifoss.snfell.common.MiniButtonPlacement
import com.svartifoss.snfell.common.OverlayBackdropResolver
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
            "wear_title_font",
            "wear_title_text_mode",
            "wear_title_font_weight",
            "wear_title_font_italic",
            "wear_title_font_scale",
            "wear_title_font_opacity",
            "wear_title_font_tracking",
            "wear_title_text_case",
            "wear_title_font_flex_width",
            "wear_title_font_flex_optical_size",
            "wear_title_font_flex_grade",
            "wear_title_font_flex_roundness",
            "wear_title_color_mode",
            "wear_title_custom_color",
            "wear_title_adaptive_contrast")

    private val artistDependentRows = setOf(
            "wear_artist_font",
            "wear_artist_font_weight",
            "wear_artist_font_italic",
            "wear_artist_font_scale",
            "wear_artist_font_opacity",
            "wear_artist_font_tracking",
            "wear_artist_text_case",
            "wear_artist_font_flex_width",
            "wear_artist_font_flex_optical_size",
            "wear_artist_font_flex_grade",
            "wear_artist_font_flex_roundness",
            "wear_artist_color_mode",
            "wear_artist_custom_color",
            "wear_artist_adaptive_contrast")

    // The ambient style rules are read from AodEditorModel rather than repeated here. They used to
    // be a second copy of the list in WatchFacePrefsFragment.updateAodDetailVisibility, which is
    // exactly how a decision list goes stale silently when a face is added.

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
            // The shading rows have no entry here on purpose. They used to be gated twice -
            // on "Dim album art" and on the colour mode - and both gates pointed at rows that are
            // now read through the Background page's layer list rather than edited one at a time.
            // A redirect would have to name a control that is no longer on screen, so the search
            // result goes to the list, where every one of these values now lives.
            "wear_quick_panel_custom_color" to ModePrerequisite(
                    "wear_quick_panel_color_mode", setOf("normal", "custom"),
                    WatchFacePrefsFragment.SECTION_COLORS),
            "wear_lyrics_custom_color" to ModePrerequisite(
                    "wear_lyrics_color_mode", setOf("normal", "custom"),
                    WatchFacePrefsFragment.SECTION_COLORS),
            "wear_queue_custom_color" to ModePrerequisite(
                    "wear_queue_color_mode", setOf("normal", "custom"),
                    WatchFacePrefsFragment.SECTION_COLORS),
            // The two file pickers behind the device-local artwork sources. Each is on screen only
            // while its own source is selected, so a search that landed on one from any other
            // source would find nothing - and unlike a colour mode, the prerequisite here is also
            // the explanation: the row exists because that source needs a file named.
            MiscPreferences.CUSTOM_ALBUM_ART_IMAGE.key to ModePrerequisite(
                    MiscPreferences.WEAR_ALBUM_ART_SOURCE.key,
                    setOf(AlbumArtSource.CUSTOM_IMAGE.preferenceValue),
                    WatchFacePrefsFragment.SECTION_BACKGROUND),
            MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key to ModePrerequisite(
                    MiscPreferences.WEAR_ALBUM_ART_SOURCE.key,
                    setOf(AlbumArtSource.CUSTOM_FOLDER.preferenceValue),
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
        // The position mark rides the edge ring, so with the ring off the Player page hides its
        // row - see renderPlayerEditor. Point at the switch rather than at a control that is not
        // on screen; the redirect never writes it.
        if (key == "wear_seek_marker" && !readBoolean("wear_edge_progress_visible", true)) {
            return redirect(WatchFacePrefsFragment.SECTION_STYLE, "wear_edge_progress_visible")
        }
        if (key.startsWith("wear_metadata_") && face != "metadata") {
            return redirect(WatchFacePrefsFragment.SECTION_STYLE, "wear_screen_face")
        }
        if (key == "wear_expressive_seek_mode" && face != "expressive" ||
                key == "wear_screen_theme" && face !in PlayerEditorModel.CONTROL_STYLE_FACES ||
                key == "wear_carousel_card_shape" && face != "carousel" ||
                key == "wear_note_cover_shape" && face != "note" ||
                key == "wear_note_show_cover" && face != "note" ||
                key == "wear_title_centered" &&
                        face !in PlayerEditorModel.TITLE_CENTERED_FACES ||
                // Two rows, two allow-lists, because nine faces offer one of them and not the
                // other. One shared clause would send somebody looking for the row that *is* on
                // this face to the face picker instead.
                key == "wear_text_block_align" &&
                        face !in PlayerEditorModel.TEXT_BLOCK_ALIGN_FACES ||
                key == "wear_text_block_position" &&
                        face !in PlayerEditorModel.TEXT_BLOCK_POSITION_FACES ||
                key == "wear_chat_cover_shape" && face != "chat" ||
                key == "wear_chat_show_cover" && face != "chat" ||
                // wear_metadata_cover_shape / wear_metadata_show_cover need no entry here: the
                // "wear_metadata_" prefix check above already redirects both.
                key == "wear_split_panel" && face != "split" ||
                key == "wear_quadrant_tap_flash" && face != "classic" ||
                key == "wear_classic_icons_visible" && face in setOf("expressive", "material") ||
                key == "wear_internal_progress_visible" && face !in PlayerEditorModel.INTERNAL_PROGRESS_FACES ||
                key in setOf("screen_buttons_curve_style", "screen_buttons_shape") &&
                    MiniButtonPlacement.isHostedByFace(face)) {
            return redirect(WatchFacePrefsFragment.SECTION_STYLE, "wear_screen_face")
        }

        val flexAxesFontKey = when {
            key.startsWith("wear_font_flex_") || key == "wear_flex_axes_hint" ->
                MiscPreferences.WEAR_FONT.key
            key.startsWith("wear_title_font_flex_") -> MiscPreferences.WEAR_TITLE_FONT.key
            key.startsWith("wear_artist_font_flex_") -> MiscPreferences.WEAR_ARTIST_FONT.key
            key.startsWith("wear_clock_font_flex_") -> MiscPreferences.WEAR_CLOCK_FONT.key
            key.startsWith("wear_lyrics_font_flex_") -> MiscPreferences.WEAR_LYRICS_FONT.key
            key.startsWith("wear_track_time_font_flex_") ->
                MiscPreferences.WEAR_TRACK_TIME_FONT.key
            else -> null
        }
        if (flexAxesFontKey != null &&
                !WatchTypography.isFlexFont(readString(flexAxesFontKey, ""))) {
            return redirect(WatchFacePrefsFragment.SECTION_TYPOGRAPHY, flexAxesFontKey)
        }

        if (key == "album_art_blur_radius" &&
                !PlayerBackgroundStyle.fromPreference(
                        readString("album_art_style", "cover")).usesBlurRadius) {
            return redirect(WatchFacePrefsFragment.SECTION_BACKGROUND, "album_art_style")
        }

        val edgeProgressAvailable =
                readBoolean("wear_edge_progress_visible", true) ||
                        readBoolean("wear_edge_seek_enabled", true)
        if (key in setOf(
                        "wear_progress_style", "wear_progress_layout", "wear_progress_gradient") &&
                !edgeProgressAvailable) {
            return redirect(WatchFacePrefsFragment.SECTION_STYLE, "wear_edge_progress_visible")
        }
        if (key == "wear_progress_gradient" &&
                readString("wear_progress_style", "solid") != "solid") {
            return redirect(WatchFacePrefsFragment.SECTION_PANELS, "wear_progress_style")
        }

        // Most OverlayBackdrop treatments are solid fields or authored gradients this radius has
        // no effect on - see OverlayBackdrop.usesAlbumBlur and panelControlApplies(BLUR) in
        // WatchFacePrefsFragment, which this mirrors. Resolved through Volume's own content style
        // since the row is anchored there (see PanelEditorModel's class doc).
        if (key == "wear_overlay_blur_radius" &&
                !OverlayBackdropResolver.resolve(
                        readString("wear_overlay_backdrop_style", "follow"),
                        readString("wear_volume_style", "glass")
                ).usesAlbumBlur) {
            return redirect(WatchFacePrefsFragment.SECTION_PANELS, "wear_overlay_backdrop_style")
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

        val effectiveAod = AodEditorModel.effectiveStyle(readString("wear_aod_style", "follow"), face)
        val styleGatedAod = AodEditorModel.specs
                .firstOrNull { it.key == key && it.control in AodEditorModel.STYLE_GATED_CONTROLS }
        if (styleGatedAod != null &&
                !AodEditorModel.appliesToStyle(styleGatedAod.control, effectiveAod)) {
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
