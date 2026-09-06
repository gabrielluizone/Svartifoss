package com.svartifoss.snfell.common

/**
 * Where a face's block of track text sits, and which edge it lines up on.
 *
 * Every face authors its own answer to this - Immersive grounds its text on the floor and centres
 * it, Split pins it to the left of its panel, Poster runs it across the middle - and until now that
 * answer was the only one available. The two controls here let a user move that block without
 * leaving the face they chose, which is the same argument [SplitPanelStyle] makes for Split's
 * panel: a composition nobody can adjust is not a design decision to the person wearing it, it is
 * just the way it is.
 *
 * ## Why the default is a sentinel and not a value
 *
 * [TextBlockAlign.FOLLOW] and [TextBlockPosition.FOLLOW] mean *keep what this face composed*, and
 * they are what every face resolves to until someone picks otherwise. That is the same identity
 * rule the per-element font keys follow ([WatchTypography]'s `follow`), and here it is what makes
 * the two keys safe to *store* on every face: a face nobody has adjusted is untouched, so adding
 * these controls changed nothing about any theme already saved, and a face may honour an override
 * in whatever way its composition allows without having to invent a "normal" position to return
 * to.
 *
 * What the sentinel does **not** buy is a control offered everywhere. Which faces may be moved at
 * all is [TextBlockPlacementSupport], and the two axes are asked separately - see its own doc for
 * why that had to stop being one question.
 *
 * Anything unrecognised resolves to [FOLLOW] for [PausedHoldPolicy]'s reason - the value can arrive
 * from an imported backup, a published community theme or a newer build on the other device, and
 * the honest answer to "I do not know this" is the face's own appearance, never one of the named
 * alternatives, which would make an unreadable value look like a deliberate choice.
 *
 * Pure and free of `android.*` so both fallbacks are pinned by a JVM test: the wear faces map these
 * onto Compose `Alignment`/`TextAlign`, the classic View face onto gravity and the phone preview
 * onto `Paint.Align`, and three renderers disagreeing about what an unknown value means is exactly
 * the drift `WatchPreviewParityTest` exists to catch.
 */
enum class TextBlockAlign(val preferenceValue: String) {

    /** Keep the horizontal alignment the face itself composed. */
    FOLLOW("follow"),

    /** Line the block up against the leading edge, as Split's panel text does. */
    START("start"),

    /** Centre the block horizontally. */
    CENTER("center"),

    /** Line the block up against the trailing edge. */
    END("end");

    companion object {

        val DEFAULT = FOLLOW

        fun fromPref(value: String?): TextBlockAlign =
                entries.firstOrNull { it.preferenceValue == value?.trim() } ?: DEFAULT
    }
}

/**
 * Where the block sits vertically. See [TextBlockAlign] for why [FOLLOW] is the default.
 *
 * Note what this deliberately is *not*: a free offset. A round screen's usable chord collapses
 * towards the top and bottom (see [RoundScreenText]), so an arbitrary vertical position is a
 * position at which the text may not fit. Three named anchors are three places a face can be asked
 * to put a block it already knows how to lay out.
 */
enum class TextBlockPosition(val preferenceValue: String) {

    /** Keep the vertical placement the face itself composed. */
    FOLLOW("follow"),

    /** Raise the block to the top of the screen. */
    TOP("top"),

    /** Centre the block vertically. */
    MIDDLE("middle"),

    /** Ground the block at the bottom of the screen. */
    BOTTOM("bottom");

    companion object {

        val DEFAULT = FOLLOW

        fun fromPref(value: String?): TextBlockPosition =
                entries.firstOrNull { it.preferenceValue == value?.trim() } ?: DEFAULT
    }
}

