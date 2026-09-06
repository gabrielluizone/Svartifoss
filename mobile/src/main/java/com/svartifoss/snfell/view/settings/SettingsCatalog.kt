package com.svartifoss.snfell.view.settings

import com.svartifoss.snfell.view.watchface.WatchFacePrefsFragment

/**
 * Which preference categories belong to which section page, for both settings screens.
 *
 * This used to live as a `when` block inside each fragment's `applySectionVisibility()`. It moved
 * here because a *second* consumer appeared: the settings search
 * ([SettingsSearchIndex]) has to answer "which page is this preference on" to be able to navigate
 * to a result, and re-deriving that from a copy would mean search silently sending people to the
 * wrong page every time a category was re-homed. One map, three readers
 * ([MiscSettingsFragment], [com.svartifoss.snfell.view.watchface.WatchFacePrefsFragment], the
 * index).
 *
 * Two structural rules, both pinned by `SettingsCatalogTest`, and both learned from real bugs:
 *
 *  - **[SETTINGS_CATEGORIES] / [WATCH_CATEGORIES] must list every category the XML declares.**
 *    That list is what the fragments iterate to *hide* the categories the current page does not
 *    want. A category missing from it is never assigned a visibility at all, so it stays visible
 *    on every page - which is how the clock's type controls ended up showing on the Style page as
 *    well as on Text.
 *  - **Every listed category must be reachable from at least one section.** One that is in the
 *    hide-loop but in no section's set is hidden on every page, i.e. dead settings the user cannot
 *    reach at all.
 *
 * A category has one conceptual home. Value-dependent categories (Metadata and Flex axes) are
 * assigned here for routing, then AND their section visibility with their runtime prerequisite.
 */
object SettingsCatalog {

    // ---- Settings tab (R.xml.settings) ----

    /** Section key -> the categories visible on that page. Keys match `MiscSettingsFragment.SECTION_*`. */
    val SETTINGS_SECTIONS: Map<String, Set<String>> = mapOf(
            MiscSettingsFragment.SECTION_GENERAL to setOf(
                    "cat_community_themes",
                    "cat_updates",
                    "cat_appearance"),
            MiscSettingsFragment.SECTION_WATCH to setOf(
                    "cat_watch_navigation",
                    "cat_rotary_input",
                    "cat_watch_feedback",
                    "cat_action_list",
                    "cat_notifications"),
            MiscSettingsFragment.SECTION_AUTOMATION to setOf("cat_automation", "cat_idle"),
            MiscSettingsFragment.SECTION_APPS to setOf(
                    "cat_apps_content",
                    "cat_streaming_services",
                    "cat_queue_artwork",
                    "cat_system_access"),
            MiscSettingsFragment.SECTION_DATA to setOf(
                    "cat_backup",
                    "cat_privacy",
                    "cat_about")
    )

    /** Every category in `settings.xml`; the fragment iterates this to hide the ones not in the
     *  current section. See the class doc for why leaving one out is a bug, not an omission. */
    val SETTINGS_CATEGORIES: List<String> = listOf(
            "cat_updates",
            "cat_appearance",
            "cat_watch_navigation",
            "cat_rotary_input",
            "cat_watch_feedback",
            "cat_action_list",
            "cat_notifications",
            "cat_automation",
            // Was missing here while being listed under Automation, so it was never assigned a
            // visibility and showed on all five Settings pages. Exactly the failure the class doc
            // describes, found by SettingsCatalogTest comparing this list against the XML.
            "cat_idle",
            "cat_apps_content",
            "cat_streaming_services",
            "cat_queue_artwork",
            "cat_system_access",
            "cat_backup",
            "cat_community_themes",
            "cat_privacy",
            "cat_about"
    )

    // ---- Watch face tab (R.xml.watch_face_settings) ----

