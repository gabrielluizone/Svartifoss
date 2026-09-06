package com.svartifoss.snfell.view.watchface

import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.TextBlockPlacementSupport
import com.svartifoss.snfell.common.TrackMetadataFields

/**
 * Where a control renders, which on this page is also *what kind of thing it is*.
 *
 * The Player page deliberately does not use the target rail its three siblings do. Text, Color and
 * Panel each apply one repeated set of controls to parallel things - Title and Artist, Volume and
 * Queue - so a rail that swaps the subject is the natural shape. Player has no such parallel: it
 * describes one screen, and its controls divide by *kind* rather than by subject. A rail here would
 * have been two tabs holding unrelated lists, which is a section header wearing a tab's clothes.
 */
internal enum class PlayerSlot {
    /** The face and the control style: what is being edited, above everything it affects. */
    IDENTITY,

    /** Does this element exist on the screen. Rendered as a field of checkable chips. */
    ELEMENT,

    /** A genuine multi-way choice, which a chip cannot express. Rendered as a row. */
    CHOICE,

    /** The Metadata face's blocks - existence toggles too, kept in their own field. */
    DETAIL,

    /** Not part of the composition: how the screen behaves while it is up. */
    BEHAVIOUR,

    /** Recovery rather than configuration. */
    ACTION
}

internal enum class PlayerControl {
    FACE,
    SCREEN_THEME,
    SOURCE_ICON,
    PLAYER_CONTROLS,
    QUADRANT_FLASH,
    INTERNAL_PROGRESS,
    EDGE_PROGRESS,
    EDGE_SEEK,
    ALWAYS_SHOW_TIME,
    CAROUSEL_SHAPE,
    TITLE_CENTERED,
    TEXT_BLOCK_ALIGN,
    TEXT_BLOCK_POSITION,
    NOTE_COVER_SHAPE,
    NOTE_SHOW_COVER,
    CHAT_COVER_SHAPE,
    CHAT_SHOW_COVER,
    METADATA_COVER_SHAPE,
    METADATA_SHOW_COVER,
    SPLIT_PANEL,
    EXPRESSIVE_SEEK,
    SEEK_MARKER,
    TRACK_TIME_MODE,
    METADATA_GROUPS,
    KEEP_SCREEN_ON,
    RESET_FACE
}

internal sealed interface PlayerValueSpec {
    data class Choice(val defaultValue: String) : PlayerValueSpec
    data class Toggle(val defaultValue: Boolean) : PlayerValueSpec

    /** A row that opens a confirmation rather than holding a value. */
    data object Action : PlayerValueSpec
}

internal data class PlayerSettingSpec(
        val key: String,
        val slot: PlayerSlot,
        val control: PlayerControl,
        val value: PlayerValueSpec,
        /**
         * Short noun for a chip, where the preference title is a sentence.
         *
         * A chip that is on already says "shown", so carrying "Show the app icon" into one would
         * repeat the verb five times down a field whose whole point is to be read at a glance. The
         * full title still reaches screen readers through the chip's content description, and the
         * search index is unaffected - it reads the XML, not this.
         */
        val chipLabelRes: Int? = null
) {
    val persisted: Boolean get() = value !is PlayerValueSpec.Action
}

/**
 * Pure description of every row that historically lived in a `cat_wf_screen_behavior`,
 * `cat_wf_player_*`, `cat_wf_metadata` or `cat_wf_layout_actions` category.
 *
 * Presentation metadata only: keys, types and defaults stay owned by [MiscPreferences], so the
 * compact editor changes what the page looks like and nothing about what a saved face, custom
 * theme, backup, preview or watch reads.
 *
 * The Details rows are **not listed individually**. They are generated from
 * [TrackMetadataFields.Group], which already owns the key and default of each block and which the
 * Metadata face renders from - CLAUDE.md records that a new group has to reach `EXPORTABLE` and
 * `SCOPED_KEYS`, and a fourth place to register it is exactly the drift this should not introduce.
 */
internal object PlayerEditorModel {

    /** The Details rows, derived rather than declared. See the class doc. */
    val metadataKeys: List<String> =
            TrackMetadataFields.Group.entries.map { it.preferenceKey }

