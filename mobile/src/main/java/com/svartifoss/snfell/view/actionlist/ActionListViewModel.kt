package com.svartifoss.snfell.view.actionlist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.config.ActionConfig
import com.svartifoss.snfell.config.actionlist.ActionList
import com.svartifoss.snfell.di.LocalActivityConfig
import com.svartifoss.snfell.util.IdentifiedItem
import com.matejdro.wearutils.lifecycle.SingleLiveEvent
import javax.inject.Inject

class ActionListViewModel @Inject constructor(@param:LocalActivityConfig val actionConfig: ActionConfig) : ViewModel() {
    val actions = MutableLiveData<List<IdentifiedItem<PhoneAction>>>()
    val openActionEditor = SingleLiveEvent<Int?>()
    private var actionStore: MutableList<IdentifiedItem<PhoneAction>>

    private var lastId = 0

    private val actionListConfig: ActionList = actionConfig.getActionList()

    init {

        actionStore = ArrayList(actionListConfig.actions.map(this::itemFromPhoneAction))

        actions.value = actionStore
    }

    fun moveItem(from: Int, to: Int) {
        val item = actionStore.removeAt(from)
        actionStore.add(to, item)

        saveActions()
    }


    fun deleteAction(position: Int) {
        actionStore.removeAt(position)
        saveActions()
    }

    fun actionEditFinished(newAction: PhoneAction, editPosition: Int) {
        actionStore[editPosition].item = newAction
        saveActions()
    }

    fun editAction(position: Int) {
        openActionEditor.value = position
        openActionEditor.value = null
    }

    fun addAction(action: PhoneAction) {
        actionStore.add(itemFromPhoneAction(action))
        saveActions()
    }

    /**
     * Rebuilds the list from the shared config so external edits are picked up - e.g. renaming or
     * repointing a saved streaming shortcut updates its assigned copies (see
     * PlaylistShortcutActionSync). The snapshot taken in [init] would otherwise stay stale, and a
     * later reorder/edit here would even write it back, undoing the propagation.
     */
    fun refreshFromConfig() {
        actionStore = ArrayList(actionListConfig.actions.map(this::itemFromPhoneAction))
        actions.value = actionStore
    }

    private fun itemFromPhoneAction(action: PhoneAction): IdentifiedItem<PhoneAction>
            = IdentifiedItem(lastId++, action)

    private fun saveActions() {
        actions.value = actionStore

        actionListConfig.actions = actionStore.map(IdentifiedItem<PhoneAction>::item)
        actionListConfig.commit()
    }
}
