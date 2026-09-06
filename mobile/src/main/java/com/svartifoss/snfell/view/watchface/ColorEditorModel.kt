package com.svartifoss.snfell.view.watchface

import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.common.MiscPreferences

/** The piece of watch colour currently being edited. */
internal enum class ColorTarget {
    TITLE,
    ARTIST,
    CLOCK,
    PROGRESS,
    VOLUME,
    QUICK_PANEL,
    LYRICS,
    QUEUE
}

/**
 * The editor affordance that owns a setting.
 *
 * The first six belong to the global palette card and the rest to whichever element the target
 * rail has selected. [TREATMENT] and [MODE] are deliberately distinct even though both open a
 * `ColorTreatmentPreference`: one decides the palette every element inherits, the other overrides
 * it for a single element, and Settings search has to pulse the right one.
 */
internal enum class ColorControl {
    ACCENT_SOURCE,
    TREATMENT,
    MODIFIER,
    HUE_SHIFT,
    GLOBAL_COLOR,
    PALETTE,
    MODE,
    CUSTOM_COLOR,
    /**
     * The element's own Tone. Distinct from [MODIFIER], which is the watch-wide one on the global
     * card: this overrides that for a single element, and search has to pulse the right control.
     */
    TONE,
    OPACITY,
    ADAPTIVE_CONTRAST
}

/**
 * Storage contract for one editor setting.
 *
 * [Hex] is kept apart from [Choice] on purpose: both persist a string, but a hex colour has no
 * entries/entryValues to look a label up in, so treating one as the other renders `#3F51B5` as a
 * missing list entry rather than as a swatch.
 */
internal sealed interface ColorValueSpec {
    data class Choice(val defaultValue: String) : ColorValueSpec
    data class Toggle(val defaultValue: Boolean) : ColorValueSpec
    data class Number(val defaultValue: Int, val range: IntRange) : ColorValueSpec
    data class Hex(val defaultValue: String) : ColorValueSpec
}

internal data class ColorSettingSpec(
        val key: String,
        val target: ColorTarget,
        val control: ColorControl,
        val value: ColorValueSpec
)

internal data class ColorSearchTarget(
        val target: ColorTarget,
        val control: ColorControl)

/**
 * Pure description of every row that historically lived in a `cat_wf_colors*` category.
 *
 * The sibling of [TypographyEditorModel], and for the same reason: this is presentation metadata
 * only. Preference keys, types and defaults stay owned by [MiscPreferences], so the compact editor
 * replaces the long preference list without changing a single value a saved face, custom theme,
 * backup, phone preview or watch consumes.
 *
 * Global palette controls use [ColorTarget.TITLE] as their canonical navigation target because
 * Title is the first tab on the rail. Their own [ColorControl] values are what let search focus
 * the global card instead of a title-only override.
 */
internal object ColorEditorModel {

    /**
     * 0-359 rather than 0-360: a full turn is the same hue as no turn, so allowing both would give
     * two stored values that render identically. Shared with the fragment's numeric validation so
     * the slider and the typed-entry path cannot disagree about the bound.
     */
    val HUE_SHIFT_RANGE: IntRange = 0..359

    /** The clock never goes fully transparent - that is what the Show clock switch is for. */
    val CLOCK_OPACITY_RANGE: IntRange = 10..100

