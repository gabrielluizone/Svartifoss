package com.svartifoss.snfell.view.watchface

import androidx.annotation.StringRes
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.TextBlockPlacementSupport
import com.svartifoss.snfell.common.TrackMetadataFields

/**
 * Which card a control lives in, and the order the cards run down the page.
 *
 * The Player page deliberately does not use the target rail its three siblings do. Text, Color and
 * Panel each apply one repeated set of controls to parallel things - Title and Artist, Volume and
 * Queue - so a rail that swaps the subject is the natural shape. Player has no such parallel: it
 * describes one screen, so its controls are grouped by *what they are about* instead, each group a
 * card of its own. A rail here would have been tabs holding unrelated lists.
 *
 * It grouped by *kind of widget* before: existence toggles as one field of chips, every multi-way
 * choice as a row underneath. That put the ring's switches beside the clock and the tap flash while
 * the ring's own style, layout and position mark sat several rows away with Track time between
 * them, and the chips had no room for the sentence each preference has always carried to say what
 * it does. A person tuning the progress ring now finds all of it in one place.
 *
 * [headingRes] is null for the cards that are not subjects - the identity card and the two at the
 * bottom - which the layout declares itself. The headings are aliases of strings every locale
 * already translates (see `player_section_*`), so a group costs no new translation.
 */
internal enum class PlayerSlot(@StringRes val headingRes: Int? = null) {
    /** The face and the control style: what is being edited, above everything it affects. */
    IDENTITY,

    /**
     * How this particular face is composed: cover shapes, the Split panel. Every control here
     * belongs to exactly one face, so on most faces the whole card is absent.
     */
    LAYOUT(R.string.player_section_layout),

    /** The title and artist block and the app icon beside it. */
    TRACK_INFO(R.string.player_section_track_info),

    /** The ring, seeking, the position mark and the track time. */
    PROGRESS(R.string.player_section_progress),

    /** What can be touched and how a touch is confirmed. */
    CONTROLS(R.string.player_section_controls),

    /** The wall clock, which is not part of the track at all. */
    CLOCK(R.string.player_section_clock),

    /** The Metadata face's blocks - existence toggles, kept in a field of chips of their own. */
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
    RING_STYLE,
    RING_LAYOUT,
    RING_GRADIENT,
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
         * The sentence under the row's title, for a picker whose Preference cannot supply one.
         *
         * A picker declared with `summary="%s"` reports only its current value, which the row
         * already shows on the right, so there is nothing to draw underneath. Four of them - Text
         * alignment, Text position, Split panel and Expressive seek - have a written, translated
         * description that no screen ever showed, because the value took the summary's place. The
         * rest either describe themselves through their summary already (Position mark, Track time
         * display) or are named by their own value ("Bezel edge"), and are listed in the test that
         * pins this rather than given a sentence they do not need.
         */
        @StringRes val descriptionRes: Int? = null
) {
    val persisted: Boolean get() = value !is PlayerValueSpec.Action
}

/**
 * What the two ring switches and the ring's style currently say - everything the ring's gates read.
 *
 * Kept as a value rather than read from preferences inside the model so [PlayerEditorModel.isRevealed]
 * stays pure and the gates, which used to live inline in the fragment, can be pinned by a JVM test.
 */
