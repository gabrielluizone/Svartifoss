package com.svartifoss.snfell.common

/**
 * The appearance values that name a file on *this* device, and therefore cannot be published.
 *
 * A community theme is data, never a reference: enumerated preference keys with typed values, no
 * URL, no file path, no intent. That is the property which makes hosting other people's themes
 * tractable at all - the worst a hostile submission can do is look bad. Two settings deliberately
 * break it for the owner's benefit, because both answer a question no enumerated vocabulary can:
 *
 *  - [MiscPreferences.WEAR_FONT] and its five per-element siblings may name [USER_FONT_KEY], a
 *    typeface the user imported from their own storage.
 *  - [MiscPreferences.WEAR_ALBUM_ART_SOURCE] may name a picture or a folder of pictures the user
 *    picked from their gallery ([AlbumArtSource.CUSTOM_IMAGE], [AlbumArtSource.CUSTOM_FOLDER]).
 *
 * Neither value means anything on anybody else's phone. The bytes behind them are not in the
 * profile and never could be - a profile is a small JSON document that has to fit the phone->watch
 * transport budget, while a font is megabytes - so a published theme carrying one would install
 * successfully and then render in a font, or behind a picture, the recipient has never seen. That
 * is worse than a refusal, because nothing about it looks wrong.
 *
 * ## Why this registry exists rather than the absence of an entry in the constraints asset
 *
 * `community-theme-constraints.json` already refuses every one of these values, and that refusal is
 * the real boundary - it is re-applied by `firestore.rules` and by the trusted publisher, neither of
 * which can execute Kotlin. This object is not a second gate. It exists so the app can tell the two
 * *reasons* apart, which the asset cannot: a value the vocabulary has never heard of is usually a
 * value that should have been added to it (fifty-one fonts and two AOD styles were exactly that
 * bug, and the resulting "this saved theme cannot be submitted" sent someone hunting through
 * layouts for days), whereas these values are outside it permanently and on purpose. Saying
 * "change this setting and try again" about a deliberate design decision would send the next person
 * on the same hunt.
 *
 * It is also what lets `CommunityThemeVocabularyParityTest` keep checking both directions. That
 * sweep requires every offered value to be either accepted by the asset or hidden as archived, and
 * these are neither - they are offered, current, and permanently unpublishable. Deriving the
 * exemption from here rather than retyping it in the test is what stops the exemption from
 * silently widening into "and anything else somebody forgot".
 *
 * Pure and free of `android.*` so both halves are pinned by a JVM test.
 */
object DeviceLocalAppearance {

    /**
     * The [MiscPreferences.WEAR_FONT] catalog key meaning "the typeface I imported".
     *
     * One slot rather than a library. A per-element override ([MiscPreferences.WEAR_TITLE_FONT] and
     * its four siblings) may select it exactly like any bundled family, so the imported face can be
     * given to the title alone; they all resolve to the same file, because the question a second
     * imported font would answer - "which of my fonts" - is one a watch with no file browser cannot
     * ask, and the phone would then have to name and manage a collection to serve a control that
     * shows one line of text.
     *
     * Deliberately not `"custom"`: that word already means an explicitly-picked *colour* across
     * this codebase ([MiscPreferences.WEAR_ARTIST_COLOR_MODE] and every sibling), and a font key
     * reading `custom` beside a colour mode reading `custom` invites exactly the confusion where
     * one is migrated as the other.
     */
    const val USER_FONT_KEY: String = "user_font"

    /** True when [fontKey] names the user's own imported typeface. */
    fun isUserFont(fontKey: String?): Boolean = fontKey == USER_FONT_KEY

    /**
     * Every preference that can hold [USER_FONT_KEY].
     *
     * The global family plus the five per-element overrides. Listed rather than pattern-matched on
     * the key name so a new font control has to be added here consciously - the failure of omitting
     * it is that a theme becomes submittable while carrying a font nobody else has.
     */
    val FONT_KEYS: Set<String> = setOf(
            MiscPreferences.WEAR_FONT.key,
            MiscPreferences.WEAR_TITLE_FONT.key,
            MiscPreferences.WEAR_ARTIST_FONT.key,
            MiscPreferences.WEAR_CLOCK_FONT.key,
            MiscPreferences.WEAR_LYRICS_FONT.key,
            MiscPreferences.WEAR_TRACK_TIME_FONT.key)

    /**
     * The device-local values each preference key may hold, keyed by preference.
     *
     * Built from the two registries that own them rather than typed out, so a new device-local
     * album-art source or a new font control reaches this map by construction.
     */
    val VALUES_BY_KEY: Map<String, Set<String>> =
            FONT_KEYS.associateWith { setOf(USER_FONT_KEY) } +
                    mapOf(MiscPreferences.WEAR_ALBUM_ART_SOURCE.key to
                            AlbumArtSource.entries
                                    .filter { it.isDeviceLocal }
                                    .map { it.preferenceValue }
                                    .toSet())

    /** Every value any key may hold that makes a theme unpublishable, without the pairing. */
    val ALL_VALUES: Set<String> = VALUES_BY_KEY.values.flatten().toSet()

    /**
     * True when [key] holding [value] makes the containing theme private to this phone.
     *
     * Pairing-sensitive rather than a set membership test, for the reason the archived-option
     * exemption is: a value that is device-local for one key can be an ordinary published value for
     * another, and a set-wide check would punch a hole in the vocabulary sweep.
     */
    fun isDeviceLocal(key: String, value: String?): Boolean =
            value != null && VALUES_BY_KEY[key]?.contains(value) == true

    /**
     * The first device-local setting in [settings], or null when the theme can be published.
     *
     * Returns the pair rather than a boolean so the caller can name the control on screen; ordered
     * scans of an unordered map are fine here because a theme with two such settings has the same
     * single thing to say either way.
     */
    fun firstDeviceLocal(settings: Map<String, String?>): Pair<String, String>? {
        for ((key, values) in VALUES_BY_KEY) {
            val value = settings[key] ?: continue
            if (value in values) return key to value
        }
        return null
    }
}
