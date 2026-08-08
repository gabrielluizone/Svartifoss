package com.svartifoss.snfell.common

/**
 * Card outline for the Carousel face's cover rail, shared so the watch and the phone preview cut
 * the same silhouette.
 *
 * The corner radius is expressed as a *fraction of the card size* rather than in dp: the hero card
 * and its two smaller neighbours must read as the same shape, which a fixed radius would break by
 * making the smaller cards look rounder.
 */
enum class CarouselCardShape(val preferenceValue: String, val cornerFraction: Float) {
    /** The reference look: a generously rounded square. */
    ROUNDED("rounded", 0.08f),

    /** Hard corners - the most "album sleeve" of the three. */
    SQUARE("square", 0f),

    /** Softer than [ROUNDED] without becoming a circle. */
    SQUIRCLE("squircle", 0.18f),

    /** Full circle. Crops the most artwork, so it is a deliberate choice rather than the default. */
    CIRCLE("circle", 0.5f);

    companion object {
        fun fromPreference(value: String?): CarouselCardShape =
                entries.firstOrNull { it.preferenceValue == value } ?: ROUNDED
    }
}
