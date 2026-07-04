package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

/**
 * Opens one specific saved playlist shortcut (see
 * [com.svartifoss.snfell.music.PlaylistShortcutStorage]) directly - unlike
 * [OpenPlaylistShortcutsAction], which shows the whole list on the watch for the user to pick
 * from. Because this is a regular parameterized [PhoneAction] (the chosen playlist's name and
 * link are baked into the action bundle, Tasker-task style), it can be assigned to anything:
 * a quadrant, a swipe, a stem button, an on-screen mini button, or the actions menu.
 *
 * Created by [PlaylistShortcutPickerAction]; never appears in the picker list itself.
 */
class PlayPlaylistShortcutAction : SelectableAction {
    companion object {
        const val KEY_PLAYLIST_NAME = "PLAYLIST_NAME"
        const val KEY_PLAYLIST_LINK = "PLAYLIST_LINK"
    }

    val playlistName: String
    val link: String

    constructor(context: Context, playlistName: String, link: String) : super(context) {
        this.playlistName = playlistName
        this.link = link
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        this.playlistName = bundle.getString(KEY_PLAYLIST_NAME)!!
        this.link = bundle.getString(KEY_PLAYLIST_LINK)!!
    }

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)

        bundle.putString(KEY_PLAYLIST_NAME, playlistName)
        bundle.putString(KEY_PLAYLIST_LINK, link)
    }

    override fun retrieveTitle(): String = playlistName

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_open_playlist)!!

    override fun isEqualToAction(other: PhoneAction): Boolean {
        other as PlayPlaylistShortcutAction
        return super.isEqualToAction(other) &&
                this.playlistName == other.playlistName &&
                this.link == other.link
    }

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<PlayPlaylistShortcutAction> {
        override suspend fun handleAction(action: PlayPlaylistShortcutAction) {
            service.playDeepLink(action.link)
        }
    }
}
