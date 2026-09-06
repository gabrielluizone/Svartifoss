package com.svartifoss.snfell.common

/**
 * Where the picture behind the player comes from.
 *
 * Deliberately separate from [PlayerBackgroundStyle], which decides how that picture is *treated* -
 * blurred, squared, washed, hidden. The two are orthogonal and were briefly not: the performer's
 * photograph first shipped as a value inside the style enum, which meant choosing it forfeited
 * every treatment, and choosing a treatment forfeited the photograph. Splitting them is what lets
 * "the artist's picture, blurred" or "an online cover, squared" exist at all.
 *
 * Only the source moves; everything downstream is untouched. The host resolves one bitmap and hands
 * it to the single artwork pipeline, so the background stack, the artwork filters, the shading, the
 * accent floor and the album-accent palette all operate on whichever picture was chosen - including
 * the clock, which is therefore tinted from what is actually on screen.
 *
 * Pure and free of `android.*` so the fallback is pinned by a JVM test. That fallback matters more
 * here than in most of these enums: [LOCAL] is the only source that never leaves the device, so an
 * unreadable value - from an imported backup, a published theme or a newer build on the other
 * device - must never resolve to one that makes a network request.
 */
enum class AlbumArtSource(val preferenceValue: String) {

    /**
     * Whatever the playing app publishes, which is what every face has always drawn.
     *
     * The default, and the only value that costs nothing: no lookup, no network, no cache.
     */
    LOCAL("local"),

    /**
     * The album cover looked up online by track and artist name.
     *
     * Worth less than it sounds for most players, which publish perfectly good artwork of their
     * own - its case is the streaming clients that publish none, the same gap
     * `QueueArtworkResolver`'s remote fetch fills for queue rows.
     */
    ONLINE("online"),

    /**
     * A picture of the performing artist rather than the record.
     *
     * The source the Artist face is built around, but available to every face: what makes a face is
     * its composition, not the picture behind it.
     */
    ARTIST("artist"),

    /**
     * One picture the user chose from their own storage, behind every track.
     *
     * The first source that is not about the music at all, and the reason the axis exists as a
     * separate control: every treatment, filter, shading layer and accent floor goes on applying,
     * and the album-accent palette resolves from this picture rather than from a sleeve nobody can
     * see - so a chosen photograph tints the clock and the progress ring the way a cover would.
     *
     * Device-local: the picture is a `content://` URI on this phone, so a theme carrying this
     * source can never be published. See [DeviceLocalAppearance].
     */
    CUSTOM_IMAGE("custom_image"),

    /**
     * A folder the user chose, with one of its pictures drawn per track, at random.
     *
     * The moving half of [CUSTOM_IMAGE]. Random rather than sequential because there is no order a
     * folder of wallpapers is *in* - alphabetical would make the same few pictures the ones an
     * ordinary listening session ever reaches - and re-rolled per track rather than per session so
     * the screen keeps changing while an album plays through.
     *
     * Device-local for the same reason [CUSTOM_IMAGE] is, and more so: a tree URI names a folder
     * whose contents only this phone can enumerate.
     */
    CUSTOM_FOLDER("custom_folder");

    /**
     * True when the phone has to leave the device to resolve this source.
     *
     * Enumerated rather than "anything but [LOCAL]", which is what it used to be and what the two
     * custom sources quietly broke: they resolve from local storage, so treating them as lookups
     * would gate a picture already on the phone behind
     * [MiscPreferences.ONLINE_ARTWORK_ENABLED] - a network switch refusing a file that never
     * touches the network.
     */
    val needsLookup: Boolean get() = this == ONLINE || this == ARTIST

    /**
     * True when this source names something only this phone can resolve.
     *
     * The property [DeviceLocalAppearance] derives its album-art half from, and the reason a theme
     * using one of these is refused at submission with an explanation rather than with the
     * vocabulary's generic "the gallery can't accept this value".
     */
    val isDeviceLocal: Boolean get() = this == CUSTOM_IMAGE || this == CUSTOM_FOLDER

    /**
     * True when this source supplies a picture other than the sleeve the player published.
     *
     * The watch's only question, and deliberately its own property rather than a reuse of
     * [needsLookup]. The two coincided while every non-[LOCAL] source was a network lookup, so the
     * wear side asked "does this need a lookup?" and got the right answer by accident. The moment a
     * source resolved a *local* file that stopped being true: the phone attached the picture to
     * `CommPaths.ASSET_BACKDROP_ART`, it crossed Bluetooth, and the watch threw it away and drew
     * the cover - a background that could be chosen, previewed on the phone, and never appear on
     * the wrist.
     */
    val usesBackdropAsset: Boolean get() = this != LOCAL

    companion object {

        /**
         * The background-style value this source briefly was, before the two axes were split.
         *
         * `artist_photo` was a value of [PlayerBackgroundStyle] for one development cycle. It never
         * reached a release, but it did reach saved settings and saved local themes - and a stored
         * value the style enum no longer knows is the worst kind of leftover: the row shows the raw
         * string, the picker has nothing checked, and the renderer silently falls back, so the face
         * loses both the photograph *and* any way to say so.
         *
         * [migrate] is therefore a lossless translation rather than a reset. It says exactly what
         * the old value said, in the two settings that now say it.
         */
        const val RETIRED_STYLE_VALUE = "artist_photo"

        /**
         * The (style, source) pair a stored [style] should become, or null when it needs nothing.
         *
         * Pure and returning null for the ordinary case so a caller can write only what changed:
         * this runs over every scope of a preference file, and rewriting values that are already
         * correct would churn the phone->watch snapshot for nothing.
         */
        fun migrate(style: String?): Pair<String, AlbumArtSource>? =
                if (style?.trim() == RETIRED_STYLE_VALUE) {
                    // COVER is what `artist_photo` drew - plain, untreated artwork - so the pair
                    // reproduces the old look exactly rather than approximating it.
                    PlayerBackgroundStyle.COVER.preferenceValue to ARTIST
                } else {
                    null
                }

        val DEFAULT = LOCAL

        /**
         * Unknown values resolve to [LOCAL].
         *
         * Not merely "the safe default" in the usual sense: two of the others make a network
         * request and two more resolve a file this device may not have, so a value this build
         * cannot read must fall on the one source that needs neither. A theme from a newer version
         * naming a source that does not exist here draws the player's own cover, which is the
         * honest answer and also the quiet one.
         */
        fun fromPref(value: String?): AlbumArtSource =
                entries.firstOrNull { it.preferenceValue == value?.trim() } ?: DEFAULT
    }
}