    /** Section key -> visible categories. Keys match `WatchFacePrefsFragment.SECTION_*`. */
    val WATCH_SECTIONS: Map<String, Set<String>> = mapOf(
            WatchFacePrefsFragment.SECTION_STYLE to setOf(
                    "cat_wf_player_editor",
                    "cat_wf_screen_behavior",
                    "cat_wf_player_layout",
                    "cat_wf_player_progress",
                    "cat_wf_metadata",
                    "cat_wf_layout_actions"),
            WatchFacePrefsFragment.SECTION_BACKGROUND to setOf(
                    "cat_wf_background_editor",
                    "cat_wf_background"),
            WatchFacePrefsFragment.SECTION_COLORS to setOf(
                    "cat_wf_colors_editor",
                    "cat_wf_colors",
                    "cat_wf_colors_title",
                    "cat_wf_colors_artist",
                    "cat_wf_colors_clock"),
            WatchFacePrefsFragment.SECTION_TYPOGRAPHY to setOf(
                    "cat_wf_typography_editor",
                    "cat_wf_typography_font",
                    "cat_wf_typography_secondary",
                    "cat_wf_typography_title",
                    "cat_wf_typography_artist",
                    "cat_wf_typography_clock",
                    "cat_wf_typography_track_time",
                    "cat_wf_typography_icon",
                    "cat_wf_typography_flex"),
            WatchFacePrefsFragment.SECTION_AOD to setOf("cat_wf_aod_editor", "cat_wf_aod"),
            WatchFacePrefsFragment.SECTION_PANELS to setOf(
                    "cat_wf_panels_editor",
                    "cat_wf_panel_shared",
                    "cat_wf_panel_volume",
                    "cat_wf_panel_seek",
                    "cat_wf_panel_quick",
                    "cat_wf_panel_queue",
                    "cat_wf_panel_lyrics",
                    "cat_wf_panel_effects"),
            // Mini buttons and the screen-gesture toggles are both input controls, so they share
            // the one section.
            WatchFacePrefsFragment.SECTION_MINI_BUTTONS to setOf(
                    "cat_wf_mini_buttons_editor", "cat_wf_mini_buttons", "cat_wf_gestures")
    )

    /** Every category in `watch_face_settings.xml`. See [SETTINGS_CATEGORIES]. */
    val WATCH_CATEGORIES: List<String> = listOf(
            "cat_wf_player_editor",
            "cat_wf_screen_behavior",
            "cat_wf_player_layout",
            "cat_wf_player_progress",
            "cat_wf_metadata",
            "cat_wf_aod_editor",
            "cat_wf_aod",
            "cat_wf_panels_editor",
            "cat_wf_panel_shared",
            "cat_wf_panel_volume",
            "cat_wf_panel_seek",
            "cat_wf_panel_quick",
            "cat_wf_panel_queue",
            "cat_wf_panel_lyrics",
            "cat_wf_panel_effects",
            "cat_wf_background_editor",
            "cat_wf_background",
            "cat_wf_colors_editor",
            "cat_wf_colors",
            "cat_wf_colors_title",
            "cat_wf_colors_artist",
            "cat_wf_colors_clock",
            "cat_wf_typography_editor",
            "cat_wf_typography_font",
            "cat_wf_typography_secondary",
            "cat_wf_typography_title",
            "cat_wf_typography_clock",
            "cat_wf_typography_track_time",
            "cat_wf_typography_artist",
            "cat_wf_typography_icon",
            "cat_wf_typography_flex",
            "cat_wf_mini_buttons_editor",
            "cat_wf_mini_buttons",
            "cat_wf_gestures",
            "cat_wf_layout_actions"
    )

    /**
     * The section page a category appears on, for search navigation.
     *
     * Categories intentionally have one owner; the first-match form remains defensive for callers
     * supplying an arbitrary map.
     */
    fun sectionForCategory(sections: Map<String, Set<String>>, category: String): String? =
            sections.entries.firstOrNull { category in it.value }?.key
}
