package com.svartifoss.snfell.view.watchface

import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.TextBackdropSpec
import com.svartifoss.snfell.common.TextShadowSpec
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

    /** The shadow's shape. The entry point: with it on None, the two below are not offered. */
    SHADOW,

    /**
     * Where the shadow's colour comes from, plus the custom colour behind it.
     *
     * Two legacy rows on one control, the way [FLEX] already collapses four axes and the way the
     * Colors page pairs a treatment with its custom hex - a colour mode and the colour it may
     * point at are one decision, not two.
     */
    SHADOW_COLOR,

    /** How far the selected shadow is pushed, as a percentage of its own geometry. */
    SHADOW_STRENGTH,

    /** The stroke drawn around the glyphs. Its colour rows share [OUTLINE_COLOR]. */
    OUTLINE,

    /** The outline's colour mode and the custom colour behind it, paired like [SHADOW_COLOR]. */
    OUTLINE_COLOR,

    /** The filled box behind the line. */
    BACKDROP,

    /** The backdrop's colour mode and the custom colour behind it. */
    BACKDROP_COLOR,

    /** How opaque that box is. */
    BACKDROP_OPACITY,
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

    /**
     * A picked colour, stored as a hex string.
     *
     * Kept apart from [Choice] for the reason [ColorValueSpec.Hex] records - both persist a string
     * and neither is the other - and for a second one this page learned the hard way. The colour
     * controls below pair a *mode* row with the hex row behind it, and the editor resolved a
     * control to whichever spec came first. Both being `Choice` made that the mode row every time,
     * so picking "Custom" left the hex row with nothing on screen that could open it: the mode was
     * set and the colour could never be chosen. Being a distinct kind is what lets the editor ask
     * for one or the other by name.
     */
    data class Hex(val defaultValue: String) : TypographyValueSpec

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
        val control: TypographyControl,
        /** True for the hex row of a paired colour control, which has its own swatch button. */
        val hex: Boolean = false)

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
            choice(
                    MiscPreferences.WEAR_TITLE_SHADOW_STYLE,
                    TypographyTarget.TITLE,
                    TypographyControl.SHADOW),
            choice(
                    MiscPreferences.WEAR_TITLE_SHADOW_COLOR_MODE,
                    TypographyTarget.TITLE,
                    TypographyControl.SHADOW_COLOR),
            hex(
                    MiscPreferences.WEAR_TITLE_SHADOW_CUSTOM_COLOR,
                    TypographyTarget.TITLE,
                    TypographyControl.SHADOW_COLOR),
            number(
                    MiscPreferences.WEAR_TITLE_SHADOW_STRENGTH,
                    TypographyTarget.TITLE,
                    TypographyControl.SHADOW_STRENGTH,
                    TextShadowSpec.MIN_STRENGTH_PERCENT..TextShadowSpec.MAX_STRENGTH_PERCENT),
            choice(
                    MiscPreferences.WEAR_TITLE_OUTLINE_STYLE,
                    TypographyTarget.TITLE,
                    TypographyControl.OUTLINE),
            choice(
                    MiscPreferences.WEAR_TITLE_OUTLINE_COLOR_MODE,
                    TypographyTarget.TITLE,
                    TypographyControl.OUTLINE_COLOR),
            hex(
                    MiscPreferences.WEAR_TITLE_OUTLINE_CUSTOM_COLOR,
                    TypographyTarget.TITLE,
                    TypographyControl.OUTLINE_COLOR),
            choice(
                    MiscPreferences.WEAR_TITLE_TEXT_BG_STYLE,
                    TypographyTarget.TITLE,
                    TypographyControl.BACKDROP),
            choice(
                    MiscPreferences.WEAR_TITLE_TEXT_BG_COLOR_MODE,
                    TypographyTarget.TITLE,
                    TypographyControl.BACKDROP_COLOR),
            hex(
                    MiscPreferences.WEAR_TITLE_TEXT_BG_CUSTOM_COLOR,
                    TypographyTarget.TITLE,
                    TypographyControl.BACKDROP_COLOR),
            number(
                    MiscPreferences.WEAR_TITLE_TEXT_BG_OPACITY,
                    TypographyTarget.TITLE,
                    TypographyControl.BACKDROP_OPACITY,
                    TextBackdropSpec.MIN_OPACITY_PERCENT..TextBackdropSpec.MAX_OPACITY_PERCENT),
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
                    MiscPreferences.WEAR_ARTIST_TEXT_MODE,
                    TypographyTarget.ARTIST,
                    TypographyControl.TEXT_BEHAVIOR),
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
            choice(
                    MiscPreferences.WEAR_ARTIST_SHADOW_STYLE,
                    TypographyTarget.ARTIST,
                    TypographyControl.SHADOW),
            choice(
                    MiscPreferences.WEAR_ARTIST_SHADOW_COLOR_MODE,
                    TypographyTarget.ARTIST,
                    TypographyControl.SHADOW_COLOR),
            hex(
                    MiscPreferences.WEAR_ARTIST_SHADOW_CUSTOM_COLOR,
                    TypographyTarget.ARTIST,
                    TypographyControl.SHADOW_COLOR),
            number(
                    MiscPreferences.WEAR_ARTIST_SHADOW_STRENGTH,
                    TypographyTarget.ARTIST,
                    TypographyControl.SHADOW_STRENGTH,
                    TextShadowSpec.MIN_STRENGTH_PERCENT..TextShadowSpec.MAX_STRENGTH_PERCENT),
            choice(
                    MiscPreferences.WEAR_ARTIST_OUTLINE_STYLE,
                    TypographyTarget.ARTIST,
                    TypographyControl.OUTLINE),
            choice(
                    MiscPreferences.WEAR_ARTIST_OUTLINE_COLOR_MODE,
                    TypographyTarget.ARTIST,
                    TypographyControl.OUTLINE_COLOR),
            hex(
                    MiscPreferences.WEAR_ARTIST_OUTLINE_CUSTOM_COLOR,
                    TypographyTarget.ARTIST,
                    TypographyControl.OUTLINE_COLOR),
            choice(
                    MiscPreferences.WEAR_ARTIST_TEXT_BG_STYLE,
                    TypographyTarget.ARTIST,
                    TypographyControl.BACKDROP),
            choice(
                    MiscPreferences.WEAR_ARTIST_TEXT_BG_COLOR_MODE,
                    TypographyTarget.ARTIST,
                    TypographyControl.BACKDROP_COLOR),
            hex(
                    MiscPreferences.WEAR_ARTIST_TEXT_BG_CUSTOM_COLOR,
                    TypographyTarget.ARTIST,
                    TypographyControl.BACKDROP_COLOR),
            number(
                    MiscPreferences.WEAR_ARTIST_TEXT_BG_OPACITY,
                    TypographyTarget.ARTIST,
                    TypographyControl.BACKDROP_OPACITY,
                    TextBackdropSpec.MIN_OPACITY_PERCENT..TextBackdropSpec.MAX_OPACITY_PERCENT),
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

    /**
     * The row a control's own button edits - the mode row of a colour pair, never its hex row.
     *
     * Resolved by value kind rather than by declaration order, so moving a spec in the list above
     * cannot silently change which of a pair the editor opens.
     */
    fun settingKeyFor(target: TypographyTarget, control: TypographyControl): String? =
            specsFor(target).firstOrNull {
                it.control == control && it.persisted && it.value !is TypographyValueSpec.Hex
            }?.key

    /** The picked-colour row behind a colour control, or null where the control has none. */
    fun customColorKeyFor(target: TypographyTarget, control: TypographyControl): String? =
            specsFor(target).firstOrNull {
                it.control == control && it.value is TypographyValueSpec.Hex
            }?.key

    /** Destination used when Settings search opens the compact editor for a legacy row key. */
    fun searchTargetFor(key: String): TypographySearchTarget? =
            specFor(key)?.let {
                TypographySearchTarget(it.target, it.control, it.value is TypographyValueSpec.Hex)
            }

    private fun choice(
            definition: PreferenceDefinition<String>,
            target: TypographyTarget,
            control: TypographyControl
    ) = TypographySettingSpec(
            definition.key,
            target,
            control,
            TypographyValueSpec.Choice(definition.defaultValue))

    private fun hex(
            definition: PreferenceDefinition<String>,
            target: TypographyTarget,
            control: TypographyControl
    ) = TypographySettingSpec(
            definition.key,
            target,
            control,
            TypographyValueSpec.Hex(definition.defaultValue))

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
