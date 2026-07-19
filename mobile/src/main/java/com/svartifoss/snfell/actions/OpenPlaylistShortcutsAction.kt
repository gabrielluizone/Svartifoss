package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Shows the user's configured playlist shortcuts (see
 * [com.svartifoss.snfell.view.settings.PlaylistShortcutsActivity]) as a list on the
 * watch. Selecting an entry comes back as a custom-list item press whose entry id is the
 * playlist's deep link - MusicService then opens it on the phone, so e.g. a YouTube Music
 * playlist starts playing without touching the phone.
 */
class OpenPlaylistShortcutsAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_my_playlists)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_open_playlist)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<OpenPlaylistShortcutsAction> {
        override suspend fun handleAction(action: OpenPlaylistShortcutsAction) {
            val shortcuts = PlaylistShortcutStorage.load(service)
            Wearable.getDataClient(service)
                    .putDataItem(PlaylistShortcutStorage.createDataRequest(service, shortcuts))
                    .await()
        }
    }
}
