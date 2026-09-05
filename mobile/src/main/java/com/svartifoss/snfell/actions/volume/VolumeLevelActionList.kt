package com.svartifoss.snfell.actions.volume

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.PickerActionGroup

class VolumeLevelActionList : PickerActionGroup {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun pickerChildren(): List<PhoneAction> = listOf(0, 25, 50, 75, 100).map {
        SetVolumePercentAction(context, it)
    }

    override fun retrieveTitle(): String = context.getString(R.string.group_volume_levels)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_volume_up)!!
}
