package com.svartifoss.snfell.actions.symfonium

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.PickerActionGroup

/** Actions backed by Symfonium's documented remote broadcast API. */
class SymfoniumActionList : PickerActionGroup {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun pickerChildren() = listOf(
            SymfoniumSyncAction(context),
            SymfoniumMediaStartAction(context)
    )

    override fun retrieveTitle(): String = context.getString(R.string.group_symfonium)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_play)!!
}
