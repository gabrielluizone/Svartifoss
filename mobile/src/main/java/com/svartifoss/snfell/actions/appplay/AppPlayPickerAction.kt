package com.svartifoss.snfell.actions.appplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.PickerActionGroup

class AppPlayPickerAction : PickerActionGroup {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.start_playback)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, R.drawable.ic_apps)!!

    override fun pickerChildren(): List<PhoneAction> = getAllMusicApps(context)
                .map { AppPlayAction(context, it) }
                .sortedBy { it.title }

    companion object {
        fun getAllMusicApps(context: Context): List<ComponentName> {
            val packageManager = context.packageManager

            val targetIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
            return packageManager
                    .queryBroadcastReceivers(targetIntent, 0)
                    .map {
                        val activityInfo = it.activityInfo
                        ComponentName(activityInfo.packageName, activityInfo.name)
                    }
                    // A player may register several MEDIA_BUTTON receivers. They all represent
                    // the same launch destination in this picker, so keep one stable component
                    // instead of listing the app two or three times.
                    .distinctBy { it.packageName }
        }
    }
}
