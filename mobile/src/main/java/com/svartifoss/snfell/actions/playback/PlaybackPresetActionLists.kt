package com.svartifoss.snfell.actions.playback

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import android.support.v4.media.session.PlaybackStateCompat
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.PickerActionGroup

class PlaybackSpeedActionList : PickerActionGroup {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun pickerChildren(): List<PhoneAction> = SPEEDS.map {
        SetPlaybackSpeedAction(context, it)
    }

    override fun retrieveTitle(): String = context.getString(R.string.group_playback_speed)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_speed)!!

    private companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}

class PlaybackPositionActionList : PickerActionGroup {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun pickerChildren(): List<PhoneAction> = listOf(25, 50, 75).map {
        SeekToPercentAction(context, it)
    }

    override fun retrieveTitle(): String = context.getString(R.string.group_track_position)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_seek_position)!!
}

class ShuffleModeActionList : PickerActionGroup {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun pickerChildren(): List<PhoneAction> = listOf(
            SetShuffleModeAction(context, true),
            SetShuffleModeAction(context, false))

    override fun retrieveTitle(): String = context.getString(R.string.group_shuffle_mode)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_shuffle)!!
}

class RepeatModeActionList : PickerActionGroup {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun pickerChildren(): List<PhoneAction> = listOf(
            SetRepeatModeAction(context, PlaybackStateCompat.REPEAT_MODE_NONE),
            SetRepeatModeAction(context, PlaybackStateCompat.REPEAT_MODE_ALL),
            SetRepeatModeAction(context, PlaybackStateCompat.REPEAT_MODE_ONE))

    override fun retrieveTitle(): String = context.getString(R.string.group_repeat_mode)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_repeat)!!
}
