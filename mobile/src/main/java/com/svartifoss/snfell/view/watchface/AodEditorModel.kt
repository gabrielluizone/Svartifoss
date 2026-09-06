package com.svartifoss.snfell.view.watchface

import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AppearanceNumericRanges
import com.svartifoss.snfell.common.MiscPreferences

/**
 * Where a control renders, which on this page is also *what kind of thing it is*.
 *
 * The Always-on page follows [PlayerEditorModel]'s shape rather than the target rail Text, Color
 * and Panel use, and for the same reason: those three apply one repeated set of controls to
 * parallel subjects, so swapping the subject is the natural gesture. Ambient describes a single
 * screen, and its controls divide by kind - what the screen is, what is on it, and how the one
 * element with a treatment of its own is drawn.
 */
internal enum class AodSlot {
    /** The ambient style, its tint and its brightness: what is being edited, above what it shows. */
    IDENTITY,

    /** Does this element exist on the ambient screen. Rendered as a field of checkable chips. */
    ELEMENT,

    /** The artwork's own treatment, which only exists once the artwork itself is on. */
    ARTWORK
}

internal enum class AodControl {
    STYLE,
    COLOR_MODE,
    CUSTOM_COLOR,
    INTENSITY,
    SHOW_ART,
    SHOW_CLOCK,
    SHOW_TRACK_INFO,
    SHOW_TRANSPORT,
    SHOW_PROGRESS,
    SHOW_PILLS,
    ART_TREATMENT,
    ART_OPACITY
}

/**
 * Storage contract for one editor setting.
 *
 * [Hex] is kept apart from [Choice] for [ColorEditorModel]'s reason: both persist a string, but a
 * hex colour has no entries to look a label up in, so treating one as the other renders `#3F51B5`
 * as a missing list entry rather than as a swatch.
 */
internal sealed interface AodValueSpec {
    data class Choice(val defaultValue: String) : AodValueSpec
    data class Toggle(val defaultValue: Boolean) : AodValueSpec
    data class Number(val defaultValue: Int, val range: IntRange) : AodValueSpec
    data class Hex(val defaultValue: String) : AodValueSpec
}

internal data class AodSettingSpec(
        val key: String,
        val slot: AodSlot,
        val control: AodControl,
        val value: AodValueSpec,
        /**
         * Short noun for a chip or a picker row, where the preference title is a sentence.
         *
         * Every ambient row is titled "... on always-on display", which is the right sentence for
         * a settings list and six repetitions of the same six words down a page that is already
         * called Always-on display. The full title still reaches screen readers through the
         * control's content description, and the search index is unaffected - it reads the XML,
         * not this.
         */
        val labelRes: Int? = null
)

/**
 * Pure description of every row that historically lived in `cat_wf_aod`.
 *
 * Presentation metadata only: keys, types and defaults stay owned by [MiscPreferences], so the
 * compact editor changes what the page looks like and nothing about what a saved face, custom
 * theme, backup, preview or watch reads.
 *
 * It also owns the two style lists that decide which ambient controls apply at all. Those were
 * written twice - in `WatchFacePrefsFragment.updateAodDetailVisibility` and again in
 * [WatchSearchTargetResolver] - which is the arrangement [PlayerEditorModel.INTERNAL_PROGRESS_FACES]
 * exists to end: a second copy of a decision list goes stale silently when a face is added, and a
 * control hidden on every style looks identical to one that was never written.
 */
internal object AodEditorModel {

    /**
     * Ambient styles drawn by a Compose face, which are the ones with a transport row, a progress
     * ring and an Up Next pill to switch off.
     *
     * An explicit allow-list rather than a negation, for [PausedHoldPolicy]'s reason: a removed or
     * unknown persisted style (legacy "minimal") then falls on the safe Classic path instead of
     * being treated as a visual face.
     */
    val VISUAL_STYLES: Set<String> = setOf(
            "expressive", "vinyl", "poster", "studio", "halo", "aurora", "eclipse",
            "spectrum", "material", "immersive", "depth", "carousel", "chat", "split",
            "note", "verse", "metadata", "ribbon", "frame")

    /** Ambient styles that draw no artwork at all, so its three controls have nothing to act on. */
    val ARTLESS_STYLES: Set<String> = setOf("chrono", "eclipse", "artist")

    // Artist is deliberately absent from VISUAL_STYLES above and present here, which together say
    // the same thing chrono's entries do: its ambient variant is two lines of type on black. It
    // drops the performer's photograph rather than dimming it - a full-screen image is the worst
    // thing to leave lit for hours on an AMOLED panel - and draws no transport, ring or pills, so
    // offering those three switches beside it would be offering controls that move nothing.

    /**
     * The controls whose visibility the ambient style decides.
     *
     * Named rather than derived from [appliesToStyle] returning false somewhere, because the
     * screen has to sweep exactly these: the style, tint and brightness always apply, and the
     * custom-tint swatch is owned by `initAccentColorTarget` (visible in "custom" alone), so a
     * blanket sweep would claim that row back on every face change.
     */
    val STYLE_GATED_CONTROLS: Set<AodControl> = setOf(
            AodControl.SHOW_TRANSPORT,
            AodControl.SHOW_PROGRESS,
            AodControl.SHOW_PILLS,
            AodControl.SHOW_ART,
            AodControl.ART_TREATMENT,
            AodControl.ART_OPACITY)

