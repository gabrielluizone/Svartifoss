package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.WatchTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSearchTargetResolverTest {

    @Test
    fun `ordinary visible row keeps its indexed destination`() {
        val target = resolve(WatchFacePrefsFragment.SECTION_COLORS, "wear_color_modifier")

        assertEquals(WatchFacePrefsFragment.SECTION_COLORS, target.section)
        assertEquals("wear_color_modifier", target.key)
        assertFalse(target.redirected)
    }

    @Test
    fun `disabled title and artist rows lead to their Typography switches`() {
        val titleKeys = listOf(
                "wear_title_font",
                "wear_title_text_mode",
                "wear_title_font_weight",
                "wear_title_font_flex_width",
                "wear_title_color_mode",
                "wear_title_custom_color",
                "wear_title_adaptive_contrast")
        titleKeys.forEach { key ->
            assertRedirect(
                    resolve(key = key, booleans = mapOf("wear_show_track_title" to false)),
                    WatchFacePrefsFragment.SECTION_TYPOGRAPHY,
                    "wear_show_track_title")
        }

        val artistKeys = listOf(
                "wear_artist_font",
                "wear_artist_font_scale",
                "wear_artist_font_tracking",
                "wear_artist_font_flex_grade",
                "wear_artist_color_mode",
                "wear_artist_custom_color",
                "wear_artist_adaptive_contrast")
        artistKeys.forEach { key ->
            assertRedirect(
                    resolve(key = key, booleans = mapOf("wear_show_track_artist" to false)),
                    WatchFacePrefsFragment.SECTION_TYPOGRAPHY,
                    "wear_show_track_artist")
        }
    }

    @Test
    fun `disabled source icon settings lead to the Player switch`() {
        listOf("wear_source_icon_scale", "wear_source_icon_opacity").forEach { key ->
            assertRedirect(
                    resolve(key = key, booleans = mapOf("wear_show_source_icon" to false)),
                    WatchFacePrefsFragment.SECTION_STYLE,
                    "wear_show_source_icon")
            assertFalse(resolve(
                    key = key,
                    booleans = mapOf("wear_show_source_icon" to true)).redirected)
        }
    }

    /**
     * The shading rows are reached through the Background page's layer list now, so neither of
     * the two gates they used to carry can be answered by pointing at a row: "Dim album art" and
     * the shading colour mode are both read through that list themselves. Pointing a user at a
     * control that is no longer on screen is worse than landing them on the list, which is where
     * the setting actually is - and where an explicit stack ignores `dim_album_art` outright.
     */
    @Test
    fun `shading rows are never redirected away from the background page`() {
        listOf(
                "wear_player_shading_style",
                "album_art_dim_strength",
                "wear_shading_color_mode",
                "wear_shading_custom_color").forEach { key ->
            assertFalse(
                    "$key should reach the layer list rather than a prerequisite row",
                    resolve(key = key, booleans = mapOf("dim_album_art" to false)).redirected)
        }
    }

    @Test
    fun `title adaptive contrast leads to color mode until a derived color is active`() {
        assertRedirect(resolve(
                key = "wear_title_adaptive_contrast",
                strings = mapOf("wear_title_color_mode" to "face"),
                booleans = mapOf("wear_show_track_title" to true)),
                WatchFacePrefsFragment.SECTION_COLORS,
                "wear_title_color_mode")

        assertFalse(resolve(
                key = "wear_title_adaptive_contrast",
                strings = mapOf("wear_title_color_mode" to "expressive"),
                booleans = mapOf("wear_show_track_title" to true)).redirected)
    }

    @Test
    fun `face specific rows lead to the face picker when unavailable`() {
        val unavailable = listOf(
                "wear_metadata_album" to "classic",
                "wear_expressive_seek_mode" to "classic",
                "wear_carousel_card_shape" to "classic",
                "wear_split_panel" to "classic",
                "wear_quadrant_tap_flash" to "expressive",
                "wear_classic_icons_visible" to "material",
                "wear_internal_progress_visible" to "classic",
                "screen_buttons_curve_style" to "chat",
                "screen_buttons_shape" to "chat")

        unavailable.forEach { (key, face) ->
            assertRedirect(
                    resolve(key = key, strings = mapOf("wear_screen_face" to face)),
                    WatchFacePrefsFragment.SECTION_STYLE,
                    "wear_screen_face")
        }
    }

    @Test
    fun `face specific rows stay direct on compatible faces`() {
        val available = listOf(
                "wear_metadata_album" to "metadata",
                "wear_expressive_seek_mode" to "expressive",
                "wear_carousel_card_shape" to "carousel",
                "wear_split_panel" to "split",
                "wear_quadrant_tap_flash" to "classic",
                "wear_classic_icons_visible" to "classic",
                "wear_internal_progress_visible" to "vinyl",
                "wear_internal_progress_visible" to "depth",
                "wear_internal_progress_visible" to "verse",
                "screen_buttons_curve_style" to "classic",
                "screen_buttons_shape" to "classic")

        available.forEach { (key, face) ->
            assertFalse(resolve(key = key, strings = mapOf("wear_screen_face" to face)).redirected)
        }
    }

    @Test
    fun `global Flex axes require the global font to use Flex`() {
        listOf(
                "wear_flex_axes_hint",
                "wear_font_flex_width",
                "wear_font_flex_optical_size",
                "wear_font_flex_grade",
                "wear_font_flex_roundness").forEach { key ->
            assertRedirect(
                    resolve(
                            key = key,
                            strings = mapOf(
                                    "wear_font" to "google_sans",
                                    "wear_clock_font" to WatchTypography.FLEX_FONT_KEY,
                                    "wear_lyrics_font" to WatchTypography.FLEX_FONT_KEY,
                                    "wear_track_time_font" to WatchTypography.FLEX_FONT_KEY)),
                    WatchFacePrefsFragment.SECTION_TYPOGRAPHY,
                    "wear_font")
            assertFalse(resolve(
                    key = key,
                    strings = mapOf("wear_font" to WatchTypography.FLEX_FONT_KEY)).redirected)
        }
    }

    @Test
    fun `individual Flex axes require Flex on their own font override`() {
        val axisFamilies = listOf(
                "wear_title_font_flex_" to "wear_title_font",
                "wear_artist_font_flex_" to "wear_artist_font",
                "wear_clock_font_flex_" to "wear_clock_font",
                "wear_lyrics_font_flex_" to "wear_lyrics_font",
                "wear_track_time_font_flex_" to "wear_track_time_font")
        val axes = listOf("width", "optical_size", "grade", "roundness")

        axisFamilies.forEach { (prefix, fontKey) ->
            axes.forEach { axis ->
                val key = "$prefix$axis"
                assertRedirect(
                        resolve(
                                key = key,
                                strings = mapOf(
                                        "wear_font" to WatchTypography.FLEX_FONT_KEY,
                                        fontKey to "google_sans")),
                        WatchFacePrefsFragment.SECTION_TYPOGRAPHY,
                        fontKey)
                assertFalse(resolve(
                        key = key,
                        strings = mapOf(fontKey to WatchTypography.FLEX_FONT_KEY)).redirected)
            }
        }
    }

    @Test
    fun `blur radius leads to background style unless selected treatment uses blur`() {
        assertRedirect(
                resolve(
                        section = WatchFacePrefsFragment.SECTION_BACKGROUND,
                        key = "album_art_blur_radius",
                        strings = mapOf("album_art_style" to "cover")),
                WatchFacePrefsFragment.SECTION_BACKGROUND,
                "album_art_style")
        assertFalse(resolve(
                section = WatchFacePrefsFragment.SECTION_BACKGROUND,
                key = "album_art_blur_radius",
                strings = mapOf("album_art_style" to "frosted")).redirected)
    }

    @Test
    fun `progress appearance leads to edge progress control when both edge features are off`() {
        val unavailable = mapOf(
                "wear_edge_progress_visible" to false,
                "wear_edge_seek_enabled" to false)

        listOf("wear_progress_style", "wear_progress_gradient").forEach { key ->
            assertRedirect(
                    resolve(key = key, booleans = unavailable),
                    WatchFacePrefsFragment.SECTION_STYLE,
                    "wear_edge_progress_visible")
        }

        assertFalse(resolve(
                key = "wear_progress_style",
                booleans = unavailable + ("wear_edge_seek_enabled" to true)).redirected)
    }

    @Test
    fun `gradient leads to progress style when ring is not solid`() {
        assertRedirect(
                resolve(
                        key = "wear_progress_gradient",
                        strings = mapOf("wear_progress_style" to "segmented")),
                WatchFacePrefsFragment.SECTION_PANELS,
                "wear_progress_style")
        assertFalse(resolve(
                key = "wear_progress_gradient",
                strings = mapOf("wear_progress_style" to "solid")).redirected)
    }

    @Test
    fun `overlay blur leads to backdrop style unless the resolved backdrop uses it`() {
        // "solid_black" never samples the blurred cover, whatever surface it resolves through.
        assertRedirect(
                resolve(
                        key = "wear_overlay_blur_radius",
                        strings = mapOf("wear_overlay_backdrop_style" to "black")),
                WatchFacePrefsFragment.SECTION_PANELS,
                "wear_overlay_backdrop_style")
        // "glass" always does.
        assertFalse(resolve(
                key = "wear_overlay_blur_radius",
                strings = mapOf("wear_overlay_backdrop_style" to "glass")).redirected)
        // "follow" resolves through Volume's own content style - a glass-family style there
        // means the backdrop it follows into does use the blur.
        assertFalse(resolve(
                key = "wear_overlay_blur_radius",
                strings = mapOf(
                        "wear_overlay_backdrop_style" to "follow",
                        "wear_volume_style" to "glass")).redirected)
        assertRedirect(
                resolve(
                        key = "wear_overlay_blur_radius",
                        strings = mapOf(
                                "wear_overlay_backdrop_style" to "follow",
                                "wear_volume_style" to "tonal")),
                WatchFacePrefsFragment.SECTION_PANELS,
                "wear_overlay_backdrop_style")
    }

    @Test
    fun `normal color leads to color treatment unless Normal is active`() {
        assertRedirect(
                resolve(
                        section = WatchFacePrefsFragment.SECTION_COLORS,
                        key = "wear_normal_color",
                        strings = mapOf("wear_color_treatment" to "expressive")),
                WatchFacePrefsFragment.SECTION_COLORS,
                "wear_color_treatment")
        assertFalse(resolve(
                section = WatchFacePrefsFragment.SECTION_COLORS,
                key = "wear_normal_color",
                strings = mapOf("wear_color_treatment" to "normal")).redirected)
    }

    @Test
    fun `AOD details lead to AOD style when renderer cannot show them`() {
        listOf("wear_aod_show_transport", "wear_aod_show_progress", "wear_aod_show_pills")
                .forEach { key ->
                    assertRedirect(
                            resolve(key = key, strings = mapOf("wear_screen_face" to "classic")),
                            WatchFacePrefsFragment.SECTION_AOD,
                            "wear_aod_style")
                }
        listOf("wear_aod_show_art", "wear_aod_art_treatment", "ambient_album_art_opacity")
                .forEach { key ->
                    assertRedirect(
                            resolve(key = key, strings = mapOf("wear_aod_style" to "eclipse")),
                            WatchFacePrefsFragment.SECTION_AOD,
                            "wear_aod_style")
                }

        assertFalse(resolve(
                key = "wear_aod_show_transport",
                strings = mapOf("wear_aod_style" to "expressive")).redirected)
        assertFalse(resolve(
                key = "wear_aod_show_art",
                strings = mapOf("wear_aod_style" to "classic")).redirected)
    }

    @Test
    fun `ribbon and frame keep visual AOD controls on their own rows`() {
        listOf("ribbon", "frame").forEach { face ->
            listOf(
                    "wear_aod_show_transport",
                    "wear_aod_show_progress",
                    "wear_aod_show_pills").forEach { key ->
                assertFalse(
                        "$face should support $key when its AOD style follows the face",
                        resolve(
                                key = key,
                                strings = mapOf("wear_screen_face" to face)).redirected)
            }
        }
    }

    @Test
    fun `disabled AOD details lead to their local switches`() {
        assertRedirect(
                resolve(
                        key = "wear_aod_show_progress",
                        strings = mapOf("wear_aod_style" to "expressive"),
                        booleans = mapOf("wear_aod_show_transport" to false)),
                WatchFacePrefsFragment.SECTION_AOD,
                "wear_aod_show_transport")

        listOf("wear_aod_art_treatment", "ambient_album_art_opacity").forEach { key ->
            assertRedirect(
                    resolve(
                            key = key,
                            strings = mapOf("wear_aod_style" to "classic"),
                            booleans = mapOf("wear_aod_show_art" to false)),
                    WatchFacePrefsFragment.SECTION_AOD,
                    "wear_aod_show_art")
        }
    }

    @Test
    fun `legacy minimal AOD uses Classic capability routing`() {
        assertRedirect(
                resolve(
                        key = "wear_aod_show_transport",
                        strings = mapOf("wear_aod_style" to "minimal")),
                WatchFacePrefsFragment.SECTION_AOD,
                "wear_aod_style")
        assertFalse(resolve(
                key = "wear_aod_show_art",
                strings = mapOf("wear_aod_style" to "minimal")).redirected)
    }

    @Test
    fun `conditional custom colors lead to their mode selectors`() {
        val prerequisites = listOf(
                Triple("wear_aod_custom_color", "wear_aod_color_mode",
                    WatchFacePrefsFragment.SECTION_AOD),
                Triple("wear_clock_custom_color", "wear_clock_color_mode",
                    WatchFacePrefsFragment.SECTION_COLORS),
                Triple("wear_title_custom_color", "wear_title_color_mode",
                    WatchFacePrefsFragment.SECTION_COLORS),
                Triple("wear_artist_custom_color", "wear_artist_color_mode",
                    WatchFacePrefsFragment.SECTION_COLORS),
                Triple("wear_progress_custom_color", "wear_progress_color_mode",
                    WatchFacePrefsFragment.SECTION_COLORS),
                Triple("wear_volume_custom_color", "wear_volume_color_mode",
                    WatchFacePrefsFragment.SECTION_COLORS),
                Triple("wear_quick_panel_custom_color", "wear_quick_panel_color_mode",
                    WatchFacePrefsFragment.SECTION_COLORS))

        prerequisites.forEach { (key, modeKey, section) ->
            assertRedirect(resolve(key = key), section, modeKey)
            assertFalse(resolve(key = key, strings = mapOf(modeKey to "custom")).redirected)
        }
    }

    @Test
    fun `disabled title wins before its custom color mode prerequisite`() {
        assertRedirect(
                resolve(
                        key = "wear_title_custom_color",
                        strings = mapOf("wear_title_color_mode" to "follow"),
                        booleans = mapOf("wear_show_track_title" to false)),
                WatchFacePrefsFragment.SECTION_TYPOGRAPHY,
                "wear_show_track_title")
    }

    private fun resolve(
            section: String = WatchFacePrefsFragment.SECTION_COLORS,
            key: String,
            strings: Map<String, String> = emptyMap(),
            booleans: Map<String, Boolean> = emptyMap()
    ): WatchSearchTargetResolver.Target = WatchSearchTargetResolver.resolve(
            section = section,
            key = key,
            readString = { preferenceKey, default -> strings[preferenceKey] ?: default },
            readBoolean = { preferenceKey, default -> booleans[preferenceKey] ?: default })

    private fun assertRedirect(
            actual: WatchSearchTargetResolver.Target,
            expectedSection: String,
            expectedKey: String
    ) {
        assertTrue(actual.redirected)
        assertEquals(expectedSection, actual.section)
        assertEquals(expectedKey, actual.key)
    }
}