/**
 * Which faces may have their block of track text moved, asked separately per axis.
 *
 * ## Why this exists at all
 *
 * The two controls shipped gated only against Frame and Ribbon, on the reasoning that the `follow`
 * sentinel made them harmless everywhere else. That confuses two different things. The sentinel
 * guarantees a face is untouched *until somebody picks a side*; it says nothing about what happens
 * when they do. On more than half the collection what happened was one of three failures, none of
 * which looks like a setting that does not apply and all of which look like a bug:
 *
 *  - the text left the glass, because a face's side padding was tuned for the depth and the
 *    alignment its author composed and a round screen's usable chord collapses away from centre;
 *  - the text landed on the face's own fixed furniture - Vinyl's record label, Halo's rings,
 *    Spectrum's bar field, Material's transport row, Verse's lyric reel, Carousel's cover rail;
 *  - or something that is not track text moved with it: Note's cover disc, Chat's whole thread and
 *    action row, Metadata's two-column table.
 *
 * ## Why two sets and not one
 *
 * Because the two questions have different answers on nine faces. Carousel pins its artist above
 * the rail and its title below it, so both may be *aligned* and neither may be *moved*; Note's
 * sentence may be raised or grounded but aligning it drags the disc; Chat's bubbles align by
 * sender, which is not a preference. Asking one question for both axes is what forced every one of
 * those faces to be all-in or all-out.
 *
 * ## Where it is enforced
 *
 * Three consumers, and the *renderers* are two of them - deliberately, because hiding a row on the
 * phone is not enough. A value can arrive from an imported backup, a published community theme, or
 * a build of the other app that still offered it, so [resolveAlign]/[resolvePosition] are applied
 * where each side reads the preference (wear `MainActivity`, `WatchPreviewView`) rather than only
 * where the picker is drawn (`PlayerEditorModel.appliesToFace`). Anything unsupported reads back
 * as [TextBlockAlign.FOLLOW]/[TextBlockPosition.FOLLOW], which is the one answer that is always
 * safe: the face's own composition.
 *
 * The keys stay in `FaceScopedPreferences.SCOPED_KEYS` and in the community-theme vocabulary
 * regardless. Narrowing this is a *rendering* decision, not a contract change - a saved theme that
 * carries a value for a face that no longer honours it still parses and installs, it just draws
 * the face as its author composed it.
 */
object TextBlockPlacementSupport {

    /**
     * Faces whose track text may be lined up on a different edge.
     *
     * Excluded, each for a reason its own composition makes:
     *  - **chat** - a bubble's side *is* who is speaking. An alignment control on a thread would
     *    say the current track was sent by whoever the user last picked.
     *  - **note** - the cover disc and the `Artist: Title` sentence centre together as one group,
     *    so aligning the text drags the artwork with it, which is not what the row promises.
     *  - **metadata** - a right-aligned table is not a table. The label column carrying the value
     *    column is the whole face.
     *  - **verse** - the running head sits in a band the lyric reel already fills across.
     *  - **frame**, **ribbon** - title, artist and time live in independent fixed bands, so one
     *    shared choice can only ever move one of them.
     */
    val ALIGN_FACES: Set<String> = ThemeAppearance.ALLOWED_BASE_FACES -
            setOf("chat", "note", "metadata", "verse", "frame", "ribbon")

    /**
     * Faces whose track text may be raised or grounded.
     *
     * A smaller set, because a face has to have somewhere to *put* the block. Excluded on top of
     * [ALIGN_FACES]' own exclusions:
     *  - **matejdro** - its two proportional bands already fill the whole text area, so there is no
     *    slack to move into. The same argument keeps it out of
     *    `PlayerEditorModel.TITLE_CENTERED_FACES`.
     *  - **carousel** - the artist is pinned above the cover rail and the title below it. Moving
     *    them together would stack two bands the composition deliberately keeps apart, onto the
     *    rail between them.
     *  - **vinyl** - the disc is centred and its label is the lower half; grounding the text puts
     *    it on the label.
     *  - **halo** - the progress rings own the middle and lower bands.
     *  - **spectrum** - the bar field occupies the lower two-thirds and is drawn behind the text,
     *    so the failure is unreadable rather than clipped.
     *  - **material** - the centred disc and its transport row are the face.
     *  - **split** - the seam is the face. Text above the seam is not Split, it is artwork with a
     *    caption on it, and the panel colour the text contrast was resolved against is no longer
     *    behind it.
     */
    val POSITION_FACES: Set<String> = setOf(
            "classic", "expressive", "poster", "studio", "aurora", "eclipse", "immersive", "depth",
            "artist", "chat", "note")

    fun allowsAlign(face: String?): Boolean = face in ALIGN_FACES

    fun allowsPosition(face: String?): Boolean = face in POSITION_FACES

    /** The alignment this face will actually honour, given what is stored for it. */
    fun resolveAlign(face: String?, stored: TextBlockAlign): TextBlockAlign =
            if (allowsAlign(face)) stored else TextBlockAlign.FOLLOW

    /** The vertical placement this face will actually honour, given what is stored for it. */
    fun resolvePosition(face: String?, stored: TextBlockPosition): TextBlockPosition =
            if (allowsPosition(face)) stored else TextBlockPosition.FOLLOW
}
