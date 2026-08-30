package com.svartifoss.snfell.view.watchface

import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.WatchTypography

/** The piece of watch text currently being edited. */
internal enum class TypographyTarget {
    TITLE,
    ARTIST,
    TRACK_TIME,
    CLOCK,
    ICON,
    LYRICS
}

/**
 * The editor affordance that owns a setting.
 *
 * [FONT], [FONT_SCOPE], [ELEMENT_FONT], [GLOBAL_FLEX] and [FLEX] are intentionally distinct: the
 * first two own the global-font card, [ELEMENT_FONT] owns the selected text element's override,
 * and the two Flex controls distinguish global axes from an explicit element override. Keeping
 * them separate lets Settings search pulse the right button even on the Title tab, where both
 * controls are visible.
 */
internal enum class TypographyControl {
    FONT,
    FONT_SCOPE,
    ELEMENT_FONT,
    VISIBILITY,
    TEXT_BEHAVIOR,
    WEIGHT,
    ITALIC,
    SIZE,
    OPACITY,
    TRACKING,
    CASE,
    GLOBAL_FLEX,
    FLEX
}

/**
 * Storage contract for one editor setting. Keeping each value kind typed makes it impossible for
 * a toggle default or numeric range to be accidentally interpreted as a list value by the UI.
 */
internal sealed interface TypographyValueSpec {
    data class Choice(val defaultValue: String) : TypographyValueSpec
    data class Toggle(val defaultValue: Boolean) : TypographyValueSpec
    data class Number(val defaultValue: Int, val range: IntRange) : TypographyValueSpec

    /** Explanatory UI with a searchable key, but no value in SharedPreferences. */
    data object Information : TypographyValueSpec
}

internal data class TypographySettingSpec(
        val key: String,
        val target: TypographyTarget,
        val control: TypographyControl,
        val value: TypographyValueSpec
) {
    val persisted: Boolean get() = value !is TypographyValueSpec.Information
}

internal data class TypographySearchTarget(
        val target: TypographyTarget,
        val control: TypographyControl)

/**
 * Pure description of every row that historically lived in a `cat_wf_typography_*` category.
 *
 * This is presentation metadata only: preference keys, types and defaults remain owned by
 * [MiscPreferences], while numeric bounds remain the same rendering bounds used by
 * [WatchTypography]. The editor can therefore replace the long preference list without changing
 * the values a saved face, custom theme, backup, phone preview or watch already consumes.
 *
 * Global font and Flex controls use [TypographyTarget.TITLE] as their canonical navigation target
 * because Title is the first editor tab. Their distinct [TypographyControl] values let search
 * focus the global-font card instead of a title-only formatting button.
 */
internal object TypographyEditorModel {

