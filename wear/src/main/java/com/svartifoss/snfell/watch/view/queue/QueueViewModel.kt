package com.svartifoss.snfell.watch.view.queue

import android.graphics.Bitmap
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.svartifoss.snfell.watch.communication.CustomListWithBitmaps
import com.svartifoss.snfell.watch.communication.PhoneConnection
import com.svartifoss.snfell.watch.theme.WatchTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fallback accent used until the album art produces one - the app-wide default accent. */
const val DEFAULT_QUEUE_ACCENT: Int = WatchTheme.ACCENT_DEFAULT

/** Now-playing track shown in the queue header. */
data class NowPlaying(val title: String, val artist: String)

/**
 * Drives [QueueActivity]. Reads the playback queue + now-playing entry from [PhoneConnection]
 * (which the phone fills after [requestQueue]) and forwards a tap back as a selection. Observing
 * its LiveData (which sources [PhoneConnection.albumArt]) also keeps the phone connection alive
 * while the queue is on screen.
 */
@HiltViewModel
class QueueViewModel @Inject constructor(
        private val phoneConnection: PhoneConnection
) : ViewModel() {

    private var latestList: CustomListWithBitmaps? = null

    val items = MediatorLiveData<List<QueueItemUi>>().apply {
        addSource(phoneConnection.customList) { list ->
            latestList = list
            value = list?.items?.map { item ->
                QueueItemUi(
                        entryId = item.listItem.entryId,
                        title = item.listItem.entryTitle,
                        subtitle = if (item.listItem.hasEntrySubtitle()) {
                            item.listItem.entrySubtitle
                        } else {
                            null
                        },
                        isPlaying = item.listItem.entryId == list.activeEntryId
                )
            } ?: emptyList()
        }
    }

    // The phone re-publishes music state on every position tick, so dedupe by value here:
    // without this the header (and with it the whole QueueScreen) recomposed once a second,
    // adding avoidable jank to an in-progress scroll. Only title/artist changes matter here.
    val nowPlaying = MediatorLiveData<NowPlaying?>().apply {
        addSource(phoneConnection.musicState) { resource ->
            val state = resource?.data
            val next = if (state != null) NowPlaying(state.title, state.artist) else null
            if (next != value) value = next
        }
    }

    val accentColor = MediatorLiveData<Int>().apply {
        value = DEFAULT_QUEUE_ACCENT
        addSource(phoneConnection.albumArt) { bitmap ->
            if (bitmap == null) {
                value = DEFAULT_QUEUE_ACCENT
            } else {
                deriveAccent(bitmap)
            }
        }
    }

    private fun deriveAccent(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val palette = Palette.from(bitmap).generate()
            accentColor.postValue(
                    palette.getVibrantColor(palette.getLightMutedColor(DEFAULT_QUEUE_ACCENT))
            )
        }
    }

    /** Asks the phone to send the current playback queue. */
    fun requestQueue() {
        viewModelScope.launch { phoneConnection.openPlaybackQueue() }
    }

    /** Tells the phone to play the tapped queue entry. */
    fun selectItem(entryId: String) {
        val listId = latestList?.listId ?: return
        viewModelScope.launch { phoneConnection.executeCustomMenuAction(listId, entryId) }
    }
}
