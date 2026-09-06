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
 * rule the per-element font keys follow ([WatchTypography]'s `follow`), and here it is load-bearing
 * twice over. It is what lets the control be offered on **all** faces rather than on a hand-kept
 * allow-list: a face whose text position is its whole identity (Chat's thread, Note's sentence,
 * Split's panel) is untouched until the user deliberately overrides it, so adding this control
 * changes nothing about any theme already saved. And it means a face may honour the override in
 * whatever way its composition allows without having to invent a "normal" position to return to.
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