    /**
     * Faces whose own composition draws a resting inner progress element.
     *
     * The single copy: `WatchFacePrefsFragment.updatePlayerCapabilityVisibility` and
     * `WatchSearchTargetResolver` both read this rather than repeating it. There were three lists
     * and they had already drifted from the faces - Ribbon's hairline under its cover rail and
     * Frame's bar along the bottom of its cover are both drawn, both honour the switch on the
     * watch, and none of the three knew about either, so the control was hidden on the phone while
     * the watch went on obeying it. Every other face ignores the switch entirely, so offering it
     * there reads as broken rather than as inapplicable.
     */
    val INTERNAL_PROGRESS_FACES: Set<String> = setOf(
            "vinyl", "poster", "studio", "halo", "aurora", "eclipse", "spectrum", "depth", "verse",
            "ribbon", "frame")

    /** The two faces that must keep their central transport, so the switch cannot apply. */
    val FIXED_TRANSPORT_FACES: Set<String> = setOf("expressive", "material")

    /**
     * Faces that centre a stacked metadata block, and so have something for
     * [MiscPreferences.WEAR_TITLE_CENTERED] to move.
     *
     * Every other face either places its title against a fixed edge of its own composition
     * (Split's panel, Chat's bubble, Verse's band, Metadata's header) or has no separate artist
     * line to weigh against it - so the switch would move nothing there, which reads as broken
     * rather than as inapplicable. The same rule Carousel's card shape and Split's panel follow.
     */
    val TITLE_CENTERED_FACES: Set<String> = setOf("classic", "poster", "studio")
    // Matejdro is deliberately absent: its two bands already fill the whole text area, so there is
    // no slack for the anchor to slide into and `applyClassicTitleAnchor` skips the face outright.
    // Offering the row would be offering a switch that provably moves nothing.

    /**
     * Faces whose own composition draws icon glyphs [MiscPreferences.WEAR_SCREEN_THEME] actually
     * restyles - it only ever changes `ScreenThemeTokens.iconAlpha`/`iconScale` (see
     * `common/.../ScreenTheme.kt`), so it does nothing wherever a face has no icon of its own.
     *
     * Classic and the icon-transport curated faces (Vinyl/Poster/Studio/Halo/Aurora/Eclipse/
     * Spectrum/Material) draw a persistent play/pause or transport row through it, and Expressive
     * always shows its cookie glyph at full opacity (see its own `screenTheme` read - the one
     * exception is that "Hidden" alone still zeroes it there). Frame and Ribbon draw no persistent
     * icon but do pass `state` into `CenterGestureRegion`, so the transient tap-confirmation glyph
     * still honours it. Every other face (Immersive, Depth, Carousel, Chat, Split, Note, Verse,
     * Metadata) either has no icon-based transport at all or calls `CenterGestureRegion` without
     * `state`, so the picker changed nothing for them - a picker that changes nothing reads as
     * broken rather than as inapplicable, the same rule Carousel's card shape and Split's panel
     * already follow.
     */
    val CONTROL_STYLE_FACES: Set<String> = setOf(
            "classic", "expressive", "vinyl", "poster", "studio", "halo", "aurora", "eclipse",
            // Matejdro is the second View face and draws Classic's four quadrant hints through the
            // very same `applyScreenThemeNow` branch, so the picker restyles it identically.
            "spectrum", "material", "frame", "ribbon", "matejdro")

    /**
     * Which faces offer *Text alignment*, and which offer *Text position* - two questions, not one.
     *
     * Read from [TextBlockPlacementSupport] rather than declared here, because the picker is only
     * one of three consumers: the watch and the phone's own miniature resolve every stored value
     * through the same registry, so a face that is not offered the row cannot be moved by a value
     * that arrives from somewhere else either. See that object for what each exclusion is about.
     *
     * They were one set until now (`ALLOWED_BASE_FACES - frame - ribbon`), which forced nine faces
     * to be all-in or all-out on a pair of controls they can only honour one of - Carousel's two
     * bands may be aligned but not moved, Note's sentence may be moved but not aligned without
     * dragging its cover disc with it.
     */
    val TEXT_BLOCK_ALIGN_FACES: Set<String> = TextBlockPlacementSupport.ALIGN_FACES
    val TEXT_BLOCK_POSITION_FACES: Set<String> = TextBlockPlacementSupport.POSITION_FACES

