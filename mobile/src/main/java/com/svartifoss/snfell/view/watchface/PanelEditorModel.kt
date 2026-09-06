package com.svartifoss.snfell.view.watchface

import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.common.MiscPreferences

/** The watch surface currently being edited. */
internal enum class PanelTarget {
    VOLUME,
    SEEK,
    QUICK_PANEL,
    QUEUE,
    LYRICS
}

/**
 * The editor affordance that owns a setting.
 *
 * One entry per distinct role rather than one per key, so a single view can serve every target the
 * way [STYLE] does. The Seek tab is why [RING_STYLE] is separate from [STYLE]: the resting progress
 * ring and the seek overlay are two different surfaces that happen to share a settings group.
 */
internal enum class PanelControl {
    /** The page-wide *Shared panel appearance* background. */
    BACKDROP,
    /** One surface's own background, which may defer to [BACKDROP]. */
    SURFACE_BACKDROP,
    BLUR,
    RING_STYLE,
    RING_LAYOUT,
    RING_GRADIENT,
    STYLE,
    LAYOUT,
    ROW_SIZE,
    UP_NEXT,
    UP_NEXT_STYLE,
    SOURCE,
    SHORTCUT_COVER,
    REMOTE_ARTWORK,
    OPEN_NOTE,
    SHORTCUTS
}

/**
 * Storage contract for one editor setting.
 *
 * [Information] and [Action] both persist nothing, and are kept apart because the editor treats
 * them differently: one is a line of explanatory text, the other opens another screen.
 */
internal sealed interface PanelValueSpec {
    data class Choice(val defaultValue: String) : PanelValueSpec
    data class Toggle(val defaultValue: Boolean) : PanelValueSpec

    /** Explanatory UI with a searchable key, but no value in SharedPreferences. */
    data object Information : PanelValueSpec

    /** A row that opens another screen rather than holding a value. */
    data object Action : PanelValueSpec
}

internal data class PanelSettingSpec(
        val key: String,
        val target: PanelTarget,
        val control: PanelControl,
        val value: PanelValueSpec
) {
    val persisted: Boolean
        get() = value is PanelValueSpec.Choice || value is PanelValueSpec.Toggle
}

internal data class PanelSearchTarget(
        val target: PanelTarget,
        val control: PanelControl)

/**
 * Pure description of every row that historically lived in a `cat_wf_panel_*` category.
 *
 * The third of the sibling set with [TypographyEditorModel] and [ColorEditorModel], on the same
 * terms: presentation metadata only, with keys, types and defaults still owned by
 * [MiscPreferences], so the compact editor changes what the page looks like and nothing about what
 * a saved face, custom theme, backup, preview or watch reads.
 *
 * Two of these keys are deliberately **not** face-scoped and that is not an oversight:
 * `wear_quick_panel_source` drives a phone-side binding with no per-face notion, and
 * `queue_remote_artwork` is a network toggle declared on two screens at once. Both are listed in
 * `AppearancePreferenceScopingTest.DELIBERATELY_GLOBAL`, and [globalKeys] names them here so the
 * editor's own test can assert the split rather than assume it.
 *
 * The global backdrop and blur controls use [PanelTarget.VOLUME] as their canonical navigation
 * target because Volume is the first tab on the rail.
 */
internal object PanelEditorModel {

    /** Keys that are page-wide rather than per-face. See the class doc. */
    val globalKeys: Set<String> = setOf(
            MiscPreferences.WEAR_QUICK_PANEL_SOURCE.key,
            "queue_remote_artwork")

