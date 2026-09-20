package com.svartifoss.snfell.view.buttonconfig

import android.content.Context
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.svartifoss.snfell.actions.NullAction
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.common.actions.StandardActions
import com.svartifoss.snfell.view.ActivityResultReceiver
import com.matejdro.wearutils.lifecycle.SingleLiveEvent
import javax.inject.Inject
import javax.inject.Named

/**
 * Drives Pick action, which is a single page (see [ActionPickerLayout]): sections of rows, groups
 * that open where they stand, and a search that replaces the sections with a flat ranked list.
 * There is no back stack because there is nothing to go back to.
 */
class ActionPickerViewModel @Inject constructor(
        @Named(ARG_SHOW_NONE) showNone: Boolean,
        @Named(ARG_SURFACE) private val surface: ActionPickerSurface,
        context: Context
) : ViewModel() {
    /** What to draw. Re-emitted whenever a group opens or closes and whenever the query changes. */
    internal val screen = MutableLiveData<PickerScreen>()
    val selectedAction = SingleLiveEvent<PhoneAction>()
    val activityStarter = SingleLiveEvent<Intent?>()

    /** The "Add a link" row was tapped; the Activity owns the sheet it opens. */
    val addLinkRequested = SingleLiveEvent<Unit>()

    private val sections = ActionPickerCatalogue.sections(context, surface)
    private val none: PhoneAction? = if (showNone) NullAction(context) else null
    private val expanded = HashSet<String>()
    private var query = ""
    private var activityResultReceiver: ActivityResultReceiver? = null

    init {
        publish()
    }

    fun setQuery(newQuery: String) {
        if (newQuery == query) return
        query = newQuery
        publish()
    }

    fun toggleGroup(groupId: String) {
        if (!expanded.add(groupId)) expanded.remove(groupId)
        publish()
    }

    internal fun onRowTapped(row: PickerRow<PhoneAction>) {
        when (row) {
            is PickerRow.Choice -> row.action.onActionPicked(this)
            is PickerRow.Group -> toggleGroup(row.id)
            is PickerRow.AddLink -> addLinkRequested.value = Unit
            is PickerRow.Header -> Unit
        }
    }

    /**
     * Buttons use null to mean unassigned. Quick-panel slots use an explicit no-op to hide the
     * dynamically supplied default action, so the same "None" row must keep that distinction.
     */
    fun selectNoAction(noAction: PhoneAction) {
        selectedAction.value = if (surface == ActionPickerSurface.QUICK_PANEL) noAction else null
    }

    /** For the rows that hand off to an external chooser (Tasker), which answers by result. */
    fun startActivityForResult(intent: Intent, receiver: ActivityResultReceiver) {
        activityResultReceiver = receiver
        activityStarter.value = intent
    }

    fun onActivityResultReceived(requestCode: Int, resultCode: Int, data: Intent?) {
        activityStarter.value = null

        activityResultReceiver?.onActivityResult(requestCode, resultCode, data)
        activityResultReceiver = null
    }

    private fun publish() {
        val info = sections.map { PickerSectionInfo(it.id, it.title, it.chipLabel, it.iconRes) }
        if (query.isBlank()) {
            val layout = ActionPickerLayout.flatten(
                    sections, expanded, leading = listOfNotNull(none))
            screen.value = PickerScreen(layout, info, searching = false)
        } else {
            val results = ActionPickerLayout.searchRows(
                    sections, { it.title }, query, leading = listOfNotNull(none))
            screen.value = PickerScreen(
                    PickerLayout(results, sectionStarts = emptyList(), sectionIds = emptyList()),
                    info,
                    searching = true)
        }
    }

    companion object {
        const val ARG_SHOW_NONE = "ShowNone"
        const val ARG_SURFACE = "Surface"
    }
}

/** Heading and shortcut for one section, as the list and the shortcut strip draw them. */
internal data class PickerSectionInfo(
        val id: String,
        val title: String,
        val chipLabel: String,
        val iconRes: Int
)

internal data class PickerScreen(
        val layout: PickerLayout<PhoneAction>,
        val sections: List<PickerSectionInfo>,
        /** True while a query is showing results instead of the sections. */
        val searching: Boolean
)

enum class ActionPickerSurface {
    BUTTON,
    WATCH_MENU,
    QUICK_PANEL;

    companion object {
        fun fromExtra(value: String?): ActionPickerSurface =
                entries.firstOrNull { it.name == value } ?: BUTTON
    }
}

/** Prevents assigning a surface's "open itself" action back to that same surface. */
internal object ActionPickerSurfacePolicy {
    fun allows(surface: ActionPickerSurface, actionKey: String): Boolean = when (surface) {
        ActionPickerSurface.WATCH_MENU -> actionKey != StandardActions.ACTION_OPEN_MENU
        ActionPickerSurface.QUICK_PANEL ->
            actionKey != StandardActions.ACTION_OPEN_QUICK_ACTIONS_PANEL
        ActionPickerSurface.BUTTON -> true
    }
}
