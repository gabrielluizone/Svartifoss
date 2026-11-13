package com.svartifoss.snfell.view.watchface.home

/**
 * What the Watch tab is showing below the preview: Home, or one of the seven areas.
 *
 * Home is the overview a new user meets first (the themes, then the list of areas); an area is one
 * of the existing editor pages, reached from Home in one tap and left again with Back.
 */
internal sealed interface WatchTabPlace {
    data object Home : WatchTabPlace

    data class Area(val section: String) : WatchTabPlace
}

/**
 * Decides where the Watch tab opens, and how that is written into a saved-state bundle.
 *
 * Pure, so the order of the rules is pinned by a JVM test instead of being re-derived from the
 * fragment every time someone changes how the tab is entered.
 */
internal object WatchTabNavigation {

    /** The saved-state token for [WatchTabPlace.Home]; an area is saved as its section key. */
    const val HOME_TOKEN = "home"

    /**
     * The place the tab opens on.
     *
     * In order: the state Android restored (a rotation, or a process brought back) wins, because
     * the person was looking at it a moment ago; then a settings-search result, which is always an
     * area, since a result is a row on a page; then wherever this process last left the tab, so
     * switching to Controls and back does not throw away the page someone was on; and finally
     * Home. A restored or remembered area that is not one of [sections] opens Home rather than an
     * arbitrary page - that is what a page removed by an update would otherwise do.
     */
    fun resolve(
            restored: WatchTabPlace?,
            searchSection: String?,
            lastShown: WatchTabPlace?,
            sections: Collection<String>
    ): WatchTabPlace {
        if (restored != null) return restored.validIn(sections) ?: WatchTabPlace.Home
        if (searchSection != null && searchSection in sections) {
            return WatchTabPlace.Area(searchSection)
        }
        return lastShown?.validIn(sections) ?: WatchTabPlace.Home
    }

    fun encode(place: WatchTabPlace): String = when (place) {
        WatchTabPlace.Home -> HOME_TOKEN
        is WatchTabPlace.Area -> place.section
    }

    fun decode(token: String?): WatchTabPlace? = when (token) {
        null, "" -> null
        HOME_TOKEN -> WatchTabPlace.Home
        else -> WatchTabPlace.Area(token)
    }

    private fun WatchTabPlace.validIn(sections: Collection<String>): WatchTabPlace? = when (this) {
        WatchTabPlace.Home -> this
        is WatchTabPlace.Area -> takeIf { section in sections }
    }
}