    val specs: List<TypographySettingSpec> = listOf(
            choice(MiscPreferences.WEAR_FONT, TypographyTarget.TITLE, TypographyControl.FONT),
            toggle(
                    MiscPreferences.WEAR_FONT_ALL_SCREENS,
                    TypographyTarget.TITLE,
                    TypographyControl.FONT_SCOPE),

            choice(
                    MiscPreferences.WEAR_TITLE_FONT,
                    TypographyTarget.TITLE,
                    TypographyControl.ELEMENT_FONT),

            toggle(
                    MiscPreferences.WEAR_SHOW_TRACK_TITLE,
                    TypographyTarget.TITLE,
                    TypographyControl.VISIBILITY),
            choice(
                    MiscPreferences.WEAR_TITLE_TEXT_MODE,
                    TypographyTarget.TITLE,
                    TypographyControl.TEXT_BEHAVIOR),
            number(
                    MiscPreferences.WEAR_TITLE_FONT_WEIGHT,
                    TypographyTarget.TITLE,
                    TypographyControl.WEIGHT,
                    WatchTypography.FLEX_WEIGHT_MIN..WatchTypography.FLEX_WEIGHT_MAX),
            toggle(
                    MiscPreferences.WEAR_TITLE_FONT_ITALIC,
                    TypographyTarget.TITLE,
                    TypographyControl.ITALIC),
            number(
                    MiscPreferences.WEAR_TITLE_FONT_SCALE,
                    TypographyTarget.TITLE,
                    TypographyControl.SIZE,
                    MiscPreferences.TYPOGRAPHY_MIN_SCALE..MiscPreferences.TYPOGRAPHY_MAX_SCALE),
            number(
                    MiscPreferences.WEAR_TITLE_FONT_OPACITY,
                    TypographyTarget.TITLE,
                    TypographyControl.OPACITY,
                    MiscPreferences.TYPOGRAPHY_MIN_OPACITY..100),
            number(
                    MiscPreferences.WEAR_TITLE_FONT_TRACKING,
                    TypographyTarget.TITLE,
                    TypographyControl.TRACKING,
                    MiscPreferences.TYPOGRAPHY_MIN_TRACKING..
                            MiscPreferences.TYPOGRAPHY_MAX_TRACKING),
            choice(
                    MiscPreferences.WEAR_TITLE_TEXT_CASE,
                    TypographyTarget.TITLE,
                    TypographyControl.CASE),
            flexAxes(
                    MiscPreferences.WEAR_TITLE_FONT_FLEX_WIDTH,
                    TypographyTarget.TITLE,
                    WatchTypography.FLEX_WIDTH_MIN.toInt()..
                            WatchTypography.FLEX_WIDTH_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_TITLE_FONT_FLEX_OPTICAL_SIZE,
                    TypographyTarget.TITLE,
                    WatchTypography.FLEX_OPTICAL_SIZE_MIN.toInt()..
                            WatchTypography.FLEX_OPTICAL_SIZE_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_TITLE_FONT_FLEX_GRADE,
                    TypographyTarget.TITLE,
                    WatchTypography.FLEX_GRADE_MIN.toInt()..
                            WatchTypography.FLEX_GRADE_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_TITLE_FONT_FLEX_ROUNDNESS,
                    TypographyTarget.TITLE,
                    WatchTypography.FLEX_ROUNDNESS_MIN.toInt()..
                            WatchTypography.FLEX_ROUNDNESS_MAX.toInt()),

            information(
                    "wear_flex_axes_hint",
                    TypographyTarget.TITLE,
                    TypographyControl.GLOBAL_FLEX),
            number(
                    MiscPreferences.WEAR_FONT_FLEX_WIDTH,
                    TypographyTarget.TITLE,
                    TypographyControl.GLOBAL_FLEX,
                    WatchTypography.FLEX_WIDTH_MIN.toInt()..
                            WatchTypography.FLEX_WIDTH_MAX.toInt()),
            number(
                    MiscPreferences.WEAR_FONT_FLEX_OPTICAL_SIZE,
                    TypographyTarget.TITLE,
                    TypographyControl.GLOBAL_FLEX,
                    WatchTypography.FLEX_OPTICAL_SIZE_MIN.toInt()..
                            WatchTypography.FLEX_OPTICAL_SIZE_MAX.toInt()),
            number(
                    MiscPreferences.WEAR_FONT_FLEX_GRADE,
                    TypographyTarget.TITLE,
                    TypographyControl.GLOBAL_FLEX,
                    WatchTypography.FLEX_GRADE_MIN.toInt()..
                            WatchTypography.FLEX_GRADE_MAX.toInt()),
            number(
                    MiscPreferences.WEAR_FONT_FLEX_ROUNDNESS,
                    TypographyTarget.TITLE,
                    TypographyControl.GLOBAL_FLEX,
                    WatchTypography.FLEX_ROUNDNESS_MIN.toInt()..
                            WatchTypography.FLEX_ROUNDNESS_MAX.toInt()),

            toggle(
                    MiscPreferences.WEAR_SHOW_TRACK_ARTIST,
                    TypographyTarget.ARTIST,
                    TypographyControl.VISIBILITY),
            choice(
                    MiscPreferences.WEAR_ARTIST_FONT,
                    TypographyTarget.ARTIST,
                    TypographyControl.ELEMENT_FONT),
            number(
                    MiscPreferences.WEAR_ARTIST_FONT_WEIGHT,
                    TypographyTarget.ARTIST,
                    TypographyControl.WEIGHT,
                    WatchTypography.FLEX_WEIGHT_MIN..WatchTypography.FLEX_WEIGHT_MAX),
            toggle(
                    MiscPreferences.WEAR_ARTIST_FONT_ITALIC,
                    TypographyTarget.ARTIST,
                    TypographyControl.ITALIC),
            number(
                    MiscPreferences.WEAR_ARTIST_FONT_SCALE,
                    TypographyTarget.ARTIST,
                    TypographyControl.SIZE,
                    MiscPreferences.TYPOGRAPHY_MIN_SCALE..MiscPreferences.TYPOGRAPHY_MAX_SCALE),
            number(
                    MiscPreferences.WEAR_ARTIST_FONT_OPACITY,
                    TypographyTarget.ARTIST,
                    TypographyControl.OPACITY,
                    MiscPreferences.TYPOGRAPHY_MIN_OPACITY..100),
            number(
                    MiscPreferences.WEAR_ARTIST_FONT_TRACKING,
                    TypographyTarget.ARTIST,
                    TypographyControl.TRACKING,
                    MiscPreferences.TYPOGRAPHY_MIN_TRACKING..
                            MiscPreferences.TYPOGRAPHY_MAX_TRACKING),
            choice(
                    MiscPreferences.WEAR_ARTIST_TEXT_CASE,
                    TypographyTarget.ARTIST,
                    TypographyControl.CASE),
            flexAxes(
                    MiscPreferences.WEAR_ARTIST_FONT_FLEX_WIDTH,
                    TypographyTarget.ARTIST,
                    WatchTypography.FLEX_WIDTH_MIN.toInt()..
                            WatchTypography.FLEX_WIDTH_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_ARTIST_FONT_FLEX_OPTICAL_SIZE,
                    TypographyTarget.ARTIST,
                    WatchTypography.FLEX_OPTICAL_SIZE_MIN.toInt()..
                            WatchTypography.FLEX_OPTICAL_SIZE_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_ARTIST_FONT_FLEX_GRADE,
                    TypographyTarget.ARTIST,
                    WatchTypography.FLEX_GRADE_MIN.toInt()..
                            WatchTypography.FLEX_GRADE_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_ARTIST_FONT_FLEX_ROUNDNESS,
                    TypographyTarget.ARTIST,
                    WatchTypography.FLEX_ROUNDNESS_MIN.toInt()..
                            WatchTypography.FLEX_ROUNDNESS_MAX.toInt()),

            choice(
                    MiscPreferences.WEAR_TRACK_TIME_FONT,
                    TypographyTarget.TRACK_TIME,
                    TypographyControl.ELEMENT_FONT),
            flexAxes(
                    MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_WIDTH,
                    TypographyTarget.TRACK_TIME,
                    WatchTypography.FLEX_WIDTH_MIN.toInt()..
                            WatchTypography.FLEX_WIDTH_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_OPTICAL_SIZE,
                    TypographyTarget.TRACK_TIME,
                    WatchTypography.FLEX_OPTICAL_SIZE_MIN.toInt()..
                            WatchTypography.FLEX_OPTICAL_SIZE_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_GRADE,
                    TypographyTarget.TRACK_TIME,
                    WatchTypography.FLEX_GRADE_MIN.toInt()..
                            WatchTypography.FLEX_GRADE_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_TRACK_TIME_FONT_FLEX_ROUNDNESS,
                    TypographyTarget.TRACK_TIME,
                    WatchTypography.FLEX_ROUNDNESS_MIN.toInt()..
                            WatchTypography.FLEX_ROUNDNESS_MAX.toInt()),
            number(
                    MiscPreferences.WEAR_TRACK_TIME_FONT_WEIGHT,
                    TypographyTarget.TRACK_TIME,
                    TypographyControl.WEIGHT,
                    WatchTypography.FLEX_WEIGHT_MIN..WatchTypography.FLEX_WEIGHT_MAX),
            toggle(
                    MiscPreferences.WEAR_TRACK_TIME_FONT_ITALIC,
                    TypographyTarget.TRACK_TIME,
                    TypographyControl.ITALIC),
            number(
                    MiscPreferences.WEAR_TRACK_TIME_FONT_SCALE,
                    TypographyTarget.TRACK_TIME,
                    TypographyControl.SIZE,
                    MiscPreferences.TYPOGRAPHY_MIN_SCALE..MiscPreferences.TYPOGRAPHY_MAX_SCALE),
            number(
                    MiscPreferences.WEAR_TRACK_TIME_FONT_OPACITY,
                    TypographyTarget.TRACK_TIME,
                    TypographyControl.OPACITY,
                    MiscPreferences.TYPOGRAPHY_MIN_OPACITY..100),
            number(
                    MiscPreferences.WEAR_TRACK_TIME_FONT_TRACKING,
                    TypographyTarget.TRACK_TIME,
                    TypographyControl.TRACKING,
                    MiscPreferences.TYPOGRAPHY_MIN_TRACKING..
                            MiscPreferences.TYPOGRAPHY_MAX_TRACKING),

            choice(
                    MiscPreferences.WEAR_CLOCK_FONT,
                    TypographyTarget.CLOCK,
                    TypographyControl.ELEMENT_FONT),
            flexAxes(
                    MiscPreferences.WEAR_CLOCK_FONT_FLEX_WIDTH,
                    TypographyTarget.CLOCK,
                    WatchTypography.FLEX_WIDTH_MIN.toInt()..
                            WatchTypography.FLEX_WIDTH_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_CLOCK_FONT_FLEX_OPTICAL_SIZE,
                    TypographyTarget.CLOCK,
                    WatchTypography.FLEX_OPTICAL_SIZE_MIN.toInt()..
                            WatchTypography.FLEX_OPTICAL_SIZE_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_CLOCK_FONT_FLEX_GRADE,
                    TypographyTarget.CLOCK,
                    WatchTypography.FLEX_GRADE_MIN.toInt()..
                            WatchTypography.FLEX_GRADE_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_CLOCK_FONT_FLEX_ROUNDNESS,
                    TypographyTarget.CLOCK,
                    WatchTypography.FLEX_ROUNDNESS_MIN.toInt()..
                            WatchTypography.FLEX_ROUNDNESS_MAX.toInt()),
            number(
                    MiscPreferences.WEAR_CLOCK_FONT_WEIGHT,
                    TypographyTarget.CLOCK,
                    TypographyControl.WEIGHT,
                    WatchTypography.FLEX_WEIGHT_MIN..WatchTypography.FLEX_WEIGHT_MAX),
            toggle(
                    MiscPreferences.WEAR_CLOCK_FONT_ITALIC,
                    TypographyTarget.CLOCK,
                    TypographyControl.ITALIC),
            number(
                    MiscPreferences.WEAR_CLOCK_FONT_SCALE,
                    TypographyTarget.CLOCK,
                    TypographyControl.SIZE,
                    MiscPreferences.TYPOGRAPHY_MIN_SCALE..MiscPreferences.TYPOGRAPHY_MAX_SCALE),
            number(
                    MiscPreferences.WEAR_CLOCK_FONT_TRACKING,
                    TypographyTarget.CLOCK,
                    TypographyControl.TRACKING,
                    MiscPreferences.TYPOGRAPHY_MIN_TRACKING..
                            MiscPreferences.TYPOGRAPHY_MAX_TRACKING),

            number(
                    MiscPreferences.WEAR_SOURCE_ICON_SCALE,
                    TypographyTarget.ICON,
                    TypographyControl.SIZE,
                    MiscPreferences.TYPOGRAPHY_MIN_SCALE..MiscPreferences.TYPOGRAPHY_MAX_SCALE),
            number(
                    MiscPreferences.WEAR_SOURCE_ICON_OPACITY,
                    TypographyTarget.ICON,
                    TypographyControl.OPACITY,
                    MiscPreferences.TYPOGRAPHY_MIN_OPACITY..100),

            choice(
                    MiscPreferences.WEAR_LYRICS_FONT,
                    TypographyTarget.LYRICS,
                    TypographyControl.ELEMENT_FONT),
            flexAxes(
                    MiscPreferences.WEAR_LYRICS_FONT_FLEX_WIDTH,
                    TypographyTarget.LYRICS,
                    WatchTypography.FLEX_WIDTH_MIN.toInt()..
                            WatchTypography.FLEX_WIDTH_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_LYRICS_FONT_FLEX_OPTICAL_SIZE,
                    TypographyTarget.LYRICS,
                    WatchTypography.FLEX_OPTICAL_SIZE_MIN.toInt()..
                            WatchTypography.FLEX_OPTICAL_SIZE_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_LYRICS_FONT_FLEX_GRADE,
                    TypographyTarget.LYRICS,
                    WatchTypography.FLEX_GRADE_MIN.toInt()..
                            WatchTypography.FLEX_GRADE_MAX.toInt()),
            flexAxes(
                    MiscPreferences.WEAR_LYRICS_FONT_FLEX_ROUNDNESS,
                    TypographyTarget.LYRICS,
                    WatchTypography.FLEX_ROUNDNESS_MIN.toInt()..
                            WatchTypography.FLEX_ROUNDNESS_MAX.toInt()))

