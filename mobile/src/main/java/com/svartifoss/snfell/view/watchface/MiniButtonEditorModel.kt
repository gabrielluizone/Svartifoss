package com.svartifoss.snfell.view.watchface

import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AppearanceNumericRanges
import com.svartifoss.snfell.common.MiniButtonPlacement
import com.svartifoss.snfell.common.MiscPreferences

/**
 * Which card a control belongs to, which on this page is also what it is about.
 *
 * Two of the three are settings groups and the third is a way out: assigning what the buttons
 * actually *do* happens on the Controls tab, not here, and that is the first question anybody
 * arriving on this page has.
 */
internal enum class MiniButtonSlot {
    /** How the row of mini buttons looks and when it appears. */
    ROW,

    /** The screen-gesture switch, which shares this page because both are input controls. */
    GESTURES,

    /** Not a value: the link to where the buttons are given their actions. */
    ACTION
}

internal enum class MiniButtonControl {
    MODE,
    ARRANGEMENT,
    SHAPE,
    BACKGROUND,
    OPACITY,
    ASSIGN,
    GESTURES_MODE
}

internal sealed interface MiniButtonValueSpec {
    data class Choice(val defaultValue: String) : MiniButtonValueSpec
    data class Number(val defaultValue: Int, val range: IntRange) : MiniButtonValueSpec

    /** A row that opens another screen rather than holding a value. */
    data object Action : MiniButtonValueSpec
}

internal data class MiniButtonSettingSpec(
        val key: String,
        val slot: MiniButtonSlot,
        val control: MiniButtonControl,
        val value: MiniButtonValueSpec,
        /**
         * Short noun for a picker row, where the preference title is a sentence.
         *
         * Four of the five row titles begin with "Mini buttons", on a page already called Mini
         * buttons - so the editor rows carry the distinguishing half and the full title still
         * reaches screen readers through the control's content description. The search index is
         * unaffected: it reads the XML, not this.
         */
        val labelRes: Int? = null
) {
    val persisted: Boolean get() = value !is MiniButtonValueSpec.Action
}

/**
 * Pure description of every row that historically lived in `cat_wf_mini_buttons` or
 * `cat_wf_gestures`.
 *
 * The sibling of [PlayerEditorModel] and [AodEditorModel], on the same terms: presentation
 * metadata only, with keys, types and defaults still owned by [MiscPreferences], so the compact
 * editor changes what the page looks like and nothing about what a saved face, custom theme,
 * backup, preview or watch reads.
 */
internal object MiniButtonEditorModel {

    val specs: List<MiniButtonSettingSpec> = listOf(
            choice(
                    MiscPreferences.WEAR_MINI_BUTTONS_MODE,
                    MiniButtonSlot.ROW,
                    MiniButtonControl.MODE,
                    R.string.mini_button_control_visibility),
            choice(
                    MiscPreferences.WEAR_SCREEN_BUTTONS_CURVE_STYLE,
                    MiniButtonSlot.ROW,
                    MiniButtonControl.ARRANGEMENT,
                    R.string.mini_button_control_arrangement),
            choice(
                    MiscPreferences.WEAR_SCREEN_BUTTONS_SHAPE,
                    MiniButtonSlot.ROW,
                    MiniButtonControl.SHAPE,
                    R.string.mini_button_control_shape),
            choice(
                    MiscPreferences.WEAR_SCREEN_BUTTONS_BG,
                    MiniButtonSlot.ROW,
                    MiniButtonControl.BACKGROUND,
                    R.string.mini_button_control_background),
            number(
                    MiscPreferences.WEAR_SCREEN_BUTTONS_OPACITY,
                    MiniButtonSlot.ROW,
                    MiniButtonControl.OPACITY,
                    R.string.mini_button_control_opacity),

            choice(
                    MiscPreferences.WEAR_GESTURES_MODE,
                    MiniButtonSlot.GESTURES,
                    MiniButtonControl.GESTURES_MODE),

            MiniButtonSettingSpec(
                    "screen_buttons_hint",
                    MiniButtonSlot.ACTION,
                    MiniButtonControl.ASSIGN,
                    MiniButtonValueSpec.Action))

    private val specsByKey: Map<String, MiniButtonSettingSpec> =
            specs.associateBy(MiniButtonSettingSpec::key).also { indexed ->
                check(indexed.size == specs.size) { "Mini button editor keys must be unique" }
            }

    val keys: Set<String> = specsByKey.keys

    fun specFor(key: String): MiniButtonSettingSpec? = specsByKey[key]

    fun specsFor(slot: MiniButtonSlot): List<MiniButtonSettingSpec> =
            specs.filter { it.slot == slot }

    /** The key owning [control], or null when nothing does. */
    fun keyFor(control: MiniButtonControl): String? =
            specs.firstOrNull { it.control == control }?.key

    /** The rows of [slot] this face can actually consume, in the order they render. */
    fun visibleIn(slot: MiniButtonSlot, face: String): List<MiniButtonSettingSpec> =
            specsFor(slot).filter { appliesToFace(it.control, face) }

    /**
     * Whether [control] applies to [face], mirroring
     * `WatchFacePrefsFragment.updatePlayerCapabilityVisibility` and [WatchSearchTargetResolver].
     *
     * A face that hosts the mini-button row inside its own composition places and shapes those
     * buttons itself (Chat's circles are the configured slots), so neither the curve/rail
     * arrangement nor the pill shape reaches them - a picker that changes nothing reads as broken
     * rather than as inapplicable. Background and opacity are deliberately *not* gated: those do
     * apply to a hosted row, through the shared `MiniButtonSurfaces`.
     */
    fun appliesToFace(control: MiniButtonControl, face: String): Boolean = when (control) {
        MiniButtonControl.ARRANGEMENT,
        MiniButtonControl.SHAPE -> !MiniButtonPlacement.isHostedByFace(face)
        else -> true
    }

    private fun choice(
            definition: PreferenceDefinition<String>,
            slot: MiniButtonSlot,
            control: MiniButtonControl,
            labelRes: Int? = null
    ) = MiniButtonSettingSpec(
            definition.key,
            slot,
            control,
            MiniButtonValueSpec.Choice(definition.defaultValue),
            labelRes)

    /** The range is read from [AppearanceNumericRanges], the registry the typed field clamps on,
     *  so the slider offered here cannot reach a value that path would reject. */
    private fun number(
            definition: PreferenceDefinition<Int>,
            slot: MiniButtonSlot,
            control: MiniButtonControl,
            labelRes: Int
    ) = MiniButtonSettingSpec(
            definition.key,
            slot,
            control,
            MiniButtonValueSpec.Number(
                    definition.defaultValue,
                    AppearanceNumericRanges.rangeFor(definition.key) ?: 0..100),
            labelRes)
}
