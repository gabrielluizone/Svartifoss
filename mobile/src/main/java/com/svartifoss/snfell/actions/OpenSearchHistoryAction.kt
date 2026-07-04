package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.MusicService
import javax.inject.Inject

/**
 * Shows recent search queries (see [com.svartifoss.snfell.music.SearchHistoryStorage]) as
 * a list on the watch. Selecting an entry re-runs that search (MusicService.playFromSearch);
 * the watch's menu can also delete individual entries directly.
 */
class OpenSearchHistoryAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_search_history)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, com.svartifoss.snfell.common.R.drawable.action_search)!!

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<OpenSearchHistoryAction> {
        override suspend fun handleAction(action: OpenSearchHistoryAction) {
            service.sendSearchHistoryToWatch()
        }
    }
}