    val specs: List<AodSettingSpec> = listOf(
            // The style leads because it is the page's subject rather than one setting among many:
            // it decides which of the controls below apply at all.
            choice(
                    MiscPreferences.WEAR_AOD_STYLE,
                    AodSlot.IDENTITY,
                    AodControl.STYLE),
            choice(
                    MiscPreferences.WEAR_AOD_COLOR_MODE,
                    AodSlot.IDENTITY,
                    AodControl.COLOR_MODE,
                    R.string.aod_control_tint),
            AodSettingSpec(
                    MiscPreferences.WEAR_AOD_CUSTOM_COLOR.key,
                    AodSlot.IDENTITY,
                    AodControl.CUSTOM_COLOR,
                    AodValueSpec.Hex(MiscPreferences.WEAR_AOD_CUSTOM_COLOR.defaultValue)),
            number(
                    MiscPreferences.WEAR_AOD_INTENSITY,
                    AodSlot.IDENTITY,
                    AodControl.INTENSITY,
                    R.string.aod_control_brightness),

            element(
                    MiscPreferences.WEAR_AOD_SHOW_ART,
                    AodControl.SHOW_ART,
                    R.string.aod_element_artwork),
            element(
                    MiscPreferences.WEAR_AOD_SHOW_CLOCK,
                    AodControl.SHOW_CLOCK,
                    R.string.aod_element_clock),
            element(
                    MiscPreferences.WEAR_AOD_SHOW_TRACK_INFO,
                    AodControl.SHOW_TRACK_INFO,
                    R.string.aod_element_track_info),
            element(
                    MiscPreferences.WEAR_AOD_SHOW_TRANSPORT,
                    AodControl.SHOW_TRANSPORT,
                    R.string.aod_element_transport),
            element(
                    MiscPreferences.WEAR_AOD_SHOW_PROGRESS,
                    AodControl.SHOW_PROGRESS,
                    R.string.aod_element_progress),
            element(
                    MiscPreferences.WEAR_AOD_SHOW_PILLS,
                    AodControl.SHOW_PILLS,
                    R.string.aod_element_up_next),

            choice(
                    MiscPreferences.WEAR_AOD_ART_TREATMENT,
                    AodSlot.ARTWORK,
                    AodControl.ART_TREATMENT,
                    R.string.aod_control_art_treatment),
            number(
                    MiscPreferences.AMBIENT_ALBUM_ART_OPACITY,
                    AodSlot.ARTWORK,
                    AodControl.ART_OPACITY,
                    R.string.aod_control_art_opacity))

    private val specsByKey: Map<String, AodSettingSpec> =
            specs.associateBy(AodSettingSpec::key).also { indexed ->
                check(indexed.size == specs.size) { "AOD editor keys must be unique" }
            }

    val keys: Set<String> = specsByKey.keys

    fun specFor(key: String): AodSettingSpec? = specsByKey[key]

    fun specsFor(slot: AodSlot): List<AodSettingSpec> = specs.filter { it.slot == slot }

    /** The key owning [control], or null when nothing does. */
    fun keyFor(control: AodControl): String? = specs.firstOrNull { it.control == control }?.key

    /**
     * What "follow" actually resolves to.
     *
     * The ambient style may name a face of its own or defer to the awake one, and every rule below
     * is about the style that ends up being drawn - reading the stored value alone would judge
     * "follow" as if it were a face key nothing renders.
     */
    fun effectiveStyle(aodStyle: String, face: String): String =
            if (aodStyle == "follow") face else aodStyle

    /**
     * Whether [control] applies to the ambient style actually drawn.
     *
     * Pure so the rules can be pinned by a JVM test rather than only by the screen that applies
     * them. The dependency between the transport and its progress ring is deliberately *not* here:
     * that one reads a preference value rather than a style, so the editor filters it where it can
     * read one, exactly as the Player page does for the seek marker.
     */
    fun appliesToStyle(control: AodControl, effectiveStyle: String): Boolean = when (control) {
        AodControl.SHOW_TRANSPORT,
        AodControl.SHOW_PROGRESS,
        AodControl.SHOW_PILLS -> effectiveStyle in VISUAL_STYLES
        AodControl.SHOW_ART,
        AodControl.ART_TREATMENT,
        AodControl.ART_OPACITY -> effectiveStyle !in ARTLESS_STYLES
        else -> true
    }

    /** The rows of [slot] this ambient style can actually consume, in the order they render. */
    fun visibleIn(slot: AodSlot, effectiveStyle: String): List<AodSettingSpec> =
            specsFor(slot).filter { appliesToStyle(it.control, effectiveStyle) }

    private fun choice(
            definition: PreferenceDefinition<String>,
            slot: AodSlot,
            control: AodControl,
            labelRes: Int? = null
    ) = AodSettingSpec(
            definition.key,
            slot,
            control,
            AodValueSpec.Choice(definition.defaultValue),
            labelRes)

    /** The range is read from [AppearanceNumericRanges], the registry the typed field clamps on,
     *  so the slider offered here cannot reach a value that path would reject. */
    private fun number(
            definition: PreferenceDefinition<Int>,
            slot: AodSlot,
            control: AodControl,
            labelRes: Int
    ) = AodSettingSpec(
            definition.key,
            slot,
            control,
            AodValueSpec.Number(
                    definition.defaultValue,
                    AppearanceNumericRanges.rangeFor(definition.key) ?: 0..100),
            labelRes)

    private fun element(
            definition: PreferenceDefinition<Boolean>,
            control: AodControl,
            labelRes: Int
    ) = AodSettingSpec(
            definition.key,
            AodSlot.ELEMENT,
            control,
            AodValueSpec.Toggle(definition.defaultValue),
            labelRes)
}
