package com.svartifoss.snfell.actions.playback

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.view.buttonconfig.ActionPickerViewModel

class PlaybackActionList : PhoneAction {
    constructor(context : Context) : super(context)
    constructor(context : Context, bundle: PersistableBundle) : super(context, bundle)

    override val opensMoreOptions: Boolean
        get() = true

    override fun onActionPicked(actionPicker: ActionPickerViewModel) {
        actionPicker.updateDisplayedActionsWithBackStack(title, listOf(
                PlayAction(context),
                PauseAction(context),
                PlayPauseToggleAction(context),
                StopAction(context),
                SkipToPrevAction(context),
                SkipToNextAction(context),
                RestartTrackAction(context),
                SkipThirtySecondsAction(context),
                ReverseThirtySecondsAction(context),
                LikeAction(context),
                ShuffleAction(context),
                RepeatAction(context),
                RepeatOneAction(context)
        ))
    }

    override fun retrieveTitle(): String {
        return context.getString(R.string.group_playback_controls)
    }

    override val defaultIcon: Drawable
        get() {
            return AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_play)!!
        }

}
