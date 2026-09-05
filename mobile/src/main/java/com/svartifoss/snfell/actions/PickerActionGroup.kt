package com.svartifoss.snfell.actions

import android.content.Context
import android.os.PersistableBundle
import com.svartifoss.snfell.view.buttonconfig.ActionPickerViewModel

/**
 * A navigable category in Pick action.
 *
 * Keeping the children available separately from [PhoneAction.onActionPicked] lets the picker
 * build a global search catalogue without duplicating the category's source of truth. External
 * choosers (Tasker and the streaming-shortcut editor) still use `opensMoreOptions` without being
 * groups, so those two concepts deliberately remain separate.
 */
abstract class PickerActionGroup : PhoneAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    final override val opensMoreOptions: Boolean
        get() = true

    abstract fun pickerChildren(): List<PhoneAction>

    final override fun onActionPicked(actionPicker: ActionPickerViewModel) {
        actionPicker.updateDisplayedActionsWithBackStack(title, pickerChildren())
    }
}