    private val specsByKey: Map<String, TypographySettingSpec> =
            specs.associateBy(TypographySettingSpec::key).also { indexed ->
                check(indexed.size == specs.size) { "Typography editor keys must be unique" }
            }

    val keys: Set<String> = specsByKey.keys

    fun specFor(key: String): TypographySettingSpec? = specsByKey[key]

    fun specsFor(target: TypographyTarget): List<TypographySettingSpec> =
            specs.filter { it.target == target }

    /** Destination used when Settings search opens the compact editor for a legacy row key. */
    fun searchTargetFor(key: String): TypographySearchTarget? =
            specFor(key)?.let { TypographySearchTarget(it.target, it.control) }

    private fun choice(
            definition: PreferenceDefinition<String>,
            target: TypographyTarget,
            control: TypographyControl
    ) = TypographySettingSpec(
            definition.key,
            target,
            control,
            TypographyValueSpec.Choice(definition.defaultValue))

    private fun toggle(
            definition: PreferenceDefinition<Boolean>,
            target: TypographyTarget,
            control: TypographyControl
    ) = TypographySettingSpec(
            definition.key,
            target,
            control,
            TypographyValueSpec.Toggle(definition.defaultValue))

    private fun number(
            definition: PreferenceDefinition<Int>,
            target: TypographyTarget,
            control: TypographyControl,
            range: IntRange
    ) = TypographySettingSpec(
            definition.key,
            target,
            control,
            TypographyValueSpec.Number(definition.defaultValue, range))

    /**
     * The four variable-font axes are one contextual control in the compact editor. Keeping this
     * wrapper distinct from [number] makes that relationship explicit and, importantly, lets
     * search open the Flex dialog for an element-specific axis rather than one of its ordinary
     * formatting fields.
     */
    private fun flexAxes(
            definition: PreferenceDefinition<Int>,
            target: TypographyTarget,
            range: IntRange
    ) = number(definition, target, TypographyControl.FLEX, range)

    private fun information(
            key: String,
            target: TypographyTarget,
            control: TypographyControl
    ) = TypographySettingSpec(key, target, control, TypographyValueSpec.Information)
}