    val specs: List<PanelSettingSpec> = listOf(
            choice(
                    MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE,
                    PanelTarget.VOLUME,
                    PanelControl.BACKDROP),
            // Deliberately not a slider, unlike the Color page's hue and opacity: this radius has
            // no validated bound anywhere, so the editor opens the real numeric field rather than
            // inventing a range the typed path would then disagree with.
            PanelSettingSpec(
                    MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS.key,
                    PanelTarget.VOLUME,
                    PanelControl.BLUR,
                    PanelValueSpec.Choice(
                            MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS.defaultValue.toString())),

            choice(
                    MiscPreferences.WEAR_VOLUME_STYLE,
                    PanelTarget.VOLUME,
                    PanelControl.STYLE),
            choice(
                    MiscPreferences.WEAR_VOLUME_LAYOUT,
                    PanelTarget.VOLUME,
                    PanelControl.LAYOUT),

            choice(
                    MiscPreferences.WEAR_PROGRESS_STYLE,
                    PanelTarget.SEEK,
                    PanelControl.RING_STYLE),
            choice(
                    MiscPreferences.WEAR_PROGRESS_LAYOUT,
                    PanelTarget.SEEK,
                    PanelControl.RING_LAYOUT),
            toggle(
                    MiscPreferences.WEAR_PROGRESS_GRADIENT,
                    PanelTarget.SEEK,
                    PanelControl.RING_GRADIENT),
            choice(
                    MiscPreferences.WEAR_SEEK_STYLE,
                    PanelTarget.SEEK,
                    PanelControl.STYLE),
            choice(
                    MiscPreferences.WEAR_SEEK_LAYOUT,
                    PanelTarget.SEEK,
                    PanelControl.LAYOUT),

            choice(
                    MiscPreferences.WEAR_QUICK_PANEL_STYLE,
                    PanelTarget.QUICK_PANEL,
                    PanelControl.STYLE),
            choice(
                    MiscPreferences.WEAR_QUICK_PANEL_LAYOUT,
                    PanelTarget.QUICK_PANEL,
                    PanelControl.LAYOUT),
            toggle(
                    MiscPreferences.WEAR_SHOW_UP_NEXT_PILL,
                    PanelTarget.QUICK_PANEL,
                    PanelControl.UP_NEXT),
            choice(
                    MiscPreferences.WEAR_UP_NEXT_PILL_STYLE,
                    PanelTarget.QUICK_PANEL,
                    PanelControl.UP_NEXT_STYLE),
            choice(
                    MiscPreferences.WEAR_QUICK_PANEL_SOURCE,
                    PanelTarget.QUICK_PANEL,
                    PanelControl.SOURCE),
            toggle(
                    MiscPreferences.WEAR_QUICK_PANEL_SHORTCUT_COVER,
                    PanelTarget.QUICK_PANEL,
                    PanelControl.SHORTCUT_COVER),
            PanelSettingSpec(
                    "quick_panel_open_note",
                    PanelTarget.QUICK_PANEL,
                    PanelControl.OPEN_NOTE,
                    PanelValueSpec.Information),
            PanelSettingSpec(
                    "watch_streaming_shortcuts",
                    PanelTarget.QUICK_PANEL,
                    PanelControl.SHORTCUTS,
                    PanelValueSpec.Action),

            choice(
                    MiscPreferences.WEAR_QUEUE_STYLE,
                    PanelTarget.QUEUE,
                    PanelControl.STYLE),
            choice(
                    MiscPreferences.WEAR_LIST_ROW_SIZE,
                    PanelTarget.QUEUE,
                    PanelControl.ROW_SIZE),
            PanelSettingSpec(
                    "queue_remote_artwork",
                    PanelTarget.QUEUE,
                    PanelControl.REMOTE_ARTWORK,
                    PanelValueSpec.Toggle(true)),

            // Each surface may keep following the shared background above or name its own.
            choice(
                    MiscPreferences.WEAR_VOLUME_BACKDROP_STYLE,
                    PanelTarget.VOLUME,
                    PanelControl.SURFACE_BACKDROP),
            choice(
                    MiscPreferences.WEAR_PROGRESS_BACKDROP_STYLE,
                    PanelTarget.SEEK,
                    PanelControl.SURFACE_BACKDROP),
            choice(
                    MiscPreferences.WEAR_QUICK_PANEL_BACKDROP_STYLE,
                    PanelTarget.QUICK_PANEL,
                    PanelControl.SURFACE_BACKDROP),
            choice(
                    MiscPreferences.WEAR_QUEUE_BACKDROP_STYLE,
                    PanelTarget.QUEUE,
                    PanelControl.SURFACE_BACKDROP),
            choice(
                    MiscPreferences.WEAR_LYRICS_BACKDROP_STYLE,
                    PanelTarget.LYRICS,
                    PanelControl.SURFACE_BACKDROP))

    private val specsByKey: Map<String, PanelSettingSpec> =
            specs.associateBy(PanelSettingSpec::key).also { indexed ->
                check(indexed.size == specs.size) { "Panel editor keys must be unique" }
            }

    val keys: Set<String> = specsByKey.keys

    fun specFor(key: String): PanelSettingSpec? = specsByKey[key]

    fun specsFor(target: PanelTarget): List<PanelSettingSpec> =
            specs.filter { it.target == target }

    /** The key owning [control] for [target], or null when that surface has no such control. */
    fun keyFor(target: PanelTarget, control: PanelControl): String? =
            specsFor(target).firstOrNull { it.control == control }?.key

    /** Destination used when Settings search opens the compact editor for a legacy row key. */
    fun searchTargetFor(key: String): PanelSearchTarget? =
            specFor(key)?.let { PanelSearchTarget(it.target, it.control) }

    private fun choice(
            definition: PreferenceDefinition<String>,
            target: PanelTarget,
            control: PanelControl
    ) = PanelSettingSpec(
            definition.key,
            target,
            control,
            PanelValueSpec.Choice(definition.defaultValue))

    private fun toggle(
            definition: PreferenceDefinition<Boolean>,
            target: PanelTarget,
            control: PanelControl
    ) = PanelSettingSpec(
            definition.key,
            target,
            control,
            PanelValueSpec.Toggle(definition.defaultValue))
}
