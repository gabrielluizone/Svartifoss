package com.svartifoss.snfell.common

/**
 * Which optional pieces of track information each face's awake composition actually draws.
 *
 * Every face draws a title and an artist, but two smaller pieces are not universal: the playing app's
 * mark ([MiscPreferences.WEAR_SHOW_SOURCE_ICON] and its size and opacity on the Text page) and the
 * elapsed/total readout ([MiscPreferences.WEAR_TRACK_TIME_MODE] and the Text page's track-time
 * controls). The phone offered both on every face, so on a face that has no place for them the switch,
 * the mode and the typography moved nothing at all - which a theme author cannot tell from a setting
 * that is broken, and is the sort of thing only somebody building a theme ever finds.
 *
 * The same shape as [TextBlockPlacementSupport], for the same reason: the Player page, the Text page
 * and settings search all have to agree, and a registry in `common` is what keeps them from drifting
 * apart. `FaceElementsContractTest` (wear) pins it against the faces' own source, so a face that starts
 * drawing one of these - or stops - cannot leave the phone offering the wrong rows.
 */
object FaceElements {

    /**
     * Faces with nowhere for the source app's mark. Each is a composition, not an omission:
     *  - **chat** - the avatar beside the voice bubble is the cover, and the thread has no caption row.
     *  - **note** - one `Artist: Title` sentence and a cover disc; a glyph inside a sentence reads as
     *    a typo.
     *  - **verse** - the running head over the lyric reel is a single typographic line.
     *  - **metadata** - the identity header sits above a table whose rows already say where the track
     *    comes from.
     */
    private val WITHOUT_SOURCE_ICON: Set<String> = setOf("chat", "note", "verse", "metadata")

    /**
     * Faces that draw no elapsed/total readout:
     *  - **carousel** - the covers are the content; the text under the rail is title, artist and
     *    the source badge, and nothing else competes with them.
     *  - **split** - the panel under the seam holds artist over title, sized for two lines.
     *  - **ribbon** - a portrait cover between four queue capsules; the rails show where the track
     *    sits in the queue, and the text under them is the title and the artist line.
     *  - **depth** - the cover is the whole screen with nothing competing with it; the text on its
     *    floor gradient is the title and the artist line.
     */
    private val WITHOUT_TRACK_TIME: Set<String> = setOf("carousel", "split", "ribbon", "depth")

    /** Faces whose awake composition draws the playing app's mark. */
    val SOURCE_ICON_FACES: Set<String> = ThemeAppearance.ALLOWED_BASE_FACES - WITHOUT_SOURCE_ICON

    /** Faces whose awake composition draws the elapsed/total track time. */
    val TRACK_TIME_FACES: Set<String> = ThemeAppearance.ALLOWED_BASE_FACES - WITHOUT_TRACK_TIME

    fun drawsSourceIcon(face: String?): Boolean = face in SOURCE_ICON_FACES

    fun drawsTrackTime(face: String?): Boolean = face in TRACK_TIME_FACES
}
