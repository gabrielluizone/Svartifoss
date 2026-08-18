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
 * Note a category may legitimately appear in *more than one* section: `cat_wf_typography_clock` is
 * deliberately on both Style and Text, so the rule is "at least one", never "exactly one".
 */
object SettingsCatalog {

    // ---- Settings tab (R.xml.settings) ----

    /** Section key -> the categories visible on that page. Keys match `MiscSettingsFragment.SECTION_*`. */
    val SETTINGS_SECTIONS: Map<String, Set<String>> = mapOf(
            MiscSettingsFragment.SECTION_GENERAL to setOf("cat_updates", "cat_appearance"),
            MiscSettingsFragment.SECTION_WATCH to setOf("cat_gestures", "cat_action_list", "cat_notifications"),
            MiscSettingsFragment.SECTION_AUTOMATION to setOf("cat_automation", "cat_idle"),
            MiscSettingsFragment.SECTION_APPS to setOf("cat_apps"),
            MiscSettingsFragment.SECTION_DATA to setOf("cat_backup", "cat_privacy", "cat_about")
    )

    /** Every category in `settings.xml`; the fragment iterates this to hide the ones not in the
     *  current section. See the class doc for why leaving one out is a bug, not an omission. */
    val SETTINGS_CATEGORIES: List<String> = listOf(
            "cat_updates",
            "cat_appearance",
            "cat_gestures",
            "cat_action_list",
            "cat_notifications",
            "cat_automation",
            // Was missing here while being listed under Automation, so it was never assigned a
            // visibility and showed on all five Settings pages. Exactly the failure the class doc
            // describes, found by SettingsCatalogTest comparing this list against the XML.
            "cat_idle",
            "cat_apps",
            "cat_backup",
            "cat_privacy",
            "cat_about"
    )

    // ---- Watch face tab (R.xml.watch_face_settings) ----

    /** Section key -> visible categories. Keys match `WatchFacePrefsFragment.SECTION_*`. */
    val WATCH_SECTIONS: Map<String, Set<String>> = mapOf(
            // Style also carries the clock and the layout reset / apply-to-all actions, which
            // operate on the whole face's look.
            WatchFacePrefsFragment.SECTION_STYLE to setOf(
                    "cat_wf_face", "cat_wf_typography_clock", "cat_wf_layout_actions"),
            WatchFacePrefsFragment.SECTION_BACKGROUND to setOf("cat_wf_background"),
            WatchFacePrefsFragment.SECTION_COLORS to setOf("cat_wf_colors"),
            // Text (font/title/artist/icon) is its own page, split out of Style so that screen does
            // not carry every typography control on top of the layout/behavior ones. The clock is
            // text like the title and the artist, so ALL of it lives here too - it used to be split
            // across two categories that both landed on this page, so "Clock" appeared twice and
            // neither half was complete.
            //
            // cat_wf_typography_flex is deliberately absent: its visibility depends on the *font
            // choice* as well as the section, so WatchFacePrefsFragment ANDs it in separately.
            WatchFacePrefsFragment.SECTION_TYPOGRAPHY to setOf(
                    "cat_wf_typography_font",
                    "cat_wf_typography_title",
                    "cat_wf_typography_artist",
                    "cat_wf_typography_clock",
                    "cat_wf_typography_icon"),
            WatchFacePrefsFragment.SECTION_AOD to setOf("cat_wf_aod"),
            WatchFacePrefsFragment.SECTION_PANELS to setOf("cat_wf_overlays"),
            // Mini buttons and the screen-gesture toggles are both input controls, so they share
            // the one section.
            WatchFacePrefsFragment.SECTION_MINI_BUTTONS to setOf("cat_wf_mini_buttons", "cat_wf_gestures")
    )

    /** Every category in `watch_face_settings.xml`. See [SETTINGS_CATEGORIES]. */
    val WATCH_CATEGORIES: List<String> = listOf(
            "cat_wf_face",
            "cat_wf_aod",
            "cat_wf_overlays",
            "cat_wf_background",
            "cat_wf_colors",
            "cat_wf_typography_font",
            "cat_wf_typography_title",
            "cat_wf_typography_clock",
            "cat_wf_typography_artist",
            "cat_wf_typography_icon",
            "cat_wf_mini_buttons",
            "cat_wf_gestures",
            "cat_wf_layout_actions"
    )

    /**
     * The section page a category appears on, for search navigation.
     *
     * A category on two pages resolves to the first one in [sections] iteration order, which is the
     * declared order above - so `cat_wf_typography_clock` lands on Style. Either page shows the
     * setting, so this only picks which one search opens.
     */
    fun sectionForCategory(sections: Map<String, Set<String>>, category: String): String? =
            sections.entries.firstOrNull { category in it.value }?.key
}