internal data class RingState(
        val edgeArcOn: Boolean,
        val edgeSeekOn: Boolean,
        val solid: Boolean
) {
    /** A drag reveals the ring whether or not it rests on screen, so either switch reaches it. */
    val reachable: Boolean get() = edgeArcOn || edgeSeekOn
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

    /**
     * Faces where [MiscPreferences.WEAR_PLAYER_CONTROLS_VISIBLE] hides something.
     *
     * The single copy: `WatchFacePrefsFragment.updatePlayerCapabilityVisibility` and
     * `WatchSearchTargetResolver` both read this rather than repeating it. The switch means a
     * different thing per face kind: on the View faces (Classic, Matejdro) it hides the quadrant
     * hints; on Vinyl/Poster/Studio/Halo/Aurora/Eclipse/Spectrum the face's own play/pause glyph;
     * on Chat the glyph in its voice bubble; on Artist, Frame and Ribbon the play/pause flash that
     * `CenterGestureRegion` draws when those faces hand it their `state`.
     *
     * Absent on purpose: Expressive and Material keep their central transport whatever the switch
     * says (`keepsEssentialTransport` on the watch), and Immersive, Depth, Carousel, Split, Note,
     * Verse and Metadata draw no control the switch could hide - offering it there reads as broken
     * rather than as inapplicable, the same rule Carousel's card shape and Split's panel follow.
     */
    val PLAYER_CONTROLS_FACES: Set<String> = setOf(
            "classic", "matejdro", "vinyl", "poster", "studio", "halo", "aurora", "eclipse",
            "spectrum", "chat", "artist", "frame", "ribbon")

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

    /**
     * Every row, in the order it renders *within its card*: the list is read top to bottom by
     * [specsFor], so where a row sits here is where it sits on the page.
     *
     * The order inside [PlayerSlot.PROGRESS] is deliberate. The two switches that put the ring on
     * screen come first and the rows that only mean something once it is there ([RING_DEPENDENTS])
     * follow directly beneath them, so what appears when a switch is turned on appears under it
     * rather than somewhere else on the page.
     */
    val specs: List<PlayerSettingSpec> = listOf(
            // The face leads because it is the page's subject rather than one setting among many:
            // it decides which of the controls below apply at all.
            choice(MiscPreferences.WEAR_SCREEN_FACE, PlayerSlot.IDENTITY, PlayerControl.FACE),
            choice(
                    MiscPreferences.WEAR_SCREEN_THEME,
                    PlayerSlot.IDENTITY,
                    PlayerControl.SCREEN_THEME),

            // How this one face is composed. Each control belongs to a single face, so this card
            // is absent everywhere else.
            choice(
                    MiscPreferences.WEAR_CAROUSEL_CARD_SHAPE,
                    PlayerSlot.LAYOUT,
                    PlayerControl.CAROUSEL_SHAPE),
            choice(
                    MiscPreferences.WEAR_NOTE_COVER_SHAPE,
                    PlayerSlot.LAYOUT,
                    PlayerControl.NOTE_COVER_SHAPE),
            toggle(
                    MiscPreferences.WEAR_NOTE_SHOW_COVER,
                    PlayerSlot.LAYOUT,
                    PlayerControl.NOTE_SHOW_COVER),
            choice(
                    MiscPreferences.WEAR_CHAT_COVER_SHAPE,
                    PlayerSlot.LAYOUT,
                    PlayerControl.CHAT_COVER_SHAPE),
            toggle(
                    MiscPreferences.WEAR_CHAT_SHOW_COVER,
                    PlayerSlot.LAYOUT,
                    PlayerControl.CHAT_SHOW_COVER),
            choice(
                    MiscPreferences.WEAR_METADATA_COVER_SHAPE,
                    PlayerSlot.LAYOUT,
                    PlayerControl.METADATA_COVER_SHAPE),
            toggle(
                    MiscPreferences.WEAR_METADATA_SHOW_COVER,
                    PlayerSlot.LAYOUT,
                    PlayerControl.METADATA_SHOW_COVER),
            choice(
                    MiscPreferences.WEAR_SPLIT_PANEL,
                    PlayerSlot.LAYOUT,
                    PlayerControl.SPLIT_PANEL,
                    R.string.setting_split_panel_description),

            toggle(
                    MiscPreferences.WEAR_SHOW_SOURCE_ICON,
                    PlayerSlot.TRACK_INFO,
                    PlayerControl.SOURCE_ICON),
            toggle(
                    MiscPreferences.WEAR_TITLE_CENTERED,
                    PlayerSlot.TRACK_INFO,
                    PlayerControl.TITLE_CENTERED),
            /*
             * Applies to the faces that can honour each axis - see [TextBlockPlacementSupport].
             * `follow` still protects every supported face from visual change until the user
             * picks an override.
             */
            choice(
                    MiscPreferences.WEAR_TEXT_BLOCK_ALIGN,
                    PlayerSlot.TRACK_INFO,
                    PlayerControl.TEXT_BLOCK_ALIGN,
                    R.string.setting_wear_text_block_align_description),
            choice(
                    MiscPreferences.WEAR_TEXT_BLOCK_POSITION,
                    PlayerSlot.TRACK_INFO,
                    PlayerControl.TEXT_BLOCK_POSITION,
                    R.string.setting_wear_text_block_position_description),

            toggle(
                    MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE,
                    PlayerSlot.PROGRESS,
                    PlayerControl.EDGE_PROGRESS),
            toggle(
                    MiscPreferences.WEAR_EDGE_SEEK_ENABLED,
                    PlayerSlot.PROGRESS,
                    PlayerControl.EDGE_SEEK),
            /*
             * The ring's own paint, gated on the ring - see [isRevealed]. These four are drawn on
             * the shared edge ring, so switching that ring off leaves them nothing to act on.
             *
             * Three of them lived on the Panels page's Seek tab until they were moved here. That
             * tab holds the *seek overlay*, a different surface, and the preview of these
             * correctly showed the player instead - so three of its five controls jumped to
             * another screen, which reads as a broken preview rather than as a tab holding two
             * subjects. They now sit beside the switches that turn their ring on.
             *
             * Applies to every face, and is deliberately *not* in [appliesToFace]: the ring is
             * drawn by the host rather than by a face's own composition, so no face is
             * inapplicable - but the ring itself can be switched off, and on Split, Verse, Note
             * and Chat it is off by default. That is a preference gate, not a face gate, which is
             * why [isRevealed] takes a [RingState] and `WatchSearchTargetResolver` redirects a
             * search for these rows to the switch instead. Keeping the distinction is what stops
             * [appliesToFace] from needing to read preferences and stop being pure.
             */
            choice(
                    MiscPreferences.WEAR_PROGRESS_STYLE,
                    PlayerSlot.PROGRESS,
                    PlayerControl.RING_STYLE),
            choice(
                    MiscPreferences.WEAR_PROGRESS_LAYOUT,
                    PlayerSlot.PROGRESS,
                    PlayerControl.RING_LAYOUT),
            toggle(
                    MiscPreferences.WEAR_PROGRESS_GRADIENT,
                    PlayerSlot.PROGRESS,
                    PlayerControl.RING_GRADIENT),
            choice(
                    MiscPreferences.WEAR_SEEK_MARKER,
                    PlayerSlot.PROGRESS,
                    PlayerControl.SEEK_MARKER),
            toggle(
                    MiscPreferences.WEAR_INTERNAL_PROGRESS_VISIBLE,
                    PlayerSlot.PROGRESS,
                    PlayerControl.INTERNAL_PROGRESS),
            choice(
                    MiscPreferences.WEAR_EXPRESSIVE_SEEK_MODE,
                    PlayerSlot.PROGRESS,
                    PlayerControl.EXPRESSIVE_SEEK,
                    R.string.setting_wear_expressive_seek_mode_description),
            choice(
                    MiscPreferences.WEAR_TRACK_TIME_MODE,
                    PlayerSlot.PROGRESS,
                    PlayerControl.TRACK_TIME_MODE),

            toggle(
                    MiscPreferences.WEAR_PLAYER_CONTROLS_VISIBLE,
                    PlayerSlot.CONTROLS,
                    PlayerControl.PLAYER_CONTROLS),
            toggle(
                    MiscPreferences.WEAR_QUADRANT_TAP_FLASH,
                    PlayerSlot.CONTROLS,
                    PlayerControl.QUADRANT_FLASH),

            toggle(
                    MiscPreferences.ALWAYS_SHOW_TIME,
                    PlayerSlot.CLOCK,
                    PlayerControl.ALWAYS_SHOW_TIME),

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

    /** The subject cards, in the order they run down the page. */
    val GROUPS: List<PlayerSlot> = PlayerSlot.entries.filter { it.headingRes != null }

    /**
     * The rows that only mean something once the progress ring is on screen.
     *
     * The single copy: [isRevealed] gates exactly these, and the spec list places them directly
     * beneath the ring's switches, so what turning a switch on reveals appears under it. Both are
     * pinned by a test. They are deliberately *not* indented: an indent with nothing connecting it
     * to the switch above read as a misalignment rather than as grouping.
     */
    val RING_DEPENDENTS: Set<PlayerControl> = setOf(
            PlayerControl.RING_STYLE,
            PlayerControl.RING_LAYOUT,
            PlayerControl.RING_GRADIENT,
            PlayerControl.SEEK_MARKER)

    /**
     * Whether [control] is worth showing given what the ring is currently doing.
     *
     * The gates differ and both are mirrored in `WatchSearchTargetResolver`, which must move with
     * them. The position mark needs the *resting* ring, since that is what it marks. The ring's
     * own style and layout survive on edge seek alone, because a drag reveals the ring whether or
     * not it rests on screen. And the gradient needs the solid ring on top of that: it is the one
     * style that blends the companion colours. A picker that changes nothing reads as broken, the
     * same reason the per-face rows are hidden rather than merely inert.
     */
    fun isRevealed(control: PlayerControl, ring: RingState): Boolean = when (control) {
        PlayerControl.SEEK_MARKER -> ring.edgeArcOn
        PlayerControl.RING_STYLE, PlayerControl.RING_LAYOUT -> ring.reachable
        PlayerControl.RING_GRADIENT -> ring.reachable && ring.solid
        else -> true
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

    /**
     * Cover switches the Player page offers *inside* their face's Cover shape picker, keyed by the
     * shape key: "No cover" is the picker's first entry, and choosing any shape brings the cover
     * back.
     *
     * Storage is untouched - each face still keeps a shape and a separate boolean, so a hidden
     * cover remembers the shape it returns with, and no saved or published theme changes. Only
     * the presentation moved: as a lone "Cover art" chip among the element toggles, far from the
     * shape row, the switch was not found by the people looking for it.
     */
    val COVER_VISIBILITY_BY_SHAPE: Map<String, String> = mapOf(
            MiscPreferences.WEAR_NOTE_COVER_SHAPE.key to MiscPreferences.WEAR_NOTE_SHOW_COVER.key,
            MiscPreferences.WEAR_METADATA_COVER_SHAPE.key to
                    MiscPreferences.WEAR_METADATA_SHOW_COVER.key)

    /**
     * The key of the control that answers [key] on this page - itself, except for a cover switch
     * folded into its shape picker, where a search result has to pulse the picker instead of a
     * chip that is no longer drawn.
     */
    fun editorKeyFor(key: String): String =
            COVER_VISIBILITY_BY_SHAPE.entries.firstOrNull { it.value == key }?.key ?: key

    /** The rows of [slot] this face can actually consume, in the order they should render. */
    fun visibleIn(slot: PlayerSlot, face: String): List<PlayerSettingSpec> =
            specsFor(slot).filter {
                appliesToFace(it.control, face) && it.key !in COVER_VISIBILITY_BY_SHAPE.values
            }

    /** [visibleIn], minus what the ring's current state leaves with nothing to act on. */
    fun revealedIn(slot: PlayerSlot, face: String, ring: RingState): List<PlayerSettingSpec> =
            visibleIn(slot, face).filter { isRevealed(it.control, ring) }

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
        // QUADRANT_FLASH is deliberately absent: the tap confirmation draws the action's glyph
        // inside the tap ripple, which the host paints above every face, so it applies to all of
        // them. It was once Classic-only here while search and the legacy row had already moved
        // on, which hid the row on the Player page while search still found it.
        PlayerControl.TITLE_CENTERED -> face in TITLE_CENTERED_FACES
        PlayerControl.TEXT_BLOCK_ALIGN -> face in TEXT_BLOCK_ALIGN_FACES
        PlayerControl.TEXT_BLOCK_POSITION -> face in TEXT_BLOCK_POSITION_FACES
        PlayerControl.CAROUSEL_SHAPE -> face == "carousel"
        PlayerControl.NOTE_COVER_SHAPE, PlayerControl.NOTE_SHOW_COVER -> face == "note"
        PlayerControl.CHAT_COVER_SHAPE, PlayerControl.CHAT_SHOW_COVER -> face == "chat"
        PlayerControl.METADATA_COVER_SHAPE, PlayerControl.METADATA_SHOW_COVER -> face == "metadata"
        PlayerControl.SPLIT_PANEL -> face == "split"
        PlayerControl.EXPRESSIVE_SEEK -> face == "expressive"
        PlayerControl.PLAYER_CONTROLS -> face in PLAYER_CONTROLS_FACES
        PlayerControl.INTERNAL_PROGRESS -> face in INTERNAL_PROGRESS_FACES
        PlayerControl.METADATA_GROUPS -> face == "metadata"
        else -> true
    }

    private fun choice(
            definition: PreferenceDefinition<String>,
            slot: PlayerSlot,
            control: PlayerControl,
            @StringRes descriptionRes: Int? = null
    ) = PlayerSettingSpec(
            definition.key,
            slot,
            control,
            PlayerValueSpec.Choice(definition.defaultValue),
            descriptionRes)

    private fun toggle(
            definition: PreferenceDefinition<Boolean>,
            slot: PlayerSlot,
            control: PlayerControl
    ) = PlayerSettingSpec(
            definition.key,
            slot,
            control,
            PlayerValueSpec.Toggle(definition.defaultValue))
}
