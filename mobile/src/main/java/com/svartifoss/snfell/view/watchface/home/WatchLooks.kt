package com.svartifoss.snfell.view.watchface.home

import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.view.watchface.theme.QuickThemeSwitch
import com.svartifoss.snfell.view.watchface.theme.WatchThemeProfile
import com.svartifoss.snfell.view.watchface.theme.activeQuickThemeKey
import com.svartifoss.snfell.view.watchface.theme.listedBuiltInFaces

/** One tile of the theme strip on the Watch tab's Home. */
internal sealed interface WatchLook {

    /**
     * What [QuickThemeSwitch.apply] takes for this look, and what its thumbnail is cached under: a
     * bare face key for a built-in theme, `custom:<id>` for a saved one. The same names the quick
     * theme window and the watch's own picker use, so a theme has one name wherever it is chosen.
     */
    val key: String

    /** One built-in theme, which is one layout with the values the user has given it. */
    data class BuiltIn(val face: String) : WatchLook {
        override val key: String get() = face
    }

    /** One of the user's saved themes. */
    data class Saved(val profile: WatchThemeProfile) : WatchLook {
        override val key: String get() = QuickThemeSwitch.CUSTOM_PREFIX + profile.id
    }

    /** The way on to the community gallery, which always closes the strip. */
    data object Community : WatchLook {
        override val key: String get() = COMMUNITY_KEY
    }

    companion object {
        const val COMMUNITY_KEY = "community"
    }
}

internal data class WatchLookTile(val look: WatchLook, val applied: Boolean)

/**
 * Lays out the theme strip: the user's own themes first, then the built-in ones in registry order,
 * then one tile that opens the community gallery.
 *
 * The order the quick theme window and the Watch themes screen use, for the reason the window
 * gives: someone with saved themes is almost always switching between those. Which built-in
 * layouts appear is [listedBuiltInFaces], shared with that window.
 */
internal object WatchLooks {

    fun build(
            profiles: List<WatchThemeProfile>,
            allowedFaces: Collection<String>,
            archivedFaces: Set<String>,
            showArchived: Boolean,
            active: AppearanceContext
    ): List<WatchLookTile> {
        val looks = buildList<WatchLook> {
            profiles.forEach { add(WatchLook.Saved(it)) }
            listedBuiltInFaces(allowedFaces, archivedFaces, showArchived, active.baseFace)
                    .forEach { add(WatchLook.BuiltIn(it)) }
        }
        val activeKey = activeQuickThemeKey(active)
        // Exactly one tile is marked. A saved theme can be active with no entry in the library
        // only through a broken restore; the watch is then drawing its layout, so that layout's
        // tile is the honest stand-in, and listedBuiltInFaces always keeps the active layout.
        val appliedKey = activeKey.takeIf { key -> looks.any { it.key == key } } ?: active.baseFace
        return looks.map { WatchLookTile(it, applied = it.key == appliedKey) } +
                WatchLookTile(WatchLook.Community, applied = false)
    }
}
