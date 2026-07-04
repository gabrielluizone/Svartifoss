package com.svartifoss.snfell.actions

import android.content.Context
import android.os.PersistableBundle
import com.svartifoss.snfell.view.buttonconfig.ActionPickerViewModel

abstract class SelectableAction : PhoneAction {
    constructor(context : Context) : super(context)
    constructor(context : Context, bundle: PersistableBundle) : super(context, bundle)

    override fun onActionPicked(actionPicker: ActionPickerViewModel) {
        actionPicker.selectedAction.value = this
    }
}