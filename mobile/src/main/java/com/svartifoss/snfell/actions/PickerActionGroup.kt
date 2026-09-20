package com.svartifoss.snfell.actions

import android.content.Context
import android.os.PersistableBundle
import com.svartifoss.snfell.view.buttonconfig.ActionPickerViewModel

/**
 * A group of related actions in Pick action - "Playback speed", "Volume levels", the installed
 * music apps - and the top-level categories themselves.
 *
 * The picker is one page, so a group is not somewhere to navigate to: as a category it becomes a
 * section of the page, and as a sub-list it becomes a row that expands where it stands (see
 * `ActionPickerCatalogue`). Keeping the children available separately from
 * [PhoneAction.onActionPicked] lets the picker build a global search over them without duplicating
 * the group's source of truth. External choosers (Tasker) still use `opensMoreOptions` without being
 * groups, so those two concepts deliberately remain separate.
 */
abstract class PickerActionGroup : PhoneAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    final override val opensMoreOptions: Boolean
        get() = true

    abstract fun pickerChildren(): List<PhoneAction>

    /** Tapping a group opens or closes it in place. */
    final override fun onActionPicked(actionPicker: ActionPickerViewModel) {
        actionPicker.toggleGroup(javaClass.name)
    }
}
