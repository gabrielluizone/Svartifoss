package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

/**
 * Browses the playing app's own library from the watch - artists, albums, playlists, one level at
 * a time - reading the MediaBrowserService tree that Android Auto uses.
 *
 * This is the one action that lets the watch *start* something that was never saved as a shortcut
 * or guessed at by voice search. Apps without a MediaBrowserService (several popular players expose
 * none) report that as a single explanatory row rather than an empty list.
 */
class OpenLibraryAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_open_library)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_open_playlist)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<OpenLibraryAction> {
        override suspend fun handleAction(action: OpenLibraryAction) {
            service.openLibraryOnWatch()
        }
    }
}
