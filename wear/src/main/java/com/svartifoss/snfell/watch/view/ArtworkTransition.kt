package com.svartifoss.snfell.watch.view

/** How a newly delivered cover should replace whatever the player is showing. */
internal enum class ArtworkTransition {
    /** Draw it straight away. Nothing to animate, or animation is not wanted here. */
    IMMEDIATE,

    /** Fade the artwork views up from transparent: there is no outgoing cover to fade *from*. */
    REVEAL,

    /** Cross-fade the outgoing cover into the new one. */
    CROSSFADE
}

/**
 * Which of the three a delivered cover gets.
 *
 * Pure because the distinction that matters here is one line deep and was wrong for as long as the
 * cross-fade has existed: it was gated on there *being* a previous cover, so every cover after the
 * first faded and the first one snapped on. That is the cold open, where it is most visible - the
 * artwork is ready as soon as its asset is decoded while the mini buttons and quadrant hints wait
 * for the button configuration and its icons, so the cover landed at full opacity a moment ahead of
 * the controls and the two pops read as the app stuttering into place.
 *
 * [samePixels] is not the same question as "is this the same object": a play/pause re-sync
 * re-delivers the current cover as a fresh `Bitmap`, and animating those made the artwork blink on
 * every pause.
 *
 * [ambient] takes everything to [IMMEDIATE] rather than being checked by the caller, so that the
 * always-on screen cannot be handed a cover part-way through a fade - it is the one place where an
 * animation is not merely unwanted but actively wrong, since ambient frames are drawn rarely and a
 * transition would simply freeze.
 */
internal fun artworkTransition(
        fadeEnabled: Boolean,
        ambient: Boolean,
        hasArtwork: Boolean,
        hadArtwork: Boolean,
        samePixels: Boolean
): ArtworkTransition = when {
    ambient || !fadeEnabled || !hasArtwork || samePixels -> ArtworkTransition.IMMEDIATE
    hadArtwork -> ArtworkTransition.CROSSFADE
    else -> ArtworkTransition.REVEAL
}
