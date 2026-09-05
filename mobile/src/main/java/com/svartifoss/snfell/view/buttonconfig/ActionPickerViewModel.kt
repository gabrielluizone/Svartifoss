package com.svartifoss.snfell.view.buttonconfig

import android.content.Context
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.svartifoss.snfell.actions.PickerActionGroup
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.RootActionList
import com.svartifoss.snfell.common.actions.StandardActions
import com.svartifoss.snfell.view.ActivityResultReceiver
import com.matejdro.wearutils.lifecycle.SingleLiveEvent
import java.util.*
import javax.inject.Inject
import javax.inject.Named

class ActionPickerViewModel @Inject constructor(
        @Named(ARG_SHOW_NONE) showNone: Boolean,
        @Named(ARG_SURFACE) private val surface: ActionPickerSurface,
        context: Context
) : ViewModel() {
    val displayedActions = MutableLiveData<List<PhoneAction>>()
    val pageTitle = MutableLiveData<String?>()
    val selectedAction = SingleLiveEvent<PhoneAction>()
    val activityStarter = SingleLiveEvent<Intent?>()

    private data class Page(val title: String?, val actions: List<PhoneAction>)
    private val backStack = Stack<Page>()
    private var activityResultReceiver: ActivityResultReceiver? = null
    private val root = RootActionList(context, showNone)
    private var searchCatalogue: List<ActionPickerRow>? = null

    init {
        displayedActions.value = allowed(root.pickerChildren())
    }

    fun updateDisplayedActionsWithBackStack(title: String, actions: List<PhoneAction>) {
        if (displayedActions.value != null) {
            backStack.push(Page(pageTitle.value, displayedActions.value!!))
        }

        pageTitle.value = title
        displayedActions.value = allowed(actions)
    }

    fun tryGoBack() : Boolean {
        if (backStack.isEmpty()) {
            return false
        }

        val page = backStack.pop()
        pageTitle.value = page.title
        displayedActions.value = page.actions

        return true
    }

    fun startActivityForResult(intent: Intent, receiver: ActivityResultReceiver) {
        activityResultReceiver = receiver
        activityStarter.value = intent
    }

    fun onActionTapped(action: PhoneAction) {
        action.onActionPicked(this)
    }

    /**
     * Buttons use null to mean unassigned. Quick-panel slots use an explicit no-op to hide the
     * dynamically supplied default action, so the same "None" row must keep that distinction.
     */
    fun selectNoAction(noAction: PhoneAction) {
        selectedAction.value = if (surface == ActionPickerSurface.QUICK_PANEL) noAction else null
    }

    fun onActivityResultReceived(requestCode: Int, resultCode: Int, data: Intent?) {
        activityStarter.value = null

        activityResultReceiver?.onActivityResult(requestCode, resultCode, data)
        activityResultReceiver = null
        searchCatalogue = null
        // A picker may have added a streaming shortcut while a global search is visible. Emit the
        // current page again so Activity re-runs that unchanged query against the fresh catalogue.
        displayedActions.value = displayedActions.value
    }

    fun rowsFor(query: String): List<ActionPickerRow> {
        if (query.isBlank()) {
            return displayedActions.value.orEmpty().map { ActionPickerRow(it, null) }
        }

        val rows = searchCatalogue ?: buildSearchCatalogue().also { searchCatalogue = it }
        val candidates = rows.mapIndexed { index, row ->
            ActionSearchCandidate(
                    value = row,
                    title = row.action.title,
                    breadcrumb = row.breadcrumb.orEmpty(),
                    sourceOrder = index)
        }
        return ActionPickerSearch.rank(candidates, query).map { it.value }
    }

    private fun buildSearchCatalogue(): List<ActionPickerRow> {
        val rows = ArrayList<ActionPickerRow>()

        fun append(actions: List<PhoneAction>, parents: List<String>) {
            actions.forEach { action ->
                if (action is PickerActionGroup) {
                    append(action.pickerChildren(), parents + action.title)
                } else if (ActionPickerSurfacePolicy.allows(surface, action.javaClass.name)) {
                    rows.add(ActionPickerRow(
                            action = action,
                            breadcrumb = parents.takeIf { it.isNotEmpty() }
                                    ?.joinToString(BREADCRUMB_SEPARATOR)))
                }
            }
        }

        append(root.pickerChildren(), emptyList())
        return rows
    }

    private fun allowed(actions: List<PhoneAction>): List<PhoneAction> = actions.filter { action ->
        action is PickerActionGroup ||
                ActionPickerSurfacePolicy.allows(surface, action.javaClass.name)
    }

    companion object {
        const val ARG_SHOW_NONE = "ShowNone"
        const val ARG_SURFACE = "Surface"
    }
}

data class ActionPickerRow(
        val action: PhoneAction,
        val breadcrumb: String?
)

private const val BREADCRUMB_SEPARATOR = " › "

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
