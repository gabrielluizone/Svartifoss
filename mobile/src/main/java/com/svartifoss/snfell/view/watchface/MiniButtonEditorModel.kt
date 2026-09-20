package com.svartifoss.snfell.view.watchface

import androidx.annotation.StringRes
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AppearanceNumericRanges
import com.svartifoss.snfell.common.MiniButtonPlacement
import com.svartifoss.snfell.common.MiscPreferences

/**
 * Which card a control belongs to, which on this page is also what it is about.
 *
 * It was two cards - the row, and the gestures - and the first held four different things: the way
 * out to where the buttons are given their actions, when the row appears, and how it looks. Those
 * are three questions, and only the last is a matter of style. The link is still first, because
 * assigning what the buttons actually *do* happens on the Controls tab, not here, and that is the
 * first question anybody arriving on this page has.
 *
 * [headingRes] is null for a card that names itself: the gestures card holds one row whose own
 * title is the heading, so a heading above it would only say the same thing twice.
 */
internal enum class MiniButtonSlot(@StringRes val headingRes: Int? = null) {
    /** The buttons themselves: where their actions are assigned, and when the row is on screen. */
    BUTTONS(R.string.category_mini_buttons),

    /** How the row looks. */
    APPEARANCE(R.string.mini_button_section_appearance),

    /** The screen-gesture setting, which shares this page because both are input controls. */
    GESTURES
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
         * Short noun for a row, where the preference title is a sentence.
         *
         * Four of the five row titles begin "Mini buttons", on a card already headed Appearance on a
         * page already called Mini buttons - so those rows carry the distinguishing half and the
         * full title still reaches screen readers through the row's content description. The search
         * index is unaffected: it reads the XML, not this.
         */
        @StringRes val labelRes: Int? = null,
        /**
         * The sentence under a row's title, for the two rows whose title and value would not say
         * enough: the link, which has no value at all, and the gestures, whose title does not say
         * which ones (up, down and left - never right). The style rows are named by their own
         * value ("Flat", "Glass", "100%") and are deliberately left without one.
         */
        @StringRes val descriptionRes: Int? = null
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

    /** Every row, in the order it renders *within its card*. */
    val specs: List<MiniButtonSettingSpec> = listOf(
            MiniButtonSettingSpec(
                    "screen_buttons_hint",
                    MiniButtonSlot.BUTTONS,
                    MiniButtonControl.ASSIGN,
                    MiniButtonValueSpec.Action,
                    labelRes = R.string.mini_button_assign_action,
                    descriptionRes = R.string.setting_screen_buttons_hint_description),
            // The full preference title, "Show mini buttons": there is no short noun for it, and
            // "Show" alone above a value reads as a question with no subject.
            choice(
                    MiscPreferences.WEAR_MINI_BUTTONS_MODE,
                    MiniButtonSlot.BUTTONS,
                    MiniButtonControl.MODE),

            choice(
                    MiscPreferences.WEAR_SCREEN_BUTTONS_CURVE_STYLE,
                    MiniButtonSlot.APPEARANCE,
                    MiniButtonControl.ARRANGEMENT,
                    R.string.mini_button_control_arrangement),
            choice(
                    MiscPreferences.WEAR_SCREEN_BUTTONS_SHAPE,
                    MiniButtonSlot.APPEARANCE,
                    MiniButtonControl.SHAPE,
                    R.string.mini_button_control_shape),
            choice(
                    MiscPreferences.WEAR_SCREEN_BUTTONS_BG,
                    MiniButtonSlot.APPEARANCE,
                    MiniButtonControl.BACKGROUND,
                    R.string.mini_button_control_background),
            number(
                    MiscPreferences.WEAR_SCREEN_BUTTONS_OPACITY,
                    MiniButtonSlot.APPEARANCE,
                    MiniButtonControl.OPACITY,
                    R.string.mini_button_control_opacity),

            choice(
                    MiscPreferences.WEAR_GESTURES_MODE,
                    MiniButtonSlot.GESTURES,
                    MiniButtonControl.GESTURES_MODE,
                    descriptionRes = R.string.mini_button_gestures_note))

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
            @StringRes labelRes: Int? = null,
            @StringRes descriptionRes: Int? = null
    ) = MiniButtonSettingSpec(
            definition.key,
            slot,
            control,
            MiniButtonValueSpec.Choice(definition.defaultValue),
            labelRes,
            descriptionRes)

    /** The range is read from [AppearanceNumericRanges], the registry the typed field clamps on,
     *  so the slider offered here cannot reach a value that path would reject. */
    private fun number(
            definition: PreferenceDefinition<Int>,
            slot: MiniButtonSlot,
            control: MiniButtonControl,
            @StringRes labelRes: Int
    ) = MiniButtonSettingSpec(
            definition.key,
            slot,
            control,
            MiniButtonValueSpec.Number(
                    definition.defaultValue,
                    AppearanceNumericRanges.rangeFor(definition.key) ?: 0..100),
            labelRes)
}
