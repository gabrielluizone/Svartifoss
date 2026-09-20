package com.svartifoss.snfell.view.buttonconfig

import android.content.Context
import com.matejdro.wearutils.tasker.TaskerIntent
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.FindMusicActionList
import com.svartifoss.snfell.actions.OpenPlaylistShortcutsAction
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.PickerActionGroup
import com.svartifoss.snfell.actions.PlayPlaylistShortcutAction
import com.svartifoss.snfell.actions.StreamingShortcutActionList
import com.svartifoss.snfell.actions.WatchScreenActionList
import com.svartifoss.snfell.actions.appplay.AppPlayPickerAction
import com.svartifoss.snfell.actions.playback.PlaybackActionList
import com.svartifoss.snfell.actions.tasker.TaskerTaskPickerAction
import com.svartifoss.snfell.actions.volume.VolumeActionList
import com.svartifoss.snfell.common.R as commonR

/**
 * What Pick action offers and how it is grouped: the one place the catalogue is arranged.
 *
 * The groups themselves ([PlaybackActionList] and the rest) still own their contents; this only
 * decides which page section each belongs to and turns their sub-lists into expanders. Two rows do
 * not come from a group as they used to. The streaming section gets an "Add a link" row of its own
 * where there was a "Choose or add" row that opened another window, and the installed-music-apps
 * group moved out of "Find & play" - where it was the odd one out - to sit with Tasker, the other
 * way of reaching something outside the watch controls.
 */
internal object ActionPickerCatalogue {
    const val SECTION_PLAYBACK = "playback"
    const val SECTION_VOLUME = "volume"
    const val SECTION_SCREENS = "screens"
    const val SECTION_FIND = "find"
    const val SECTION_STREAMING = "streaming"
    const val SECTION_APPS = "apps"

    fun sections(context: Context, surface: ActionPickerSurface): List<PickerSection<PhoneAction>> {
        fun allowed(action: PhoneAction) =
                ActionPickerSurfacePolicy.allows(surface, action.javaClass.name)

        fun choice(action: PhoneAction): PickerItem<PhoneAction>? =
                if (allowed(action)) PickerItem.Choice(action) else null

        /** A group's own sub-lists (playback speed and so on) become expanders, not pages. */
        fun itemsOf(children: List<PhoneAction>): List<PickerItem<PhoneAction>> =
                children.mapNotNull { child ->
                    if (child is PickerActionGroup) {
                        PickerItem.Group(child.javaClass.name, child) {
                            child.pickerChildren().filter(::allowed)
                        }
                    } else {
                        choice(child)
                    }
                }

        val playback = PlaybackActionList(context)
        val volume = VolumeActionList(context)
        val screens = WatchScreenActionList(context)
        val find = FindMusicActionList(context)
        val streaming = StreamingShortcutActionList(context)

        // The saved links come first, then the account-wide ones ("Play liked songs"), and the row
        // that opens the whole saved list on the watch last, since it is the least specific.
        val streamingChildren = streaming.pickerChildren()
        val saved = streamingChildren.filterIsInstance<PlayPlaylistShortcutAction>()
        val showAll = streamingChildren.filterIsInstance<OpenPlaylistShortcutsAction>()
        val account = streamingChildren.filter {
            it !is PlayPlaylistShortcutAction && it !is OpenPlaylistShortcutsAction
        }

        val playWithApp = AppPlayPickerAction(context).apply {
            customTitle = context.getString(R.string.picker_play_with_app)
        }
        val appItems = ArrayList<PickerItem<PhoneAction>>()
        appItems.add(PickerItem.Group(playWithApp.javaClass.name, playWithApp) {
            playWithApp.pickerChildren().filter(::allowed)
        })
        if (TaskerIntent.getInstalledTaskerPackage(context) != null) {
            choice(TaskerTaskPickerAction(context))?.let(appItems::add)
        }

        return listOf(
                PickerSection(
                        SECTION_PLAYBACK, playback.title,
                        context.getString(R.string.picker_chip_playback),
                        commonR.drawable.action_play,
                        itemsOf(playback.pickerChildren())),
                PickerSection(
                        SECTION_VOLUME, volume.title,
                        context.getString(R.string.picker_chip_volume),
                        commonR.drawable.action_volume_up,
                        itemsOf(volume.pickerChildren())),
                PickerSection(
                        SECTION_SCREENS, screens.title,
                        context.getString(R.string.picker_chip_screens),
                        commonR.drawable.action_open_menu,
                        itemsOf(screens.pickerChildren())),
                PickerSection(
                        SECTION_FIND, find.title,
                        context.getString(R.string.picker_chip_find),
                        commonR.drawable.action_search,
                        itemsOf(find.pickerChildren().filter { it !is AppPlayPickerAction })),
                PickerSection(
                        SECTION_STREAMING, streaming.title,
                        context.getString(R.string.picker_chip_streaming),
                        commonR.drawable.action_open_playlist,
                        listOf<PickerItem<PhoneAction>>(PickerItem.AddLink) +
                                (saved + account + showAll).mapNotNull(::choice)),
                PickerSection(
                        SECTION_APPS, context.getString(R.string.picker_section_apps),
                        context.getString(R.string.picker_chip_apps),
                        R.drawable.ic_apps,
                        appItems)
        )
    }
}
