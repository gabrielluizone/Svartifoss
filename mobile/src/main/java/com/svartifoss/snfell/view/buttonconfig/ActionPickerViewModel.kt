package com.svartifoss.snfell.view.buttonconfig

import android.content.Context
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.RootActionList
import com.svartifoss.snfell.view.ActivityResultReceiver
import com.matejdro.wearutils.lifecycle.SingleLiveEvent
import java.util.*
import javax.inject.Inject
import javax.inject.Named

class ActionPickerViewModel @Inject constructor(@Named(ARG_SHOW_NONE) showNone: Boolean, context: Context) : ViewModel() {
    val displayedActions = MutableLiveData<List<PhoneAction>>()
    val pageTitle = MutableLiveData<String?>()
    val selectedAction = SingleLiveEvent<PhoneAction>()
    val activityStarter = SingleLiveEvent<Intent?>()

    private data class Page(val title: String?, val actions: List<PhoneAction>)
    private val backStack = Stack<Page>()
    private var activityResultReceiver: ActivityResultReceiver? = null

    init {
        RootActionList(context, showNone).onActionPicked(this)
    }

    fun updateDisplayedActionsWithBackStack(title: String, actions: List<PhoneAction>) {
        if (displayedActions.value != null) {
            backStack.push(Page(pageTitle.value, displayedActions.value!!))
        }

        pageTitle.value = title
        displayedActions.value = actions
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

    fun onActionTapped(index : Int) {
        displayedActions.value?.get(index)?.onActionPicked(this)
    }

    fun onActivityResultReceived(requestCode: Int, resultCode: Int, data: Intent?) {
        activityStarter.value = null

        activityResultReceiver?.onActivityResult(requestCode, resultCode, data)
        activityResultReceiver = null
    }

    companion object {
        const val ARG_SHOW_NONE = "ShowNone"
    }
}
