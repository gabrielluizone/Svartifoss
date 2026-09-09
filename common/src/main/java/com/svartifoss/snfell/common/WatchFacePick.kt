package com.svartifoss.snfell.common

/**
 * The appearance metadata a watch-local face pick must leave behind.
 *
 * `null` [activeCustomThemeId] means *remove* the key rather than write an empty one.
 */
data class WatchFacePick(
        val baseFace: String,
        val activeCustomThemeId: String?,
        val customComplete: Boolean
)

/**
 * What the on-watch picker writes into its own preferences the moment an option is tapped.
 *
 * The picker writes locally *and* tells the phone, because neither half alone is enough: the local
 * write is what makes the tap feel instant, and the phone owns every synced preference so a
 * watch-only write would eventually be reverted. What the local write cannot do is *activate* a
 * theme - a theme is a complete snapshot of scoped values that only the phone holds.
 *
 * The rule this exists to pin is that **a local pick must never leave another theme active**, and
 * it applies to picking a saved theme exactly as much as to picking a built-in face. The picker
 * cleared the theme metadata only in the built-in case, on the reasoning that a theme pick should
 * not clear theme metadata - which reads sensibly and is wrong, because the metadata still names
 * the theme being switched *away from*. Since the local write also moves `wear_screen_face` to the
 * new option's base face, [ThemeAppearance.resolve] went on returning `Custom(previous theme)` and
 * a Custom context reads only the `custom_active` scope: the watch rendered the newly picked
 * layout wearing the **previous** theme's entire appearance. A colour just edited on the phone was
 * the visible case - it appeared to follow the user onto the theme they had switched to, which is
 * indistinguishable from the colours not being per-theme at all.
 *
 * Marking the snapshot incomplete is the mechanism already built for this: `resolve` refuses to
 * activate a partially synchronised theme and falls back to the base face, so the watch shows the
 * new layout's own styling for the one round trip it takes the phone to deliver the real snapshot
 * (`MESSAGE_SET_SCREEN_FACE` → `MusicService.applyScreenFaceFromWatch` → `applyProfile`). Briefly
 * honest defaults beat indefinitely wearing someone else's theme, and it is the same cost a
 * built-in pick has always paid.
 */
object WatchFacePicks {

    /**
     * @param optionBaseFace the structural face the picked option renders with - for a built-in
     *   option its own key, and for a saved theme the base face the profile was built on, which is
     *   the one part of a theme the watch can honestly render without the phone's snapshot.
     * @param isCustomTheme whether the picked option is one of the user's saved themes. It changes
     *   only whether the awaited theme is *named*; it never re-activates one.
     * @param customThemeId the picked theme's id, when [isCustomTheme].
     */
    fun localWriteFor(
            optionBaseFace: String,
            isCustomTheme: Boolean,
            customThemeId: String? = null
    ): WatchFacePick = WatchFacePick(
            baseFace = ThemeAppearance.normalizeBaseFace(optionBaseFace),
            // A theme pick records which theme is being awaited, so the watch is not left holding
            // an id for a theme nobody chose; a built-in pick has none to record. Either way the
            // snapshot is incomplete, so neither can activate.
            activeCustomThemeId = customThemeId?.trim()?.takeIf { isCustomTheme && it.isNotEmpty() },
            customComplete = false)
}
