package com.svartifoss.snfell.view.buttonconfig

/**
 * The shape of Pick action, free of Android types so it stays JVM-testable.
 *
 * Pick action used to be a stack of pages: a root list of six categories, each opening a page of its
 * own, some opening a third page (playback speed, the installed music apps), and the streaming
 * category opening a whole other window to add a link. A person choosing an action had to know
 * which category held it before seeing it, and each step added a place to get lost in.
 *
 * It is now one page. Everything is a section on it; the few rows that used to open a sub-list
 * ([PickerItem.Group]) expand where they stand, and the categories are shortcuts along the top that
 * scroll to their section rather than doors that lead somewhere else. Nothing here navigates.
 */
internal data class PickerSection<A>(
        val id: String,
        /** The heading over the section's rows. */
        val title: String,
        /** A short form of [title] for the shortcut strip, which has room for six names, not six sentences. */
        val chipLabel: String,
        val iconRes: Int,
        val items: List<PickerItem<A>>
)

internal sealed interface PickerItem<out A> {
    /** A row that is itself the choice. */
    data class Choice<A>(val action: A) : PickerItem<A>

    /**
     * A row that expands in place to reveal [children], each a choice of its own. The children are
     * built on first use: the installed-music-apps group asks the package manager about every app
     * on the phone, and there is no reason to pay that before someone opens it.
     */
    class Group<A>(val id: String, val header: A, children: () -> List<A>) : PickerItem<A> {
        val children: List<A> by lazy(children)
    }

    /** The "Add a link" row: the way to make a new streaming shortcut without leaving the page. */
    object AddLink : PickerItem<Nothing>
}

/** What the list draws, top to bottom. */
internal sealed interface PickerRow<out A> {
    data class Header(val sectionId: String) : PickerRow<Nothing>

    /**
     * [indent] marks a child of an expanded group. [breadcrumb] is only set on a search result, where
     * the row has been lifted out of its section and needs to say where it came from.
     */
    data class Choice<A>(
            val action: A,
            val indent: Boolean = false,
            val breadcrumb: String? = null
    ) : PickerRow<A>

    data class Group<A>(val id: String, val header: A, val expanded: Boolean) : PickerRow<A>

    object AddLink : PickerRow<Nothing>
}

internal data class PickerLayout<A>(
        val rows: List<PickerRow<A>>,
        /** Index in [rows] of each section's header, parallel to [sectionIds]. */
        val sectionStarts: List<Int>,
        val sectionIds: List<String>
)

internal object ActionPickerLayout {
    private const val BREADCRUMB_SEPARATOR = " › "

    /**
     * Lays the sections out in one list. [leading] are choices that come before the first section
     * (the "None" row), which belongs to no section and so has no header of its own.
     *
     * A section with no rows is left out altogether - a category the surface does not allow, or one
     * with nothing installed to fill it - so there is neither an empty heading nor a shortcut to it.
     */
    fun <A> flatten(
            sections: List<PickerSection<A>>,
            expandedGroups: Set<String>,
            leading: List<A> = emptyList()
    ): PickerLayout<A> {
        val rows = ArrayList<PickerRow<A>>()
        val starts = ArrayList<Int>()
        val ids = ArrayList<String>()

        leading.forEach { rows.add(PickerRow.Choice(it)) }

        for (section in sections) {
            if (section.items.isEmpty()) continue
            starts.add(rows.size)
            ids.add(section.id)
            rows.add(PickerRow.Header(section.id))

            for (item in section.items) {
                when (item) {
                    is PickerItem.Choice -> rows.add(PickerRow.Choice(item.action))
                    is PickerItem.AddLink -> rows.add(PickerRow.AddLink)
                    is PickerItem.Group -> {
                        val open = item.id in expandedGroups
                        rows.add(PickerRow.Group(item.id, item.header, open))
                        if (open) item.children.forEach { rows.add(PickerRow.Choice(it, indent = true)) }
                    }
                }
            }
        }
        return PickerLayout(rows, starts, ids)
    }

    /**
     * The section a scroll position is in, for highlighting its shortcut: the last section whose
     * header is at or above [firstVisibleRow]. Rows above the first header (the "None" row) count
     * as the first section, so a shortcut is always lit. -1 only when there are no sections.
     */
    fun sectionAt(sectionStarts: List<Int>, firstVisibleRow: Int): Int {
        if (sectionStarts.isEmpty()) return -1
        var found = 0
        for (index in sectionStarts.indices) {
            if (sectionStarts[index] <= firstVisibleRow) found = index else break
        }
        return found
    }

    /**
     * Every choice on the page as a flat, ranked list for a search query - the group children too,
     * since a search is for the action and nobody typing "speed" wants to be told to open a group.
     * Each result carries where it lives ("Playback controls › Playback speed").
     */
    fun <A> searchRows(
            sections: List<PickerSection<A>>,
            titleOf: (A) -> String,
            query: String,
            leading: List<A> = emptyList()
    ): List<PickerRow.Choice<A>> {
        val candidates = ArrayList<ActionSearchCandidate<PickerRow.Choice<A>>>()

        // The "None" row belongs to no section, so it has no breadcrumb - but it is still a choice,
        // and someone typing "none" is looking for exactly it.
        leading.forEach {
            candidates.add(ActionSearchCandidate(
                    value = PickerRow.Choice(it),
                    title = titleOf(it),
                    breadcrumb = "",
                    sourceOrder = candidates.size))
        }

        fun add(action: A, breadcrumb: String) {
            candidates.add(ActionSearchCandidate(
                    value = PickerRow.Choice(action, breadcrumb = breadcrumb),
                    title = titleOf(action),
                    breadcrumb = breadcrumb,
                    sourceOrder = candidates.size))
        }

        for (section in sections) {
            for (item in section.items) {
                when (item) {
                    is PickerItem.Choice -> add(item.action, section.title)
                    is PickerItem.Group -> item.children.forEach {
                        add(it, section.title + BREADCRUMB_SEPARATOR + titleOf(item.header))
                    }
                    is PickerItem.AddLink -> Unit
                }
            }
        }
        return ActionPickerSearch.rank(candidates, query).map { it.value }
    }
}
