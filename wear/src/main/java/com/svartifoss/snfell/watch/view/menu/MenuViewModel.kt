package com.svartifoss.snfell.watch.view.menu

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.watch.communication.CustomListWithBitmaps
import com.svartifoss.snfell.watch.communication.PhoneConnection
import com.svartifoss.snfell.watch.config.ButtonAction
import com.svartifoss.snfell.watch.config.PreferencesBus
import com.svartifoss.snfell.watch.config.WatchActionMenuProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [MenuActivity]. Only supplies the data to *display* - executing the picked entry is
 * MainActivity's job (via its own MusicViewModel), because watch-executed actions (volume,
 * search, ...) raise events like the volume popup that only MainActivity can render.
 */
@HiltViewModel
class MenuViewModel @Inject constructor(
        application: Application,
        private val phoneConnection: PhoneConnection
) : ViewModel() {

    private val actionMenuProvider =
            WatchActionMenuProvider(application, viewModelScope, phoneConnection.rawActionMenuConfig)

    /** The configurable actions-menu entries, decoded from the phone's config DataItem. */
    val actions: LiveData<List<ButtonAction>> = actionMenuProvider.config

    /** Latest custom list (playlists, search results, ...) the phone pushed. */
    val customList: LiveData<CustomListWithBitmaps> = phoneConnection.customList

    /** Dedicated, persistent cache. The generic-list source is a compatibility fallback for a
     * phone build that still responds on the old transient path. */
    val streamingShortcuts = MediatorLiveData<CustomListWithBitmaps>().apply {
        addSource(phoneConnection.streamingShortcuts) { list ->
            if (list?.listId == CustomLists.PLAYLIST_SHORTCUTS) value = list
        }
        addSource(phoneConnection.customList) { list ->
            if (list?.listId == CustomLists.PLAYLIST_SHORTCUTS) value = list
        }
    }

    val preferences: LiveData<SharedPreferences> = PreferencesBus

    /** Deletes one entry from a watch-managed deletable custom list (currently just search
     *  history) - the phone re-pushes the updated list afterwards, updating [customList]. */
    fun deleteCustomListEntry(listId: String, entryId: String) {
        viewModelScope.launch { phoneConnection.deleteCustomListItem(listId, entryId) }
    }

    /**
     * Selects an entry without closing the menu, for rows whose result is another list rather than
     * playback - currently walking into a library folder. The phone answers by pushing the next
     * page, which lands in [customList] and swaps the content in place.
     */
    fun selectCustomListEntry(listId: String, entryId: String) {
        viewModelScope.launch { phoneConnection.executeCustomMenuAction(listId, entryId) }
    }

    override fun onCleared() {
        super.onCleared()
        // Detach the provider's observeForever hook on PhoneConnection's (@Singleton) LiveData.
        actionMenuProvider.destroy()
    }
}