    val specs: List<ColorSettingSpec> = listOf(
            choice(
                    MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE,
                    ColorTarget.TITLE,
                    ColorControl.ACCENT_SOURCE),
            choice(
                    MiscPreferences.WEAR_COLOR_TREATMENT,
                    ColorTarget.TITLE,
                    ColorControl.TREATMENT),
            choice(
                    MiscPreferences.WEAR_COLOR_MODIFIER,
                    ColorTarget.TITLE,
                    ColorControl.MODIFIER),
            number(
                    MiscPreferences.WEAR_COLOR_HUE_SHIFT,
                    ColorTarget.TITLE,
                    ColorControl.HUE_SHIFT,
                    HUE_SHIFT_RANGE),
            hex(
                    MiscPreferences.WEAR_NORMAL_COLOR,
                    ColorTarget.TITLE,
                    ColorControl.GLOBAL_COLOR),
            toggle(
                    MiscPreferences.WEAR_NORMAL_COLOR_MULTI,
                    ColorTarget.TITLE,
                    ColorControl.PALETTE),

            choice(
                    MiscPreferences.WEAR_TITLE_COLOR_MODE,
                    ColorTarget.TITLE,
                    ColorControl.MODE),
            hex(
                    MiscPreferences.WEAR_TITLE_CUSTOM_COLOR,
                    ColorTarget.TITLE,
                    ColorControl.CUSTOM_COLOR),
            choice(
                    MiscPreferences.WEAR_TITLE_COLOR_MODIFIER,
                    ColorTarget.TITLE,
                    ColorControl.TONE),
            toggle(
                    MiscPreferences.WEAR_TITLE_ADAPTIVE_CONTRAST,
                    ColorTarget.TITLE,
                    ColorControl.ADAPTIVE_CONTRAST),

            choice(
                    MiscPreferences.WEAR_ARTIST_COLOR_MODE,
                    ColorTarget.ARTIST,
                    ColorControl.MODE),
            hex(
                    MiscPreferences.WEAR_ARTIST_CUSTOM_COLOR,
                    ColorTarget.ARTIST,
                    ColorControl.CUSTOM_COLOR),
            choice(
                    MiscPreferences.WEAR_ARTIST_COLOR_MODIFIER,
                    ColorTarget.ARTIST,
                    ColorControl.TONE),
            toggle(
                    MiscPreferences.WEAR_ARTIST_ADAPTIVE_CONTRAST,
                    ColorTarget.ARTIST,
                    ColorControl.ADAPTIVE_CONTRAST),

            choice(
                    MiscPreferences.WEAR_CLOCK_COLOR_MODE,
                    ColorTarget.CLOCK,
                    ColorControl.MODE),
            hex(
                    MiscPreferences.WEAR_CLOCK_CUSTOM_COLOR,
                    ColorTarget.CLOCK,
                    ColorControl.CUSTOM_COLOR),
            number(
                    MiscPreferences.WEAR_CLOCK_OPACITY,
                    ColorTarget.CLOCK,
                    ColorControl.OPACITY,
                    CLOCK_OPACITY_RANGE),
            choice(
                    MiscPreferences.WEAR_CLOCK_COLOR_MODIFIER,
                    ColorTarget.CLOCK,
                    ColorControl.TONE),
            toggle(
                    MiscPreferences.WEAR_CLOCK_ADAPTIVE_CONTRAST,
                    ColorTarget.CLOCK,
                    ColorControl.ADAPTIVE_CONTRAST),

            choice(
                    MiscPreferences.WEAR_PROGRESS_COLOR_MODE,
                    ColorTarget.PROGRESS,
                    ColorControl.MODE),
            hex(
                    MiscPreferences.WEAR_PROGRESS_CUSTOM_COLOR,
                    ColorTarget.PROGRESS,
                    ColorControl.CUSTOM_COLOR),

            choice(
                    MiscPreferences.WEAR_VOLUME_COLOR_MODE,
                    ColorTarget.VOLUME,
                    ColorControl.MODE),
            hex(
                    MiscPreferences.WEAR_VOLUME_CUSTOM_COLOR,
                    ColorTarget.VOLUME,
                    ColorControl.CUSTOM_COLOR),

            choice(
                    MiscPreferences.WEAR_QUICK_PANEL_COLOR_MODE,
                    ColorTarget.QUICK_PANEL,
                    ColorControl.MODE),
            hex(
                    MiscPreferences.WEAR_QUICK_PANEL_CUSTOM_COLOR,
                    ColorTarget.QUICK_PANEL,
                    ColorControl.CUSTOM_COLOR),

            choice(
                    MiscPreferences.WEAR_LYRICS_COLOR_MODE,
                    ColorTarget.LYRICS,
                    ColorControl.MODE),
            hex(
                    MiscPreferences.WEAR_LYRICS_CUSTOM_COLOR,
                    ColorTarget.LYRICS,
                    ColorControl.CUSTOM_COLOR),

            choice(
                    MiscPreferences.WEAR_QUEUE_COLOR_MODE,
                    ColorTarget.QUEUE,
                    ColorControl.MODE),
            hex(
                    MiscPreferences.WEAR_QUEUE_CUSTOM_COLOR,
                    ColorTarget.QUEUE,
                    ColorControl.CUSTOM_COLOR))

    private val specsByKey: Map<String, ColorSettingSpec> =
            specs.associateBy(ColorSettingSpec::key).also { indexed ->
                check(indexed.size == specs.size) { "Color editor keys must be unique" }
            }

    val keys: Set<String> = specsByKey.keys

    fun specFor(key: String): ColorSettingSpec? = specsByKey[key]

    fun specsFor(target: ColorTarget): List<ColorSettingSpec> =
            specs.filter { it.target == target }

    /** The key owning [control] for [target], or null when that element has no such control. */
    fun keyFor(target: ColorTarget, control: ColorControl): String? =
            specsFor(target).firstOrNull { it.control == control }?.key

    /** Destination used when Settings search opens the compact editor for a legacy row key. */
    fun searchTargetFor(key: String): ColorSearchTarget? =
            specFor(key)?.let { ColorSearchTarget(it.target, it.control) }

    private fun choice(
            definition: PreferenceDefinition<String>,
            target: ColorTarget,
            control: ColorControl
    ) = ColorSettingSpec(
            definition.key,
            target,
            control,
            ColorValueSpec.Choice(definition.defaultValue))

    private fun hex(
            definition: PreferenceDefinition<String>,
            target: ColorTarget,
            control: ColorControl
    ) = ColorSettingSpec(
            definition.key,
            target,
            control,
            ColorValueSpec.Hex(definition.defaultValue))

    private fun toggle(
            definition: PreferenceDefinition<Boolean>,
            target: ColorTarget,
            control: ColorControl
    ) = ColorSettingSpec(
            definition.key,
            target,
            control,
            ColorValueSpec.Toggle(definition.defaultValue))

    private fun number(
            definition: PreferenceDefinition<Int>,
            target: ColorTarget,
            control: ColorControl,
            range: IntRange
    ) = ColorSettingSpec(
            definition.key,
            target,
            control,
            ColorValueSpec.Number(definition.defaultValue, range))
}
