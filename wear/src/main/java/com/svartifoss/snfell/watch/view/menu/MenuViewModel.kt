package com.svartifoss.snfell.watch.view.menu

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    val preferences: LiveData<SharedPreferences> = PreferencesBus

    /** Deletes one entry from a watch-managed deletable custom list (currently just search
     *  history) - the phone re-pushes the updated list afterwards, updating [customList]. */
    fun deleteCustomListEntry(listId: String, entryId: String) {
        viewModelScope.launch { phoneConnection.deleteCustomListItem(listId, entryId) }
    }

    override fun onCleared() {
        super.onCleared()
        // Detach the provider's observeForever hook on PhoneConnection's (@Singleton) LiveData.
        actionMenuProvider.destroy()
    }
}
