package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.appplay.AppPlayPickerAction

/** Ways to find or resume music without relying on a saved streaming shortcut. */
class FindMusicActionList : PickerActionGroup {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun pickerChildren(): List<PhoneAction> = listOf(
            AppPlayPickerAction(context),
            SearchAction(context),
            OpenSearchHistoryAction(context),
            OpenLibraryAction(context))

    override fun retrieveTitle(): String = context.getString(R.string.group_find_music)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_search)!!
}
