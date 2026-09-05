package com.svartifoss.snfell.view.settings

/**
 * The colours most recently applied from the shared picker, newest first.
 *
 * One list for every colour row in the app rather than one per setting, because the thing it
 * exists for is carrying a colour *between* rows - the title's colour onto the artist's, the
 * shading's onto the floor's. A per-row history would remember only what that row already is.
 *
 * Kept free of Android so the parsing can be pinned by a plain JVM test: everything subtle here is
 * in what counts as a colour. The picker only ever writes `#RRGGBB`, but a pasted value comes from
 * wherever the person copied it - a design tool writing lowercase, a CSS shorthand, an Android
 * `#AARRGGBB` literal - and refusing those would make paste look broken for values that are
 * perfectly unambiguous.
 */
internal object ColorHistory {

    /**
     * Phone-local, and deliberately outside [com.svartifoss.snfell.common.MiscPreferences.EXPORTABLE].
     *
     * Which colours this person reached for recently is a property of the editing session, not of
     * the theme: the watch never reads it, a backup restoring it onto another phone would say
     * nothing true, and a saved theme carrying it would publish a working list to the gallery.
     * Being outside that registry also keeps it out of the phone to watch snapshot, which is
     * measured against a transport budget.
     */
    const val PREFERENCE_KEY = "color_picker_recent"

    /**
     * Enough to hold the colours of one composition, short enough to stay one row on a narrow
     * dialog. A longer list would mostly be scrolled past to reach the recent end of it.
     */
    const val MAX_ENTRIES = 8

    private const val SEPARATOR = ","

    /**
     * [raw] as `#RRGGBB`, or null when it is not a colour.
     *
     * Alpha is dropped rather than rejected. Every preference behind this picker stores an opaque
     * `#RRGGBB`, so keeping the alpha of a pasted `#AARRGGBB` would be storing a value the rest of
     * the app cannot express - and the person pasting it plainly meant the colour.
     */
    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim()?.removePrefix("#")?.uppercase() ?: return null
        if (trimmed.any { it !in "0123456789ABCDEF" }) return null
        val rgb = when (trimmed.length) {
            3 -> trimmed.flatMap { listOf(it, it) }.joinToString("")
            6 -> trimmed
            8 -> trimmed.substring(2)
            else -> return null
        }
        return "#$rgb"
    }

    /** The stored list, newest first, with anything unreadable dropped rather than fatal. */
    fun parse(stored: String?): List<String> = stored
            .orEmpty()
            .split(SEPARATOR)
            .mapNotNull(::normalize)
            .distinct()
            .take(MAX_ENTRIES)

    /**
     * [stored] with [color] at the front.
     *
     * A colour already in the list *moves* rather than being added again, so re-applying the one
     * you have been using cannot push the rest of them out one at a time.
     */
    fun remember(stored: String?, color: String?): String {
        val normalized = normalize(color) ?: return encode(parse(stored))
        return encode((listOf(normalized) + parse(stored)).distinct().take(MAX_ENTRIES))
    }

    fun encode(colors: List<String>): String = colors.joinToString(SEPARATOR)
}