    val specs: List<PlayerSettingSpec> = listOf(
            // The face leads because it is the page's subject rather than one setting among many:
            // it decides which of the controls below apply at all.
            choice(MiscPreferences.WEAR_SCREEN_FACE, PlayerSlot.IDENTITY, PlayerControl.FACE),
            choice(
                    MiscPreferences.WEAR_SCREEN_THEME,
                    PlayerSlot.IDENTITY,
                    PlayerControl.SCREEN_THEME),

            element(
                    MiscPreferences.WEAR_SHOW_SOURCE_ICON,
                    PlayerControl.SOURCE_ICON,
                    R.string.player_element_app_icon),
            element(
                    MiscPreferences.WEAR_PLAYER_CONTROLS_VISIBLE,
                    PlayerControl.PLAYER_CONTROLS,
                    R.string.player_element_controls),
            element(
                    MiscPreferences.WEAR_INTERNAL_PROGRESS_VISIBLE,
                    PlayerControl.INTERNAL_PROGRESS,
                    R.string.player_element_inner_progress),
            element(
                    MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE,
                    PlayerControl.EDGE_PROGRESS,
                    R.string.player_element_edge_arc),
            element(
                    MiscPreferences.WEAR_EDGE_SEEK_ENABLED,
                    PlayerControl.EDGE_SEEK,
                    R.string.player_element_edge_seek),
            element(
                    MiscPreferences.ALWAYS_SHOW_TIME,
                    PlayerControl.ALWAYS_SHOW_TIME,
                    R.string.player_element_clock),
            element(
                    MiscPreferences.WEAR_QUADRANT_TAP_FLASH,
                    PlayerControl.QUADRANT_FLASH,
                    R.string.player_element_tap_flash),

            choice(
                    MiscPreferences.WEAR_TRACK_TIME_MODE,
                    PlayerSlot.CHOICE,
                    PlayerControl.TRACK_TIME_MODE),
            /*
             * Applies to every face, and is deliberately *not* in [appliesToFace].
             *
             * The tick is drawn on the shared edge ring rather than by a face's own composition,
             * so no face is inapplicable — but the ring itself can be switched off, and on Split,
             * Verse, Note and Chat it is off by default. That is a preference gate, not a face
             * gate, so `WatchFacePrefsFragment.renderPlayerEditor` filters this row on
             * `wear_edge_progress_visible` where it can actually read a value, and
             * `WatchSearchTargetResolver` redirects a search for it to that switch. Keeping the
             * distinction is what stops this function from needing to read preferences and stop
             * being pure.
             */
            choice(
                    MiscPreferences.WEAR_SEEK_MARKER,
                    PlayerSlot.CHOICE,
                    PlayerControl.SEEK_MARKER),
            choice(
                    MiscPreferences.WEAR_CAROUSEL_CARD_SHAPE,
                    PlayerSlot.CHOICE,
                    PlayerControl.CAROUSEL_SHAPE),
            choice(
                    MiscPreferences.WEAR_NOTE_COVER_SHAPE,
                    PlayerSlot.CHOICE,
                    PlayerControl.NOTE_COVER_SHAPE),
            element(
                    MiscPreferences.WEAR_TITLE_CENTERED,
                    PlayerControl.TITLE_CENTERED,
                    R.string.player_element_title_centered),
            /*
             * Applies to the faces that can honour each axis - see [TextBlockPlacementSupport].
             * `follow` still protects every supported face from visual change until the user
             * picks an override.
             */
            choice(
                    MiscPreferences.WEAR_TEXT_BLOCK_ALIGN,
                    PlayerSlot.CHOICE,
                    PlayerControl.TEXT_BLOCK_ALIGN),
            choice(
                    MiscPreferences.WEAR_TEXT_BLOCK_POSITION,
                    PlayerSlot.CHOICE,
                    PlayerControl.TEXT_BLOCK_POSITION),
            element(
                    MiscPreferences.WEAR_NOTE_SHOW_COVER,
                    PlayerControl.NOTE_SHOW_COVER,
                    R.string.player_element_cover_art),
            choice(
                    MiscPreferences.WEAR_CHAT_COVER_SHAPE,
                    PlayerSlot.CHOICE,
                    PlayerControl.CHAT_COVER_SHAPE),
            element(
                    MiscPreferences.WEAR_CHAT_SHOW_COVER,
                    PlayerControl.CHAT_SHOW_COVER,
                    R.string.player_element_cover_art),
            choice(
                    MiscPreferences.WEAR_METADATA_COVER_SHAPE,
                    PlayerSlot.CHOICE,
                    PlayerControl.METADATA_COVER_SHAPE),
            element(
                    MiscPreferences.WEAR_METADATA_SHOW_COVER,
                    PlayerControl.METADATA_SHOW_COVER,
                    R.string.player_element_cover_art),
            choice(
                    MiscPreferences.WEAR_SPLIT_PANEL,
                    PlayerSlot.CHOICE,
                    PlayerControl.SPLIT_PANEL),
            choice(
                    MiscPreferences.WEAR_EXPRESSIVE_SEEK_MODE,
                    PlayerSlot.CHOICE,
                    PlayerControl.EXPRESSIVE_SEEK),

            toggle(
                    MiscPreferences.WEAR_KEEP_SCREEN_ON,
                    PlayerSlot.BEHAVIOUR,
                    PlayerControl.KEEP_SCREEN_ON),

            PlayerSettingSpec(
                    "reset_appearance",
                    PlayerSlot.ACTION,
                    PlayerControl.RESET_FACE,
                    PlayerValueSpec.Action)
    ) + TrackMetadataFields.Group.entries.map { group ->
        PlayerSettingSpec(
                group.preferenceKey,
                PlayerSlot.DETAIL,
                PlayerControl.METADATA_GROUPS,
                PlayerValueSpec.Toggle(group.defaultVisible))
    }

