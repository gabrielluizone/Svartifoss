package com.svartifoss.snfell.watch.view

/**
 * The two rules an edge-seek drag needs beyond "where is the finger": where playback actually is
 * while the finger is somewhere else, and how the drag is abandoned.
 *
 * Pure and Android-free so both can be pinned by a JVM test - the project's usual shape for a
 * decision subtle enough to get wrong quietly. Both of these are: an overlapping cancel zone makes
 * an ordinary scrub cancel itself, and a snapped rather than interpolated return reads as the ring
 * glitching rather than as the seek being undone.
 */
object SeekDragPolicy {

    /**
     * How far in from the ring the "let go here and nothing happens" zone reaches, as a fraction of
     * the ring's radius.
     *
     * Deliberately well inside rather than "anywhere off the ring": a drag that wanders a few
     * pixels inward is a shaky finger, not a change of mind, and treating it as one would make the
     * seek fail with no explanation. Reaching the middle of the screen is unambiguous.
     */
    const val CANCEL_ZONE_RADIUS_FRACTION = 0.45f

    /**
     * The cancel zone's radius in pixels.
     *
     * Clamped so it can never come within [touchBandPx] of the ring itself - the band is where the
     * drag lives, and a zone that reached it would arm the cancel during the ordinary scrub. On a
     * ring small enough that the two would overlap the zone collapses to zero, which disables the
     * gesture rather than breaking seeking; a ring that small has no room for the affordance
     * either.
     */
    fun cancelZoneRadius(ringRadius: Float, touchBandPx: Float): Float {
        val ceiling = ringRadius - touchBandPx * 2f
        return minOf(ringRadius * CANCEL_ZONE_RADIUS_FRACTION, ceiling).coerceAtLeast(0f)
    }

    fun isInsideCancelZone(
            distanceFromCenter: Float,
            ringRadius: Float,
            touchBandPx: Float
    ): Boolean {
        val radius = cancelZoneRadius(ringRadius, touchBandPx)
        return radius > 0f && distanceFromCenter <= radius
    }

    /**
     * What the ring and the time readout show, between the finger's position and the position
     * playback is really at, as the cancel affordance reveals itself ([reveal] 0f..1f).
     *
     * Interpolated rather than switched: releasing has to be predictable, so the screen shows the
     * seek being *undone* - the arc travelling back and the clock counting back to where the track
     * is - for as long as the cancel is armed. A snap would leave the user guessing whether the
     * ring had jumped because the gesture was cancelled or because the phone had sent a new
     * position.
     */
    fun previewProgress(fingerProgress: Float, originProgress: Float, reveal: Float): Float {
        val t = reveal.coerceIn(0f, 1f)
        return fingerProgress + (originProgress - fingerProgress) * t
    }
}
