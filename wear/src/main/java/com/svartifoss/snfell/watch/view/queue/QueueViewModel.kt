package com.svartifoss.snfell.watch.view.queue

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.QueuePaging
import com.svartifoss.snfell.common.SwatchInfo
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.selectPrimaryAccent
import com.svartifoss.snfell.watch.communication.CustomListWithBitmaps
import com.svartifoss.snfell.watch.communication.PhoneConnection
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.theme.selectAlbumCompanionColors
import com.svartifoss.snfell.watch.view.panel.AlbumPaletteCache
import com.svartifoss.snfell.watch.view.panel.PanelAppearanceResolver
import com.svartifoss.snfell.watch.view.panel.PanelTriad
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fallback accent used until the album art produces one - the app-wide default accent. */
const val DEFAULT_QUEUE_ACCENT: Int = WatchTheme.ACCENT_DEFAULT

/** How long a "load more" request may stay pending before the row becomes tappable again. Generous
 *  because the reply carries a page of cover art across Bluetooth, which is genuinely slow. */
private const val LOAD_MORE_TIMEOUT_MS = 12_000L

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
        @ApplicationContext context: Context,
        private val phoneConnection: PhoneConnection
) : ViewModel() {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    /** Read once: preferences are phone-owned and this screen lives for a single visit. */
    private val appearanceContext = ThemeAppearance.resolve(prefs)

    /** The cover the ground is painted from - see ScreenBackdrop. */
    val albumArt = phoneConnection.albumArt

    /** The album triad stands in for a cover that carries no colour, or none at all. */
    private val fallbackTriad = PanelTriad(
            DEFAULT_QUEUE_ACCENT,
            PanelAppearanceResolver.albumToneFallback(DEFAULT_QUEUE_ACCENT, .42f),
            PanelAppearanceResolver.albumToneFallback(DEFAULT_QUEUE_ACCENT, .68f))

    private val albumAccentSource: AlbumAccentSource = AlbumAccentSource.fromPreference(
            FaceScopedPreferences.getString(
                    prefs,
                    MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE,
                    appearanceContext))

    private var latestList: CustomListWithBitmaps? = null

    val items = MediatorLiveData<List<QueueItemUi>>().apply {
        addSource(phoneConnection.customList) { list ->
            // phoneConnection.customList is shared and may still hold an unrelated list (search
            // results, playlist shortcuts, ...) left over from a previous menu interaction. Only
            // the live queue and its recently-played fallback belong to this screen; ignore the
            // rest so the queue doesn't briefly render someone else's list before the real queue
            // arrives (the loading spinner stays until then).
            if (list != null &&
                    list.listId != CustomLists.PLAYLIST &&
                    list.listId != CustomLists.HISTORY) {
                return@addSource
            }

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
                        isPlaying = item.listItem.entryId == list.activeEntryId,
                        artwork = item.icon
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

    /** A second real quantized cover swatch for Gradient/Duotone queue rows. */
    val secondaryAccentColor = MutableLiveData(DEFAULT_QUEUE_ACCENT)
    /** Third real quantized cover swatch used only by the Prisma queue style. */
    val tertiaryAccentColor = MutableLiveData(DEFAULT_QUEUE_ACCENT)

    val accentColor = MediatorLiveData<Int>().apply {
        value = DEFAULT_QUEUE_ACCENT
        addSource(phoneConnection.albumArt) { bitmap ->
            if (bitmap == null) {
                // Still through the resolver: a chosen queue colour is a choice about the queue,
                // not about the cover, so it has to survive a track with no artwork - publishing
                // the bare fallback here is what made a custom colour vanish on those tracks.
                publishAccent(fallbackTriad)
            } else {
                deriveAccent(bitmap)
            }
        }
    }

    private fun deriveAccent(bitmap: Bitmap) {
        // The player has almost always extracted this very Bitmap already - see AlbumPaletteCache.
        AlbumPaletteCache.get(bitmap, albumAccentSource)?.let { publishAccent(it); return }
        viewModelScope.launch(Dispatchers.Default) {
            val palette = Palette.from(bitmap).generate()
            val preferredColors = listOfNotNull(
                    palette.vibrantSwatch,
                    palette.mutedSwatch,
                    palette.lightVibrantSwatch,
                    palette.darkVibrantSwatch,
                    palette.lightMutedSwatch,
                    palette.darkMutedSwatch,
                    palette.dominantSwatch
            ).map { it.rgb }.distinct()
            // The shared selector, honouring the user's album-accent choice. This screen used to
            // take the first named swatch outright, which is a fourth opinion about a question
            // AlbumAccentSelection exists to answer once: on a cover whose vibrant swatch is a
            // lens flare, the queue went red while the player stayed blue.
            val primary = selectPrimaryAccent(
                    palette.vibrantSwatch?.let { SwatchInfo(it.rgb, it.population) },
                    palette.swatches.map { SwatchInfo(it.rgb, it.population) },
                    albumAccentSource) ?: DEFAULT_QUEUE_ACCENT
            val rankedAlbumColors = palette.swatches
                    .sortedByDescending { it.population }
                    .map { it.rgb }
            // Named tonal swatches first, population-ranked raw swatches only as a fallback - see
            // MainActivity.kt's palette extraction for why.
            val companions = selectAlbumCompanionColors(primary, preferredColors + rankedAlbumColors)
            val raw = PanelTriad(
                    primary,
                    companions.secondary ?: sameHueTone(primary, .42f),
                    companions.tertiary ?: sameHueTone(primary, .68f))
            AlbumPaletteCache.put(bitmap, albumAccentSource, raw)
            publishAccent(raw)
        }
    }

    /**
     * Applies this screen's own colour settings to a raw album triad and publishes the result.
     *
     * Routed through the shared component resolver rather than published raw, which is what this
     * screen used to do: the global treatment, modifier, hue shift and Normal colour reached every
     * other surface in the app and stopped at the queue, and there was no per-screen override to
     * reach for either. [MiscPreferences.WEAR_QUEUE_COLOR_MODE] is that override.
     */
    private fun publishAccent(raw: PanelTriad) {
        val resolved = PanelAppearanceResolver.componentTriad(
                prefs,
                appearanceContext,
                MiscPreferences.WEAR_QUEUE_COLOR_MODE,
                MiscPreferences.WEAR_QUEUE_CUSTOM_COLOR,
                raw,
                DEFAULT_QUEUE_ACCENT)
        accentColor.postValue(resolved.primary)
        secondaryAccentColor.postValue(resolved.secondary)
        tertiaryAccentColor.postValue(resolved.tertiary)
    }

    /** Monochromatic covers still get two readable tones without fabricating another hue. */
    private fun sameHueTone(color: Int, lightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceIn(.25f, .82f)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * Whether the phone is holding queue entries it hasn't sent yet, so the list should offer to
     * fetch another page. False for the history fallback and the error row, which are never paged.
     */
    val canLoadMore = MediatorLiveData<Boolean>().apply {
        value = false
        addSource(items) { value = hasMorePages() }
    }

    /**
     * True when what arrived is the recently-played fallback rather than the playing app's own
     * queue - see `OpenPlaylistAction`, which sends [CustomLists.HISTORY] whenever the app exposes
     * no queue at all (SoundCloud is one such app).
     *
     * This screen renders both lists identically, so without it the fallback is indistinguishable
     * from a real queue: the rows are tracks the user *already played*, they carry no cover art
     * (the history entries are artist/title text and nothing else), and tapping one searches for
     * the track again rather than jumping the queue. All three read as "the queue is broken" when
     * the list does not say what it is.
     */
    val isHistoryFallback = MediatorLiveData<Boolean>().apply {
        value = false
        addSource(items) { value = latestList?.listId == CustomLists.HISTORY }
    }

    /** True while a "load more" request is in flight, so the row can show progress and not fire
     *  twice. Cleared by the arriving list rather than by a timer - the phone answers by replacing
     *  the whole DataItem, so a longer list *is* the completion signal. */
    val loadingMore = MediatorLiveData<Boolean>().apply {
        value = false
        addSource(items) { value = false }
    }

    /** Entries asked for in the most recent request. Grows by a page each time the user loads more,
     *  since the phone replaces the list rather than appending to it. */
    private var requestedLimit = QueuePaging.PAGE_SIZE

    private fun hasMorePages(): Boolean {
        val list = latestList ?: return false
        if (list.listId != CustomLists.PLAYLIST) return false
        return list.items.size < list.totalEntryCount
    }

    /** Asks the phone to send the current playback queue. */
    fun requestQueue() {
        requestedLimit = QueuePaging.PAGE_SIZE
        viewModelScope.launch { phoneConnection.openPlaybackQueue(requestedLimit) }
    }

    /** Asks for one more page. No-op when everything is already on screen. */
    fun loadMore() {
        if (loadingMore.value == true || !hasMorePages()) return
        val loaded = latestList?.items?.size ?: return
        requestedLimit = QueuePaging.nextLimit(loaded)
        loadingMore.value = true
        viewModelScope.launch {
            phoneConnection.openPlaybackQueue(requestedLimit)
            // The arriving list is what normally clears this, but nothing arrives at all if the
            // phone is out of range or the request is dropped. Without a floor the row would keep
            // spinning forever and, because a request in flight suppresses taps, there would be no
            // way to retry short of leaving the screen.
            delay(LOAD_MORE_TIMEOUT_MS)
            if (loadingMore.value == true) loadingMore.value = false
        }
    }

    /** Tells the phone to play the tapped queue entry. */
    fun selectItem(entryId: String) {
        val listId = latestList?.listId ?: return
        // executeCustomMenuAction is uncancellable inside, which is what lets the caller close this
        // screen in the same gesture without dropping the selection - see PhoneConnection.
        viewModelScope.launch { phoneConnection.executeCustomMenuAction(listId, entryId) }
    }
}