    private val specsByKey: Map<String, PlayerSettingSpec> =
            specs.associateBy(PlayerSettingSpec::key).also { indexed ->
                check(indexed.size == specs.size) { "Player editor keys must be unique" }
            }

    val keys: Set<String> = specsByKey.keys

    fun specFor(key: String): PlayerSettingSpec? = specsByKey[key]

    fun specsFor(slot: PlayerSlot): List<PlayerSettingSpec> = specs.filter { it.slot == slot }

    /** The key owning [control], or null when nothing does. */
    fun keyFor(control: PlayerControl): String? =
            specs.firstOrNull { it.control == control }?.key

    /** The rows of [slot] this face can actually consume, in the order they should render. */
    fun visibleIn(slot: PlayerSlot, face: String): List<PlayerSettingSpec> =
            specsFor(slot).filter { appliesToFace(it.control, face) }

    /**
     * Whether [control] applies to [face], mirroring
     * `WatchFacePrefsFragment.updatePlayerCapabilityVisibility` and
     * `updateBackgroundCapabilityVisibility`.
     *
     * Pure so the rules can be pinned by a JVM test rather than only by the screen that applies
     * them: this is the second copy of a decision list that goes stale silently when a face is
     * added, and a control hidden on every face looks identical to one that was never written.
     */
    fun appliesToFace(control: PlayerControl, face: String): Boolean = when (control) {
        PlayerControl.SCREEN_THEME -> face in CONTROL_STYLE_FACES
        PlayerControl.QUADRANT_FLASH -> face == "classic"
        PlayerControl.TITLE_CENTERED -> face in TITLE_CENTERED_FACES
        PlayerControl.TEXT_BLOCK_ALIGN -> face in TEXT_BLOCK_ALIGN_FACES
        PlayerControl.TEXT_BLOCK_POSITION -> face in TEXT_BLOCK_POSITION_FACES
        PlayerControl.CAROUSEL_SHAPE -> face == "carousel"
        PlayerControl.NOTE_COVER_SHAPE, PlayerControl.NOTE_SHOW_COVER -> face == "note"
        PlayerControl.CHAT_COVER_SHAPE, PlayerControl.CHAT_SHOW_COVER -> face == "chat"
        PlayerControl.METADATA_COVER_SHAPE, PlayerControl.METADATA_SHOW_COVER -> face == "metadata"
        PlayerControl.SPLIT_PANEL -> face == "split"
        PlayerControl.EXPRESSIVE_SEEK -> face == "expressive"
        PlayerControl.PLAYER_CONTROLS -> face !in FIXED_TRANSPORT_FACES
        PlayerControl.INTERNAL_PROGRESS -> face in INTERNAL_PROGRESS_FACES
        PlayerControl.METADATA_GROUPS -> face == "metadata"
        else -> true
    }

    private fun choice(
            definition: PreferenceDefinition<String>,
            slot: PlayerSlot,
            control: PlayerControl
    ) = PlayerSettingSpec(
            definition.key,
            slot,
            control,
            PlayerValueSpec.Choice(definition.defaultValue))

    private fun toggle(
            definition: PreferenceDefinition<Boolean>,
            slot: PlayerSlot,
            control: PlayerControl
    ) = PlayerSettingSpec(
            definition.key,
            slot,
            control,
            PlayerValueSpec.Toggle(definition.defaultValue))

    private fun element(
            definition: PreferenceDefinition<Boolean>,
            control: PlayerControl,
            chipLabelRes: Int
    ) = PlayerSettingSpec(
            definition.key,
            PlayerSlot.ELEMENT,
            control,
            PlayerValueSpec.Toggle(definition.defaultValue),
            chipLabelRes)
}
